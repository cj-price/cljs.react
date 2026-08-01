(ns cljs.react.demo.state
  (:require [cljs.react.core :refer [use-state]]
            [cljs.react.sx :refer [use-sx]]
            [cljs.react.demo.ui :refer [CodeAndOutput Btn Row Stack
                                               TextInput SectionTitle Badge]]
            [cljs.react.demo.util :refer [Div P Span Input Section Ul Li]])
  (:require-macros [cljs.react.core :refer [defnc]]
                   [cljs.react.sx :refer [defstyle]]))

(defnc Counter
  []
  (let [count (use-state 0)]
    (Stack {:gap 2 :align :center}
      (Div {:className (use-sx {:font-size "3rem" :font-weight 700
                                :line-height 1
                                :font-family :typography.font-family-mono
                                :color :palette.primary.main})}
        @count)
      (P {:className (use-sx {:font-size "0.75rem" :text-transform :uppercase
                              :letter-spacing "0.06em"
                              :color :palette.text.secondary})}
        "Current count")
      (Row {:gap 1 :justify :center}
        (Btn {:variant :secondary :onClick #(swap! count dec)} "−")
        (Btn {:variant :ghost :onClick #(reset! count 0)} "Reset")
        (Btn {:onClick #(swap! count inc)} "+")))))

(defnc TextInputDemo
  []
  (let [text (use-state "")]
    (Stack {:gap 1.5}
      (TextInput {:value @text
                  :placeholder "Type something…"
                  :onChange #(reset! text (-> % .-target .-value))})
      (Row {:justify :space-between :gap 1}
        (P {:className (use-sx {:font-size "0.875rem"
                                :color :palette.text.secondary})}
          "You typed: "
          (Span {:className (use-sx {:font-weight 500
                                     :color :palette.text.primary})}
            (if (empty? @text) "(nothing)" @text)))
        (Badge {:tone :primary} (count @text) " chars")))))

(defstyle todo-row
  {:display :flex :align-items :center :gap 1.5 :p 1.25
   :bgcolor :palette.background.paper
   :border "1px solid" :border-color :palette.divider
   :border-radius 1 :transition "border-color 140ms ease"
   :&:hover {:border-color :palette.grey.300}})

(defnc TodoList
  []
  (let [todos (use-state [])
        input (use-state "")
        add-todo (fn []
                   (when-not (empty? @input)
                     (swap! todos conj {:id (random-uuid)
                                        :text @input
                                        :done false})
                     (reset! input "")))
        toggle-todo (fn [id]
                      (swap! todos (fn [ts]
                                     (mapv (fn [todo]
                                             (if (= (:id todo) id)
                                               (update todo :done not)
                                               todo))
                                           ts))))
        remove-todo (fn [id]
                      (swap! todos (fn [ts]
                                     (filterv (fn [todo] (not= (:id todo) id)) ts))))
        remaining (count (remove :done @todos))]
    (Stack {:gap 1.5}
      (Row {:justify :space-between}
        (Span {:className (use-sx {:font-size "0.875rem" :font-weight 600
                                   :color :palette.text.primary})}
          "Tasks")
        (Badge {:tone :neutral} remaining " of " (count @todos) " left"))

      (Row {:gap 1 :wrap? false}
        (TextInput {:value @input
                    :placeholder "Add a todo…"
                    :onKeyDown #(when (= (.-key %) "Enter") (add-todo))
                    :onChange #(reset! input (-> % .-target .-value))})
        (Btn {:onClick add-todo} "Add"))

      ;; Classes hoisted out of the loop: `for` is lazy, so a `use-sx` in the
      ;; body would run after this render returned, with no hook dispatcher.
      (let [row-cls   (use-sx todo-row)
            check-cls (use-sx {:width "1rem" :height "1rem"
                               :flex-shrink 0 :cursor :pointer
                               :accent-color :palette.primary.main})
            label-cls (use-sx {:flex 1 :font-size "0.875rem"
                               :color :palette.text.primary})
            done-cls  (use-sx [{:flex 1 :font-size "0.875rem"}
                               {:text-decoration :line-through
                                :color :palette.text.disabled}])
            ;; Both branches' classes, computed unconditionally — a hook that
            ;; runs in only one arm of the `if` changes the render's hook count.
            empty-cls (use-sx {:py 3 :text-align :center :font-size "0.875rem"
                               :font-style :italic
                               :color :palette.text.disabled
                               :border "2px dashed" :border-color :palette.divider
                               :border-radius 1})
            list-cls  (use-sx {:display :flex :flex-direction :column :gap 1
                               :list-style :none})]
        (if (empty? @todos)
          (Div {:className empty-cls}
            "No tasks yet — add one above.")
          (Ul {:className list-cls}
            (for [todo @todos]
              (Li {:key (str (:id todo))
                   :className row-cls}
                (Input {:type "checkbox"
                        :checked (:done todo)
                        :className check-cls
                        :onChange #(toggle-todo (:id todo))})
                (Span {:className (if (:done todo) done-cls label-cls)}
                  (:text todo))
                (Btn {:variant :ghost :size :sm
                      :aria-label "Remove todo"
                      :onClick #(remove-todo (:id todo))}
                  "×")))))))))

(defnc StateTab
  []
  (Section
    (SectionTitle {} "📊 State Management")

    (CodeAndOutput
     {:title "Counter with useState"
      :code "(defnc Counter\n  []\n  (let [count (use-state 0)]\n    (Div\n      (H3 \"Count: \" @count)\n      (Button {:onClick #(swap! count dec)} \"Decrement\")\n      (Button {:onClick #(swap! count inc)} \"Increment\"))))"}
     (Counter))

    (CodeAndOutput
     {:title "Text Input"
      :code "(defnc TextInput\n  []\n  (let [text (use-state \"\")]\n    (Div\n      (Input {:value @text\n              :onChange #(reset! text\n                          (-> % .-target .-value))})\n      (P \"You typed: \" @text))))"}
     (TextInputDemo))

    (CodeAndOutput
     {:title "Todo List"
      :code "(defnc TodoList\n  []\n  (let [todos (use-state [])\n        input (use-state \"\")\n        add-todo (fn []\n                   (when-not (empty? @input)\n                     (swap! todos conj {:id (random-uuid)\n                                         :text @input\n                                         :done false})\n                     (reset! input \"\")))\n        toggle (fn [id]\n                 (swap! todos\n                   (fn [ts]\n                     (mapv\n                       (fn [todo]\n                         (if (= (:id todo) id)\n                           (update todo :done not)\n                           todo))\n                       ts))))]\n    ...))"}
     (TodoList))))
