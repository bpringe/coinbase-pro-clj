(ns gdax-clojure.utilities-test
  (:require [clojure.test :refer :all]
            [gdax-clojure.utilities :refer :all]))

(def default-options {:accept :json
                      :as :json})

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
        expected (merge {:method method :url url} default-options)]
    (is (= expected (build-base-request method url)))))

(deftest test-build-get-request
  (let [url "test url"
        expected (merge {:method "GET" :url url} default-options)]
    (is (= expected (build-get-request url)))))

(deftest test-build-post-request
  (let [url "test url"
        body {:hello "world"}
        options {:some "options"}
        expected (merge {:method "POST"
                         :url url
                         :body "{\"hello\":\"world\"}"
                         :content-type :json}
                        default-options)]
    (is (= expected (build-post-request url body))
        "without options")
    (is (= (merge expected options)
           (build-post-request url body options))
        "with options")))

(deftest test-build-delete-request
  (let [url "test url"
        options {:some "options"}
        expected (merge {:method "DELETE" :url url} default-options)]
    (is (= expected (build-delete-request url))
        "without options")
    (is (= (merge expected options) (build-delete-request url options))
        "with options")))
                         
(deftest test-map->query-string
  (let [params {:a 1 :b 2 :c "hello"}
        expected "a=1&b=2&c=hello"]
    (is (= expected (map->query-string params))
      "with non-empty map")
    (is (= "" (map->query-string {}))
      "with empty map")))
                  
(deftest test-append-query-params
  (let [base-url "https://test"]
    (is (= {:url (str base-url "?a=1&b=hello")} 
           (append-query-params {:a 1 :b "hello"} {:url base-url}))
      "without existing query params on url")
    (is (= {:url (str base-url "?answer=42&message=hello")}
           (append-query-params {:message "hello"} {:url (str base-url "?answer=42")})))))

