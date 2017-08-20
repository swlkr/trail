(ns trail.core-test
  (:require [clojure.test :refer :all]
            [trail.core :as trail]))

(deftest route-test
  (testing "adding a route"
    (let [_ (trail/route {:method :get
                          :uri "/users"
                          :fn :all})]
      (is (= {'("users") {:get :all}} (deref trail/routes)))))

  (testing "adding an invalid route"
    (let [_ (reset! trail/routes {})
          _ (trail/route {:method nil :uri "/users" :fn :all})]
      (is (= {} (deref trail/routes)))))

  (testing "just adding nil"
    (let [_ (reset! trail/routes {})
          _ (trail/route nil)]
      (is (= {} (deref trail/routes))))))

(deftest match-route-test
  (testing "matching nil"
    (is (= nil (trail/match-route nil))))

  (testing "matching nil vals"
    (is (= nil (trail/match-route {:uri nil :method nil}))))

  (testing "matching route that doesn't exist"
    (is (= nil (trail/match-route {:uri "/users" :method :get}))))

  (testing "matching route that does exist"
    (let [_ (trail/route {:method :get
                          :uri "/users"
                          :fn :all})]
      (is (= {:fn :all :params {}} (trail/match-route {:uri "/users"
                                                       :request-method :get})))))

  (testing "match /"
    (let [_ (trail/route {:method :get
                          :uri "/"
                          :fn :home})])
    (is (= {:fn :home :params {}} (trail/match-route {:uri "/" :request-method :get})))))
