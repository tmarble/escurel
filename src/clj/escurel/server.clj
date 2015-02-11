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

(def index (slurp "resources/index.html"))

(def debug-template (slurp "resources/debug.html.template"))

(defn debug [req]
  (let [requests (map (fn [[k v]]
                        (format "<b>%s</b>%s\n" k v))
                      req)
        n (count requests)
        params (:params req)
        say (:say params)
        rs (reduce str requests)
        state @server-state]
    (when say
      (println "someone said:" say)
      (if-let [clients (seq (vals (:clients @server-state)))]
        (doseq [client clients]
          (put! (:ws-write client)
                (format "Somone said: '%s' at %s."
                        say
                        (java.util.Date.))))))
    (format debug-template n rs state)))

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

(defn connection-open-msg! [remote-addr ac]
  (println "Opened connection from"
           remote-addr
           "async-channel"
           async-channel))

(defn ws-handler [{:keys [remote-addr
                          async-channel
                          websocket?]}]
  (if-not websocket?
    "Sorry, websocket required"
    (let [client (get-client async-channel)
          {:keys [ws-read ws-write]} client]
      (connection-open-msg! remote-addr ac)
      (with-channel req ws-channel {:format :transit-json
                                    :read-ch ws-read
                                    :write-ch ws-write}
        (go-loop []
          (when-let [{:keys [message error] :as msg} (<! ws-channel)]
            (prn "Message received:" msg)
            (>! ws-channel
                (if error
                  (format "Error: '%s'." (pr-str msg))
                  (format "You passed: %s at %s with %s."
                          (pr-str message)
                          (java.util.Date.)
                          (str msg)))))
            (recur))))))

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
