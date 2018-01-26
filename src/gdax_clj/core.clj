(ns gdax-clj.core
  (:require 
    [pandect.algo.sha256 :refer :all]
    [cheshire.core :refer :all]
    [clj-http.client :as http]
    [environ.core :refer [env]]
    [clj-time.core :as t]
    [clojure.data.codec.base64 :as b64]
    [clojure.string :as str]
    [clojure.pprint :refer [pprint]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;; Configuration ;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def config {:api-base-url "https://api.gdax.com"
             :granularities {:1m 60
                             :5m 300
                             :15m 900
                             :1h 3600
                             :6h 21600
                             :1d 86400}
             :api-key (env :api-key)
             :api-secret (env :api-secret)
             :api-passphrase (env :api-passphrase)
             :debug-requests false})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;; Request Building ;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- build-request
  [method path & [opts]]
  (merge {:method (str/upper-case method)
          :url (str (:api-base-url config)
                    (if (str/starts-with? path "/") path (str "/" path)))
          :as :json
          :debug (:debug-requests config)
          :headers {"Content-Type" "application/json"}}
         opts))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;; Authentication ;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- parse-request-path
  [request-url]
  (second (str/split request-url #".com")))

(defn- create-prehash-string
  [request timestamp]
  (str timestamp (:method request) (parse-request-path (:url request)) (:body request)))

(defn- create-signature
  [request timestamp]
  (let [secret-decoded (b64/decode (.getBytes (:api-secret config)))
        prehash-string (create-prehash-string request timestamp)
        hmac (sha256-hmac* prehash-string secret-decoded)]
    (-> hmac
        b64/encode
        String.)))

(defn- sign-request 
  [request]
  (let [timestamp (quot (System/currentTimeMillis) 1000)]
    (assoc request :headers
      (merge (:headers request) {"CB-ACCESS-KEY" (:api-key config)
                                 "CB-ACCESS-SIGN" (create-signature request timestamp)
                                 "CB-ACCESS-TIMESTAMP" timestamp
                                 "CB-ACCESS-PASSPHRASE" (:api-passphrase config)}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;; Public Endpoints ;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-time
  []
  (http/request (build-request "get" "/time")))

(defn get-products 
  []
  (http/request (build-request "get" "/products")))

(defn get-order-book
  ([product-id]
   (get-order-book product-id 1))
  ([product-id level]
   (->> (str "/products/" product-id "/book?level=" level)
        (build-request "get")
        http/request)))

(defn get-ticker
  [product-id]
  (->> (str "/products/" product-id "/ticker")
       (build-request "get")
       http/request))

(defn get-trades 
  [product-id]
  (->> (str "/products/" product-id "/trades")
       (build-request "get")
       http/request))

;; Remember to use (t/today-at 00 00) to avoid sending time ahead of server time
(defn get-historic-rates
  ([product-id]
   (->> "/products/eth-usd/candles"
        (build-request "GET")
        http/request))
  ([product-id start end granularity]
   (->> (str "/products/" product-id "/candles?start="
         start "&end=" end "&granularity=" granularity)
        (build-request "GET")
        http/request)))
      
(defn get-product-stats
  [product-id]
  (->> (str "/products/" product-id "/stats")
       (build-request "GET")
       http/request))

(defn get-currencies
  []
  (http/request (build-request "GET" "/currencies")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;; Private Endpoints ;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-accounts 
  []
  (-> (build-request "get" "/accounts")
      sign-request
      http/request))

(defn get-account-by-id
  [account-id]
  (->> (build-request "get" (str "/accounts/" account-id))
       sign-request
       http/request))

;; TODO: implement paging
(defn get-account-history
  [account-id]
  (->> (build-request "get" (str "/accounts/" account-id "/ledger"))
      sign-request
      http/request))

;; TODO: implement paging
(defn get-account-holds
  [account-id]
  (->> (build-request "get" (str "/accounts/" account-id "/holds"))
       sign-request
       http/request))



  

