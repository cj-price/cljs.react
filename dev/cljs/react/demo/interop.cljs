(ns cljs.react.demo.interop
  (:require [cljs.react.core :refer [Element use-ref
                                     use-imperative-handle use-sync-external-store]]
            [cljs.react.demo.util :refer [CodeAndOutput Div P Span H2
                                          Button Section]])
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
    (Element {:tag "input"
              :ref input-ref
              :aria-label "Imperatively-controlled text field"
              :placeholder placeholder
              :className "w-full px-4 py-2.5 border-2 border-gray-200 rounded-lg focus:border-koi-orange focus:ring-2 focus:ring-koi-orange/20 outline-none transition-all"})))

(defnc ImperativeHandleDemo
  []
  (let [api-ref (use-ref nil)]
    (Div {:className "w-full space-y-3"}
      (FancyInput {:ref api-ref :placeholder "Controlled imperatively from the parent…"})
      (Div {:className "flex gap-2"}
        (Button {:className "flex-1 px-3 py-2 bg-koi-orange text-white rounded-lg font-medium text-sm shadow hover:bg-orange-600 transition-colors"
                 :onClick #(.focus @api-ref)}
          "Focus")
        (Button {:className "flex-1 px-3 py-2 bg-gray-200 text-gray-700 rounded-lg font-medium text-sm hover:bg-gray-300 transition-colors"
                 :onClick #(.clear @api-ref)}
          "Clear"))
      (P {:className "text-xs text-gray-500 italic"}
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
    (Div {:className "w-full space-y-3"}
      (Div {:role "status" :aria-live "polite"
            :className (str "flex items-center gap-2 p-3 rounded-lg border text-sm font-medium "
                            (if online?
                              "bg-emerald-50 border-emerald-200 text-emerald-700"
                              "bg-red-50 border-red-200 text-red-700"))}
        (Span {:aria-hidden true
               :className (str "inline-block w-2.5 h-2.5 rounded-full "
                               (if online? "bg-emerald-500" "bg-red-500"))})
        (if online? "Browser is online" "Browser is offline"))
      (P {:className "text-xs text-gray-500 italic"}
        "Toggle your network (or DevTools offline mode) — the store re-renders on the navigator online/offline events."))))

(defnc InteropTab
  []
  (Section
    (H2 "🔌 Interop — Imperative Handles & External Stores")

    (CodeAndOutput
     {:title "useImperativeHandle via forward-ref"
      :code "(defnc FancyInput :forward-ref\n  [{:keys [ref]}]\n  (let [input-ref (use-ref nil)]\n    (use-imperative-handle ref\n      (fn []\n        #js {:focus #(.focus @input-ref)\n             :clear #(set! (.-value @input-ref) \"\")})\n      [])\n    (Element {:tag \"input\" :ref input-ref})))\n\n(let [api (use-ref nil)]\n  (FancyInput {:ref api})\n  (Button {:onClick #(.focus @api)} \"Focus\"))"}
     (ImperativeHandleDemo))

    (CodeAndOutput
     {:title "useSyncExternalStore — online status"
      :code "(use-sync-external-store\n  (fn [cb]                       ; subscribe\n    (.addEventListener js/window \"online\" cb)\n    (.addEventListener js/window \"offline\" cb)\n    (fn [] ...remove listeners...))\n  (fn [] (.-onLine js/navigator)) ; snapshot\n  (fn [] true))                   ; server snapshot"}
     (OnlineStatusDemo))))
