(ns gdax-clojure.authentication
  (:require
    [gdax-clojure.utilities :refer :all]
    [clojure.data.codec.base64 :as b64]
    [pandect.algo.sha256 :refer :all]))

(defn- create-prehash-string
  [timestamp request]
  (str timestamp (clojure.string/upper-case (:method request)) 
    (parse-request-path (:url request)) (:body request)))
                    
(defn- create-http-signature
  [secret timestamp request]
  (let [secret-decoded (b64/decode (.getBytes secret))
        prehash-string (create-prehash-string timestamp request)
        hmac (sha256-hmac* prehash-string secret-decoded)]
    (-> hmac
        b64/encode
        String.)))

(defn- create-websocket-signature
  [secret timestamp]
  (let [secret-decoded (b64/decode (.getBytes secret))
        prehash-string (str timestamp "GET/users/self/verify")
        hmac (sha256-hmac* prehash-string secret-decoded)]
    (-> hmac
        b64/encode
        String.)))

(defn sign-request 
  [client request]
  (let [timestamp (get-timestamp)]
    (update-in request [:headers] merge {"CB-ACCESS-KEY" (:key client)
                                         "CB-ACCESS-SIGN" (create-http-signature (:secret client) timestamp request)
                                         "CB-ACCESS-TIMESTAMP" timestamp
                                         "CB-ACCESS-PASSPHRASE" (:passphrase client)})))

(defn sign-message
  [message {:keys [key secret passphrase]}]
  (let [timestamp (get-timestamp)]
    (merge message
           {:key key
            :passphrase passphrase
            :timestamp timestamp
            :signature (create-websocket-signature secret timestamp)})))
