(defproject coinbase-pro-clj "1.0.0"
  :description "A Clojure wrapper for the Coinbase Pro API (formerly GDAX)."
  :url "https://github.com/bpringe/coinbase-pro-clj"
  :license {:name "MIT"
            :url "https://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [pandect "0.6.1"]
                 [clj-http "3.7.0"]
                 [cheshire "5.8.0"]
                 [org.clojure/data.codec "0.1.1"]
                 [stylefruits/gniazdo "1.0.1"]
                 [org.clojure/data.json "0.2.6"]
                 [ring/ring-codec "1.1.1"]]
  :codox {:namespaces [coinbase-pro-clj.core]
          :metadata {:doc "FIXME: write docs"
                     :doc/format :markdown}
          :output-path "docs"}
  :set-version {:updates [{:path "README.md"}]})
