(ns trail.core
  (:require [clout.core :as clout]
            [clojure.string :as string]
            [inflections.core :as inflections])
  (:refer-clojure :exclude [get]))

(defn route
  "Sugar for making a trail map"
  ([method route-map uri f]
   (assoc route-map [method uri] f))
  ([method uri f]
   (route method {} uri f)))

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
        params (clout/route-matches uri request)]
    (when params
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
  (map-vals middleware route-map))

(defn match-routes [route-map]
  "Turn a map of routes into a ring handler"
  (if (map? route-map)
    (fn [request]
      (let [not-found-fn (:not-found route-map)
            filtered-routes (filter (fn [[k v]] (or (= (first k) (:request-method request))
                                                    (= (first k) (-> request :params :_method keyword))))
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

   {[:get /resources] resources/index
    [:get /resources/:id] resources/show
    [:get /resources/new resources/new-] resources/new- ; because new is a reserved word
    [:get /resources/:id/edit resources/edit] resources/edit
    [:post /resources] resources/create
    [:put /resources/:id] resources/update- ; again update is part of clojure.core
    [:delete /resources/:id] resources/delete}"
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
         routes {:index {:method :get
                         :route (str prefix "/" n)
                         :handler (str n "/index")}
                 :new {:method :get
                       :route (str prefix "/" n "/new")
                       :handler (str n "/new-")}
                 :show {:method :get
                        :route (str prefix "/" n "/:id")
                        :handler (str n "/show")}
                 :create {:method :post
                          :route (str prefix "/" n)
                          :handler (str n "/create")}
                 :edit {:method :get
                        :route (str prefix "/" n "/:id/edit")
                        :handler (str n "/edit")}
                 :update {:method :put
                          :route (str prefix "/" n "/:id")
                          :handler (str n "/update-")}
                 :delete {:method :delete
                          :route (str prefix "/" n "/:id")
                          :handler (str n "/delete")}}
         routes (if (nil? only)
                  routes
                  (filter (fn [[k v]] (.contains filter-resources k)) routes))
         routes (if (nil? except)
                  routes
                  (filter (fn [[k v]] (not (.contains filter-resources k))) routes))
         routes (into {} (map (fn [[k v]] [[(:method v) (:route v)] (resolve (symbol (:handler v)))])
                              routes))]
     (merge route-map routes)))
  ([ks]
   (resource {} ks)))
