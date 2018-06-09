(ns gdax-clj.utilities-test
  (:require [clojure.test :refer :all]
            [gdax-clj.utilities :refer :all]))

(deftest test-edn->json
  (is (= "{\"hello\":\"world\"}"
         (edn->json {:hello "world}))))

(deftest test-json->edn
  (is (= {:hello "world"}
         (json->edn "{\"hello\":\"world\"}"))))


