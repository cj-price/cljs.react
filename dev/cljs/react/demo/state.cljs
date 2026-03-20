(ns cljs.react.demo.state
  (:require ["react" :as react]
            [cljs.react.core :refer [Element]]
            [cljs.react.hook :refer [use-state]]
            [cljs.react.demo.util :refer [CodeAndOutput]])
  (:require-macros [cljs.react.core :refer [defnc]]))

(defnc Counter
  []
  (let [count (use-state 0)]
    (Element {:tag "div" :className "space-y-4"}
      (Element {:tag "div" :className "text-center"}
        (Element {:tag "div" :className "text-5xl font-bold text-koi-orange mb-2"} @count)
        (Element {:tag "p" :className "text-sm text-gray-600"} "Current Count"))
      (Element {:tag "div" :className "flex gap-2 justify-center"}
        (Element {:tag "button"
                  :className "px-6 py-2 bg-gray-200 text-gray-700 rounded-lg font-medium hover:bg-gray-300"
                  :onClick #(swap! count dec)}
          "−")
        (Element {:tag "button"
                  :className "px-6 py-2 bg-gray-100 text-gray-600 rounded-lg font-medium hover:bg-gray-200"
                  :onClick #(reset! count 0)}
          "Reset")
        (Element {:tag "button"
                  :className "px-6 py-2 bg-koi-orange text-white rounded-lg font-medium shadow-lg"
                  :onClick #(swap! count inc)}
          "+")))))

(defnc TextInput
  []
  (let [text (use-state "")]
    (Element {:tag "div" :className "space-y-3"}
      (Element {:tag "input"
                :type "text"
                :value @text
                :placeholder "Type something..."
                :className "w-full px-4 py-3 border-2 border-gray-200 rounded-lg focus:border-koi-orange transition-colors"
                :onChange #(reset! text (-> % .-target .-value))})
      (Element {:tag "div" :className "flex justify-between text-sm"}
        (Element {:tag "p" :className "text-gray-600"}
          "You typed: "
          (Element {:tag "span" :className "font-medium text-gray-900"}
            (if (empty? @text) "(nothing)" @text)))
        (Element {:tag "p" :className "text-gray-500"}
          "Characters: "
          (Element {:tag "span" :className "font-semibold text-koi-orange"}
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
    (Element {:tag "div" :className "demo-box"}
      (Element {:tag "h3"} "Todo List (" (count @todos) " items)")
      (Element {:tag "div" :className "input-group"}
        (Element {:tag "input"
                  :type "text"
                  :value @input
                  :placeholder "Add a todo..."
                  :onKeyPress #(when (= (.-key %) "Enter") (add-todo))
                  :onChange #(reset! input (-> % .-target .-value))})
        (Element {:tag "button"
                  :onClick add-todo}
          "Add"))
      (Element {:tag "ul" :className "todo-list"}
        (for [todo @todos]
          (Element {:tag "li"
                    :key (str (:id todo))
                    :className (if (:done todo) "done" "")}
            (Element {:tag "input"
                      :type "checkbox"
                      :checked (:done todo)
                      :onChange #(toggle-todo (:id todo))})
            (Element {:tag "span"} (:text todo))
            (Element {:tag "button"
                      :className "delete-btn"
                      :onClick #(remove-todo (:id todo))}
              "×")))))))

(defnc ReducerCounter
  []
  (let [reducer (fn [state action]
                  (case (:type action)
                    :increment (update state :count inc)
                    :decrement (update state :count dec)
                    :reset (assoc state :count 0)
                    :add (update state :count + (:value action))
                    state))
        [state dispatch] (react/useReducer reducer #js {:count 0})]
    (Element {:tag "div" :className "demo-box"}
      (Element {:tag "h3"} "useReducer Counter: " (.-count state))
      (Element {:tag "div" :className "button-group"}
        (Element {:tag "button"
                  :onClick #(dispatch #js {:type :decrement})}
          "-1")
        (Element {:tag "button"
                  :onClick #(dispatch #js {:type :reset})}
          "Reset")
        (Element {:tag "button"
                  :onClick #(dispatch #js {:type :increment})}
          "+1")
        (Element {:tag "button"
                  :onClick #(dispatch #js {:type :add :value 10})}
          "+10")))))

(defnc StateTab
  []
  (Element {:tag "section"}
    (Element {:tag "h2"} "📊 State Management")

    (CodeAndOutput
     {:title "Counter with useState"
      :code "(defnc Counter\n  []\n  (let [count (use-state 0)]\n    (Element {:tag \"div\"}\n      (Element {:tag \"h3\"} \"Count: \" @count)\n      (Element {:tag \"button\"\n                :onClick #(swap! count dec)}\n        \"Decrement\")\n      (Element {:tag \"button\"\n                :onClick #(swap! count inc)}\n        \"Increment\"))))"}
     (Counter))

    (CodeAndOutput
     {:title "Text Input"
      :code "(defnc TextInput\n  []\n  (let [text (use-state \"\")]\n    (Element {:tag \"div\"}\n      (Element {:tag \"input\"\n                :value @text\n                :onChange #(reset! text\n                            (-> % .-target .-value))})\n      (Element {:tag \"p\"}\n        \"You typed: \" @text))))"}
     (TextInput))

    (CodeAndOutput
     {:title "Todo List"
      :code "(defnc TodoList\n  []\n  (let [todos (use-state [])\n        input (use-state \"\")\n        add-todo (fn []\n                   (when-not (empty? @input)\n                     (swap! todos conj {:id (random-uuid)\n                                         :text @input\n                                         :done false})\n                     (reset! input \"\")))\n        toggle (fn [id]\n                 (swap! todos\n                   (fn [ts]\n                     (mapv\n                       (fn [todo]\n                         (if (= (:id todo) id)\n                           (update todo :done not)\n                           todo))\n                       ts))))]\n    ...))"}
     (TodoList))

    (CodeAndOutput
     {:title "useReducer"
      :code "(defnc ReducerCounter\n  []\n  (let [reducer (fn [state action]\n                  (case (:type action)\n                    :increment (update state :count inc)\n                    :decrement (update state :count dec)\n                    :reset (assoc state :count 0)\n                    state))\n        [state dispatch] (react/useReducer\n                          reducer\n                          #js {:count 0})]\n    ...))"}
     (ReducerCounter))))
