(ns trail.core-test
  (:require [clojure.test :refer :all]
            [clout.core :as clout]
            [trail.core :refer :all]
            [trail.users :as users])
  (:refer-clojure :exclude [get]))

(defn auth [handler]
  (fn [request]
    (handler (assoc request :test "test"))))

(deftest match-routes-test
  (let [protected-routes (-> (get "/" (fn [r] (str "GET / " (:test r))))
                             (get "/sign-out" (fn [r] (str "GET /sign-out " (:test r)))))
        routes (-> (wrap-routes auth protected-routes)
                   (get "/sign-up" (fn [r] "GET /sign-up"))
                   (post "/users" (fn [r] "POST /users"))
                   (put "/users/:id" (fn [r] (str "PUT /users " (-> r :params :id))))
                   (patch "/users/:user-id" (fn [r] (str "PATCH /users " (-> r :params :user-id))))
                   (delete "/users/:uid" (fn [r] (str "DELETE /users " (-> r :params :uid))))
                   (not-found (fn [r] "not found")))]

    (testing "custom not found route"
      (is (= "not found" ((match-routes routes) {:request-method :get :uri "/not-found"}))))

    (testing "default not found route"
      (let [routes (dissoc routes :not-found)]
        (is (= {:status 404} ((match-routes routes) {:request-method :get :uri "/something"})))))

    (testing "matched route"
      (is (= "GET /sign-up" ((match-routes routes) {:request-method :get :uri "/sign-up"}))))

    (testing "matched wrapped route"
      (is (= "GET / test" ((match-routes routes) {:request-method :get :uri "/"}))))

    (testing "matched POST route"
      (is (= "POST /users" ((match-routes routes) {:request-method :post :uri "/users"}))))

    (testing "matched PUT route"
      (is (= "PUT /users 123" ((match-routes routes) {:request-method :put :uri "/users/123"}))))

    (testing "matched PATCH route"
      (is (= "PATCH /users 2" ((match-routes routes) {:request-method :patch :uri "/users/2"}))))

   (testing "matched DELETE route"
      (is (= "DELETE /users 321" ((match-routes routes) {:request-method :delete :uri "/users/321"}))))))

(deftest resource-test
  (let [routes (resource {} :users)]
    (testing "resolves controller functions"
      (is (= "GET /users" ((match-routes routes) {:request-method :get :uri "/users"}))))

    (testing "resolves delete function"
      (is (= "DELETE /users/321" ((match-routes routes) {:request-method :delete :uri "/users/321"}))))))

