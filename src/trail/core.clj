(ns trail.core
  (:require [clout.core :as clout]))


(def route-map (atom {}))


(defn not-nil? [v]
  (not (nil? v)))


(defn route [{:keys [uri method fn]}]
  (when
    (and (every? not-nil? [uri method fn])
         (contains? #{:get :put :post :delete} method))
    (let [compiled-route (clout/route-compile uri)]
      (swap! route-map update-in [method] assoc uri {:fn fn
                                                     :compiled-route compiled-route}))))


(defn match-route [route-data request]
  (let [compiled-route (:compiled-route route-data)
        route-params (clout/route-matches compiled-route request)]
    (when (not-nil? route-params)
      {:fn (:fn route-data)
       :route-params route-params})))


(defn route-matches [routes request]
  (let [request-method (:request-method request)
        uri (:uri request)
        route-map (get routes request-method)]
    (when (every? not-nil? [request-method uri routes])
      (first (filter not-nil? (map (fn [[k v]] (match-route v request)) route-map))))))


(defn match [request]
  (route-matches @route-map request))


(defn routes [request]
  (let [match (match request)
        f (:fn match)
        route-params (:route-params match)
        params (merge route-params (:params request))]
    (when (not (nil? f))
      (f (assoc request :route-params route-params
                        :params params)))))

