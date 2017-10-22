(ns trail.core-test
  (:require [clojure.test :refer :all]
            [clout.core :as clout]
            [trail.core :refer :all])
  (:refer-clojure :exclude [get]))

(defn auth [handler]
  (fn [request]
    (handler (assoc request :test "test"))))

(deftest match-routes-test
  (let [protected-routes (-> (get "/" (fn [r] (str "GET / " (:test r))))
                             (get "/sign-out" (fn [r] (str "GET /sign-out " (:test r))))
                             (get "/users/:id" (fn [r] (str "GET /users/" (-> r :params :id))))
                             (get "/users/new" (fn [r] "GET /users/new"))
                             (wrap-routes-with auth))
        routes (-> (get "/sign-up" (fn [r] "GET /sign-up"))
                   (post "/users" (fn [r] "POST /users"))
                   (put "/users/:id" (fn [r] (str "PUT /users " (-> r :params :id))))
                   (patch "/users/:user-id" (fn [r] (str "PATCH /users " (-> r :params :user-id))))
                   (delete "/users/:uid" (fn [r] (str "DELETE /users " (-> r :params :uid))))
                   (delete "/sessions" (fn [r] (str "DELETE /sessions")))
                   (route-not-found (fn [r] "not found")))
        routes (merge routes protected-routes)]

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
      (is (= "DELETE /users 321" ((match-routes routes) {:request-method :delete :uri "/users/321"}))))

    (testing "matched DELETE route"
      (is (= "DELETE /users 321" ((match-routes routes) {:request-method :get :params {:_method :delete} :uri "/users/321"}))))

    (testing "matching new vs :id"
      (is (= "GET /users/new" ((match-routes routes) {:request-method :get :uri "/users/new"}))))

    (testing "matching :id vs new"
      (is (= "GET /users/1" ((match-routes routes) {:request-method :get :uri "/users/1"}))))))
