(ns trail.core-test
  (:require [clojure.test :refer :all]
            [clout.core :as clout]
            [trail.core :as trail]))

(defn add-routes []
  (do
    (trail/route {:method :get :uri "/users" :fn :all})
    (trail/route {:method :post :uri "/users" :fn :create})
    (trail/route {:method :get :uri "/" :fn :home})
    (trail/route {:method :get :uri "/users/:id" :fn :users.http/fetch})
    (trail/route {:method :get :uri "/users/:user-id/flags/:flag-id/tags" :fn :tags.http/fetch})))

(defn route-fixture [f]
  (add-routes)
  (f))

(use-fixtures :once route-fixture)

(deftest route-test
  (testing "adding a route"
    (is (and (= (keys @trail/route-map) [:get :post])
             (= (keys (get @trail/route-map :get)) ["/users" "/" "/users/:id" "/users/:user-id/flags/:flag-id/tags"])
             (= (keys (get @trail/route-map :post)) ["/users"]))))

  (testing "adding an invalid route"
    (let [_ (trail/route {:method nil :uri "/users" :fn :all})]
      (is (and (= (keys @trail/route-map) [:get :post])
               (= (keys (get @trail/route-map :get)) ["/users" "/" "/users/:id" "/users/:user-id/flags/:flag-id/tags"])
               (= (keys (get @trail/route-map :post)) ["/users"])))))

  (testing "adding nil"
    (let [_ (trail/route nil)]
      (is (and (= (keys @trail/route-map) [:get :post])
               (= (keys (get @trail/route-map :get)) ["/users" "/" "/users/:id" "/users/:user-id/flags/:flag-id/tags"])
               (= (keys (get @trail/route-map :post)) ["/users"])))))

  (testing "adding nil uri and nil fn"
    (let [_ (trail/route {:method :get :uri nil :fn nil})]
      (is (and (= (keys @trail/route-map) [:get :post])
               (= (keys (get @trail/route-map :get)) ["/users" "/" "/users/:id" "/users/:user-id/flags/:flag-id/tags"])
               (= (keys (get @trail/route-map :post)) ["/users"]))))))

(deftest match-route-test
  (testing "matching nil"
    (is (= nil (trail/match nil))))

  (testing "matching nil vals"
    (is (= nil (trail/match {:uri nil :request-method nil}))))

  (testing "matching route that doesn't exist"
    (is (= nil (trail/match {:uri "/exists" :request-method :get}))))

  (testing "matching route that does exist"
    (is (= {:fn :all :route-params {}} (trail/match {:uri "/users" :request-method :get}))))

  (testing "match /"
    (is (= {:fn :home :route-params {}} (trail/match {:uri "/" :request-method :get}))))

  (testing "match /users/:id"
    (is (= {:fn :users.http/fetch :route-params {:id "123"}}
           (trail/match {:uri "/users/123"
                         :request-method :get}))))

  (testing "a relatively deeply nested route"
    (is (= {:fn :tags.http/fetch :route-params {:user-id "123"
                                                :flag-id "333"}}
           (trail/match {:uri "/users/123/flags/333/tags"
                         :request-method :get})))))
