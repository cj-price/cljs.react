(ns cljs.react.demo.reagent
  (:require [reagent.core :as r]
            [cljs.react.core :refer [Element]]
            [cljs.react.sx :refer [use-sx]]
            [cljs.react.demo.interop :refer [Chip]]
            [cljs.react.demo.ui :refer [CodeAndOutput SectionTitle Muted Caption
                                        Stack]]
            [cljs.react.demo.util :refer [Section]])
  (:require-macros [cljs.react.core :refer [defnc]]))

;; Both directions of the Reagent boundary, one round trip. Every foreign
;; component here reads theme values through `var(--cx-…)`, so it follows the
;; theme with no adapter. Plain JS-component interop lives on the Interop tab.

;; ── 1. Reagent → cljs.react ──────────────────────────────────────────────────
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

;; ── 2. cljs.react → Reagent ──────────────────────────────────────────────────
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
      "the boundary both ways. Reagent's `reactify-component` turns a Reagent "
      "component into a React component that `Element` renders as a :tag; a "
      "`defnc` call is a valid hiccup child.")

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
