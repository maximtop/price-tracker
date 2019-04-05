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
  (if (and (> (count tags) 1) (= (clojure.string/lower-case (first tags)) "html"))
    (rest tags)
    tags))

(extend-type js/NodeList
  ISeqable
  (-seq [array] (array-seq array 0)))

(defn count-siblings [el]
  (let [parent-node (.-parentNode el)]
    (if (nil? parent-node)
      0
      (count (array-seq (.-children parent-node))))))

(defn count-nth-child [el]
  (let [nth-counter (loop [prev-sibling (.-previousSibling el) counter 1]
                      (cond
                        (nil? prev-sibling) counter
                        :else (recur (.-previousSibling prev-sibling)
                                     (if (= 1 (.-nodeType prev-sibling))
                                       (inc counter)
                                       counter))))
        children (count-siblings el)]
    (if (> children 1)
      (str (.-tagName el) ":nth-child(" nth-counter ")")
      (.-tagName el))))

(defn get-selector [node]
  (let [result (loop [current-node node acc ()]
                 (let [parent-node (.-parentNode current-node)
                       id (.-id current-node)]
                   (cond
                     (not-empty id) (conj acc (str "#" id))
                     (nil? parent-node) acc
                     :else (recur parent-node (conj acc (clojure.string/lower-case (count-nth-child current-node)))))))]
    (clojure.string/join " > " (strip-html-tag result))))

(defn handle-mouse-over [e]
  "on mouse over add border to the element"
  (let [target (.-target e)]
    (do
      (log (get-selector target))
      (when (:prev-element @state)
        (return-prev-state))
      (update-prev-el target)
      (set! (.. target -style -border) "1px solid"))))

(defn init-assistant []
  (.addEventListener js/document "mouseover" (debounce handle-mouse-over 100)))

; -- main entry point -------------------------------------------------------------------------------------------------------

(defn init! []
  (log "CONTENT SCRIPT: init")
  (init-assistant)
  (connect-to-background-page!))
