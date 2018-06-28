(ns gdax-clojure.core
  "Public and private endpoint functions and websocket feed functionality."
  (:require 
    [gdax-clojure.utilities :refer :all]
    [gdax-clojure.authentication :refer :all]
    [cheshire.core :refer :all]
    [clj-http.client :as http]
    [environ.core :refer [env]]
    [clj-time.core :as t]
    [clojure.pprint :refer [pprint]]
    [gniazdo.core :as ws])
  (:import (org.eclipse.jetty.websocket.client WebSocketClient)
           (org.eclipse.jetty.util.ssl SslContextFactory)))

;; ## Convenience/config values

(def granularities {:1m 60
                    :5m 300
                    :15m 900
                    :1h 3600
                    :6h 21600
                    :1d 86400})
(def rest-url "https://api.gdax.com")
(def websocket-url "wss://ws-feed.gdax.com")
(def sandbox-rest-url "https://public.sandbox.gdax.com")
(def sandbox-websocket-url "wss://ws-feed-public.sandbox.gdax.com")
(def default-channels ["heartbeat"])

(def my-client {:url rest-url
                :key (env :key)
                :secret (env :secret)
                :passphrase (env :passphrase)})

(def my-sandbox-client {:url sandbox-rest-url
                        :key (env :sandbox-key)
                        :secret (env :sandbox-secret)
                        :passphrase (env :sandbox-passphrase)})

;; ## Public endpoints

;; - `client` will take the following shape
;; {:url
;;  :key
;;  :secret
;;  :passphrase}

(defn get-time
  [client]
  (http/request (build-get-request (str (:url client) "/time"))))

(defn get-products
  [client]
  (http/request (build-get-request (str (:url client) "/products"))))

(defn get-order-book
  ([client product-id]
   (get-order-book client product-id 1))
  ([client product-id level]
   (->> (str (:url client) "/products/" product-id "/book?level=" level)
        build-get-request
        http/request)))
  
(defn get-ticker
  ([client product-id]
   (get-ticker client product-id {}))
  ([client product-id paging-opts]
   (->> (str (:url client) "/products/" product-id "/ticker")
        build-get-request
        (append-query-params paging-opts)
        http/request)))

(defn get-trades
  ([client product-id]
   (get-trades client product-id {}))
  ([client product-id paging-opts]
   (->> (str (:url client) "/products/" product-id "/trades")
        build-get-request
        (append-query-params paging-opts)
        http/request)))
    
(defn get-historic-rates
  ([client product-id]
   (->> (str (:url client) "/products/" product-id "/stats")
        build-get-request
        http/request))
  ([client product-id start end granularity]
   (->> (str (:url client) "/products/" product-id "/candles?start="
           start "&end=" end "&granularity=" granularity)
        build-get-request
        http/request)))
       
(defn get-product-stats
  [client product-id]
  (->> (str (:url client) "/products/" product-id "/stats")
       build-get-request
       http/request))
     
(defn get-currencies
  [client]
  (http/request (build-get-request (str (:url client) "/currencies"))))

;; ## Private endpoints

(defn get-accounts 
  [client]
  (->> (build-get-request (str (:url client) "/accounts"))
       (sign-request client)
       http/request))

(defn get-account-by-id
  [client account-id]
  (->> (build-get-request (str (:url client) "/accounts/" account-id))
       (sign-request client)
       http/request))

(defn get-account-history
  ([client account-id]
   (get-account-history client account-id {}))
  ([client account-id paging-opts]
   (->> (build-get-request (str (:url client) "/accounts/" account-id "/ledger"))
        (append-query-params paging-opts)
        (sign-request client)
        http/request)))

(defn get-account-holds
  ([client account-id]
   (get-account-holds client account-id {}))
  ([client account-id paging-opts]
   (->> (build-get-request (str (:url client) "/accounts/" account-id "/holds"))
        (append-query-params paging-opts)
        (sign-request client)
        http/request)))

(defn place-order
  ([client side product-id]
   (place-order client side product-id {}))
  ([client side product-id opts]
   (let [body (merge opts {:side side
                              :product_id (clojure.string/upper-case product-id)})]
     (->> (build-post-request (str (:url client) "/orders") body)
          (sign-request client)
          http/request))))

(defn place-limit-order
  ([client side product-id price size]
   (place-limit-order client side product-id price size {}))
  ([client side product-id price size opts]
   (place-order client side product-id (merge opts {:price price
                                                    :size size
                                                    :type "limit"}))))

(defn place-market-order
  ([client side product-id]
   (place-market-order client side product-id {}))
  ([client side product-id opts]
   (place-order client side product-id (merge opts {:type "market"}))))

(defn place-stop-order
  ([client side product-id price]
   (place-stop-order client side product-id price {}))
  ([client side product-id price opts]
   (place-order client side product-id (merge opts {:type "stop"
                                                    :price price}))))

(defn get-orders
  ([client]
   (get-orders client {:status ["all"]}))
  ([client opts]
   (let [query-string (clojure.string/join "&" (map #(str "status=" %) (:status opts)))
         rest-opts (dissoc opts :status)]
    (->> (build-get-request (str (:url client)
                                 "/orders"
                                 (when-not (clojure.string/blank? query-string) "?")
                                 query-string))
         (append-query-params rest-opts)
         (sign-request client)
         http/request))))

(defn cancel-order
  [client order-id]
  (->> (build-delete-request (str (:url client) "/orders/" order-id))
       (sign-request client)
       http/request))

(defn cancel-all
  ([client]
   (cancel-all client nil))  
  ([client product-id]
   (->> (build-delete-request 
          (str (:url client) "/orders" (when-not (nil? product-id) (str "?product_id=" product-id))))
        (sign-request client)
        http/request)))

(defn get-order
  [client order-id]
  (->> (build-get-request (str (:url client) "/orders/" order-id))
       (sign-request client)
       http/request))

(defn get-fills
  ([client]
   (get-fills client {}))
  ([client opts]
   (->> (build-get-request (str (:url client) "/fills"))
        (append-query-params opts)
        (sign-request client)
        http/request)))

(defn get-payment-methods
  [client]
  (->> (build-get-request (str (:url client) "/payment-methods"))
       (sign-request client)
       http/request))

(defn deposit-from-payment-method
  [client amount currency payment-method-id]
  (->> (build-post-request 
         (str (:url client) "/deposits/payment-method") 
         {:amount amount
          :currency (clojure.string/upper-case currency)
          :payment_method_id payment-method-id})
       (sign-request client)
       http/request))

(defn withdraw-to-payment-method
  [client amount currency payment-method-id]
  (->> (build-post-request 
          (str (:url client) "/withdrawals/payment-method")
          {:amount amount
           :currency (clojure.string/upper-case currency)
           :payment_method_id payment-method-id})
       (sign-request client)
       http/request))

(defn get-coinbase-accounts
  [client]
  (->> (build-get-request (str (:url client) "/coinbase-accounts"))
       (sign-request client)
       http/request))

(defn deposit-from-coinbase
  [client amount currency coinbase-account-id]
  (->> (build-post-request 
         (str (:url client) "/deposits/coinbase-account") 
         {:amount amount
          :currency (clojure.string/upper-case currency)
          :coinbase_account_id coinbase-account-id})
       (sign-request client)
       http/request))

(defn withdraw-to-coinbase
  [client amount currency coinbase-account-id]
  (->> (build-post-request 
         (str (:url client) "/withdrawals/coinbase-account")
         {:amount amount
          :currency (clojure.string/upper-case currency)
          :coinbase_account_id coinbase-account-id})
       (sign-request client)
       http/request))

(defn withdraw-to-crypto-address
  [client amount currency crypto-address]
  (->> (build-post-request
         (str (:url client) "/withdrawals/crypto")
         {:amount amount
          :currency (clojure.string/upper-case currency)
          :crypto_address crypto-address})
       (sign-request client)
       http/request))

(defn generate-fills-report
  ([client start-date end-date product-id]
   (generate-fills-report client start-date end-date product-id {}))
  ([client start-date end-date product-id opts]
   (let [params (merge opts
                       {:type "fills"
                        :start_date start-date
                        :end_date end-date
                        :product_id (clojure.string/upper-case product-id)})]
     (->> (build-post-request (str (:url client) "/reports") params)
          (sign-request client)
          http/request))))

(defn generate-account-report
  ([client start-date end-date account-id]
   (generate-account-report client start-date end-date account-id {}))
  ([client start-date end-date account-id opts]
   (let [params (merge opts 
                       {:type "account"
                        :start_date start-date
                        :end_date end-date
                        :account_id (clojure.string/upper-case account-id)})]
     (->> (build-post-request (str (:url client) "/reports") params)
          (sign-request client)
          http/request))))

(defn get-report-status
  [client report-id]
  (->> (build-get-request (str (:url client) "/reports/" report-id))
       (sign-request client)
       http/request))

(defn get-trailing-volume
  [client]
  (->> (build-get-request (str (:url client) "/users/self/trailing-volume"))
       (sign-request client)
       http/request))

;; ## Websocket feed

(defn- get-subscribe-message
  [opts]
  (let [message {:type "subscribe" 
                 :product_ids (:product_ids opts) 
                 :channels (or (:channels opts) default-channels)}]
    (if (contains-many? opts :key :secret :passphrase)
      (sign-message message opts)
      message)))

(defn- get-unsubscribe-message
  ([product_ids]
   (get-unsubscribe-message product_ids []))
  ([product_ids channels]
   {:type "unsubscribe" 
    :product_ids product_ids 
    :channels channels}))

;; - `opts` will take the following shape
;; {:product_ids
;;  :channels (optional)
;;  :key (optional)
;;  :secret (optional)
;;  :passphrase (optional)}
(defn subscribe
  [connection opts]
  (->> (get-subscribe-message opts)
       edn->json
       (ws/send-msg connection)))

(defn unsubscribe
  [connection opts]
  (->> (get-unsubscribe-message opts)
       edn->json
       (ws/send-msg connection)))

(defn close
  [connection]
  (ws/close connection))

(defn- get-socket-connection
  [url opts]
  (let [client (WebSocketClient. (SslContextFactory.))]
    (.setMaxTextMessageSize (.getPolicy client) (* 1024 1024))
    (.start client)
    (ws/connect
      url
      :client client
      :on-connect (or (:on-connect opts) (constantly nil))
      :on-receive (or (:on-receive opts) (constantly nil))
      :on-close (or (:on-close opts) (constantly nil))
      :on-error (or (:on-error opts) (constantly nil)))))
      
;; - `opts` will take the following shape
;; {:channels
;;  :sandbox
;;  :on-connect
;;  :on-receive
;;  :on-close
;;  :on-error
;;  :key
;;  :secret
;;  :passphrase}
(defn create-websocket-connection
  ([product_ids]
   (create-websocket-connection product_ids {}))
  ([product_ids opts]
   (let [url (if (:sandbox opts) sandbox-websocket-url websocket-url)
         connection (get-socket-connection url opts)]
     ;; subscribe immediately so the connection isn't lost
     (subscribe connection (merge {:product_ids product_ids} opts))
     connection)))





