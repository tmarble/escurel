(ns escurel.server
  (:require [clojure.string :as string]
            [org.httpkit.server :refer [run-server]]
            [compojure.core :refer [defroutes GET POST]]
            [compojure.route :as route]
            [compojure.handler :refer [site]]
            ))

(defonce server (atom nil))

(defn index [req]
  "<!DOCTYPE html>
<html lang=\"en\">
  <head>
    <meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\"/>
    <link href=\"css/escurel.css\" rel=\"stylesheet\"/>
    <title>escurel</title>
  </head>
  <body>
    <h1>escurel</h1>
    This is escurel
    <div id=\"app\"></div>
    <script src=\"js/react-0.11.1.js\" type=\"text/javascript\"></script>
    <script src=\"js/goog/base.js\" type=\"text/javascript\"></script>
    <script src=\"js/escurel.js\" type=\"text/javascript\"></script>
    <script type=\"text/javascript\">goog.require(\"escurel.client\")</script>
  </body>
</html>
")

(defn debug [req]
  (str
    "<!DOCTYPE html>
<html lang=\"en\">
  <head>
    <meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\"/>
    <link href=\"css/escurel.css\" rel=\"stylesheet\"/>
    <title>escurel: debug</title>
  </head>
  <body>
    <h1>escurel: debug</h1>
    <pre>"
    (string/replace (str req) "," ",\n")
    "</pre>
  </body>
</html>
"))

(defroutes all-routes
  (GET "/" [] index)
  (GET "/debug" [] debug)
  (POST "/sqrl" [] debug)
  (route/files "/" {:root "resources/public"})
  (route/not-found "<p>Page not found.</p>"))

(defn stop-server []
  (when-not (nil? @server)
    (@server :timeout 100)
    (reset! server nil)))

(defn -main [ & args ]
  (let [port (if args (Integer. (first args)) 8080)]
    (println "starting on port" port)
    (reset! server (run-server (site #'all-routes) {:port port}))))
