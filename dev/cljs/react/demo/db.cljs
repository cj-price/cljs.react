(ns cljs.react.demo.db
  (:require [clojure.string :as str]
            [cljs.react.core :refer [DBProvider use-db]]
            [cljs.react.demo.util :refer [CodeAndOutput Div P H2 H4 Label
                                          Input Button Section]])
  (:require-macros [cljs.react.core :refer [defnc]]))

(defnc DBCounterDisplay
  []
  (let [count (use-db [:counter :value])]
    (Div {:className "text-center"}
      (Div {:className "text-5xl font-bold text-koi-orange mb-2"} @count)
      (P {:className "text-sm text-gray-600"} "Shared Counter Value"))))

(defnc DBCounterControls
  []
  (let [count (use-db [:counter :value])]
    (Div {:className "flex gap-2 justify-center flex-wrap"}
      (Button {:className "px-6 py-2 bg-gray-200 text-gray-700 rounded-lg font-medium hover:bg-gray-300 transition-colors"
               :onClick #(swap! count dec)}
        "−")
      (Button {:className "px-6 py-2 bg-gray-100 text-gray-600 rounded-lg font-medium hover:bg-gray-200 transition-colors"
               :onClick #(reset! count 0)}
        "Reset")
      (Button {:className "px-6 py-2 bg-koi-orange text-white rounded-lg font-medium shadow hover:bg-orange-600 transition-colors"
               :onClick #(swap! count inc)}
        "+"))))

(defnc DBUserForm
  []
  (let [name (use-db [:user :name])
        email (use-db [:user :email])]
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
  (let [name (use-db [:user :name])
        email (use-db [:user :email])
        empty-name? (str/blank? @name)
        empty-email? (str/blank? @email)]
    (Div {:className "p-4 bg-gradient-to-br from-gray-50 to-white border border-gray-200 rounded-lg space-y-3"}
      (Div {:className "text-xs uppercase tracking-wide text-gray-500 font-semibold"}
        "Live preview")
      (Div
        (Div {:className "text-xs text-gray-500 mb-0.5"} "Name")
        (Div {:className (str "text-sm break-words "
                              (if empty-name?
                                "text-gray-400 italic"
                                "text-gray-900 font-medium"))}
          (if empty-name? "—" @name)))
      (Div
        (Div {:className "text-xs text-gray-500 mb-0.5"} "Email")
        (Div {:className (str "text-sm break-all "
                              (if empty-email?
                                "text-gray-400 italic"
                                "text-gray-900 font-medium"))}
          (if empty-email? "—" @email))))))

(defnc DBDemo
  []
  (DBProvider {:initial-value {:counter {:value 0}
                               :user {:name "" :email ""}}}
    (Div {:className "w-full space-y-6"}
      (Div {:className "space-y-4"}
        (H4 {:className "text-sm font-semibold text-gray-700 uppercase tracking-wide"}
          "Shared Counter")
        (P {:className "text-xs text-gray-500 -mt-3"}
          "Two components, one cursor each — both stay in sync.")
        (DBCounterDisplay)
        (DBCounterControls))
      (Div {:className "space-y-4"}
        (H4 {:className "text-sm font-semibold text-gray-700 uppercase tracking-wide"}
          "User Form")
        (P {:className "text-xs text-gray-500 -mt-3"}
          "Cursors scoped to nested paths.")
        (Div {:className "grid grid-cols-1 md:grid-cols-2 gap-4"}
          (DBUserForm)
          (DBUserDisplay))))))

(defnc DBTab
  []
  (Section
    (H2 "🗄️ Global State (DBProvider & Cursors)")

    (CodeAndOutput
     {:title "DBProvider & use-db"
      :code "(defnc CounterDisplay []\n  (let [count (use-db [:counter])]\n    (Div @count)))\n\n(defnc CounterButton []\n  (let [count (use-db [:counter])]\n    (Button {:onClick #(swap! count inc)}\n      \"Increment\")))\n\n(defnc App []\n  (DBProvider {:initial-value {:counter 0}}\n    (CounterDisplay)\n    (CounterButton)))"}
     (DBDemo))))
