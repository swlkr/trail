(ns trail.core
  (:require [clout.core :as clout])
  (:refer-clojure :exclude [get]))

(defn route
  ([method route-map uri f]
   (assoc route-map [method uri] f))
  ([method uri f]
   (route method {} uri f)))

(def get (partial route :get))
(def post (partial route :post))
(def put (partial route :put))
(def patch (partial route :patch))
(def delete (partial route :delete))

(defn not-found [route-map f]
  (assoc route-map :not-found f))

(defn match-route [request [k v]]
  (let [uri (second k)
        params (clout/route-matches uri request)]
    (when params
      {:params params
       :key k
       :fn v})))

(defn match-routes [route-map]
  (fn [request]
    (let [not-found-fn (:not-found route-map)
          filtered-routes (filter (fn [[k v]] (= (first k) (:request-method request))) (dissoc route-map :not-found))
          matched-route (first (filter some? (map #(match-route request %) filtered-routes)))
          handler (clojure.core/get route-map (:key matched-route))
          merged-params (merge (:params request) (:params matched-route))]
      (if (not (nil? handler))
        (handler (assoc request :params merged-params))
        ((or not-found-fn
             (fn [request] {:status 404})) request)))))

(defn wrap-routes
  ([route-map middleware routes-to-wrap]
   (merge route-map (into {} (map (fn [[k v]] [k (middleware v)]) routes-to-wrap))))
  ([middleware routes-to-wrap]
   (wrap-routes {} middleware routes-to-wrap)))

(defn resource
  ([route-map k]
   (let [n (name k)
         url (str "/" n)
         index (str url)
         create (str url)
         show (str url "/:id")
         new (str url "/new")
         edit (str url "/:id/edit")
         update (str url "/:id")
         del (str url "/:id")]
      (-> route-map
          (get index (resolve (symbol (str n "/index"))))
          (get show (resolve (symbol (str n "/show"))))
          (post create (resolve (symbol (str n "/create"))))
          (get new (resolve (symbol (str n "/new!"))))
          (get edit (resolve (symbol (str n "/edit"))))
          (put update (resolve (symbol (str n "/update!"))))
          (delete del (resolve (symbol (str n "/delete")))))))
  ([k]
   (resource {} k)))

(defmacro defroutes [name & routes]
  `(def ~name (-> ~@routes)))
