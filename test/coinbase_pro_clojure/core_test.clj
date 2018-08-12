(ns coinbase-pro-clojure.core-test
  (:require 
    [clojure.test :refer :all]
    [coinbase-pro-clojure.core :refer :all]))

;; Redefine clj-http.client/request to be a function that returns its argument
;; Then in tests we verify that the return of the called function equals the
;; expected argument to clj-http.client/request. This also verifies that the
;; clj-http.client/request call was returned from the function being tested.
(defn http-fixture 
  [test-function]
  (with-redefs [clj-http.client/request #(identity %)
                coinbase-pro-clojure.utilities/get-timestamp (constantly 1530305893)]
    (test-function)))
    
(use-fixtures :each http-fixture)

(def test-client {:url "https://example.com"
                  :key "testkey"
                  :secret "testsecret"
                  :passphrase "testpassphrase"})
  
;; ## Public endpoint tests

(deftest get-time-test
  (is (= {:method "GET" 
          :url "https://example.com/time"
          :accept :json
          :as :json}
         (get-time test-client))))
        
(deftest get-products-test
  (is (= {:method "GET", :url "https://example.com/products", :accept :json, :as :json} 
         (get-products test-client))))

(deftest get-order-book-test
  (testing "without level argument"
    (is (= {:method "GET", :url "https://example.com/products/ETH-USD/book?level=1", :accept :json, :as :json}
           (get-order-book test-client "ETH-USD"))))
  (testing "with level argument"
    (is (= {:method "GET", :url "https://example.com/products/ETH-USD/book?level=2", :accept :json, :as :json} 
           (get-order-book test-client "ETH-USD" 2)))))

(deftest get-ticker-test
  (testing "without paging options"
    (is (= {:method "GET", :url "https://example.com/products/ETH-USD/ticker", :accept :json, :as :json}
           (get-ticker test-client "ETH-USD"))))
  (testing "with paging options"
    (is (= {:method "GET", :url "https://example.com/products/ETH-USD/ticker?before=3&after=1&limit=3", :accept :json, :as :json}
           (get-ticker test-client "ETH-USD" {:before 3 :after 1 :limit 3})))))

(deftest get-trades-test
  (testing "without paging options"
    (is (= {:method "GET", :url "https://example.com/products/ETH-USD/trades", :accept :json, :as :json}
           (get-trades test-client "ETH-USD"))))
  (testing "with paging options"
    (is (= {:method "GET", :url "https://example.com/products/ETH-USD/trades?before=3&after=1&limit=3", :accept :json, :as :json}
           (get-trades test-client "ETH-USD" {:before 3 :after 1 :limit 3})))))
      
(deftest get-historic-rates-test
  (testing "without options"
    (is (= {:method "GET", :url "https://example.com/products/ETH-USD/candles", :accept :json, :as :json}
           (get-historic-rates test-client "ETH-USD"))))
  (testing "with options"
    (is (= {:method "GET", :url "https://example.com/products/ETH-USD/candles?start=6-1-18&end=6-20-18&granularity=86400", :accept :json, :as :json}
           (get-historic-rates test-client "ETH-USD" {:start "6-1-18"
                                                      :end "6-20-18"
                                                      :granularity "86400"})))))

(deftest get-product-stats-test
  (is (= {:method "GET", :url "https://example.com/products/ETH-USD/stats", :accept :json, :as :json}
         (get-product-stats test-client "ETH-USD"))))

(deftest get-currencies-test
  (is (= {:method "GET", :url "https://example.com/currencies", :accept :json, :as :json}
         (get-currencies test-client))))

;; ## Private endpoint tests

(deftest get-accounts-test
  (is (= {:method "GET", :url "https://example.com/accounts", :accept :json, :as :json, :headers {"CB-ACCESS-KEY" "testkey", "CB-ACCESS-SIGN" "8UDJgimtx0N8IMr0G9yIL2EwDKOxxuEPOhLMjKLY5Dc=", "CB-ACCESS-TIMESTAMP" 1530305893, "CB-ACCESS-PASSPHRASE" "testpassphrase"}}
         (get-accounts test-client))))

(deftest get-account-test
  (is (= {:method "GET", :url "https://example.com/accounts/test-account-id", :accept :json, :as :json, :headers {"CB-ACCESS-KEY" "testkey", "CB-ACCESS-SIGN" "oNBHQYDTi7KrjaBnHvBgJIyKbzS+GCtiiRwAECKeJB8=", "CB-ACCESS-TIMESTAMP" 1530305893, "CB-ACCESS-PASSPHRASE" "testpassphrase"}}
         (get-account test-client "test-account-id"))))

(deftest get-account-history-test
  (testing "without paging options"
    (is (= {:method "GET", :url "https://example.com/accounts/test-account-id/ledger", :accept :json, :as :json, :headers {"CB-ACCESS-KEY" "testkey", "CB-ACCESS-SIGN" "RLvKEq3WhLq4yXaIT+yaf+ySgI37Iy4gKaIj9gPFnKA=", "CB-ACCESS-TIMESTAMP" 1530305893, "CB-ACCESS-PASSPHRASE" "testpassphrase"}}
           (get-account-history test-client "test-account-id"))))
  (testing "with paging options"
    (is (= {:method "GET", :url "https://example.com/accounts/test-account-id/ledger?before=3&after=1&limit=3", :accept :json, :as :json, :headers {"CB-ACCESS-KEY" "testkey", "CB-ACCESS-SIGN" "1Q9wo7a/uQm4PNXkRAYCzud43IabUvmuyPajeqao7ag=", "CB-ACCESS-TIMESTAMP" 1530305893, "CB-ACCESS-PASSPHRASE" "testpassphrase"}}
           (get-account-history test-client "test-account-id" {:before 3 :after 1 :limit 3})))))

(deftest get-account-holds-test
  (testing "without paging options"
    (is (= {:method "GET", :url "https://example.com/accounts/test-account-id/holds", :accept :json, :as :json, :headers {"CB-ACCESS-KEY" "testkey", "CB-ACCESS-SIGN" "GI1rwgRBjWL4mUh32NORBxiWBgV6X8NtdGcXdWwl2u0=", "CB-ACCESS-TIMESTAMP" 1530305893, "CB-ACCESS-PASSPHRASE" "testpassphrase"}}
           (get-account-holds test-client "test-account-id"))))
  (testing "with paging options"
    (is (= {:method "GET", :url "https://example.com/accounts/test-account-id/holds?before=3&after=1&limit=3", :accept :json, :as :json, :headers {"CB-ACCESS-KEY" "testkey", "CB-ACCESS-SIGN" "EtipcVnvK++uFG8kn1eAr5wFV+U1SMtGYTnBh7GIe8c=", "CB-ACCESS-TIMESTAMP" 1530305893, "CB-ACCESS-PASSPHRASE" "testpassphrase"}}
           (get-account-holds test-client "test-account-id" {:before 3 :after 1 :limit 3})))))

(deftest place-order-test
  (is (= {:method "POST", :url "https://example.com/orders", :accept :json, :as :json, :body "{\"price\":5000,\"size\":1,\"type\":\"limit\",\"side\":\"buy\",\"product_id\":\"BTC-USD\"}", :content-type :json, :headers {"CB-ACCESS-KEY" "testkey", "CB-ACCESS-SIGN" "xxorIBVzpPSlinbxQOrWc1xieL9QdJ0OIj0AWMlfv/A=", "CB-ACCESS-TIMESTAMP" 1530305893, "CB-ACCESS-PASSPHRASE" "testpassphrase"}}
         (place-order test-client "buy" "BTC-USD" {:price 5000 :size 1 :type "limit"}))))
 
(deftest place-limit-order-test
  (testing "without options"
    (is (= {:method "POST", :url "https://example.com/orders", :accept :json, :as :json, :body "{\"price\":5000,\"size\":1,\"type\":\"limit\",\"side\":\"buy\",\"product_id\":\"BTC-USD\"}", :content-type :json, :headers {"CB-ACCESS-KEY" "testkey", "CB-ACCESS-SIGN" "xxorIBVzpPSlinbxQOrWc1xieL9QdJ0OIj0AWMlfv/A=", "CB-ACCESS-TIMESTAMP" 1530305893, "CB-ACCESS-PASSPHRASE" "testpassphrase"}}
           (place-limit-order test-client "buy"  "BTC-USD" 5000 1))))
  (testing "with options"
    (is (= {:method "POST", :url "https://example.com/orders", :accept :json, :as :json, :body "{\"time_in_force\":\"GTC\",\"post_only\":true,\"price\":5000,\"size\":1,\"type\":\"limit\",\"side\":\"buy\",\"product_id\":\"BTC-USD\"}", :content-type :json, :headers {"CB-ACCESS-KEY" "testkey", "CB-ACCESS-SIGN" "x4ylTGFhrg7recYUdnIBfg6ybKWzF516n2bKmxQ47HE=", "CB-ACCESS-TIMESTAMP" 1530305893, "CB-ACCESS-PASSPHRASE" "testpassphrase"}}
           (place-limit-order test-client "buy" "BTC-USD" 5000 1 {:time_in_force "GTC" :post_only true})))))

(deftest place-market-order-test
  (is (= {:method "POST", :url "https://example.com/orders", :accept :json, :as :json, :body "{\"size\":4,\"type\":\"market\",\"side\":\"sell\",\"product_id\":\"BTC-USD\"}", :content-type :json, :headers {"CB-ACCESS-KEY" "testkey", "CB-ACCESS-SIGN" "NVezZxERvaZZgVmaednPVayTmJMYUlESOfeaqjup27M=", "CB-ACCESS-TIMESTAMP" 1530305893, "CB-ACCESS-PASSPHRASE" "testpassphrase"}}
         (place-market-order test-client "sell" "btc-usd" {:size 4}))))

(deftest place-stop-order-test
  (prn (place-stop-order test-client "buy" "BTC-USD" 2 4000 "entry")))