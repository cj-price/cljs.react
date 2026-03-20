(ns cljs.react.demo.db
  (:require [cljs.react.core :refer [Element DBProvider use-cursor]]
            [cljs.react.demo.util :refer [CodeAndOutput]])
  (:require-macros [cljs.react.core :refer [defnc]]))

(defnc DBCounterDisplay
  []
  (let [count (use-cursor [:counter :value])]
    (Element {:tag "div" :className "text-center"}
      (Element {:tag "div" :className "text-5xl font-bold text-koi-orange mb-2"} @count)
      (Element {:tag "p" :className "text-sm text-gray-600"} "Shared Counter Value"))))

(defnc DBCounterControls
  []
  (let [count (use-cursor [:counter :value])]
    (Element {:tag "div" :className "flex gap-2 justify-center"}
      (Element {:tag "button"
                :className "px-6 py-2 bg-gray-200 text-gray-700 rounded-lg font-medium hover:bg-gray-300"
                :onClick #(swap! count dec)}
        "-")
      (Element {:tag "button"
                :className "px-6 py-2 bg-gray-100 text-gray-600 rounded-lg font-medium hover:bg-gray-200"
                :onClick #(reset! count 0)}
        "Reset")
      (Element {:tag "button"
                :className "px-6 py-2 bg-koi-orange text-white rounded-lg font-medium shadow-lg"
                :onClick #(swap! count inc)}
        "+"))))

(defnc DBUserForm
  []
  (let [name (use-cursor [:user :name])
        email (use-cursor [:user :email])]
    (Element {:tag "div" :className "space-y-3"}
      (Element {:tag "div"}
        (Element {:tag "label" :className "block text-sm font-medium text-gray-700 mb-1"} "Name")
        (Element {:tag "input"
                  :type "text"
                  :value (or @name "")
                  :className "w-full px-4 py-2 border-2 border-gray-200 rounded-lg focus:border-koi-orange transition-colors"
                  :onChange #(reset! name (-> % .-target .-value))}))
      (Element {:tag "div"}
        (Element {:tag "label" :className "block text-sm font-medium text-gray-700 mb-1"} "Email")
        (Element {:tag "input"
                  :type "email"
                  :value (or @email "")
                  :className "w-full px-4 py-2 border-2 border-gray-200 rounded-lg focus:border-koi-orange transition-colors"
                  :onChange #(reset! email (-> % .-target .-value))})))))

(defnc DBUserDisplay
  []
  (let [name (use-cursor [:user :name])
        email (use-cursor [:user :email])]
    (Element {:tag "div" :className "p-4 bg-gray-50 rounded-lg"}
      (Element {:tag "p" :className "text-gray-700"}
        (Element {:tag "strong"} "Name: ") (or @name "(empty)"))
      (Element {:tag "p" :className "text-gray-700"}
        (Element {:tag "strong"} "Email: ") (or @email "(empty)")))))

(defnc DBDemo
  []
  (DBProvider {:initial-value {:counter {:value 0}
                               :user {:name "" :email ""}}}
    (Element {:tag "div" :className "space-y-6"}
      (Element {:tag "div" :className "space-y-4"}
        (Element {:tag "h4" :className "font-medium text-gray-700"} "Shared Counter (two components, one cursor each)")
        (DBCounterDisplay)
        (DBCounterControls))
      (Element {:tag "div" :className "space-y-4"}
        (Element {:tag "h4" :className "font-medium text-gray-700"} "User Form (cursors to nested paths)")
        (Element {:tag "div" :className "grid grid-cols-2 gap-4"}
          (DBUserForm)
          (DBUserDisplay))))))

(defnc DBTab
  []
  (Element {:tag "section"}
    (Element {:tag "h2"} "🗄️ Global State (DBProvider & Cursors)")

    (CodeAndOutput
     {:title "DBProvider & use-cursor"
      :code "(defnc CounterDisplay []\n  (let [count (use-cursor [:counter])]\n    (Element {:tag \"div\"} @count)))\n\n(defnc CounterButton []\n  (let [count (use-cursor [:counter])]\n    (Element {:tag \"button\"\n              :onClick #(swap! count inc)}\n      \"Increment\")))\n\n(defnc App []\n  (DBProvider {:initial-value {:counter 0}}\n    (CounterDisplay)\n    (CounterButton)))"}
     (DBDemo))))
