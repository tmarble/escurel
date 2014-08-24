(ns escurel.server
  (:require [clojure.string :as string]
            [clojure.core.async :as async
             :refer [chan <! >! put! go-loop]]
            [org.httpkit.server :refer [run-server]]
            [compojure.core :refer [defroutes GET POST]]
            [compojure.route :as route]
            [compojure.handler :refer [site]]
            [chord.http-kit :refer [with-channel]]))

(def server-state (atom {:server nil :clients {}}))

(defn index [req]
  "<!DOCTYPE html>
<html lang=\"en\">
  <head>
    <meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\"/>
    <link href=\"css/escurel.css\" rel=\"stylesheet\"/>
    <title>escurel</title>
  </head>
  <body>
    <div id=\"app\"></div>
    <script src=\"js/react-0.11.1.js\" type=\"text/javascript\"></script>
    <script src=\"js/goog/base.js\" type=\"text/javascript\"></script>
    <script src=\"js/escurel.js\" type=\"text/javascript\"></script>
    <script type=\"text/javascript\">goog.require(\"escurel.client\")</script>
  </body>
</html>
")

(defn debug [req]
  (let [requests (map
                   #(str "<b>" (first %) "</b> " (second %) "\n")
                   req)
        n (count requests)
        params (:params req)
        say (:say params)]
    (when say
      (println "someone said:" say)
      (let [clients (vals (:clients @server-state))]
        (when clients
          (doseq [client clients]
            (put! (:ws-write client)
              (format "Somone said: '%s' at %s." say (java.util.Date.)))))))
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
      "The request contains " n " items:\n"
      (apply str requests) ;; realize lazy seq
      "server-state:"
      @server-state
      "</pre>
  </body>
</html>
")))

(defn get-client [async-channel]
  (let [ac (str async-channel)
        existing-client (get-in @server-state [:clients ac])]
    (if existing-client
      (do
        (println "client already registered:" ac)
        existing-client)
      (let [new-client {:ws-read (chan (async/sliding-buffer 10))
                        :ws-write (chan (async/dropping-buffer 10))}]
        (println "adding new client:" ac)
        (swap! server-state
          #(assoc-in % [:clients ac] new-client))
        new-client))))

(defn ws-handler [req]
  (if (not (:websocket? req))
    "Sorry, websocket required"
    (let [remote-addr (:remote-addr req)
          async-channel (:async-channel req)
          client (get-client async-channel)
          ws-read (:ws-read client)
          ws-write (:ws-write client)]
      (println "Opened connection from" remote-addr
        "async-channel" async-channel)
      (with-channel req ws-channel {:format :str
                                    :read-ch ws-read
                                    :write-ch ws-write}
        (go-loop []
          (when-let [{:keys [message error] :as msg} (<! ws-channel)]
            (prn "Message received:" msg)
            (>! ws-channel (if error
                             (format "Error: '%s'." (pr-str msg))
                             (format "You passed: %s at %s with %s." (pr-str message) (java.util.Date.) (str msg))
                             ))
            (recur)))))))

(defroutes all-routes
  (GET "/" [] index)
  (GET "/debug" [] debug)
  (POST "/sqrl" [] debug)
  (GET "/ws" req (ws-handler req))
  (route/files "/" {:root "resources/public"})
  (route/not-found "<p>Page not found.</p>"))

(defn stop-server []
  (let [server (:server @server-state)]
    (when-not (nil? server)
      (server :timeout 100)
      (swap! server-state
        #(assoc-in % [:server] nil)))))

(defn -main [ & args ]
  (let [port (if args (Integer. (first args)) 8080)]
    (println "starting on port" port)
    (let [server (run-server (site #'all-routes) {:port port})]
      (swap! server-state
        #(assoc-in % [:server] server)))))
