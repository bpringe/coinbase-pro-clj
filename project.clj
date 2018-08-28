(defproject coinbase-pro-clj "0.1.0-SNAPSHOT"
  :description "A Clojure wrapper for the Coinbase Pro API (formerly GDAX)."
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [pandect "0.6.1"]
                 [clj-http "3.7.0"]
                 [cheshire "5.8.0"]
                 [environ "1.1.0"]
                 [clj-time "0.14.2"]
                 [org.clojure/data.codec "0.1.1"]
                 [stylefruits/gniazdo "1.0.1"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/test.check "0.9.0"]
                 [pjstadig/humane-test-output "0.8.3"]
                 [ring/ring-codec "1.1.1"]]
  :injections [(require 'pjstadig.humane-test-output)
               (pjstadig.humane-test-output/activate!)]
  :plugins [[lein-environ "1.1.0"]
            [com.jakemccrary/lein-test-refresh "0.22.0"]
            [lein-gorilla "0.4.0"]]
  :codox {:namespaces [coinbase-pro-clj.core]
          :metadata {:doc "FIXME: write docs"
                     :doc/format :markdown}
          :output-path "docs"})
