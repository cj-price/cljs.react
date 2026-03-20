(ns cljs.react.demo.advanced
  (:require ["react" :as react]
            [cljs.react.core :refer [Element]]
            [cljs.react.hook :refer [use-ref]]
            [cljs.react.demo.util :refer [CodeAndOutput]])
  (:require-macros [cljs.react.core :refer [defnc]]))

(defnc NestedComponent
  [{:keys [level message]}]
  (Element {:tag "div" :className "nested-component"}
    (Element {:tag "h4"} "Level " level)
    (Element {:tag "p"} message)))

(defnc PropsDemo
  []
  (Element {:tag "div" :className "demo-box"}
    (Element {:tag "h3"} "Props & Data Flow")
    (Element {:tag "p"} "Components receive immutable CLJS data structures as props:")
    (NestedComponent {:level 1 :message "First nested component"})
    (NestedComponent {:level 2 :message "Second nested component"})
    (NestedComponent {:level 3 :message "Third nested component"})))

(defnc Badge
  [{:keys [text type]}]
  (Element {:tag "span"
            :className (str "badge badge-" (name type))}
    text))

(defnc UserCard
  [{:keys [name email role active]}]
  (Element {:tag "div" :className (str "user-card" (when-not active " inactive"))}
    (Element {:tag "div" :className "user-header"}
      (Element {:tag "h4"} name)
      (Badge {:text (clojure.string/upper-case role) :type role}))
    (Element {:tag "p" :className "user-email"} email)
    (Element {:tag "p" :className "user-status"}
      (if active "✓ Active" "○ Inactive"))))

(defnc CompositionDemo
  []
  (let [users [{:id 1 :name "Alice Johnson" :email "alice@example.com" :role "admin" :active true}
               {:id 2 :name "Bob Smith" :email "bob@example.com" :role "user" :active true}
               {:id 3 :name "Carol White" :email "carol@example.com" :role "moderator" :active false}]]
    (Element {:tag "div" :className "demo-box"}
      (Element {:tag "h3"} "Component Composition")
      (Element {:tag "div" :className "user-grid"}
        (for [user users]
          (Element {:tag "div" :key (:id user)}
            (UserCard user)))))))

(defnc RenderCounter
  [{:keys [name]}]
  (let [render-count (use-ref 0)]
    (swap! render-count inc)
    (Element {:tag "div" :className "render-counter"}
      (Element {:tag "strong"} name)
      (Element {:tag "span"} " - Renders: " @render-count))))

(defnc MemoizationDemo
  []
  (let [[count set-count] (react/useState 0)
        [unrelated-state set-unrelated-state] (react/useState "")]
    (Element {:tag "div" :className "demo-box"}
      (Element {:tag "h3"} "Memoization Behavior")
      (Element {:tag "p"} "Components are memoized with React.memo using CLJS equality")
      (Element {:tag "div" :className "button-group"}
        (Element {:tag "button" :onClick #(set-count inc)}
          "Increment Count")
        (Element {:tag "button" :onClick #(set-unrelated-state (str (random-uuid)))}
          "Update Unrelated State"))
      (Element {:tag "div" :className "memo-test"}
        (RenderCounter {:name "Parent Component"})
        (RenderCounter {:name (str "Child with count=" count)})
        (Element {:tag "p" :className "hint"}
          "Try clicking 'Update Unrelated State' - child shouldn't re-render!")))))

(defnc AdvancedTab
  []
  (Element {:tag "section"}
    (Element {:tag "h2"} "🚀 Advanced Features")

    (CodeAndOutput
     {:title "Props & Data Flow"
      :code "(defnc NestedComponent\n  [{:keys [level message]}]\n  (Element {:tag \"div\"}\n    (Element {:tag \"h4\"} \"Level \" level)\n    (Element {:tag \"p\"} message)))\n\n(defnc PropsDemo\n  []\n  (Element {:tag \"div\"}\n    (NestedComponent {:level 1\n                      :message \"First\"})\n    (NestedComponent {:level 2\n                      :message \"Second\"})))"}
     (PropsDemo))

    (CodeAndOutput
     {:title "Component Composition"
      :code "(defnc Badge\n  [{:keys [text type]}]\n  (Element {:tag \"span\"\n            :className (str \"badge badge-\"\n                           (name type))}\n    text))\n\n(defnc UserCard\n  [{:keys [name role]}]\n  (Element {:tag \"div\"}\n    (Element {:tag \"h4\"} name)\n    (Badge {:text role :type role})))"}
     (CompositionDemo))

    (CodeAndOutput
     {:title "Memoization with React.memo"
      :code "(defnc RenderCounter\n  [{:keys [name]}]\n  (let [count (use-ref 0)]\n    (swap! count inc)\n    (Element {:tag \"div\"}\n      (Element {:tag \"strong\"} name)\n      (Element {:tag \"span\"}\n        \" - Renders: \"\n        @count))))\n\n;; defnc automatically wraps\n;; components with React.memo\n;; using CLJS equality"}
     (MemoizationDemo))))
