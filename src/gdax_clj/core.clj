(ns gdax-clj.core
  (:require 
    [pandect.algo.sha256 :refer :all]
    [cheshire.core :refer :all]
    [clj-http.client :as http]
    [environ.core :refer [env]]
    [clj-time.core :as t]
    [clojure.data.codec.base64 :as b64]
    [clojure.data.json :as json]
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
  {:method method
   :url (str (:api-base-url config)
          (if (clojure.string/starts-with? path "/") path (str "/" path)))
   :accept :json
   :as :json
   :debug (:debug-requests config)})

(defn- build-get-request
  [path & [options]]
  (merge (build-base-request "GET" path)
         options))

(defn- build-post-request
  [path body & [options]]
  (merge (build-base-request "POST" path)
         {:body (json/write-str body)
          :content-type :json}
         options))

(defn- build-delete-request
  [path & [options]]
  (merge (build-base-request "DELETE" path)
         options))

(defn- map->query-string
  [params]
  (clojure.string/join "&"
    (for [[k v] params]
      (str (name k) "=" (java.net.URLEncoder/encode (str v))))))

(defn- append-query-params
  [query-params request]
  (if (empty? query-params)
    request
    (update-in request [:url] 
      #(str % 
        (if (clojure.string/includes? % "?") "&" "?") 
        (map->query-string query-params)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;; Authentication ;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- parse-request-path
  [request-url]
  (second (clojure.string/split request-url #".com")))

(defn- create-prehash-string
  [timestamp request]
  (str timestamp (clojure.string/upper-case (:method request)) 
    (parse-request-path (:url request)) (:body request)))

(def request {:method "get" 
              :url "https://api-public.sandbox.gdax.com/orders"
              :body {:side "buy"
                     :product-id "btc-usd"
                     :price "11500.00"
                     :size 2}})

;; (create-prehash-string 123456 request)
                    
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
       (append-query-params paging-options)
       sign-request
       http/request))

(defn get-account-holds
  [account-id & [paging-options]]
  (->> (build-get-request (str "/accounts/" account-id "/holds"))
       (append-query-params paging-options)
       sign-request
       http/request))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;; Orders ;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn place-order
  [side product-id & [options]]
  (let [body (merge options {:side side
                             :product_id (clojure.string/upper-case product-id)})]
    (->> (build-post-request "/orders" body)
         sign-request
         http/request)))

(defn place-limit-order
  [side product-id price size & [options]]
  (place-order side product-id (merge options {:price price
                                               :size size
                                               :type "limit"})))

(defn place-market-order
  [side product-id & [options]]
  (place-order side product-id (merge options {:type "market"})))

(defn place-stop-order
  [side product-id price & [options]]
  (place-order side product-id (merge options {:type "stop"
                                               :price price})))

(defn get-orders
  [& {:keys [statuses] :as options}]
  (let [query-string (clojure.string/join "&" (map #(str "status=" (name %)) statuses))
        rest-options (dissoc options :statuses)]
    (->> (build-get-request (str "/orders"
                                 (when-not (clojure.string/blank? query-string) "?")
                                 query-string))
         (append-query-params rest-options)
         sign-request
         http/request)))

(defn cancel-order
  [order-id]
  (->> (build-delete-request (str "/orders/" order-id))
       (sign-request)
       http/request))

(defn cancel-all
  [& [product-id]]
  (->> (build-delete-request 
          (str "/orders" (when-not (nil? product-id) (str "?product_id=" product-id))))
       sign-request
       http/request))

(defn get-order
  [order-id]
  (->> (build-get-request (str "/orders/" order-id))
       sign-request
       http/request))

(defn get-fills
  [& [options]]
  (->> (build-get-request "/fills")
       (append-query-params options)
       sign-request
       http/request))

(defn get-payment-methods
  []
  (->> (build-get-request "/payment-methods")
       sign-request
       http/request))

(defn get-coinbase-accounts
  []
  (->> (build-get-request "/coinbase-accounts")
       sign-request
       http/request))

(defn deposit-from-coinbase
  [amount currency coinbase-account-id]
  (->> (build-post-request 
         "/deposits/coinbase-account" 
         {:amount amount
          :currency (clojure.string/upper-case currency)
          :coinbase_account_id coinbase-account-id})
       sign-request
       http/request))

(defn withdraw-to-coinbase
  [amount currency coinbase-account-id]
  (->> (build-post-request 
         "/withdrawals/coinbase-account"
         {:amount amount
          :currency (clojure.string/upper-case currency)
          :coinbase_account_id coinbase-account-id})
       sign-request
       http/request))

;; TODO: try this with actual API instead of sandbax. If it works, also implement
;; deposit and withdraw for payment methods too
(defn withdraw-to-crypto-address
  [amount currency crypto-address]
  (->> (build-post-request
         "/withdrawals/crypto"
         {:amount amount
          :currency (clojure.string/upper-case currency)
          :crypto_address crypto-address})
       sign-request
       http/request))

(defn generate-fills-report
  [start-date end-date product-id & [options]]
  (let [params (merge options
                      {:type "fills"
                       :start_date start-date
                       :end_date end-date
                       :product_id (clojure.string/upper-case product-id)})]
    (->> (build-post-request "/reports" params)
         sign-request
         http/request)))

;; TODO: test this method
(defn generate-account-report
  [start-date end-date account-id & [options]]
  (let [params (merge options 
                      {:type "account"
                       :start_date start-date
                       :end_date end-date
                       :account_id (clojure.string/upper-case account-id)})]
    (->> (build-post-request "/reports" params)
         sign-request
         http/request)))

(defn get-report-status
  [report-id]
  (->> (build-get-request (str "/reports/" report-id))
       sign-request
       http/request))

(defn get-trailing-volume
  []
  (->> (build-get-request "/users/self/trailing-volume")
       sign-request
       http/request))
