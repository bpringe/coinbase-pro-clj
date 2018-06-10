(ns gdax-clojure.utilities-test
  (:require [clojure.test :refer :all]
            [gdax-clojure.utilities :refer :all]))

(deftest test-edn->json
  (is (= "{\"hello\":\"world\"}"
         (edn->json {:hello "world"}))))

(deftest test-json->edn
  (is (= {:hello "world"}
         (json->edn "{\"hello\":\"world\"}"))))


