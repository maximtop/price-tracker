(ns chromex-sample.content-script.core
  (:require-macros [cljs.core.async.macros :refer [go-loop]])
  (:require [cljs.core.async :refer [<!]]
            [chromex.logging :refer-macros [log info warn error group group-end]]
            [chromex.protocols.chrome-port :refer [post-message!]]
            [chromex.ext.runtime :as runtime :refer-macros [connect]])
  (:import [goog.async Throttle Debouncer]))
; -- helper functions -------------------------------------------------------------------------------------------------------

(defn disposable->function [disposable listener interval]
  (let [disposable-instance (disposable. listener interval)]
    (fn [& args]
      (.apply (.-fire disposable-instance) disposable-instance (to-array args)))))

(defn throttle [listener interval]
  (disposable->function Throttle listener interval))

(defn debounce [listener interval]
  (disposable->function Debouncer listener interval))

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

(defn strip-html-tag [tags]
  (if (and (> (count tags) 1) (= (first tags) "HTML"))
    (rest tags)
    tags))

; TODO handle nth siblings
; TODO do not change letter case for id
(defn log-target-selector [target]
  (let [result (loop [current-target target tags ()]
                 (let [parent-node (.-parentNode current-target)
                       id (.-id current-target)]
                   (cond
                     (not-empty id) (conj tags (str "#" id))
                     (nil? parent-node) tags
                     :else (recur parent-node (conj tags (.-tagName current-target))))))]
    (log (clojure.string/lower-case (clojure.string/join " > " (strip-html-tag result))))))

(defn handle-mouse-over [e]
  "on mouse over add border to the element"
  (let [target (. e -target)]
    (do
      (log-target-selector target)
      (when (:prev-element @state)
        (return-prev-state))
      (update-prev-el target)
      (set! (.. target -style -border) "1px solid"))))

(defn generate-selector []
  (.addEventListener js/document "mouseover" (debounce handle-mouse-over 100)))

; -- main entry point -------------------------------------------------------------------------------------------------------

(defn init! []
  (log "CONTENT SCRIPT: init")
  (generate-selector)
  (connect-to-background-page!))
