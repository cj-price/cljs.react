(ns cljs.react.demo.db
  (:require [clojure.string :as str]
            [cljs.react.core :refer [DBProvider use-db]]
            [cljs.react.sx :refer [use-sx]]
            [cljs.react.demo.ui :refer [CodeAndOutput Btn Row Stack Grid Panel
                                        TextInput FieldLabel SectionTitle
                                        Caption]]
            [cljs.react.demo.util :refer [Div P H4 Section]])
  (:require-macros [cljs.react.core :refer [defnc]]))

(defnc DBCounterDisplay
  []
  (let [count (use-db [:counter :value])]
    (Stack {:gap 0.5 :align :center}
      (Div {:className (use-sx {:font-size "3rem" :font-weight 700
                                :line-height 1
                                :font-family :typography.font-family-mono
                                :color :palette.primary.main})}
        @count)
      (P {:className (use-sx {:font-size "0.75rem" :text-transform :uppercase
                              :letter-spacing "0.06em"
                              :color :palette.text.secondary})}
        "Shared counter value"))))

(defnc DBCounterControls
  []
  (let [count (use-db [:counter :value])]
    (Row {:gap 1 :justify :center}
      (Btn {:variant :secondary :onClick #(swap! count dec)} "−")
      (Btn {:variant :ghost :onClick #(reset! count 0)} "Reset")
      (Btn {:onClick #(swap! count inc)} "+"))))

(defnc DBUserForm
  []
  (let [name (use-db [:user :name])
        email (use-db [:user :email])]
    (Stack {:gap 1.5}
      (Div
        (FieldLabel {} "Name")
        (TextInput {:value (or @name "")
                    :onChange #(reset! name (-> % .-target .-value))}))
      (Div
        (FieldLabel {} "Email")
        (TextInput {:type "email"
                    :value (or @email "")
                    :onChange #(reset! email (-> % .-target .-value))})))))

(defnc PreviewField
  [{:keys [label value break]}]
  (let [blank? (str/blank? value)]
    (Div
      (Div {:className (use-sx {:font-size "0.6875rem" :mb 0.25
                                :color :palette.text.secondary})}
        label)
      (Div {:className (use-sx [{:font-size "0.875rem"
                                 :overflow-wrap (or break "break-word")}
                                (if blank?
                                  {:color :palette.text.disabled
                                   :font-style :italic}
                                  {:color :palette.text.primary
                                   :font-weight 500})])}
        (if blank? "—" value)))))

(defnc DBUserDisplay
  []
  (let [name (use-db [:user :name])
        email (use-db [:user :email])]
    (Panel {:className (use-sx {:display :flex :flex-direction :column :gap 1.5})}
      (Div {:className (use-sx {:font-size "0.6875rem" :font-weight 600
                                :text-transform :uppercase
                                :letter-spacing "0.06em"
                                :color :palette.text.secondary})}
        "Live preview")
      (PreviewField {:label "Name" :value @name})
      (PreviewField {:label "Email" :value @email :break "anywhere"}))))

(defnc SectionHeading
  [{:keys [title note]}]
  (Div
    (H4 {:className (use-sx {:font-size "0.8125rem" :font-weight 600
                             :text-transform :uppercase
                             :letter-spacing "0.06em"
                             :color :palette.text.primary})}
      title)
    (Caption {} note)))

(defnc DBDemo
  []
  (DBProvider {:value {:counter {:value 0}
                       :user {:name "" :email ""}}}
    (Stack {:gap 4}
      (Stack {:gap 2}
        (SectionHeading {:title "Shared counter"
                         :note "Two components, one cursor each — both stay in sync."})
        (DBCounterDisplay)
        (DBCounterControls))
      (Stack {:gap 2}
        (SectionHeading {:title "User form"
                         :note "Cursors scoped to nested paths."})
        (Grid {:min-width "220px" :gap 2}
          (DBUserForm)
          (DBUserDisplay))))))

(defnc DBTab
  []
  (Section
    (SectionTitle {} "🗄️ Global State (DBProvider & Cursors)")

    (CodeAndOutput
     {:title "DBProvider & use-db"
      :code "(defnc CounterDisplay []\n  (let [count (use-db [:counter])]\n    (Div @count)))\n\n(defnc CounterButton []\n  (let [count (use-db [:counter])]\n    (Button {:onClick #(swap! count inc)}\n      \"Increment\")))\n\n(defnc App []\n  (DBProvider {:value {:counter 0}}\n    (CounterDisplay)\n    (CounterButton)))"}
     (DBDemo))))
