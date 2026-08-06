(ns cljs.react.demo.interop
  (:require ["react" :as react]
            [cljs.react.core :refer [Element use-ref
                                     use-imperative-handle use-sync-external-store]]
            [cljs.react.sx :refer [use-sx]]
            [cljs.react.demo.ui :as ui :refer [CodeAndOutput Btn Row Stack
                                               SectionTitle Caption Alert]]
            [cljs.react.demo.util :refer [Span Section]])
  (:require-macros [cljs.react.core :refer [defnc]]))

;;;; JS component → cljs.react — a raw React component (react/createElement,
;;;; no CLJS machinery) used as a :tag. It reads theme values through
;;;; `var(--cx-…)`, so it follows the theme with no adapter.

(def RawStars
  (fn [^js props]
    (react/createElement "span"
      #js {:role "img"
           :aria-label (str (or (.-count props) 0) " stars")
           :style #js {:color "var(--cx-palette-warning-main)"
                       :fontSize "1.35rem" :letterSpacing "0.15em"}}
      (apply str (repeat (or (.-count props) 0) "★")))))

;;;; cljs.react → JS component — a defnc call returns a React element, so it
;;;; drops straight into a JS component's children. `RawFrame` is a plain React
;;;; component that renders whatever children it is handed. `Chip` is also used
;;;; by the Reagent tab's hiccup demo.

(defnc Chip
  [{:keys [label]}]
  (Element {:tag "span"
            :className (use-sx {:display :inline-flex :align-items :center
                                :px 1 :py 0.25 :border-radius "999px"
                                :bgcolor :palette.primary.main
                                :color :palette.primary.contrast-text
                                :font-size "0.75rem" :font-weight 600
                                :letter-spacing "0.02em"})}
    label))

(def RawFrame
  (fn [^js props]
    (react/createElement "div"
      #js {:style #js {:display "flex" :flexDirection "column" :gap "0.5rem"
                       :padding "0.75rem"
                       :border "2px dashed var(--cx-palette-divider)"
                       :borderRadius "var(--cx-shape-border-radius)"
                       :background "var(--cx-palette-surface-sunken)"}}
      (react/createElement "span"
        #js {:style #js {:fontSize "0.6875rem" :fontWeight 600
                         :textTransform "uppercase" :letterSpacing "0.05em"
                         :color "var(--cx-palette-text-secondary)"}}
        (.-title props))
      (.-children props))))

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
    (SectionTitle {} "🔌 JS Interop")

    (CodeAndOutput
     {:title "JS component → cljs.react"
      :code ";; Any React component is a :tag — no adapter needed.\n\n(def RawStars\n  (fn [^js props]\n    (react/createElement \"span\" #js {...}\n      (apply str (repeat (.-count props) \"★\")))))\n\n(Element {:tag RawStars :count 4})"}
     (Element {:tag RawStars :count 4}))

    (CodeAndOutput
     {:title "cljs.react → JS component"
      :code ";; A defnc call returns a React element, so it nests\n;; directly inside a JS component's children.\n\n(defnc Chip [{:keys [label]}]\n  (Element {:tag \"span\" ...} label))\n\n(Element {:tag RawFrame :title \"Rendered by a JS component\"}\n  (Chip {:label \"cljs.react child\"}))"}
     (Element {:tag RawFrame :title "Rendered by a JS component"}
       (Chip {:label "cljs.react child"})))

    (CodeAndOutput
     {:title "useImperativeHandle via forward-ref"
      :code "(defnc FancyInput :forward-ref\n  [{:keys [ref]}]\n  (let [input-ref (use-ref nil)]\n    (use-imperative-handle ref\n      (fn []\n        #js {:focus #(.focus @input-ref)\n             :clear #(set! (.-value @input-ref) \"\")})\n      [])\n    (Element {:tag \"input\" :ref input-ref})))\n\n(let [api (use-ref nil)]\n  (FancyInput {:ref api})\n  (Button {:onClick #(.focus @api)} \"Focus\"))"}
     (ImperativeHandleDemo))

    (CodeAndOutput
     {:title "useSyncExternalStore — online status"
      :code "(use-sync-external-store\n  (fn [cb]                       ; subscribe\n    (.addEventListener js/window \"online\" cb)\n    (.addEventListener js/window \"offline\" cb)\n    (fn [] ...remove listeners...))\n  (fn [] (.-onLine js/navigator)) ; snapshot\n  (fn [] true))                   ; server snapshot"}
     (OnlineStatusDemo))))
