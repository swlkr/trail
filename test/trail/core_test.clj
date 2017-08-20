(ns trail.core-test
  (:require [clojure.test :refer :all]
            [trail.core :as trail]))

(deftest route-test
  (let [_ (trail/route {:method :get :uri "/users" :fn :all})
        _ (trail/route {:method :post :uri "/users" :fn :create})
        _ (trail/route {:method :get :uri "/" :fn :home})]
    (testing "adding a route"
        (is (= {'("/users") {:get :all :post :create}
                '("/") {:get :home}}
               (deref trail/routes))))

    (testing "adding an invalid route"
      (let [_ (trail/route {:method nil :uri "/users" :fn :all})]
        (is (= {'("/users") {:get :all :post :create}
                '("/") {:get :home}}
               (deref trail/routes)))))

    (testing "just adding nil"
      (let [_ (trail/route nil)]
        (is (= {'("/users") {:get :all :post :create}
                '("/") {:get :home}}
               (deref trail/routes))))))

  (deftest match-route-test
    (testing "matching nil"
      (is (= nil (trail/match-route nil))))

    (testing "matching nil vals"
      (is (= nil (trail/match-route {:uri nil :method nil}))))

    (testing "matching route that doesn't exist"
      (is (= nil (trail/match-route {:uri "/users" :method :get}))))

    (testing "matching route that does exist"
      (is (= {:fn :all :params {}} (trail/match-route {:uri "/users"
                                                       :request-method :get}))))

    (testing "match /"
      (is (= {:fn :home :params {}} (trail/match-route {:uri "/" :request-method :get}))))))
