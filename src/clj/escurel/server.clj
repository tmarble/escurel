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

(defn debug-say-msg! [msg]
  (println "Someone said: " msg))

(defn debug-said-msg! [client msg]
  (put! (:ws-write client)
        (format "Somone said: '%s' at %s."
                msg
                (java.util.Date.))))

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
      (debug-say-msg! say)
      (if-let [clients (seq (-> state :clients vals))]
        (doseq [client clients]
          (debug-said-msg! client say))))
    (format debug-template n rs state)))

(defn already-registered-client-msg! [ac]
  (println "Client already registered:" ac))

(defn already-registered-client! [ac client]
  (already-registered-client-msg! ac)
  client)

(defn adding-registered-client-msg! [ac]
  (println "Adding new client: " ac))

(defn adding-registered-client! [state-atom ac client]
  (adding-registered-client-msg! ac)
  (let [korks [:clients ac]
        state! (swap! state-atom assoc-in [:clients ac] client)]
    (get-in state! korks)))

(defn existing-client [state-atom & ks]
  (get-in state-atom (reduce conj [:clients] ks)))

(defn init-client-read-chan []
  (chan (async/sliding-buffer 10)))

(defn init-client-write-chan []
  (chan (async/dropping-buffer 10)))

(defn new-client []
  {:ws-read (init-client-read-chan)
   :ws-write  (init-client-write-chan)})

(defn get-client! [async-channel]
  (let [ac (str async-channel)]
    (if-let [client (existing-client server-state ac)]
      (already-registered-client! ac client)
      (adding-registered-client! server-state
                                 ac
                                 (new-client)))))

(defn connection-open-msg! [remote-addr ac]
  (println "Opened connection from"
           remote-addr
           "async-channel"
           ac))

(defn ws-handler [req]
  (let [{:keys [remote-addr
                async-channel
                websocket?]} req]
    (if-not websocket?
      "Sorry, websocket required"
      (let [client (get-client! async-channel)
            {:keys [ws-read ws-write]} client]
        (connection-open-msg! remote-addr async-channel)
        (with-channel req ws-channel {:format :transit-json
                                      :read-ch ws-read
                                      :write-ch ws-write}
          (go-loop []
            (when-let [{:keys [message error] :as msg} (<! ws-channel)]
              (prn "Message received:" msg)
              (>! ws-channel
                  (if error
                    (format "Error: '%s'." (pr-str msg))
                    (format "Client at %s said %s."
                            (java.util.Date.)
                            (str msg)))))
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
