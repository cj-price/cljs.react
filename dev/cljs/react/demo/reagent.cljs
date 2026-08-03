(ns cljs.react.demo.reagent
  (:require ["react" :as react]
            [reagent.core :as r]
            [cljs.react.core :refer [Element]]
            [cljs.react.sx :refer [use-sx]]
            [cljs.react.demo.ui :refer [CodeAndOutput SectionTitle Muted Caption
                                        Stack]]
            [cljs.react.demo.util :refer [Section]])
  (:require-macros [cljs.react.core :refer [defnc]]))

;; Four interop directions, one round trip. Every foreign component here reads
;; theme values through `var(--cx-…)`, so it follows the theme with no adapter —
;; the same trick the JS-interop demos in `basics.cljs` use.

;; ── 1. JS component → cljs.react ─────────────────────────────────────────────
;; A raw React component (react/createElement, no CLJS machinery) used as a :tag.

(def RawStars
  (fn [^js props]
    (react/createElement "span"
      #js {:role "img"
           :aria-label (str (or (.-count props) 0) " stars")
           :style #js {:color "var(--cx-palette-warning-main)"
                       :fontSize "1.35rem" :letterSpacing "0.15em"}}
      (apply str (repeat (or (.-count props) 0) "★")))))

;; ── 2. cljs.react → JS component ─────────────────────────────────────────────
;; A defnc call returns a React element, so it drops straight into a JS
;; component's children. `RawFrame` is a plain React component that renders
;; whatever children it is handed.

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

;; ── 3. Reagent → cljs.react ──────────────────────────────────────────────────
;; A self-contained Reagent component with its own r/atom state. reactify-component
;; turns it into a React component that Element renders like any other :tag.

(defn reagent-counter
  [props]
  (let [n (r/atom (or (:start props) 0))]
    (fn [_]
      [:button {:type "button"
                :on-click #(swap! n inc)
                :style {:padding "0.5rem 1rem"
                        :border "1px solid var(--cx-palette-divider)"
                        :borderRadius "var(--cx-shape-border-radius)"
                        :background "var(--cx-palette-background-paper)"
                        :color "var(--cx-palette-text-primary)"
                        :cursor "pointer" :fontSize "0.9rem" :fontWeight 500}}
       "Reagent clicks: " @n])))

(def ReactifiedCounter (r/reactify-component reagent-counter))

;; ── 4. cljs.react → Reagent ──────────────────────────────────────────────────
;; A Reagent hiccup tree wrapping a cljs.react child — closing the round trip.
;; The parent is reactified so it can mount in the Element tree below.

(defn reagent-parent
  [_]
  [:div {:style {:display "flex" :flexDirection "column" :gap "0.5rem"
                 :padding "0.75rem"
                 :border "1px solid var(--cx-palette-divider)"
                 :borderRadius "var(--cx-shape-border-radius)"
                 :background "var(--cx-palette-surface-sunken)"}}
   [:span {:style {:fontSize "0.6875rem" :fontWeight 600
                   :textTransform "uppercase" :letterSpacing "0.05em"
                   :color "var(--cx-palette-text-secondary)"}}
    "Reagent hiccup"]
   (Chip {:label "cljs.react child"})])

(def ReactifiedParent (r/reactify-component reagent-parent))

;; ── Tab root ─────────────────────────────────────────────────────────────────

(defnc ReagentTab
  []
  (Section
    (SectionTitle {} "⚛️ Reagent Interop")
    (Muted {:style {:marginBottom "1.5rem"}}
      "cljs.react and Reagent render the same React tree, so components cross "
      "the boundary both ways. `Element` consumes any React component as a "
      ":tag; a `defnc` call is itself a React element. Reagent's "
      "`reactify-component` bridges the other direction.")

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
     {:title "Reagent → cljs.react"
      :code ";; reactify-component turns a Reagent component into a\n;; React component; Element renders it like any :tag.\n\n(defn reagent-counter [props]\n  (let [n (r/atom (or (:start props) 0))]\n    (fn [_]\n      [:button {:on-click #(swap! n inc)}\n       \"Reagent clicks: \" @n])))\n\n(def ReactifiedCounter\n  (r/reactify-component reagent-counter))\n\n(Element {:tag ReactifiedCounter :start 3})"}
     (Stack {:gap 1 :align :flex-start}
       (Element {:tag ReactifiedCounter :start 3})
       (Caption {:className (use-sx {:font-style :italic})}
         "Reagent's r/atom drives the re-render, inside the cljs.react tree.")))

    (CodeAndOutput
     {:title "cljs.react → Reagent"
      :code ";; A Reagent hiccup tree wrapping a cljs.react child.\n;; The parent is reactified to mount it here.\n\n(defn reagent-parent [_]\n  [:div ...\n   ;; a defnc call is a valid hiccup child\n   (Chip {:label \"cljs.react child\"})])\n\n(def ReactifiedParent\n  (r/reactify-component reagent-parent))\n\n(Element {:tag ReactifiedParent})\n\n;; For a prop-driven React component, Reagent also\n;; offers [:> ReactComp props] / adapt-react-class."}
     (Stack {:gap 1 :align :flex-start}
       (Element {:tag ReactifiedParent})
       (Caption {:className (use-sx {:font-style :italic})}
         "The blue Chip is a defnc component living inside Reagent hiccup.")))))
