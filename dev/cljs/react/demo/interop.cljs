(ns cljs.react.demo.interop
  (:require [cljs.react.core :refer [Element use-ref
                                     use-imperative-handle use-sync-external-store]]
            [cljs.react.sx :refer [use-sx]]
            [cljs.react.demo.ui :as ui :refer [CodeAndOutput Btn Row Stack
                                               SectionTitle Caption Alert]]
            [cljs.react.demo.util :refer [Span Section]])
  (:require-macros [cljs.react.core :refer [defnc]]))

;;;; use-imperative-handle — expose an imperative API through forward-ref

(defnc FancyInput :forward-ref
  [{:keys [ref placeholder]}]
  (let [input-ref (use-ref nil)]
    ;; Expose a small imperative API to the parent instead of the raw DOM node.
    (use-imperative-handle ref
      (fn []
        #js {:focus (fn [] (some-> @input-ref .focus))
             :clear (fn [] (when-let [el @input-ref] (set! (.-value el) "")))})
      [])
    ;; Styled by the same rules the CLJS TextInput uses, reached through
    ;; `ui/field-class` — a forward-ref component builds its own element, so it
    ;; cannot go through the TextInput wrapper.
    (Element {:tag "input"
              :ref input-ref
              :aria-label "Imperatively-controlled text field"
              :placeholder placeholder
              :className (ui/field-class)})))

(defnc ImperativeHandleDemo
  []
  (let [api-ref (use-ref nil)]
    (Stack {:gap 1.5}
      (FancyInput {:ref api-ref :placeholder "Controlled imperatively from the parent…"})
      (Row {:gap 1 :wrap? false}
        (Btn {:size :sm :full? true :onClick #(.focus @api-ref)} "Focus")
        (Btn {:variant :secondary :size :sm :full? true
              :onClick #(.clear @api-ref)}
          "Clear"))
      (Caption {:className (use-sx {:font-style :italic})}
        "The parent's ref holds the handle returned by use-imperative-handle, not the DOM node."))))

;;;; use-sync-external-store — subscribe to a non-atom external source

(defn- subscribe-online [cb]
  (.addEventListener js/window "online" cb)
  (.addEventListener js/window "offline" cb)
  (fn []
    (.removeEventListener js/window "online" cb)
    (.removeEventListener js/window "offline" cb)))

(defnc OnlineStatusDemo
  []
  (let [online? (use-sync-external-store
                  subscribe-online
                  (fn [] (.-onLine js/navigator))
                  (fn [] true))]
    (Stack {:gap 1.5}
      (Alert {:tone (if online? :success :error)
              :role "status" :aria-live "polite"}
        (Span {:aria-hidden "true"
               :className (use-sx {:display :inline-block :flex-shrink 0
                                   :width "0.625rem" :height "0.625rem"
                                   :border-radius "50%"
                                   :bgcolor "currentColor"})})
        (Span {:className (use-sx {:font-weight 500})}
          (if online? "Browser is online" "Browser is offline")))
      (Caption {:className (use-sx {:font-style :italic})}
        "Toggle your network (or DevTools offline mode) — the store re-renders on the navigator online/offline events."))))

(defnc InteropTab
  []
  (Section
    (SectionTitle {} "🔌 Interop — Imperative Handles & External Stores")

    (CodeAndOutput
     {:title "useImperativeHandle via forward-ref"
      :code "(defnc FancyInput :forward-ref\n  [{:keys [ref]}]\n  (let [input-ref (use-ref nil)]\n    (use-imperative-handle ref\n      (fn []\n        #js {:focus #(.focus @input-ref)\n             :clear #(set! (.-value @input-ref) \"\")})\n      [])\n    (Element {:tag \"input\" :ref input-ref})))\n\n(let [api (use-ref nil)]\n  (FancyInput {:ref api})\n  (Button {:onClick #(.focus @api)} \"Focus\"))"}
     (ImperativeHandleDemo))

    (CodeAndOutput
     {:title "useSyncExternalStore — online status"
      :code "(use-sync-external-store\n  (fn [cb]                       ; subscribe\n    (.addEventListener js/window \"online\" cb)\n    (.addEventListener js/window \"offline\" cb)\n    (fn [] ...remove listeners...))\n  (fn [] (.-onLine js/navigator)) ; snapshot\n  (fn [] true))                   ; server snapshot"}
     (OnlineStatusDemo))))
