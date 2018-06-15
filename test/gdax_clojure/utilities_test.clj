(ns gdax-clojure.utilities-test
  (:require [clojure.test :refer :all]
            [gdax-clojure.utilities :refer :all]))

(def default-options {:accept :json
                      :as :json})

(deftest edn->json-test
  (is (= "{\"hello\":\"world\"}"
         (edn->json {:hello "world"}))))

(deftest json->edn-test
  (is (= {:hello "world"}
         (json->edn "{\"hello\":\"world\"}"))))

(deftest contains-many?-test
  (let [coll {:a 1 :b 2 :c 3}]
    (is (contains-many? coll :a :b))))

(deftest get-timestamp-test
  (let [current-time-secs (quot (System/currentTimeMillis) 1000)
        time (get-timestamp)]
    (is (< (- time current-time-secs) 1))))

(deftest build-base-request-test
  (let [method "GET"
        url "test url"
        expected (merge {:method method :url url} default-options)]
    (is (= expected (build-base-request method url)))))

(deftest build-get-request-test
  (let [url "test url"
        expected (merge {:method "GET" :url url} default-options)]
    (is (= expected (build-get-request url)))))

(deftest build-post-request-test
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

(deftest build-delete-request-test
  (let [url "test url"
        options {:some "options"}
        expected (merge {:method "DELETE" :url url} default-options)]
    (is (= expected (build-delete-request url))
        "without options")
    (is (= (merge expected options) (build-delete-request url options))
        "with options")))
                  
(deftest append-query-params-test
  (let [base-url "https://test"]
    (is (= {:url (str base-url "?a=1&b=hello")} 
           (append-query-params {:a 1 :b "hello"} {:url base-url}))
      "without existing query params on url")
    (is (= {:url (str base-url "?answer=42&message=hello")}
           (append-query-params {:message "hello"} {:url (str base-url "?answer=42")}))
        "with existing query params on url")))

