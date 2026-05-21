(ns cljs.react.demo.state
  (:require [cljs.react.hook :refer [use-state]]
            [cljs.react.demo.util :refer [CodeAndOutput Div P H2 H3 Span
                                          Button Input Section Ul Li]])
  (:require-macros [cljs.react.core :refer [defnc]]))

(defnc Counter
  []
  (let [count (use-state 0)]
    (Div {:className "space-y-4"}
      (Div {:className "text-center"}
        (Div {:className "text-5xl font-bold text-koi-orange mb-2"} @count)
        (P {:className "text-sm text-gray-600"} "Current Count"))
      (Div {:className "flex gap-2 justify-center"}
        (Button {:className "px-6 py-2 bg-gray-200 text-gray-700 rounded-lg font-medium hover:bg-gray-300"
                 :onClick #(swap! count dec)}
          "−")
        (Button {:className "px-6 py-2 bg-gray-100 text-gray-600 rounded-lg font-medium hover:bg-gray-200"
                 :onClick #(reset! count 0)}
          "Reset")
        (Button {:className "px-6 py-2 bg-koi-orange text-white rounded-lg font-medium shadow-lg"
                 :onClick #(swap! count inc)}
          "+")))))

(defnc TextInput
  []
  (let [text (use-state "")]
    (Div {:className "space-y-3"}
      (Input {:type "text"
              :value @text
              :placeholder "Type something..."
              :className "w-full px-4 py-3 border-2 border-gray-200 rounded-lg focus:border-koi-orange transition-colors"
              :onChange #(reset! text (-> % .-target .-value))})
      (Div {:className "flex justify-between text-sm"}
        (P {:className "text-gray-600"}
          "You typed: "
          (Span {:className "font-medium text-gray-900"}
            (if (empty? @text) "(nothing)" @text)))
        (P {:className "text-gray-500"}
          "Characters: "
          (Span {:className "font-semibold text-koi-orange"}
            (count @text)))))))

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
                                     (filterv (fn [todo] (not= (:id todo) id)) ts))))]
    (Div {:className "demo-box"}
      (H3 "Todo List (" (count @todos) " items)")
      (Div {:className "input-group"}
        (Input {:type "text"
                :value @input
                :placeholder "Add a todo..."
                :onKeyPress #(when (= (.-key %) "Enter") (add-todo))
                :onChange #(reset! input (-> % .-target .-value))})
        (Button {:onClick add-todo} "Add"))
      (Ul {:className "todo-list"}
        (for [todo @todos]
          (Li {:key (str (:id todo))
               :className (if (:done todo) "done" "")}
            (Input {:type "checkbox"
                    :checked (:done todo)
                    :onChange #(toggle-todo (:id todo))})
            (Span (:text todo))
            (Button {:className "delete-btn"
                     :onClick #(remove-todo (:id todo))}
              "×")))))))

(defnc StateTab
  []
  (Section
    (H2 "📊 State Management")

    (CodeAndOutput
     {:title "Counter with useState"
      :code "(defnc Counter\n  []\n  (let [count (use-state 0)]\n    (Div\n      (H3 \"Count: \" @count)\n      (Button {:onClick #(swap! count dec)} \"Decrement\")\n      (Button {:onClick #(swap! count inc)} \"Increment\"))))"}
     (Counter))

    (CodeAndOutput
     {:title "Text Input"
      :code "(defnc TextInput\n  []\n  (let [text (use-state \"\")]\n    (Div\n      (Input {:value @text\n              :onChange #(reset! text\n                          (-> % .-target .-value))})\n      (P \"You typed: \" @text))))"}
     (TextInput))

    (CodeAndOutput
     {:title "Todo List"
      :code "(defnc TodoList\n  []\n  (let [todos (use-state [])\n        input (use-state \"\")\n        add-todo (fn []\n                   (when-not (empty? @input)\n                     (swap! todos conj {:id (random-uuid)\n                                         :text @input\n                                         :done false})\n                     (reset! input \"\")))\n        toggle (fn [id]\n                 (swap! todos\n                   (fn [ts]\n                     (mapv\n                       (fn [todo]\n                         (if (= (:id todo) id)\n                           (update todo :done not)\n                           todo))\n                       ts))))]\n    ...))"}
     (TodoList))))
