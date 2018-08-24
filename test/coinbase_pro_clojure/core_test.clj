(ns coinbase-pro-clojure.core-test
  (:require 
    [clojure.test :refer :all]
    [coinbase-pro-clojure.core :as core]))

(def last-request (atom {}))

(defn mock-request
  [request]
  (reset! last-request request)
  {:body 1})

(defn http-fixture 
  [test-function]
  (with-redefs [clj-http.client/request mock-request
                coinbase-pro-clojure.utilities/get-timestamp (constantly 1530305893)]
    (test-function)))
    
(use-fixtures :each http-fixture)

(def test-client {:url "https://example.com"
                  :key "testkey"
                  :secret "testsecret"
                  :passphrase "testpassphrase"})
  
;; ## Public endpoint tests

(deftest get-time-test
  (is (= 1 (core/get-time test-client)))
  (is (= @last-request {:method "GET", :url "https://example.com/time", :accept :json, :as :json}))) 
      
(deftest get-products-test
  (is (= 1 (core/get-products test-client)))
  (is (= @last-request {:method "GET", :url "https://example.com/products", :accept :json, :as :json})))

(deftest get-order-book-test
  (testing "without level argument"
    (is (= 1 (core/get-order-book test-client "ETH-USD")))
    (is (= @last-request {:method "GET", :url "https://example.com/products/ETH-USD/book?level=1", :accept :json, :as :json})))    
  (testing "with level argument"
    (is (= 1 (core/get-order-book test-client "ETH-USD" 2)))
    (is (= @last-request {:method "GET", :url "https://example.com/products/ETH-USD/book?level=2", :accept :json, :as :json}))))
            
(deftest get-ticker-test
  (testing "without paging options"
    (is (= 1 (core/get-ticker test-client "ETH-USD")))
    (is (= @last-request {:method "GET", :url "https://example.com/products/ETH-USD/ticker", :accept :json, :as :json})))
           
  (testing "with paging options"
    (is (= 1 (core/get-ticker test-client "ETH-USD" {:before 3 :after 1 :limit 3})))
    (is (= @last-request {:method "GET", :url "https://example.com/products/ETH-USD/ticker?before=3&after=1&limit=3", :accept :json, :as :json}))))

(deftest get-trades-test
  (testing "without paging options"
    (is (= 1 (core/get-trades test-client "ETH-USD")))
    (is (= @last-request {:method "GET", :url "https://example.com/products/ETH-USD/trades", :accept :json, :as :json})))
  (testing "with paging options"
    (is (= 1 (core/get-trades test-client "ETH-USD" {:before 3 :after 1 :limit 3})))
    (is (= @last-request {:method "GET", :url "https://example.com/products/ETH-USD/trades?before=3&after=1&limit=3", :accept :json, :as :json}))))
      
(deftest get-historic-rates-test
  (testing "without options"
    (is (= 1 (core/get-historic-rates test-client "ETH-USD")))
    (is (= @last-request {:method "GET", :url "https://example.com/products/ETH-USD/candles", :accept :json, :as :json})))
  (testing "with options"
    (is (= 1 (core/get-historic-rates test-client "ETH-USD" {:start "6-1-18" :end "6-20-18" :granularity "86400"})))
    (is (= @last-request {:method "GET", :url "https://example.com/products/ETH-USD/candles?start=6-1-18&end=6-20-18&granularity=86400", :accept :json, :as :json}))))

(deftest get-product-stats-test
  (is (= 1 (core/get-product-stats test-client "ETH-USD")))
  (is (= @last-request {:method "GET", :url "https://example.com/products/ETH-USD/stats", :accept :json, :as :json})))

(deftest get-currencies-test
  (is (= 1 (core/get-currencies test-client)))
  (is (= @last-request {:method "GET", :url "https://example.com/currencies", :accept :json, :as :json})))

; ;; ## Private endpoint tests

(deftest get-accounts-test
  (is (= 1 (core/get-accounts test-client)))
  (is (= @last-request {:method "GET", :url "https://example.com/accounts", :accept :json, :as :json, :headers {"CB-ACCESS-KEY" "testkey", "CB-ACCESS-SIGN" "8UDJgimtx0N8IMr0G9yIL2EwDKOxxuEPOhLMjKLY5Dc=", "CB-ACCESS-TIMESTAMP" 1530305893, "CB-ACCESS-PASSPHRASE" "testpassphrase"}})))

(deftest get-account-test
  (is (= 1 (core/get-account test-client "test-account-id")))
  (is (= @last-request {:method "GET", :url "https://example.com/accounts/test-account-id", :accept :json, :as :json, :headers {"CB-ACCESS-KEY" "testkey", "CB-ACCESS-SIGN" "oNBHQYDTi7KrjaBnHvBgJIyKbzS+GCtiiRwAECKeJB8=", "CB-ACCESS-TIMESTAMP" 1530305893, "CB-ACCESS-PASSPHRASE" "testpassphrase"}})))

(deftest get-account-history-test
  (testing "without paging options"
    (is (= 1 (core/get-account-history test-client "test-account-id")))
    (is (= @last-request {:method "GET", :url "https://example.com/accounts/test-account-id/ledger", :accept :json, :as :json, :headers {"CB-ACCESS-KEY" "testkey", "CB-ACCESS-SIGN" "RLvKEq3WhLq4yXaIT+yaf+ySgI37Iy4gKaIj9gPFnKA=", "CB-ACCESS-TIMESTAMP" 1530305893, "CB-ACCESS-PASSPHRASE" "testpassphrase"}})))
  (testing "with paging options"
    (is (= 1 (core/get-account-history test-client "test-account-id" {:before 3 :after 1 :limit 3})))
    (is (= @last-request {:method "GET", :url "https://example.com/accounts/test-account-id/ledger?before=3&after=1&limit=3", :accept :json, :as :json, :headers {"CB-ACCESS-KEY" "testkey", "CB-ACCESS-SIGN" "1Q9wo7a/uQm4PNXkRAYCzud43IabUvmuyPajeqao7ag=", "CB-ACCESS-TIMESTAMP" 1530305893, "CB-ACCESS-PASSPHRASE" "testpassphrase"}}))))

(deftest get-account-holds-test
  (testing "without paging options"
    (is (= 1 (core/get-account-holds test-client "test-account-id")))
    (is (= @last-request {:method "GET", :url "https://example.com/accounts/test-account-id/holds", :accept :json, :as :json, :headers {"CB-ACCESS-KEY" "testkey", "CB-ACCESS-SIGN" "GI1rwgRBjWL4mUh32NORBxiWBgV6X8NtdGcXdWwl2u0=", "CB-ACCESS-TIMESTAMP" 1530305893, "CB-ACCESS-PASSPHRASE" "testpassphrase"}})))
  (testing "with paging options"
    (is (= 1 (core/get-account-holds test-client "test-account-id" {:before 3 :after 1 :limit 3})))
    (is (= @last-request {:method "GET", :url "https://example.com/accounts/test-account-id/holds?before=3&after=1&limit=3", :accept :json, :as :json, :headers {"CB-ACCESS-KEY" "testkey", "CB-ACCESS-SIGN" "EtipcVnvK++uFG8kn1eAr5wFV+U1SMtGYTnBh7GIe8c=", "CB-ACCESS-TIMESTAMP" 1530305893, "CB-ACCESS-PASSPHRASE" "testpassphrase"}}))))

(deftest place-order-test
  (is (= 1 (core/place-order test-client {:side "buy" :product_id "BTC-USD" :price 5000 :size 1 :type "limit"})))
  (is (= @last-request {:method "POST", :url "https://example.com/orders", :accept :json, :as :json, :body "{\"side\":\"buy\",\"product_id\":\"BTC-USD\",\"price\":5000,\"size\":1,\"type\":\"limit\"}"
                        :content-type :json, :headers {"CB-ACCESS-KEY" "testkey", "CB-ACCESS-SIGN" "tBxtmw8tNiVnKgVsabXJJ0S0ahA4l+1PRfAo8yYsFIk=", "CB-ACCESS-TIMESTAMP" 1530305893, "CB-ACCESS-PASSPHRASE" "testpassphrase"}})))

(deftest get-orders-test
  (testing "without options"
    (is (= 1 (core/get-orders test-client)))
    (is (= @last-request {:method "GET", :url "https://example.com/orders?status=all", :accept :json, :as :json, :headers {"CB-ACCESS-KEY" "testkey", "CB-ACCESS-SIGN" "1sgQol+/+Wqw2hVsdTTo3vVJwycF2JuaTCNQOmQ1k6I=", "CB-ACCESS-TIMESTAMP" 1530305893, "CB-ACCESS-PASSPHRASE" "testpassphrase"}})))
  (testing "with options"
    (is (= 1 (core/get-orders test-client {:status ["open" "pending"]})))
    (is (= @last-request {:method "GET", :url "https://example.com/orders?status=open&status=pending", :accept :json, :as :json, :headers
                          {"CB-ACCESS-KEY" "testkey", "CB-ACCESS-SIGN" "CgDB2CdjPT1B5Xg3ULdqfVF6h0+BCYMKNNP0X56dAMw=", "CB-ACCESS-TIMESTAMP" 1530305893, "CB-ACCESS-PASSPHRASE" "testpassphrase"}}))))

(deftest cancel-order-test
  (is (= 1 (core/cancel-order test-client "order-id")))
  (is (= @last-request {:method "DELETE", :url "https://example.com/orders/order-id", :accept :json, :as :json, :headers {"CB-ACCESS-KEY" "testkey", "CB-ACCESS-SIGN" "+HssA1c69im8yImwgAYP7BSp5pNlVEnZihU43QrFGk4=", "CB-ACCESS-TIMESTAMP" 1530305893, "CB-ACCESS-PASSPHRASE" "testpassphrase"}})))

(deftest cancel-all-test
  (testing "without product-id"
    (is (= 1 (core/cancel-all test-client)))
    (is (= @last-request {:method "DELETE", :url "https://example.com/orders", :accept :json, :as :json, :headers {"CB-ACCESS-KEY" "testkey", "CB-ACCESS-SIGN" "CghT1DGKA3TeDXp2Jx8RufqDQB4mCaIa9TBVFlhqgFc=", "CB-ACCESS-TIMESTAMP" 1530305893, "CB-ACCESS-PASSPHRASE" "testpassphrase"}})))
  (testing "with product-id"
    (is (= 1 (core/cancel-all test-client "BTC-USD")))
    (is (= @last-request {:method "DELETE", :url "https://example.com/orders?product_id=BTC-USD", :accept :json, :as :json, :headers {"CB-ACCESS-KEY" "testkey", "CB-ACCESS-SIGN" "R6ERvO6+hR66IxD9sNH9NmMlftru9ayJWUVgzXhs5Eo=", "CB-ACCESS-TIMESTAMP" 1530305893, "CB-ACCESS-PASSPHRASE" "testpassphrase"}}))))

(deftest get-order-test
  (is (= 1 (core/get-order test-client "order-id")))
  (is (= @last-request {:method "GET", :url "https://example.com/orders/order-id", :accept :json, :as :json, :headers {"CB-ACCESS-KEY" "testkey", "CB-ACCESS-SIGN" "46r3AT+BPTbZpO6y6LnUriETvs6vGf+zL+HBm/68ODE=", "CB-ACCESS-TIMESTAMP" 1530305893, "CB-ACCESS-PASSPHRASE" "testpassphrase"}})))

(deftest get-fills-test
  (is (= 1 (core/get-fills test-client {:order_id "123"})))
  (is (= @last-request {:method "GET", :url "https://example.com/fills?order_id=123", :accept :json, :as :json, :headers {"CB-ACCESS-KEY" "testkey", "CB-ACCESS-SIGN" "WX6ahZnpZn9kPyxalFBV/1Sdq7qtz2ZxT6vqWCZs1es=", "CB-ACCESS-TIMESTAMP" 1530305893, "CB-ACCESS-PASSPHRASE" "testpassphrase"}})))

; (deftest get-payment-methods-test
;   (is (= {:method "GET", :url "https://example.com/payment-methods", :accept :json, :as :json, :headers {"CB-ACCESS-KEY" "testkey", "CB-ACCESS-SIGN" "T4GeL7Mvky+MhXD+xhEpWPXP3oBzf4GbfbTJScwzFOs=", "CB-ACCESS-TIMESTAMP" 1530305893, "CB-ACCESS-PASSPHRASE" "testpassphrase"}}
;          (core/get-payment-methods test-client))))

; (deftest deposit-from-payment-method-test
;   (is (= {:method "POST", :url "https://example.com/deposits/payment-method", :accept :json, :as :json, :body "{\"amount\":100,\"currency\":\"USD\",\"payment_method_id\":\"123\"}", :content-type :json, :headers {"CB-ACCESS-KEY" "testkey", "CB-ACCESS-SIGN" "2RRELFmwzewk2gnW+IzUTtl3lNf/kaV1YM7qF56NAZ4=", "CB-ACCESS-TIMESTAMP" 1530305893, "CB-ACCESS-PASSPHRASE" "testpassphrase"}}
;          (core/deposit-from-payment-method test-client {:amount 100 
;                                                         :currency "USD" 
;                                                         :payment_method_id "123"}))))

; (deftest withdraw-to-payment-method-test
;   (is (= {:method "POST", :url "https://example.com/withdrawals/payment-method", :accept :json, :as :json, :body
;           "{\"amount\":100,\"currency\":\"USD\",\"payment_method_id\":\"123\"}", :content-type :json, :headers {"CB-ACCESS-KEY" "testkey", "CB-ACCESS-SIGN" "8LcqJCjATrmacKf3dQnQskFGioALMRb8MHMKfY2KxHY=", "CB-ACCESS-TIMESTAMP" 1530305893, "CB-ACCESS-PASSPHRASE" "testpassphrase"}}
;          (core/withdraw-to-payment-method test-client {:amount 100
;                                                        :currency "USD"
;                                                        :payment_method_id "123"}))))

; (deftest get-coinbase-accounts-test
;  (is (= {:method "GET", :url "https://example.com/coinbase-accounts", :accept :json, :as :json, :headers {"CB-ACCESS-KEY" "testkey", "CB-ACCESS-SIGN" "lwt4UUQhkuV9A3b7ME2qUesgvZp1g6zg0ikTI8mvv74=", "CB-ACCESS-TIMESTAMP" 1530305893, "CB-ACCESS-PASSPHRASE" "testpassphrase"}}
;         (core/get-coinbase-accounts test-client))))

; (deftest deposit-from-coinbase-test
;  (is (= {:method "POST", :url "https://example.com/deposits/coinbase-account", :accept :json, :as :json, :body "{\"amount\":100,\"currency\":\"USD\",\"coinbase_account_id\":\"123\"}", :content-type :json, :headers {"CB-ACCESS-KEY" "testkey", "CB-ACCESS-SIGN" "/hFiIV7gf25VVwhw4dGsdBIujDROnJ4HrPRIj0A6GZ4=", "CB-ACCESS-TIMESTAMP" 1530305893, "CB-ACCESS-PASSPHRASE" "testpassphrase"}}
;         (core/deposit-from-coinbase test-client {:amount 100
;                                                  :currency "USD"
;                                                  :coinbase_account_id "123"}))))

; (deftest withdraw-to-coinbase-test
;   (is (= {:method "POST", :url "https://example.com/withdrawals/coinbase-account", :accept :json, :as :json, :body "{\"amount\":100,\"currency\":\"USD\",\"coinbase_account_id\":\"123\"}", :content-type :json, :headers {"CB-ACCESS-KEY" "testkey", "CB-ACCESS-SIGN" "IBJT986bAHr3xDP0vL9Fgg5j5mTaH3G7hB1iPUeXfXw=", "CB-ACCESS-TIMESTAMP" 1530305893, "CB-ACCESS-PASSPHRASE" "testpassphrase"}}
;          (core/withdraw-to-coinbase test-client {:amount 100
;                                                  :currency "USD"
;                                                  :coinbase_account_id "123"}))))

; (deftest withdraw-to-crypto-address-test
;   (is (= {:method "POST", :url "https://example.com/withdrawals/crypto", :accept :json, :as :json, :body "{\"amount\":100,\"currency\":\"BTC\",\"crypto_address\":\"123\"}", :content-type :json, :headers {"CB-ACCESS-KEY" "testkey", "CB-ACCESS-SIGN" "L+IPN58ZRMOSNOUH33LBwXlZw9xxu5mixrqVUPkhwcE=", "CB-ACCESS-TIMESTAMP" 1530305893, "CB-ACCESS-PASSPHRASE" "testpassphrase"}}
;          (core/withdraw-to-crypto-address test-client {:amount 100
;                                                        :currency "BTC"
;                                                        :crypto_address "123"}))))
                                                     
; (deftest generate-report-test
;   (is (= {:method "POST", :url "https://example.com/reports", :accept :json, :as :json, :body "{\"type\":\"fills\",\"product_id\":\"BTC-USD\",\"start_date\":\"2018-1-1\"}", :content-type :json, :headers {"CB-ACCESS-KEY" "testkey", "CB-ACCESS-SIGN" "Sxmef57/sVxBoo8yR7WrIcwTpm3kpZ7ZzPqclaDEOeY=", "CB-ACCESS-TIMESTAMP" 1530305893, "CB-ACCESS-PASSPHRASE" "testpassphrase"}}
;          (core/generate-report test-client {:type "fills" 
;                                             :product_id "BTC-USD"
;                                             :start_date "2018-1-1"}))))

; (deftest get-report-status-test
;   (is (= {:method "GET", :url "https://example.com/reports/123", :accept :json, :as :json, :headers {"CB-ACCESS-KEY" "testkey", "CB-ACCESS-SIGN" "IxwWA/IAHNoYKlCTkdsyWLp4/Y73af8rTue37T2BXa4=", "CB-ACCESS-TIMESTAMP" 1530305893, "CB-ACCESS-PASSPHRASE" "testpassphrase"}}
;          (core/get-report-status test-client "123"))))


; (deftest get-trailing-volume-test
;   (is (= {:method "GET", :url "https://example.com/users/self/trailing-volume", :accept :json, :as :json, :headers {"CB-ACCESS-KEY" "testkey", "CB-ACCESS-SIGN" "1kHkV6sb0z8F7mHScmpei/Q5KQB4BsOkYBg4tK06E0E=", "CB-ACCESS-TIMESTAMP" 1530305893, "CB-ACCESS-PASSPHRASE" "testpassphrase"}}
;          (core/get-trailing-volume test-client))))





                                                                                                        







