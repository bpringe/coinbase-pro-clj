(ns gdax-clojure.utilities-test
  (:require [clojure.test :refer :all]
            [gdax-clojure.utilities :refer :all]))

(deftest test-edn->json
  (is (= "{\"hello\":\"world\"}"
         (edn->json {:hello "world"}))))

(deftest test-json->edn
  (is (= {:hello "world"}
         (json->edn "{\"hello\":\"world\"}"))))

(deftest test-contains-many?
  (let [coll {:a 1 :b 2 :c 3}]
    (is (contains-many? coll :a :b))))

(deftest test-get-timestamp
  (let [current-time-secs (quot (System/currentTimeMillis) 1000)
        time (get-timestamp)]
    (is (< (- time current-time-secs) 1))))

(deftest test-build-base-request
  (let [method "GET"
        url "test url"
        expected {:method method
                  :url url
                  :accept :json
                  :as :json}]
    (is (= expected (build-base-request method url)))))

(deftest test-build-get-request
  (let [url "test url"
        expected {:method "GET"
                  :url url
                  :accept :json
                  :as :json}]
    (is (= expected (build-get-request url)))))

  

