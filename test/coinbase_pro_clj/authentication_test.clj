(ns coinbase-pro-clj.authentication-test
  (:require [clojure.test :refer :all]
            [coinbase-pro-clj.authentication :refer :all]))

(deftest create-prehash-string-test
    (is (= "123GET/time"
           (#'coinbase-pro-clj.authentication/create-prehash-string 
            123 
            {:method "GET" :url "https://api.gdax.com/time"}))
      "without request body")
    (is (= "123POST/orders{:hello \"world\"}"
           (#'coinbase-pro-clj.authentication/create-prehash-string 
            123
            {:method "POST" :url "https://api.gdax.com/orders" :body {:hello "world"}}))
      "with request body"))

(deftest create-http-signature-test
  (is (= "feI9Pm5uupzIiDq9p80pL1/z36vwBwIlVrSiXwa14k4="
        (#'coinbase-pro-clj.authentication/create-http-signature
          "aGVsbG8="
          123
          {:method "POST" :url "https://api.gdax.com/orders" :body {:hello "world"}}))
    "with request body")
  (is (= "aSRkZmpa6LE+0TL3lShoAASVS0jSZIWKVQr42u5KKro="
         (#'coinbase-pro-clj.authentication/create-http-signature
           "aGVsbG8="
           123
           {:method "GET" :url "https://api.gdax.com/accounts"}))
    "without request body"))

(deftest create-websocket-signature-test
  (is (= "C1DZ6pZxgL7VxLvMCCs+8XRCC+lZjYHa1NsATV8kbLc="
         (#'coinbase-pro-clj.authentication/create-websocket-signature
          "aGVsbG8="
          123))))

(deftest sign-request-test
  (with-redefs [coinbase-pro-clj.utilities/get-timestamp (constantly 123)]
    (is (= {:method "GET"
            :url "https://api.gdax.com/accounts"
            :headers {"CB-ACCESS-KEY" "123123", 
                      "CB-ACCESS-SIGN" "aSRkZmpa6LE+0TL3lShoAASVS0jSZIWKVQr42u5KKro=",
                      "CB-ACCESS-TIMESTAMP" 123,
                      "CB-ACCESS-PASSPHRASE" "passphrase123"}}
           (sign-request {:url "https://api.gdax.com"
                          :key "123123"
                          :secret "aGVsbG8="
                          :passphrase "passphrase123"}
                         {:method "GET"
                          :url "https://api.gdax.com/accounts"}))
      "without request body")
    (is (= {:method "POST"
            :url "https://api.gdax.com/orders"
            :body {:hello "world"}
            :headers {"CB-ACCESS-KEY" "123123",
                      "CB-ACCESS-SIGN" "feI9Pm5uupzIiDq9p80pL1/z36vwBwIlVrSiXwa14k4=",
                      "CB-ACCESS-TIMESTAMP" 123
                      "CB-ACCESS-PASSPHRASE" "passphrase123"}}
           (sign-request {:url "https://api.gdax.com"
                          :key "123123"
                          :secret "aGVsbG8="
                          :passphrase "passphrase123"}
                         {:method "POST"
                          :url "https://api.gdax.com/orders"
                          :body {:hello "world"}}))
        "with request body")))

;; Write tests for sign-message if decide to keep implementation tests
;; Currently switching to only test surface level api, which inherently tests implementation
;; This makes changing implementation much easier while still making sure no
;; breaking changes occur
