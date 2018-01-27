(ns gdax-clj.core
  (:require 
    [pandect.algo.sha256 :refer :all]
    [cheshire.core :refer :all]
    [clj-http.client :as http]
    [environ.core :refer [env]]
    [clj-time.core :as t]
    [clojure.data.codec.base64 :as b64]
    [clojure.pprint :refer [pprint]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;; Configuration ;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def config {:api-base-url "https://api-public.sandbox.gdax.com"
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

(defn- build-base-request
  [method path]
  {:method (clojure.string/upper-case method)
   :url (str (:api-base-url config)
          (if (clojure.string/starts-with? path "/") path (str "/" path)))
   :accept :json
   :as :json
   :debug (:debug-requests config)})

(defn- build-get-request
  [path & [opts]]
  (merge (build-base-request "GET" path)
         opts))

(defn- build-post-request
  [path body & [opts]]
  (merge (build-base-request "POST" path)
         {:body body}
         opts))

(defn- map->query-string
  [params]
  (clojure.string/join "&"
    (for [[k v] params]
      (str (name k) "=" (java.net.URLEncoder/encode (str v))))))

(defn- append-paging-options
  [paging-options request]
  (if (empty? paging-options)
    request
    (update-in request [:url] 
      #(str % 
        (if (clojure.string/includes? % "?") "&" "?") 
        (map->query-string paging-options)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;; Authentication ;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- parse-request-path
  [request-url]
  (second (clojure.string/split request-url #".com")))

(defn- create-prehash-string
  [timestamp request]
  (str timestamp (:method request) (parse-request-path (:url request)) (:body request)))

(defn- create-signature
  [timestamp request]
  (let [secret-decoded (b64/decode (.getBytes (:api-secret config)))
        prehash-string (create-prehash-string timestamp request)
        hmac (sha256-hmac* prehash-string secret-decoded)]
    (-> hmac
        b64/encode
        String.)))

(defn- sign-request 
  [request]
  (let [timestamp (quot (System/currentTimeMillis) 1000)]
    (update-in request [:headers] merge {"CB-ACCESS-KEY" (:api-key config)
                                         "CB-ACCESS-SIGN" (create-signature timestamp request)
                                         "CB-ACCESS-TIMESTAMP" timestamp
                                         "CB-ACCESS-PASSPHRASE" (:api-passphrase config)})))
                       
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;; Public Endpoints ;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-time
  []
  (http/request (build-get-request "/time")))

(defn get-products 
  []
  (http/request (build-get-request "/products")))

(defn get-order-book
  ([product-id]
   (get-order-book product-id 1))
  ([product-id level]
   (->> (str "/products/" product-id "/book?level=" level)
        build-get-request
        http/request)))

(defn get-ticker
  [product-id]
  (->> (str "/products/" product-id "/ticker")
       build-get-request
       http/request))

(defn get-trades 
  [product-id]
  (->> (str "/products/" product-id "/trades")
       build-get-request
       http/request))

;; Remember to use (t/today-at 00 00) to avoid sending time ahead of server time
(defn get-historic-rates
  ([product-id]
   (->> "/products/eth-usd/candles"
        build-get-request
        http/request))
  ([product-id start end granularity]
   (->> (str "/products/" product-id "/candles?start="
         start "&end=" end "&granularity=" granularity)
        build-get-request
        http/request)))
      
(defn get-product-stats
  [product-id]
  (->> (str "/products/" product-id "/stats")
       build-get-request
       http/request))

(defn get-currencies
  []
  (http/request (build-get-request "/currencies")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;; Private Endpoints ;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;; Accounts ;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-accounts 
  []
  (->> (build-get-request "/accounts")
       sign-request
       http/request))

(defn get-account-by-id
  [account-id]
  (->> (build-get-request (str "/accounts/" account-id))
       sign-request
       http/request))

(defn get-account-history
  [account-id & [paging-options]]
  (->> (build-get-request (str "/accounts/" account-id "/ledger"))
       (append-paging-options paging-options)
       sign-request
       http/request))

(defn get-account-holds
  [account-id & [paging-options]]
  (->> (build-get-request (str "/accounts/" account-id "/holds"))
       (append-paging-options paging-options)
       sign-request
       http/request))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;; Orders ;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

