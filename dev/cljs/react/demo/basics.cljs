(ns cljs.react.demo.basics
  (:require ["react" :as react]
            [cljs.react.core :refer [Element use-state use-ref]]
            [cljs.react.sx :refer [use-sx]]
            [cljs.react.demo.ui :refer [CodeAndOutput Btn Row Card CardTitle
                                        SectionTitle]]
            [cljs.react.demo.util :refer [Section]])
  (:require-macros [cljs.react.core :refer [defnc]]))

(defnc HelloWorld
  []
  (Element {:tag "div"
            :className (use-sx {:font-size "1.25rem" :font-weight 500
                                :color :palette.text.primary})}
    "Hello" " " "World!"))

(defnc Greeting
  [{:keys [name emoji]}]
  (Element {:tag "div"
            :className (use-sx {:font-size "1.25rem"
                                :color :palette.text.primary})}
    (Element {:tag "span" :className (use-sx {:font-size "1.5rem" :mr 1})} emoji)
    (Element {:tag "strong" :className (use-sx {:font-weight 600
                                                :color :palette.primary.main})}
      "Hello, " name "!")))

(defnc CardWithChildren
  [{:keys [title children]}]
  (Card {}
    (CardTitle {} title)
    (apply Element {:tag "div"
                    :className (use-sx {:display :flex :flex-direction :column
                                        :gap 1 :font-size "0.875rem"
                                        :color :palette.text.secondary})}
      children)))

;; ── Iterating over children ──────────────────────────────────────────────────
;; `:children` arrives as a CLJS seq — iterate it directly with `for`.

(defnc NumberedList
  [{:keys [children]}]
  ;; Every class is computed here rather than in the loop. `for` is lazy and
  ;; React realizes it after this render returns, so a `use-sx` in the body
  ;; would be a hook call with no dispatcher installed.
  (let [item-cls (use-sx {:display :flex :align-items :center :gap 1.5
                          :px 1.25 :py 1 :border-radius 1
                          :bgcolor :palette.surface.sunken
                          :border "1px solid" :border-color :palette.divider})
        num-cls  (use-sx {:display :inline-flex :flex-shrink 0
                          :align-items :center :justify-content :center
                          :width "1.5rem" :height "1.5rem"
                          :border-radius "999px"
                          :bgcolor :palette.primary.main
                          :color :palette.primary.contrast-text
                          :font-size "0.75rem" :font-weight 700})
        text-cls (use-sx {:flex 1 :font-size "0.9rem"
                          :color :palette.text.primary})]
    (Element {:tag "ol"
              :className (use-sx {:display :flex :flex-direction :column :gap 1
                                  :p 0 :m 0 :list-style :none})}
      (for [[idx item] (map-indexed vector children)]
        (Element {:tag "li" :key idx :className item-cls}
          (Element {:tag "span" :className num-cls} (inc idx))
          (Element {:tag "div" :className text-cls} item))))))

(defnc IterChildrenDemo []
  (NumberedList nil
    (Element {:tag "span"} "Receive children as a regular CLJS arg")
    (Element {:tag "span"} ":children is a seq — iterate it directly")
    (Element {:tag "span"} "Iterate with for — don't forget :key")))

;; ── Vector of elements passed as a prop ──────────────────────────────────────

(defnc BreadcrumbLink
  [{:keys [href children]}]
  (Element {:tag "a" :href href
            :className (use-sx {:color :palette.primary.main
                                :text-decoration :none :font-weight 500
                                :&:hover {:text-decoration :underline}})}
    children))

(defnc BreadcrumbCurrent
  [{:keys [children]}]
  (Element {:tag "span"
            :className (use-sx {:color :palette.text.primary :font-weight 600})}
    children))

(defnc Breadcrumbs
  [{:keys [items]}]
  (let [seg-cls (use-sx {:display :flex :align-items :center :gap 0.5})
        sep-cls (use-sx {:color :palette.text.disabled})]
    (Element {:tag "nav"
              :className (use-sx {:display :flex :flex-wrap :wrap
                                  :align-items :center :gap 0.5
                                  :font-size "0.9rem"})}
      (for [[idx item] (map-indexed vector items)]
        (Element {:tag "span" :key idx :className seg-cls}
          (when (pos? idx)
            (Element {:tag "span" :className sep-cls} "/"))
          item)))))

(defnc VectorPropDemo []
  (Breadcrumbs
    {:items [(BreadcrumbLink {:href "#"} "Home")
             (BreadcrumbLink {:href "#"} "Library")
             (BreadcrumbLink {:href "#"} "Topics")
             (BreadcrumbCurrent {} "ClojureScript")]}))

;; ── JS interop: props ─────────────────────────────────────────────────────────
;; Raw React function components — pretend these came from an npm package. Each
;; one uses `react/createElement` directly, so no CLJS machinery is involved on
;; the component side, and none of them requires `cljs.react.sx`.
;;
;; They still follow the theme, because sx puts every theme value behind a CSS
;; custom property. `var(--cx-…)` is plain CSS: it crosses the interop boundary
;; with no adapter, and these components stay legible in dark mode without
;; knowing the theme exists.

(def RawBadge
  (fn [^js props]
    (react/createElement "span"
      #js {:style #js {:display "inline-block"
                       :padding "0.25rem 0.75rem"
                       :borderRadius "9999px"
                       :background "var(--cx-palette-info-main)"
                       :color "var(--cx-palette-info-contrast-text)"
                       :fontSize "0.75rem"
                       :fontWeight 700
                       :letterSpacing "0.05em"
                       :textTransform "uppercase"}}
      (.-label props))))

(defn Badge
  "Thin CLJS adapter so callers write (Badge {...}) instead of
  (Element {:tag RawBadge ...})."
  [props]
  (Element (assoc props :tag RawBadge)))

;; ── JS interop: event handlers ────────────────────────────────────────────────

(def RawButton
  (fn [^js props]
    (react/createElement "button"
      #js {:type "button"
           :onClick (.-onClick props)
           :style #js {:padding "0.5rem 1rem"
                       :borderRadius "var(--cx-shape-border-radius)"
                       :border "1px solid var(--cx-palette-divider)"
                       :background "var(--cx-palette-background-paper)"
                       :color "var(--cx-palette-text-primary)"
                       :cursor "pointer"
                       :fontSize "0.9rem"}}
      (.-label props))))

(defnc ClickDemo []
  (let [clicks (use-state 0)]
    (Row {:gap 1.5}
      (Element {:tag RawButton :label "Click me" :onClick #(swap! clicks inc)})
      (Element {:tag "span"
                :className (use-sx {:font-size "0.875rem"
                                    :color :palette.text.secondary})}
        "Clicks: " @clicks))))

;; ── JS interop: children ──────────────────────────────────────────────────────

(def RawPanel
  (fn [^js props]
    (react/createElement "div"
      #js {:style #js {:border "2px dashed var(--cx-palette-divider)"
                       :borderRadius "var(--cx-shape-border-radius)"
                       :padding "1rem"
                       :background "var(--cx-palette-surface-sunken)"
                       :color "var(--cx-palette-text-secondary)"}}
      (react/createElement "div"
        #js {:style #js {:fontWeight 600
                         :marginBottom "0.5rem"
                         :color "var(--cx-palette-text-primary)"}}
        (.-title props))
      (.-children props))))

;; ── JS interop: refs ──────────────────────────────────────────────────────────

(def RawTextBox
  (react/forwardRef
    (fn [^js props ref]
      (react/createElement "input"
        #js {:ref ref
             :type "text"
             :placeholder (.-placeholder props)
             :style #js {:padding "0.5rem 0.75rem"
                         :border "2px solid var(--cx-palette-divider)"
                         :borderRadius "var(--cx-shape-border-radius)"
                         :background "var(--cx-palette-background-paper)"
                         :color "var(--cx-palette-text-primary)"
                         :fontSize "0.9rem"}}))))

(defnc FocusDemo []
  (let [input-ref (use-ref nil)]
    (Row {:gap 1}
      (Element {:tag RawTextBox
                :ref input-ref
                :placeholder "Some text to select"
                :defaultValue "Hello from CLJS"})
      (Btn {:variant :secondary :size :sm
            :onClick #(when-let [el @input-ref] (.focus el))}
        "Focus")
      (Btn {:variant :secondary :size :sm
            :onClick #(when-let [el @input-ref]
                        (.focus el)
                        (.select el))}
        "Select all"))))

(defnc BasicsTab
  []
  (Section
    (SectionTitle {} "🧱 Basic Components")

    (CodeAndOutput
     {:title "Simple Component"
      :code "(defnc HelloWorld\n  []\n  (Element {:tag \"div\"}\n    \"Hello\" \" \" \"World!\"))"}
     (HelloWorld))

    (CodeAndOutput
     {:title "Props Destructuring"
      :code "(defnc Greeting\n  [{:keys [name emoji]}]\n  (Element {:tag \"div\"}\n    (Element {:tag \"span\"} emoji \" \")\n    (Element {:tag \"strong\"} \"Hello, \" name \"!\")))\n\n(Greeting {:name \"ClojureScript\"\n           :emoji \"👋\"})"}
     (Greeting {:name "ClojureScript" :emoji "👋"}))

    (CodeAndOutput
     {:title "Children Passing"
      :code "(defnc CardWithChildren\n  [{:keys [title children]}]\n  (Card {}\n    (CardTitle {} title)\n    ;; children is a seq — spread it, or React\n    ;; reads it as a keyless list\n    (apply Element {:tag \"div\"} children)))\n\n(CardWithChildren {:title \"Card Title\"}\n  (Element {:tag \"p\"} \"Content 1\")\n  (Element {:tag \"p\"} \"Content 2\"))"}
     (CardWithChildren {:title "Card Title"}
       (Element {:tag "p"} "This is the card content.")
       (Element {:tag "p"} "Multiple children are supported!")))

    (CodeAndOutput
     {:title "Iterating Over Children"
      :code "(defnc NumberedList\n  [{:keys [children]}]\n  (Element {:tag \"ol\"}\n    (for [[idx item] (map-indexed vector children)]\n      (Element {:tag \"li\" :key idx}\n        (Element {:tag \"span\"} (inc idx))\n        item))))\n\n(NumberedList nil\n  (Element {:tag \"span\"} \"First item\")\n  (Element {:tag \"span\"} \"Second item\")\n  (Element {:tag \"span\"} \"Third item\"))"}
     (IterChildrenDemo))

    (CodeAndOutput
     {:title "Vector of Elements as a Prop"
      :code ";; Elements are plain CLJS data — pass them through any prop key.\n\n(defnc Breadcrumbs\n  [{:keys [items]}]\n  (Element {:tag \"nav\"}\n    (for [[idx item] (map-indexed vector items)]\n      (Element {:tag \"span\" :key idx}\n        (when (pos? idx)\n          (Element {:tag \"span\"} \"/\"))\n        item))))\n\n(Breadcrumbs\n  {:items [(BreadcrumbLink {:href \"#\"} \"Home\")\n           (BreadcrumbLink {:href \"#\"} \"Library\")\n           (BreadcrumbCurrent {} \"ClojureScript\")]})"}
     (VectorPropDemo))

    (CodeAndOutput
     {:title "Interop — Using a JS Component"
      :code ";; Pass any JS component as :tag — keyword props become JS props.\n;; The raw component knows nothing about sx, but reads\n;; var(--cx-…) directly, so it follows the theme anyway.\n\n(Element {:tag RawBadge :label \"New\"})\n(Element {:tag RawBadge :label \"Beta\"})\n(Element {:tag RawBadge :label \"Alpha\"})"}
     (Row {:gap 1}
       (Element {:tag RawBadge :label "New"})
       (Element {:tag RawBadge :label "Beta"})
       (Element {:tag RawBadge :label "Alpha"})))

    (CodeAndOutput
     {:title "Interop — Adapting a JS Component"
      :code "(defn Badge [props]\n  (Element (assoc props :tag RawBadge)))\n\n(Badge {:label \"New\"})\n(Badge {:label \"Beta\"})\n(Badge {:label \"Alpha\"})"}
     (Row {:gap 1}
       (Badge {:label "New"})
       (Badge {:label "Beta"})
       (Badge {:label "Alpha"})))

    (CodeAndOutput
     {:title "Interop — Event Handlers"
      :code "(defnc ClickDemo []\n  (let [clicks (use-state 0)]\n    (Element {:tag \"div\"}\n      (Element {:tag RawButton\n                :label \"Click me\"\n                :onClick #(swap! clicks inc)})\n      (Element {:tag \"span\"}\n        \"Clicks: \" @clicks))))"}
     (ClickDemo))

    (CodeAndOutput
     {:title "Interop — Children Forwarding"
      :code "(Element {:tag RawPanel :title \"Hello from CLJS\"}\n  (Element {:tag \"p\"}\n    \"These children were passed in from ClojureScript.\")\n  (Element {:tag \"p\"}\n    \"The JS component sees them as props.children.\"))"}
     (Element {:tag RawPanel :title "Hello from CLJS"}
       (Element {:tag "p"} "These children were passed in from ClojureScript.")
       (Element {:tag "p"} "The JS component sees them as props.children.")))

    (CodeAndOutput
     {:title "Interop — Ref to a JS Component"
      :code ";; use-ref returns a RefAtom — pass as :ref; @ref is the DOM node.\n\n(defnc FocusDemo []\n  (let [input-ref (use-ref nil)]\n    (Element {:tag \"div\"}\n      (Element {:tag RawTextBox :ref input-ref})\n      (Element {:tag \"button\"\n                :onClick #(when-let [el @input-ref]\n                            (.focus el))}\n        \"Focus\")\n      (Element {:tag \"button\"\n                :onClick #(when-let [el @input-ref]\n                            (.focus el)\n                            (.select el))}\n        \"Select all\"))))"}
     (FocusDemo))))
