(ns coinbase-pro-clojure.core
  "Public and private endpoint functions and websocket feed functionality."
  (:require 
    [coinbase-pro-clojure.utilities :refer :all]
    [coinbase-pro-clojure.authentication :refer :all]
    [cheshire.core :refer :all]
    [clj-http.client :as http]
    [environ.core :refer [env]]
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
(def rest-url "https://api.pro.coinbase.com")
(def websocket-url "wss://ws-feed.gdax.com")
(def sandbox-rest-url "https://api-public.sandbox.gdax.com")
(def sandbox-websocket-url "wss://ws-feed-public.sandbox.pro.coinbase.com")
(def default-channels ["heartbeat"])

; (def my-client {:url rest-url
;                 :key (env :key)
;                 :secret (env :secret)
;                 :passphrase (env :passphrase)})

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
  ([client product-id opts]
   (->> (str (:url client) "/products/" product-id "/ticker")
        build-get-request
        (append-query-params opts)
        http/request)))

(defn get-trades
  ([client product-id]
   (get-trades client product-id {}))
  ([client product-id opts]
   (->> (str (:url client) "/products/" product-id "/trades")
        build-get-request
        (append-query-params opts)
        http/request)))
    
(defn get-historic-rates
  ([client product-id]
   (get-historic-rates client product-id {}))
  ([client product-id opts]
   (->> (str (:url client) "/products/" product-id "/candles")
        build-get-request
        (append-query-params opts)
        http/request)))

;; Example
; (get-historic-rates my-client "ETH-USD" {:start "2018-06-01" 
;                                          :end "2018-06-27"
;                                          :granularity (:1d granularities)})

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

(defn get-account
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
  [client opts]
    (->> (build-post-request (str (:url client) "/orders") opts)
         (sign-request client)
         http/request))

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
  [client opts]
  (->> (build-post-request 
         (str (:url client) "/deposits/payment-method") 
         opts)
       (sign-request client)
       http/request))

(defn withdraw-to-payment-method
  [client opts]
  (->> (build-post-request 
          (str (:url client) "/withdrawals/payment-method")
          opts)
       (sign-request client)
       http/request))

(defn get-coinbase-accounts
  [client]
  (->> (build-get-request (str (:url client) "/coinbase-accounts"))
       (sign-request client)
       http/request))

(defn deposit-from-coinbase
  [client opts]
  (->> (build-post-request 
         (str (:url client) "/deposits/coinbase-account") 
         opts)
       (sign-request client)
       http/request))

(defn withdraw-to-coinbase
  [client opts]
  (->> (build-post-request 
         (str (:url client) "/withdrawals/coinbase-account")
         opts)
       (sign-request client)
       http/request))

(defn withdraw-to-crypto-address
  [client opts]
  (->> (build-post-request
         (str (:url client) "/withdrawals/crypto")
         opts)
       (sign-request client)
       http/request))

(defn generate-fills-report
  [client opts]
     (->> (build-post-request (str (:url client) "/reports") opts)
          (sign-request client)
          http/request))

(defn generate-account-report
  [client opts]
  (->> (build-post-request (str (:url client) "/reports") opts)
       (sign-request client)
       http/request))

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





