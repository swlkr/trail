(ns trail.core
  (:require [clout.core :as clout]
            [clojure.string :as string]
            [inflections.core :as inflections]
            [clojure.pprint :as pprint])
  (:refer-clojure :exclude [get]))

(defn make-route-name [uri method]
  (let [ns (-> (string/replace uri #"^/" "")
               (string/replace #"/" "-")
               (string/replace #":" "")
               (string/trim))
        name (string/trim (if (keyword? method)
                            (name method)
                            method))]
    (if (= uri "/")
      :root
      (keyword name ns))))

(defn route
  "Sugar for making a trail map"
  ([method route-map uri f & n]
   (let [route-name (or (first n) (make-route-name uri method))]
     (assoc route-map [method uri route-name] f)))
  ([method uri f]
   (route method {} uri f nil)))

(def get (partial route :get))
(def post (partial route :post))
(def put (partial route :put))
(def patch (partial route :patch))
(def delete (partial route :delete))

(defn route-not-found [route-map f]
  "Special route for 404s"
  (assoc route-map :not-found f))

(defn match-route [request [k v]]
  "Wrap clout to return a map that trail can use"
  (let [uri (second k)
        method (first k)
        params (clout/route-matches uri request)]
    (when
      (and (not (nil? params))
           (= method (or
                       (-> request :params :_method keyword)
                       (-> request :request-method))))
      {:params params
       :key k
       :fn v})))

(defn map-vals [f m]
  (->> m
    (map (fn [[k v]] [k (f v)]))
    (into {})))

(defn wrap-routes
  "Wrap a certain set of routes in a function. Useful for auth."
  ([route-map middleware routes-to-wrap]
   (merge route-map (map-vals middleware routes-to-wrap)))
  ([middleware routes-to-wrap]
   (wrap-routes {} middleware routes-to-wrap)))

(defn wrap-routes-with [route-map middleware]
  "Wraps a given set of routes in a function."
  (map-vals middleware route-map))

(defn match-routes [route-map]
  "Turn a map of routes into a ring handler"
  (if (map? route-map)
    (fn [request]
      (let [not-found-fn (:not-found route-map)
            filtered-routes (filter (fn [[k v]] (or (= (first k) (-> request :params :_method keyword))
                                                    (= (first k) (:request-method request))))
                                    (dissoc route-map :not-found))
            matched-route (or (first (filter #(= (-> % first second) (:uri request)) filtered-routes))
                              (first (filter some? (map #(match-route request %) filtered-routes))))
            matched-route (if (and (not (nil? matched-route))
                                   (not (map? matched-route)))
                            {:params {}
                             :key (first matched-route)
                             :fn (second matched-route)}
                            matched-route)
            handler (clojure.core/get route-map (:key matched-route))
            merged-params (merge (:params request) (:params matched-route))]
        (if (not (nil? handler))
          (handler (assoc request :params merged-params))
          ((or not-found-fn
               (fn [request] {:status 404})) request))))
    (fn [request]
      (route-map request))))

(defn resource
  "Creates a set of seven functions that map to a conventional set of named functions.
   Generates routes that look like this:

   {[:get /resources :resources/index] :resources/index
    [:get /resources/:id :resources/show] :resources/show
    [:get /resources/new resources/new- :resources/new-] resources/new- ; because new is a reserved word
    [:get /resources/:id/edit resources/edit :resources/edit] resources/edit
    [:post /resources :resources/create] :resources/create
    [:put /resources/:id :resources/update-] :resources/update- ; again update is part of clojure.core
    [:delete /resources/:id :resources/delete] :resources/delete}"
  ([route-map & ks]
   (let [[filterer filter-resources] (take-last 2 ks)
         only (if (and (= :only filterer)
                       (vector? filter-resources)
                       (not (empty? filter-resources)))
                filterer
                nil)
         except (if (and (= :except filterer)
                         (vector? filter-resources)
                         (not (empty? filter-resources)))
                  filterer
                  nil)
         rest (if (and (nil? only)
                       (nil? except))
                (drop-last ks)
                (drop-last 3 ks))
         n (if (and (nil? only)
                    (nil? except))
             (name (last ks))
             (name (last (drop-last 2 ks))))
         prefix (->> (map name rest)
                     (map (fn [x] [x (str ":" (inflections/singular x) "-id")]))
                     (flatten)
                     (clojure.string/join "/"))
         prefix (when (not (empty? prefix)) (str "/" prefix))
         name-prefix (->> (map name rest)
                          (clojure.string/join "-"))
         name-prefix (when (not (empty? name-prefix)) (str name-prefix "-"))
         routes {:index {:method :get
                         :route (str prefix "/" n)
                         :handler (str n "/index")
                         :name (keyword (str name-prefix n))}
                 :new {:method :get
                       :route (str prefix "/" n "/new")
                       :handler (str n "/new-")
                       :name (keyword (str name-prefix n) "new-")}
                 :show {:method :get
                        :route (str prefix "/" n "/:id")
                        :handler (str n "/show")
                        :name (keyword (str name-prefix (inflections/singular n)))}
                 :create {:method :post
                          :route (str prefix "/" n)
                          :handler (str n "/create")
                          :name (keyword (str name-prefix n) "create")}
                 :edit {:method :get
                        :route (str prefix "/" n "/:id/edit")
                        :handler (str n "/edit")
                        :name (keyword (str name-prefix n) "edit")}
                 :update {:method :put
                          :route (str prefix "/" n "/:id")
                          :handler (str n "/update-")
                          :name (keyword (str name-prefix n) "update-")}
                 :delete {:method :delete
                          :route (str prefix "/" n "/:id")
                          :handler (str n "/delete")
                          :name (keyword (str name-prefix n) "delete")}}
         routes (if (nil? only)
                  routes
                  (filter (fn [[k v]] (.contains filter-resources k)) routes))
         routes (if (nil? except)
                  routes
                  (filter (fn [[k v]] (not (.contains filter-resources k))) routes))
         routes (into {} (map (fn [[k v]] [[(:method v) (:route v) (:name v)] (resolve (symbol (:handler v)))])
                              routes))]
     (merge route-map routes)))
  ([ks]
   (resource {} ks)))

(defn map-replace [m s]
  (reduce
    (fn [acc [k v]] (string/replace acc (str k) (str v)))
    s m))

(defn url-for
  ([routes route-name m]
   (let [route (->> (dissoc routes :not-found)
                    (filter (fn [[k v]] (= (last k) route-name)))
                    (first))]
     (if (not (nil? route))
       (->> route
            (first)
            (second)
            (map-replace m)))))
  ([routes route-name]
   (url-for routes route-name {})))

(defn pretty-print-route [[k v]]
  (let [method (-> k first name string/upper-case)
        route (second k)
        n (last k)]
    (str method " " route " => " n)))

(defn route-names [routes]
  (let [routes (dissoc routes :not-found)]
    (map pretty-print-route routes)))
