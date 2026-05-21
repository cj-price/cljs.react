(ns cljs.react.demo.basics
  (:require ["react" :as react]
            [cljs.react.core :refer [Element]]
            [cljs.react.hook :refer [use-state use-ref]]
            [cljs.react.demo.util :refer [CodeAndOutput H2 Section]])
  (:require-macros [cljs.react.core :refer [defnc]]))

(defnc HelloWorld
  []
  (Element {:tag "div" :className "text-xl font-medium text-gray-700"}
    "Hello" " " "World!"))

(defnc Greeting
  [{:keys [name emoji]}]
  (Element {:tag "div" :className "text-xl text-gray-700"}
    (Element {:tag "span" :className "text-2xl mr-2"} emoji)
    (Element {:tag "strong" :className "font-semibold text-koi-orange"} "Hello, " name "!")))

(defnc CardWithChildren
  [{:keys [title children]}]
  (Element {:tag "div" :className "card"}
    (Element {:tag "h3" :className "card-title"} title)
    (Element {:tag "div" :className "card-content"} children)))

;; ── JS interop: props ─────────────────────────────────────────────────────────
;; Raw React function components — pretend these came from an npm package.
;; Each one uses `react/createElement` directly so no CLJS machinery is involved
;; on the component side.

(def RawBadge
  (fn [^js props]
    (react/createElement "span"
      #js {:style #js {:display "inline-block"
                       :padding "0.25rem 0.75rem"
                       :borderRadius "9999px"
                       :background "#dbeafe"
                       :color "#1e40af"
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
                       :borderRadius "0.5rem"
                       :border "1px solid #d1d5db"
                       :background "white"
                       :cursor "pointer"
                       :fontSize "0.9rem"}}
      (.-label props))))

(defnc ClickDemo []
  (let [clicks (use-state 0)]
    (Element {:tag "div" :style #js {:display "flex" :gap "0.75rem" :alignItems "center"}}
      (Element {:tag RawButton :label "Click me" :onClick #(swap! clicks inc)})
      (Element {:tag "span"} "Clicks: " @clicks))))

;; ── JS interop: children ──────────────────────────────────────────────────────

(def RawPanel
  (fn [^js props]
    (react/createElement "div"
      #js {:style #js {:border "2px dashed #cbd5e1"
                       :borderRadius "0.5rem"
                       :padding "1rem"
                       :background "#f8fafc"}}
      (react/createElement "div"
        #js {:style #js {:fontWeight 600
                         :marginBottom "0.5rem"
                         :color "#475569"}}
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
                         :border "2px solid #e5e7eb"
                         :borderRadius "0.5rem"
                         :fontSize "0.9rem"}}))))

(defnc FocusDemo []
  (let [input-ref (use-ref nil)]
    (Element {:tag "div" :style #js {:display "flex" :gap "0.5rem" :alignItems "center"}}
      (Element {:tag RawTextBox :ref input-ref :placeholder "Type here"})
      (Element {:tag "button"
                :type "button"
                :onClick #(when-let [el @input-ref] (.focus el))
                :style #js {:padding "0.5rem 1rem"
                            :border "1px solid #d1d5db"
                            :borderRadius "0.5rem"
                            :background "white"
                            :cursor "pointer"
                            :fontSize "0.9rem"}}
        "Focus input"))))

(defnc BasicsTab
  []
  (Section
    (H2 "🧱 Basic Components")

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
      :code "(defnc CardWithChildren\n  [{:keys [title children]}]\n  (Element {:tag \"div\" :className \"card\"}\n    (Element {:tag \"h3\"} title)\n    (Element {:tag \"div\"} children)))\n\n(CardWithChildren {:title \"Card Title\"}\n  (Element {:tag \"p\"} \"Content 1\")\n  (Element {:tag \"p\"} \"Content 2\"))"}
     (CardWithChildren {:title "Card Title"}
       (Element {:tag "p"} "This is the card content.")
       (Element {:tag "p"} "Multiple children are supported!")))

    (CodeAndOutput
     {:title "Interop — Using a JS Component"
      :code ";; RawBadge is a plain JS React component\n;; (imagine it came from an npm package).\n;; Pass the reference as :tag — keyword props\n;; are forwarded as flat JS props.\n\n(Element {:tag RawBadge :label \"New\"})\n(Element {:tag RawBadge :label \"Beta\"})\n(Element {:tag RawBadge :label \"Alpha\"})"}
     (Element {:tag "div" :style #js {:display "flex" :gap "0.5rem" :flexWrap "wrap"}}
       (Element {:tag RawBadge :label "New"})
       (Element {:tag RawBadge :label "Beta"})
       (Element {:tag RawBadge :label "Alpha"})))

    (CodeAndOutput
     {:title "Interop — Adapting a JS Component"
      :code ";; Wrap the Element call in a plain fn so callers\n;; don't have to keep typing :tag RawBadge.\n\n(defn Badge [props]\n  (Element (assoc props :tag RawBadge)))\n\n(Badge {:label \"New\"})\n(Badge {:label \"Beta\"})\n(Badge {:label \"Alpha\"})"}
     (Element {:tag "div" :style #js {:display "flex" :gap "0.5rem" :flexWrap "wrap"}}
       (Badge {:label "New"})
       (Badge {:label "Beta"})
       (Badge {:label "Alpha"})))

    (CodeAndOutput
     {:title "Interop — Event Handlers"
      :code ";; Keyword prop names pass through unchanged —\n;; use camelCase like React expects (:onClick, :onChange).\n\n(defnc ClickDemo []\n  (let [clicks (use-state 0)]\n    (Element {:tag \"div\"}\n      (Element {:tag RawButton\n                :label \"Click me\"\n                :onClick #(swap! clicks inc)})\n      (Element {:tag \"span\"}\n        \"Clicks: \" @clicks))))"}
     (ClickDemo))

    (CodeAndOutput
     {:title "Interop — Children Forwarding"
      :code ";; Children passed to Element become React\n;; props.children on the JS side — no wrapping needed.\n\n(Element {:tag RawPanel :title \"Hello from CLJS\"}\n  (Element {:tag \"p\"}\n    \"These children were passed in from ClojureScript.\")\n  (Element {:tag \"p\"}\n    \"The JS component sees them as props.children.\"))"}
     (Element {:tag RawPanel :title "Hello from CLJS"}
       (Element {:tag "p"} "These children were passed in from ClojureScript.")
       (Element {:tag "p"} "The JS component sees them as props.children.")))

    (CodeAndOutput
     {:title "Interop — Ref to a JS Component"
      :code ";; use-ref returns a RefAtom. Pass it directly as :ref —\n;; clj->js-props unwraps it automatically before handing it\n;; to the JS component. Deref @ref to access the DOM element.\n\n(defnc FocusDemo []\n  (let [input-ref (use-ref nil)]\n    (Element {:tag \"div\"}\n      (Element {:tag RawTextBox\n                :ref input-ref\n                :placeholder \"Type here\"})\n      (Element {:tag \"button\"\n                :type \"button\"\n                :onClick #(when-let [el @input-ref]\n                            (.focus el))}\n        \"Focus input\"))))"}
     (FocusDemo))))
