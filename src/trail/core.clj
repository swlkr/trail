(ns trail.core
  (:require [clojure.string :as string]
            [inflections.core :as inflections])
  (:refer-clojure :exclude [get]))

(def param-re #":([\w-_]+)")

(defn replacement [match m]
  (let [default (first match)
        k (-> match last keyword)]
    (str (clojure.core/get m k default))))

(defn route-str [s m]
  (string/replace s param-re #(replacement % m)))

(defn url-for
  "Generates a url based on http method, route syntax and params"
  [v]
  (when (and (vector? v)
             (not (empty? v))
             (every? (comp not nil?) v))
    (let [[arg1 arg2 arg3] v
          [method route params] (if (nil? arg3) ["get" arg1 arg2] [arg1 arg2 arg3])
          method (name method)
          href (route-str route params)
          method (if (= "get" method) "" (str "?_method="  method))]
      (str href method))))

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

(defn route-not-found [routes f]
  "Special route for 404s"
  (conj routes [:404 f]))

(defn not-found-route? [v]
  (= :404 (first v)))

(defn match-routes [routes]
  "Turns trail routes into a ring handler"
  (fn [request]
    (let [{:keys [request-method uri params]} request
          method (or (clojure.core/get params :_method) request-method)
          not-found-handler (-> (filter not-found-route? routes)
                                (first)
                                (last))
          routes (filter (comp not not-found-route?) routes)
          route (-> (filter #(match [method uri] %) routes)
                    (last))
          [_ route-uri handler] route
          params (route-params uri route-uri)
          handler (or handler not-found-handler (fn [_] {:status 404}))
          request (assoc request :params params)]
      (handler request))))

(defn prefix-param [s]
  (as-> (inflections/singular s) %
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
        resource-map {:index {:method :get
                              :route route-str
                              :handler (str resource-name "/index")}
                      :fresh {:method :get
                              :route (str route-str "/fresh")
                              :handler (str resource-name "/fresh")}
                      :show {:method :get
                             :route (str route-str "/:id")
                             :handler (str resource-name "/show")}
                      :create {:method :post
                               :route route-str
                               :handler (str resource-name "/create")}
                      :edit {:method :get
                             :route (str route-str "/:id/edit")
                             :handler (str resource-name "/edit")}
                      :change {:method :put
                               :route (str route-str "/:id")
                               :handler (str resource-name "/change")}
                      :delete {:method :delete
                               :route (str route-str "/:id")
                               :handler (str resource-name "/delete")}}
        resource-map (if only?
                       (into {} (filter (fn [[k _]] (.contains filter-resources k)) resource-map))
                       resource-map)
        resource-map (if except?
                       (into {} (filter (fn [[k _]] (not (.contains filter-resources k))) resource-map))
                       resource-map)
        resources (map #(resource-route % not-found-handler) (vals resource-map))]
    (vec (concat routes resources))))
