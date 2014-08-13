(defproject escurel "0.1.0-SNAPSHOT"
  :description "Secure Quick Reliable Login (SQRL) support for Clojure."
  :url "https://github.com/tmarble/escurel"
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "same as Clojure"}

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2280"]
                 [org.clojure/core.async "0.1.303.0-886421-alpha"]
                 [om "0.7.1"]
                 [javax.servlet/servlet-api "2.5"]
                 [http-kit "2.1.18"]
                 [compojure "1.1.8"]]

  :plugins [[lein-cljsbuild "1.0.4-SNAPSHOT"]]

  :source-paths ["src/clj"]

  :jvm-opts ["-server" "-Xmx256m"] ;; optional

  :cljsbuild {
    :builds [{:id "escurel"
              :source-paths ["src/cljs"]
              :compiler {
                         :output-dir "resources/public/js/"
                         :output-to "resources/public/js/escurel.js"
                         :optimizations :none
                         :source-map true}}]}

  :main escurel.server
  )
