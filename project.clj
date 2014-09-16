(defproject escurel "0.1.0-SNAPSHOT"
  :description "Secure Quick Reliable Login (SQRL) support for Clojure."
  :url "https://github.com/tmarble/escurel"
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "same as Clojure"}

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2322"]
                 [org.clojure/core.async "0.1.338.0-5c5012-alpha"]
                 [om "0.7.3"]
                 [javax.servlet/servlet-api "2.5"]
                 [http-kit "2.1.19"]
                 [compojure "1.1.9"]
                 [jarohen/chord "0.4.2" :exclusions [org.clojure/clojure]]]

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
