(defproject escurel "0.1.0-SNAPSHOT"
  :description "Secure Quick Reliable Login (SQRL) support for Clojure."
  :url "https://github.com/tmarble/escurel"
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "same as Clojure"}

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2850"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [org.omcljs/om "0.8.8"]
                 [javax.servlet/servlet-api "2.5"]
                 [http-kit "2.1.19"]
                 [compojure "1.3.1"]
                 ;; for chord
                 [com.fasterxml.jackson.core/jackson-core "2.5.1"]
                 [jarohen/chord "0.6.0" :exclusions [org.clojure/clojure commons-codec com.fasterxml.jackson.core/jackson-core]]]

  :plugins [[lein-cljsbuild "1.0.4"]]

  :source-paths ["src/clj"]

  :jvm-opts ["-server" "-Xmx256m"] ;; optional

  :cljsbuild {
    :builds [{:id "dev"
              :source-paths ["src/cljs"]
              :compiler {
                         ;; :main main.core
                         :output-dir "resources/public/js/"
                         :output-to "resources/public/js/escurel.js"
                         :optimizations :none
                         :source-map true}}
             ;; {:id "release"
             ;;  :source-paths ["src/cljs"]
             ;;  :compiler {
             ;;             ;; :main main.core
             ;;             :output-dir "resources/public/js/"
             ;;             :output-to "resources/public/js/escurel.js"
             ;;             :optimizations :advanced
             ;;             :pretty-print false}}
             ]}

  :main escurel.server
  )
