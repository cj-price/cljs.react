(ns cljs.react.demo.db
  (:require [cljs.react.core :refer [DBProvider use-cursor]]
            [cljs.react.demo.util :refer [CodeAndOutput Div P H2 H4 Label
                                          Input Strong Button Section]])
  (:require-macros [cljs.react.core :refer [defnc]]))

(defnc DBCounterDisplay
  []
  (let [count (use-cursor [:counter :value])]
    (Div {:className "text-center"}
      (Div {:className "text-5xl font-bold text-koi-orange mb-2"} @count)
      (P {:className "text-sm text-gray-600"} "Shared Counter Value"))))

(defnc DBCounterControls
  []
  (let [count (use-cursor [:counter :value])]
    (Div {:className "flex gap-2 justify-center"}
      (Button {:className "px-6 py-2 bg-gray-200 text-gray-700 rounded-lg font-medium hover:bg-gray-300"
               :onClick #(swap! count dec)}
        "-")
      (Button {:className "px-6 py-2 bg-gray-100 text-gray-600 rounded-lg font-medium hover:bg-gray-200"
               :onClick #(reset! count 0)}
        "Reset")
      (Button {:className "px-6 py-2 bg-koi-orange text-white rounded-lg font-medium shadow-lg"
               :onClick #(swap! count inc)}
        "+"))))

(defnc DBUserForm
  []
  (let [name (use-cursor [:user :name])
        email (use-cursor [:user :email])]
    (Div {:className "space-y-3"}
      (Div
        (Label {:className "block text-sm font-medium text-gray-700 mb-1"} "Name")
        (Input {:type "text"
                :value (or @name "")
                :className "w-full px-4 py-2 border-2 border-gray-200 rounded-lg focus:border-koi-orange transition-colors"
                :onChange #(reset! name (-> % .-target .-value))}))
      (Div
        (Label {:className "block text-sm font-medium text-gray-700 mb-1"} "Email")
        (Input {:type "email"
                :value (or @email "")
                :className "w-full px-4 py-2 border-2 border-gray-200 rounded-lg focus:border-koi-orange transition-colors"
                :onChange #(reset! email (-> % .-target .-value))})))))

(defnc DBUserDisplay
  []
  (let [name (use-cursor [:user :name])
        email (use-cursor [:user :email])]
    (Div {:className "p-4 bg-gray-50 rounded-lg"}
      (P {:className "text-gray-700"}
        (Strong "Name: ") (or @name "(empty)"))
      (P {:className "text-gray-700"}
        (Strong "Email: ") (or @email "(empty)")))))

(defnc DBDemo
  []
  (DBProvider {:initial-value {:counter {:value 0}
                               :user {:name "" :email ""}}}
    (Div {:className "space-y-6"}
      (Div {:className "space-y-4"}
        (H4 {:className "font-medium text-gray-700"} "Shared Counter (two components, one cursor each)")
        (DBCounterDisplay)
        (DBCounterControls))
      (Div {:className "space-y-4"}
        (H4 {:className "font-medium text-gray-700"} "User Form (cursors to nested paths)")
        (Div {:className "grid grid-cols-2 gap-4"}
          (DBUserForm)
          (DBUserDisplay))))))

(defnc DBTab
  []
  (Section
    (H2 "🗄️ Global State (DBProvider & Cursors)")

    (CodeAndOutput
     {:title "DBProvider & use-cursor"
      :code "(defnc CounterDisplay []\n  (let [count (use-cursor [:counter])]\n    (Div @count)))\n\n(defnc CounterButton []\n  (let [count (use-cursor [:counter])]\n    (Button {:onClick #(swap! count inc)}\n      \"Increment\")))\n\n(defnc App []\n  (DBProvider {:initial-value {:counter 0}}\n    (CounterDisplay)\n    (CounterButton)))"}
     (DBDemo))))
