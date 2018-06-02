(ns gdax-clj.utilities-test
  (:require [expectations :refer :all]
            [gdax-clj.utilities :refer :all]))

(expect "{\"hello\":\"world\"}"
        (edn->json {:hello "world"}))

(expect {:hello "world"}
        (json->edn "{\"hello\":\"world\"}"))

(expect true
        (contains-many? {:a 0 :b 1 :c 2} :a :b :c))

(expect false
        (contains-many? {:a 0 :b 1 :c 2} :a :d))

(expect (quot (System/currentTimeMillis) 1000)
        (get-timestamp))

(expect {:method "GET"
         :url "https://google.com"
         :accept :json
         :as :json}
        (build-base-request "GET" "https://google.com"))

(expect {:method "GET"
         :url "https://google.com"
         :accept :json
         :as :json}
        (build-get-request "https://google.com"))


