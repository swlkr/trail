(ns trail.core-test
  (:require [clojure.test :refer :all]
            [trail.core :as trail]))

(deftest match-routes-test
  (testing "empty vector"
    (let [routes []
          handler (trail/match-routes [])]
      (is (= {:status 404} (handler {})))))

  (testing "nil routes"
    (let [handler (trail/match-routes nil)]
      (is (= {:status 404} (handler {})))))

  (testing "nil route vector"
    (let [handler (trail/match-routes [[nil nil nil]])]
      (is (= {:status 404} (handler {})))))

  (testing "valid route vector"
    (let [users-index (fn [r] "/users")
          routes [[:get "/users" users-index]]
          app (trail/match-routes routes)]
      (is (= "/users" (app {:request-method :get
                            :uri "/users"})))))

  (testing "not found middleware"
    (let [users-show (fn [r])
          routes (-> (trail/get "/users/:id" (fn [r]))
                     (trail/route-not-found (fn [r] {:status 404
                                                     :body "not found"})))
          app (trail/match-routes routes)]
      (is (= {:status 404
              :body "not found"} (app {:request-method :get
                                       :uri "/users"})))))
  (testing "route with params"
    (let [users-show (fn [r] (str "/users/" (get-in r [:params :id])))
          routes (-> (trail/get "/users/:id" users-show))
          app (trail/match-routes routes)]
      (is (= "/users/123" (app {:request-method :get
                                :uri "/users/123"})))))

  (testing "one url to n functions without parameters"
    (let [one (fn [request] "one")
          two (fn [request] "two")
          routes (-> (trail/get "/same" one)
                     (trail/get "/same" two))
          app (trail/match-routes routes)]
      (is (= "one" (app {:request-method :get
                         :uri "/same"})))))

  (testing "one url to n functions with parameters"
    (let [one (fn [request] "one")
          two (fn [request] (-> request :params :hello))
          routes (-> (trail/get "/same/:hello" two)
                     (trail/get "/same/:id" one))
          app (trail/match-routes routes)]
      (is (= "sean" (app {:request-method :get
                          :uri "/same/sean"})))))

  (testing "one url to n functions without parameters"
    (let [user (fn [r] (-> r :params :id))
          admin (fn [r] (-> r :params :id))
          routes (-> (trail/get "/users/admin" admin)
                     (trail/get "/users/:id" user))
          app (trail/match-routes routes)]
      (is (= nil (app {:request-method :get
                       :uri "/users/admin"})))))

  (testing "one url to n functions with/without parameters"
    (let [user (fn [r] (-> r :params :id))
          admin (fn [r] (-> r :params :id))
          routes (-> (trail/get "/users/:id" user)
                     (trail/get "/users/admin" admin))
          app (trail/match-routes routes)]
      (is (= "123" (app {:request-method :get
                         :uri "/users/123"})))))

  (testing "url without parameter comes first"
    (let [user (fn [r] (-> r :params :id))
          admin (fn [r] (-> r :params :id))
          routes (-> (trail/get "/users/admin" admin)
                     (trail/get "/users/:id" user))
          app (trail/match-routes routes)]
      (is (= "123" (app {:request-method :get
                         :uri "/users/123"}))))))

(deftest url-for-test
  (testing "nil"
    (is (= nil (trail/url-for nil))))

  (testing "empty vector"
    (is (= nil (trail/url-for []))))

  (testing "vector with nils"
    (is (= nil (trail/url-for [nil nil nil]))))

  (testing "valid url-for"
    (is (= "/users/123" (trail/url-for [:get "/users/:id" {:id 123}]))))

  (testing "valid url-for multiple params"
    (is (= "/users/123/tags/321" (trail/url-for [:get "/users/:id/tags/:tag-id" {:id 123 :tag-id 321}]))))

  (testing "mismatched params"
    (is (= "/users/:id" (trail/url-for [:get "/users/:id" {}]))))

  (testing "mismatched params multiple params"
    (is (= "/users/:id/tags/321" (trail/url-for [:get "/users/:id/tags/:tag-id" {:tag-id 321}])))))

(defn auth [handler]
  (fn [request]
    (handler (assoc request :test "test"))))

(deftest wrap-routes-with-test
  (testing "valid middleware"
    (let [handler (fn [request] (get request :test))
          routes (-> (trail/get "/" handler)
                     (trail/wrap-routes-with auth))
          app (trail/match-routes routes)]
      (is (= "test" (app {:request-method :get :uri "/"}))))))

(deftest resource-test
  (testing "resource"
    (let [resource-routes (trail/resource :items)]
      (is (= 7 (count resource-routes)))))

  (testing "resources :only"
    (let [routes (trail/resource :items :only [:index])]
      (is (= 1 (count routes)))))

  (testing "resources :except"
    (let [routes (trail/resource :items :except [:index])]
      (is (= 6 (count routes)))))

  (testing "resource routes"
    (let [routes (trail/resource :users :only [:index])]
      (is (= '(:get "/users") (take 2 (first routes)))))))
