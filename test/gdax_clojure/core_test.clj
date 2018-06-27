(ns gdax-clojure.core-test
  (:require 
    [clojure.test :refer :all]
    [gdax-clojure.core :refer :all]))

;; Redefine clj-http.client/request to be a function that returns its argument
;; Then in tests we verify that the return of the called function equals the
;; expected argument to clj-http.client/request. This also verifies that the
;; clj-http.client/request call was returned from the function being tested.
(defn http-fixture 
  [test-function]
  (with-redefs [clj-http.client/request #(identity %)]
    (test-function)))
    
(use-fixtures :each http-fixture)

(def test-client {:url "https://public.sandbox.gdax.com"
                  :key "testkey"
                  :secret "testsecret"
                  :passphrase "testpassphrase"})
  
;; ## Public endpoint tests

(deftest get-time-test
  (is (= {:method "GET" 
          :url "https://public.sandbox.gdax.com/time"
          :accept :json
          :as :json}
         (get-time test-client))))
        
(deftest get-products-test
  (is (= {:method "GET", :url "https://public.sandbox.gdax.com/products", :accept :json, :as :json} 
         (get-products test-client))))

(deftest get-order-book-test

  (testing "without level argument"
    (is (= {:method "GET", :url "https://public.sandbox.gdax.com/products/ETH-USD/book?level=1", :accept :json, :as :json}
           (get-order-book test-client "ETH-USD"))))

  (testing "with level argument"
    (is (= {:method "GET", :url "https://public.sandbox.gdax.com/products/ETH-USD/book?level=2", :accept :json, :as :json} 
           (get-order-book test-client "ETH-USD" 2)))))

(deftest get-ticker-test
  (is (= {:method "GET", :url "https://api.gdax.com/products/ETH-USD/ticker", :accept :json, :as :json}
         (get-ticker my-client "ETH-USD"))))

