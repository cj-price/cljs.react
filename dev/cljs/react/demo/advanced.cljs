(ns cljs.react.demo.advanced
  (:require [clojure.string :as string]
            [cljs.react.core :refer [use-ref use-state]]
            [cljs.react.demo.util :refer [CodeAndOutput Div P H2
                                          Span Strong Button Section]])
  (:require-macros [cljs.react.core :refer [defnc]]))

(defnc NestedComponent
  [{:keys [level message]}]
  (Div {:className "flex items-start gap-3 p-3 bg-gray-50 border border-gray-200 rounded-lg"}
    (Div {:className "flex items-center justify-center w-8 h-8 rounded-full bg-koi-orange/10 text-koi-orange font-bold text-sm flex-shrink-0"}
      level)
    (Div
      (Div {:className "text-xs uppercase tracking-wide text-gray-500"}
        "Level " level)
      (Div {:className "text-sm text-gray-800 mt-0.5"} message))))

(defnc PropsDemo
  []
  (Div {:className "w-full space-y-2"}
    (P {:className "text-sm text-gray-600 mb-3"}
      "Components receive immutable CLJS data structures as props:")
    (NestedComponent {:level 1 :message "First nested component"})
    (NestedComponent {:level 2 :message "Second nested component"})
    (NestedComponent {:level 3 :message "Third nested component"})))

(defnc Badge
  [{:keys [text type]}]
  (Span {:className (str "badge badge-" (name type))} text))

(defnc UserCard
  [{:keys [name email role active]}]
  (Div {:className (str "p-4 bg-white border-2 rounded-lg transition-all hover:border-koi-orange/60 hover:shadow-md "
                        (if active "border-gray-200" "border-gray-200 opacity-70"))}
    (Div {:className "flex items-center justify-between gap-2 mb-2"}
      (Span {:className "font-semibold text-gray-900"} name)
      (Badge {:text (string/upper-case role) :type role}))
    (P {:className "text-sm text-gray-600 break-all"} email)
    (P {:className (str "text-xs mt-2 font-medium "
                        (if active "text-emerald-600" "text-gray-400"))}
      (if active "● Active" "○ Inactive"))))

(defnc CompositionDemo
  []
  (let [users [{:id 1 :name "Alice Johnson" :email "alice@example.com" :role "admin" :active true}
               {:id 2 :name "Bob Smith" :email "bob@example.com" :role "user" :active true}
               {:id 3 :name "Carol White" :email "carol@example.com" :role "moderator" :active false}]]
    (Div {:className "w-full grid grid-cols-1 sm:grid-cols-2 gap-3"}
      (for [user users]
        (Div {:key (:id user)}
          (UserCard user))))))

(defnc RenderCounter
  [{:keys [name]}]
  (let [render-count (use-ref 0)]
    (swap! render-count inc)
    (Div {:className "flex items-center justify-between p-3 bg-white border border-gray-200 rounded-lg"}
      (Strong {:className "text-sm text-gray-800"} name)
      (Span {:className "text-xs font-mono px-2 py-0.5 bg-orange-50 text-koi-orange rounded-full border border-orange-100"}
        "renders: " @render-count))))

(defnc MemoizationDemo
  []
  (let [count (use-state 0)
        unrelated (use-state "")]
    (Div {:className "w-full space-y-3"}
      (P {:className "text-sm text-gray-600"}
        "Components are memoized with React.memo using CLJS equality.")
      (Div {:className "flex flex-wrap gap-2"}
        (Button {:className "flex-1 min-w-[160px] px-3 py-2 bg-koi-orange text-white rounded-lg font-medium text-sm shadow hover:bg-orange-600 transition-colors"
                 :onClick #(swap! count inc)}
          "Increment Count")
        (Button {:className "flex-1 min-w-[160px] px-3 py-2 bg-gray-200 text-gray-700 rounded-lg font-medium text-sm hover:bg-gray-300 transition-colors"
                 :onClick #(reset! unrelated (str (random-uuid)))}
          "Update Unrelated State"))
      (Div {:className "space-y-2"}
        (RenderCounter {:name "Parent Component"})
        (RenderCounter {:name (str "Child with count=" @count)}))
      (P {:className "text-xs text-gray-500 italic"}
        "Try 'Update Unrelated State' — the child shouldn't re-render."))))

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
      :code "(defnc RenderCounter\n  [{:keys [name]}]\n  (let [count (use-ref 0)]\n    (swap! count inc)\n    (Div\n      (Strong name)\n      (Span \" - Renders: \" @count))))\n\n;; defnc auto-wraps with React.memo + CLJS equality"}
     (MemoizationDemo))))
