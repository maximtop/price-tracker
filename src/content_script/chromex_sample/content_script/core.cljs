(ns chromex-sample.content-script.core
  (:require-macros [cljs.core.async.macros :refer [go-loop]])
  (:require [cljs.core.async :refer [<!]]
            [chromex.logging :refer-macros [log info warn error group group-end]]
            [chromex.protocols.chrome-port :refer [post-message!]]
            [chromex.ext.runtime :as runtime :refer-macros [connect]]))

; -- a message loop ---------------------------------------------------------------------------------------------------------

(defn process-message! [message]
  (log "CONTENT SCRIPT: got message:" message))

(defn run-message-loop! [message-channel]
  (log "CONTENT SCRIPT: starting message loop...")
  (go-loop []
    (when-some [message (<! message-channel)]
      (process-message! message)
      (recur))
    (log "CONTENT SCRIPT: leaving message loop")))

; -- a simple page analysis  ------------------------------------------------------------------------------------------------

(defn do-page-analysis! [background-port]
  (let [script-elements (.getElementsByTagName js/document "script")
        script-count (.-length script-elements)
        title (.-title js/document)
        msg (str "CONTENT SCRIPT: document '" title "' contains " script-count " script tags.")]
    (log msg)
    (post-message! background-port msg)))

(defn connect-to-background-page! []
  (let [background-port (runtime/connect)]
    (post-message! background-port "hello from CONTENT SCRIPT!")
    (run-message-loop! background-port)
    (do-page-analysis! background-port)))

; -- generate selector ------------------------------------------------------------------------------------------------------

(def state (atom {:prev-element nil :prev-element-background nil}))

(defn update-prev-el [el]
  (swap! state assoc :prev-element el)
  (swap! state assoc :prev-element-background (.. el -style -border)))

(defn return-prev-state []
  (let [prev-element (:prev-element @state)
        prev-element-border (:prev-element-border @state)]
    (set! (.. prev-element -style -border) prev-element-border)))

(defn handle-mouse-over [e]
  "on mouse over add border to the element"
  (let [target (. e -target)]
    (do
     (when (:prev-element @state)
       (return-prev-state))
     (update-prev-el target)
     (set! (.. target -style -border) "1px solid"))))

(defn generate-selector []
  (.addEventListener js/document "mouseover" handle-mouse-over))

; -- main entry point -------------------------------------------------------------------------------------------------------

(defn init! []
  (log "CONTENT SCRIPT: init")
  (generate-selector)
  (connect-to-background-page!))
