(ns cljs.react.demo.advanced
  (:require [cljs.react.core :refer [use-ref use-state]]
            [cljs.react.sx :refer [use-sx]]
            [cljs.react.demo.ui :refer [CodeAndOutput Btn Row Stack Grid Panel
                                        SectionTitle Caption Badge Card]]
            [cljs.react.demo.util :refer [Div P Span Strong Section]])
  (:require-macros [cljs.react.core :refer [defnc]]
                   [cljs.react.sx :refer [defstyle]]))

(defnc NestedComponent
  [{:keys [level message]}]
  (Panel {:className (use-sx {:display :flex :align-items :flex-start :gap 1.5})}
    (Div {:className (use-sx {:display :flex :align-items :center
                              :justify-content :center :flex-shrink 0
                              :width "2rem" :height "2rem" :border-radius "50%"
                              :bgcolor :palette.surface.tint
                              :color :palette.primary.main
                              :font-weight 700 :font-size "0.875rem"})}
      level)
    (Div
      (Div {:className (use-sx {:font-size "0.6875rem" :text-transform :uppercase
                                :letter-spacing "0.06em"
                                :color :palette.text.secondary})}
        "Level " level)
      (Div {:className (use-sx {:mt 0.25 :font-size "0.875rem"
                                :color :palette.text.primary})}
        message))))

(defnc PropsDemo
  []
  (Stack {:gap 1.5}
    (Caption {} "Components receive immutable CLJS data structures as props:")
    (NestedComponent {:level 1 :message "First nested component"})
    (NestedComponent {:level 2 :message "Second nested component"})
    (NestedComponent {:level 3 :message "Third nested component"})))

(def ^:private role-tone
  {"admin" :error "moderator" :warning "user" :info})

(defstyle user-card
  {:transition "border-color 140ms ease, box-shadow 140ms ease"
   :&:hover {:border-color :palette.primary.main :box-shadow 2}})

(defnc UserCard
  [{:keys [name email role active]}]
  (Card {:className (use-sx [user-card (when-not active {:opacity 0.65})])}
    (Row {:justify :space-between :gap 1}
      (Span {:className (use-sx {:font-weight 600 :color :palette.text.primary})}
        name)
      ;; The role no longer builds a class name at runtime — it selects a tone.
      (Badge {:tone (role-tone role :neutral)} role))
    (P {:className (use-sx {:mt 1 :font-size "0.875rem" :overflow-wrap :anywhere
                            :color :palette.text.secondary})}
      email)
    (P {:className (use-sx {:mt 1 :font-size "0.75rem" :font-weight 500
                            :color (if active
                                     :palette.success.main
                                     :palette.text.disabled)})}
      (if active "● Active" "○ Inactive"))))

(defnc CompositionDemo
  []
  (let [users [{:id 1 :name "Alice Johnson" :email "alice@example.com" :role "admin" :active true}
               {:id 2 :name "Bob Smith" :email "bob@example.com" :role "user" :active true}
               {:id 3 :name "Carol White" :email "carol@example.com" :role "moderator" :active false}]]
    (Grid {:min-width "220px" :gap 1.5}
      (for [user users]
        (UserCard (assoc user :key (:id user)))))))

(defnc RenderCounter
  [{:keys [name]}]
  (let [render-count (use-ref 0)]
    (swap! render-count inc)
    (Card {:className (use-sx {:display :flex :align-items :center
                               :justify-content :space-between :gap 1 :p 1.5})}
      (Strong {:className (use-sx {:font-size "0.875rem"
                                   :color :palette.text.primary})}
        name)
      (Badge {:tone :primary} "renders: " @render-count))))

(defnc MemoizationDemo
  []
  (let [count (use-state 0)
        unrelated (use-state "")]
    (Stack {:gap 1.5}
      (Caption {} "Components are memoized with React.memo using CLJS equality.")
      (Row {:gap 1}
        (Btn {:size :sm :onClick #(swap! count inc)} "Increment count")
        (Btn {:variant :secondary :size :sm
              :onClick #(reset! unrelated (str (random-uuid)))}
          "Update unrelated state"))
      (Stack {:gap 1}
        (RenderCounter {:name "Parent Component"})
        (RenderCounter {:name (str "Child with count=" @count)}))
      (Caption {:className (use-sx {:font-style :italic})}
        "Try 'Update unrelated state' — the child shouldn't re-render."))))

(defnc AdvancedTab
  []
  (Section
    (SectionTitle {} "🚀 Advanced Features")

    (CodeAndOutput
     {:title "Props & Data Flow"
      :code "(defnc NestedComponent\n  [{:keys [level message]}]\n  (Div\n    (H4 \"Level \" level)\n    (P message)))\n\n(defnc PropsDemo\n  []\n  (Div\n    (NestedComponent {:level 1\n                      :message \"First\"})\n    (NestedComponent {:level 2\n                      :message \"Second\"})))"}
     (PropsDemo))

    (CodeAndOutput
     {:title "Component Composition"
      :code ";; A tone selects tokens; nothing builds a class\n;; name at runtime any more.\n\n(defnc UserCard\n  [{:keys [name role active]}]\n  (Card {:className\n          (use-sx [user-card\n                   (when-not active\n                     {:opacity 0.65})])}\n    (Row {:justify :space-between}\n      (Span name)\n      (Badge {:tone (role-tone role)} role))))"}
     (CompositionDemo))

    (CodeAndOutput
     {:title "Memoization with React.memo"
      :code "(defnc RenderCounter\n  [{:keys [name]}]\n  (let [count (use-ref 0)]\n    (swap! count inc)\n    (Div\n      (Strong name)\n      (Span \" - Renders: \" @count))))\n\n;; defnc auto-wraps with React.memo + CLJS equality"}
     (MemoizationDemo))))
