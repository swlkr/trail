(ns trail.core
  (:require [clojure.string :as string]
            [word.core :as word]
            [clojure.edn :as edn])
  (:refer-clojure :exclude [get]))

(def param-re #":([\w-_]+)")

(defn replacement [match m]
  (let [default (first match)
        k (-> match last keyword)]
    (str (clojure.core/get m k default))))

(defn route-str [s m]
  (string/replace s param-re #(replacement % m)))

(def verbs [:get :post :put :patch :delete])

(defn in? [val coll]
  (not= -1 (.indexOf coll val)))

(defn verb? [value]
  (in? value verbs))

(defn method-verb? [value]
  (let [method-verbs (-> (drop 1 verbs)
                         (vec))]
    (in? value method-verbs)))

(defn param-method [method]
  (when (method-verb? method)
    (str "?_method=" (name (or method "")))))

(defn uri-for
  "Generates a uri based on method, route syntax and params"
  [v]
  (when (and (vector? v)
             (not (empty? v))
             (every? (comp not nil?) v))
    (let [[arg1 arg2 arg3] v
          [_ route params] (if (not (verb? arg1))
                              [:get arg1 arg2]
                              [arg1 arg2 arg3])]
      (route-str route params))))

(defn url-for
  "Generates a url based on http method, route syntax and params"
  [v]
  (let [uri (uri-for v)
        [method] v]
    (str uri (param-method method))))

(defn action-for
  "Generates a form action based on http method"
  [v]
  (str "" (uri-for v)))

(defn route-params [req-uri route-uri]
  (when (and (some? req-uri)
             (some? route-uri))
    (let [req-seq (string/split req-uri #"/")
          route-seq (string/split route-uri #"/")]
      (when (= (count req-seq) (count route-seq))
        (let [idxs (filter #(not= (nth req-seq %) (nth route-seq %)) (range (count req-seq)))
              req-vals (map #(nth req-seq %) idxs)
              route-vals (->> (map #(nth route-seq %) idxs)
                              (filter #(string/starts-with? % ":"))
                              (map #(string/replace % #":" ""))
                              (map keyword))]
          (zipmap route-vals req-vals))))))

(defn match [request-route route]
  (when (and (vector? request-route)
             (vector? route)
             (every? some? request-route)
             (every? some? route))
    (let [[request-method request-uri] request-route
          [route-method route-uri] route
          params (route-params request-uri route-uri)]
      (and (= request-method route-method)
           (= request-uri (route-str route-uri params))))))

(defn route
  "Sugar for making a trail vector"
  ([method routes uri f]
   (conj routes [method uri f]))
  ([method uri f]
   (route method [] uri f)))

(def get (partial route :get))
(def post (partial route :post))
(def put (partial route :put))
(def patch (partial route :patch))
(def delete (partial route :delete))

(defn wrap-route-with [route middleware]
  "Wraps a single route in a ring middleware fn"
  (let [[method route f] route]
    [method route (middleware f)]))

(defn wrap-routes-with [routes middleware]
  "Wraps a given set of routes in a function."
  (map #(wrap-route-with % middleware) routes))

(defn map-vals [f m]
  (->> m
       (map (fn [[k v]] [k (f v)]))
       (into {})))

(defn coerce-params [val]
  (let [val (if (vector? val) (last val) val)]
    (cond
      (some? (re-find #"^\d+\.?\d*$" val)) (edn/read-string val)
      (and (empty? val) (string? val)) (edn/read-string val)
      (and (string? val) (= val "false")) false
      (and (string? val) (= val "true")) true
      :else val)))

(defn wrap-coerce-params [handler]
  "Coerces integers and uuid values in params"
  (fn [request]
    (let [{:keys [params]} request
          request (assoc request :params (map-vals coerce-params params))]
      (handler request))))

(defn route-not-found [routes f]
  "Special route for 404s"
  (conj routes [:404 f]))

(defn not-found-route? [v]
  (= :404 (first v)))

(defn match-routes [routes]
  "Turns trail routes into a ring handler"
  (fn [request]
    (let [{:keys [request-method uri params]} request
          method (or (-> params :_method keyword) request-method)
          not-found-handler (-> (filter not-found-route? routes)
                                (first)
                                (last))
          routes (filter (comp not not-found-route?) routes)
          route (-> (filter #(match [method uri] %) routes)
                    (first))
          [_ route-uri handler] route
          trail-params (route-params uri route-uri)
          params (merge params trail-params)
          handler (or handler not-found-handler (fn [_] {:status 404}))
          params (map-vals coerce-params params)
          request (assoc request :params params)]
      (handler request))))

(defn wrap-match-routes [arg]
  (if (fn? arg)
    (fn [request]
      (arg request))
    (match-routes arg)))

(defn prefix-param [s]
  (as-> (word/singular s) %
        (str  % "-id")))

(defn resource-route [m not-found-handler]
  (let [{:keys [method route handler]} m
        handler (or (-> (symbol handler) (resolve))
                    not-found-handler
                    (fn [_] {:status 404}))]
    [method route handler]))

(defn resource
  "Creates a set of seven functions that map to a conventional set of named functions.
   Generates routes that look like this:

   [[:get    '/resources          resources/index]
    [:get    '/resources/:id      resources/show]
    [:get    '/resources/fresh    resources/fresh] ; this is 'fresh' not 'new' because new is reserved
    [:get    '/resources/:id/edit resources/edit]
    [:post   '/resources          resources/create]
    [:put    '/resources/:id      resources/change] ; this is 'change' not 'update' because update is in clojure.core
    [:delete '/resources/:id      resources/delete]]

   Examples:

   (resource :items)
   (resource :items :only [:create :delete])
   (resource :items :sub-items :only [:index :create])
   (resource :items :except [:index])
   "
  [routes & ks]
  (let [ks (if (not (vector? routes))
             (apply conj [routes] ks)
             (vec ks))
        routes (if (vector? routes)
                routes
                [])
        not-found-handler (-> (filter not-found-route? routes)
                              (first)
                              (last))
        only? (and (not (empty? (filter #(= % :only) ks)))
                   (vector? (last ks))
                   (not (empty? (last ks))))
        except? (and (not (empty? (filter #(= % :except) ks)))
                     (vector? (last ks))
                     (not (empty? (last ks))))
        filter-resources (when (or only? except?) (last ks))
        route-ks (if (or only? except?)
                   (vec (take (- (count ks) 2) ks))
                   ks)
        resource-ks (take-last 1 route-ks)
        resource-names (map name resource-ks)
        resource-name (first resource-names)
        prefix-ks (drop-last route-ks)
        route-str (as-> (map str prefix-ks) %
                        (map prefix-param %)
                        (concat '("") (interleave (map name prefix-ks) %) resource-names)
                        (string/join "/" %))
        resources [{:method :get
                    :route route-str
                    :handler (str resource-name "/index")
                    :name :index}
                   {:method :get
                    :route (str route-str "/fresh")
                    :handler (str resource-name "/fresh")
                    :name :fresh}
                   {:method :get
                    :route (str route-str "/:id")
                    :handler (str resource-name "/show")
                    :name :show}
                   {:method :post
                    :route route-str
                    :handler (str resource-name "/create")
                    :name :create}
                   {:method :get
                    :route (str route-str "/:id/edit")
                    :handler (str resource-name "/edit")
                    :name :edit}
                   {:method :put
                    :route (str route-str "/:id")
                    :handler (str resource-name "/change")
                    :name :change}
                   {:method :delete
                    :route (str route-str "/:id")
                    :handler (str resource-name "/delete")
                    :name :delete}]
        resources (if only?
                   (filter #(not= -1 (.indexOf filter-resources (clojure.core/get % :name))) resources)
                   resources)
        resources (if except?
                   (filter #(= -1 (.indexOf filter-resources (clojure.core/get % :name))) resources)
                   resources)
        resources (map #(resource-route % not-found-handler) resources)]
    (vec (concat routes resources))))
