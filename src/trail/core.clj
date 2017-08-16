(ns trail.core)

(def routes (atom {}))

(defn route [{:keys [uri method fn]}]
  (when
    (and (every? #(not (nil? %)) [uri method fn])
         (contains? #{:get :put :post :delete} method)
      (let [segments (filter #(not (empty? %)) (clojure.string/split uri #"/"))]
        (swap! routes update-in (list segments) assoc method fn)))))

(defn seg-matches? [route-seg uri-seg]
  (and (= (first route-seg) (first uri-seg))
       (= (count route-seg) (count uri-seg))))

(defn seg? [str]
  (not (empty? str)))

(defn segments [uri]
  (when uri
    (filter seg? (clojure.string/split uri #"/"))))

(defn route-match-map [route-map uri]
    (let [segs (segments uri)
          routes (filter #(seg-matches? segs %) (keys route-map))]
      (first (map #(zipmap % segs) routes))))

(defn route-params [match-map]
  (let [ks (keys match-map)
        param-keys (filter #(clojure.string/starts-with? % ":") ks)
        param-str-map (select-keys match-map param-keys)]
    (into {} (map (fn [[k v]] [(keyword (subs k 1)) v]) param-str-map))))

(defn get-fn [request-method route-map match-map]
  (let [k (keys match-map)
        fns (get route-map k)]
    (get fns request-method)))

(defn match-route [{:keys [request-method uri]}]
  (when (every? #(not (nil? %)) [request-method uri])
    (let [route-map @routes
          match-map (route-match-map route-map uri)
          params (route-params match-map)
          f (get-fn request-method route-map match-map)]
      (if (nil? f)
        nil
        {:fn f :params params}))))
