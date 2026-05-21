(ns cljs.react.demo.advanced
  (:require [clojure.string :as string]
            [cljs.react.hook :refer [use-ref use-state]]
            [cljs.react.demo.util :refer [CodeAndOutput Div P H2 H3 H4
                                          Span Strong Button Section]])
  (:require-macros [cljs.react.core :refer [defnc]]))

(defnc NestedComponent
  [{:keys [level message]}]
  (Div {:className "nested-component"}
    (H4 "Level " level)
    (P message)))

(defnc PropsDemo
  []
  (Div {:className "demo-box"}
    (H3 "Props & Data Flow")
    (P "Components receive immutable CLJS data structures as props:")
    (NestedComponent {:level 1 :message "First nested component"})
    (NestedComponent {:level 2 :message "Second nested component"})
    (NestedComponent {:level 3 :message "Third nested component"})))

(defnc Badge
  [{:keys [text type]}]
  (Span {:className (str "badge badge-" (name type))} text))

(defnc UserCard
  [{:keys [name email role active]}]
  (Div {:className (str "user-card" (when-not active " inactive"))}
    (Div {:className "user-header"}
      (H4 name)
      (Badge {:text (string/upper-case role) :type role}))
    (P {:className "user-email"} email)
    (P {:className "user-status"}
      (if active "✓ Active" "○ Inactive"))))

(defnc CompositionDemo
  []
  (let [users [{:id 1 :name "Alice Johnson" :email "alice@example.com" :role "admin" :active true}
               {:id 2 :name "Bob Smith" :email "bob@example.com" :role "user" :active true}
               {:id 3 :name "Carol White" :email "carol@example.com" :role "moderator" :active false}]]
    (Div {:className "demo-box"}
      (H3 "Component Composition")
      (Div {:className "user-grid"}
        (for [user users]
          (Div {:key (:id user)}
            (UserCard user)))))))

(defnc RenderCounter
  [{:keys [name]}]
  (let [render-count (use-ref 0)]
    (swap! render-count inc)
    (Div {:className "render-counter"}
      (Strong name)
      (Span " - Renders: " @render-count))))

(defnc MemoizationDemo
  []
  (let [count (use-state 0)
        unrelated (use-state "")]
    (Div {:className "demo-box"}
      (H3 "Memoization Behavior")
      (P "Components are memoized with React.memo using CLJS equality")
      (Div {:className "button-group"}
        (Button {:onClick #(swap! count inc)} "Increment Count")
        (Button {:onClick #(reset! unrelated (str (random-uuid)))}
          "Update Unrelated State"))
      (Div {:className "memo-test"}
        (RenderCounter {:name "Parent Component"})
        (RenderCounter {:name (str "Child with count=" @count)})
        (P {:className "hint"}
          "Try clicking 'Update Unrelated State' - child shouldn't re-render!")))))

(defnc AdvancedTab
  []
  (Section
    (H2 "🚀 Advanced Features")

    (CodeAndOutput
     {:title "Props & Data Flow"
      :code "(defnc NestedComponent\n  [{:keys [level message]}]\n  (Div\n    (H4 \"Level \" level)\n    (P message)))\n\n(defnc PropsDemo\n  []\n  (Div\n    (NestedComponent {:level 1\n                      :message \"First\"})\n    (NestedComponent {:level 2\n                      :message \"Second\"})))"}
     (PropsDemo))

    (CodeAndOutput
     {:title "Component Composition"
      :code "(defnc Badge\n  [{:keys [text type]}]\n  (Span {:className (str \"badge badge-\"\n                          (name type))}\n    text))\n\n(defnc UserCard\n  [{:keys [name role]}]\n  (Div\n    (H4 name)\n    (Badge {:text role :type role})))"}
     (CompositionDemo))

    (CodeAndOutput
     {:title "Memoization with React.memo"
      :code "(defnc RenderCounter\n  [{:keys [name]}]\n  (let [count (use-ref 0)]\n    (swap! count inc)\n    (Div\n      (Strong name)\n      (Span \" - Renders: \" @count))))\n\n;; defnc automatically wraps\n;; components with React.memo\n;; using CLJS equality"}
     (MemoizationDemo))))
