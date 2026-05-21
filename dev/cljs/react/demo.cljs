(ns cljs.react.demo
  (:require [cljs.react.dom :as dom]
            [cljs.react.hook :refer [use-state]]
            [cljs.react.demo.basics :refer [BasicsTab]]
            [cljs.react.demo.state :refer [StateTab]]
            [cljs.react.demo.effects :refer [EffectsTab]]
            [cljs.react.demo.advanced :refer [AdvancedTab]]
            [cljs.react.demo.db :refer [DBTab]]
            [cljs.react.demo.forms :refer [FormsTab]]
            [cljs.react.demo.mui :refer [MUITab]]
            [cljs.react.demo.util :refer [Div Span P H1 Button Header
                                          Nav Main Footer A Strong]])
  (:require-macros [cljs.react.core :refer [defnc]]))

(defnc App
  []
  (let [selected (use-state :basics)
        sections [{:id :basics :title "Basic Components" :emoji "🧱"}
                  {:id :state :title "State Management" :emoji "📊"}
                  {:id :effects :title "Side Effects" :emoji "⚡"}
                  {:id :advanced :title "Advanced" :emoji "🚀"}
                  {:id :db :title "Global State" :emoji "🗄️"}
                  {:id :forms :title "Forms" :emoji "📝"}
                  {:id :mui :title "MUI" :emoji "🎨"}]]
    (Div
      (Header
        (Div {:className "container"}
          (H1
            (Span {:className "accent"} "cljs.react")
            " Comprehensive Demo")
          (P {:className "subtitle"}
            "A modern React wrapper for ClojureScript")))

      (Nav
        (Div {:className "container"}
          (for [section sections]
            (Button {:key (:id section)
                     :className (if (= @selected (:id section)) "active" "")
                     :onClick #(reset! selected (:id section))}
              (:emoji section) " " (:title section)))))

      (Main
        (when (= @selected :basics)   (BasicsTab))
        (when (= @selected :state)    (StateTab))
        (when (= @selected :effects)  (EffectsTab))
        (when (= @selected :advanced) (AdvancedTab))
        (when (= @selected :db)       (DBTab))
        (when (= @selected :forms)    (FormsTab))
        (when (= @selected :mui)      (MUITab)))

      (Footer
        (Div {:className "container"}
          (P
            "Built with "
            (Strong "cljs.react")
            " - A modern React wrapper for ClojureScript")
          (Div {:style #js {:display "flex" :justifyContent "center" :gap "1rem" :marginTop "1rem" :fontSize "0.875rem"}}
            (A {:href "https://github.com/cj-price/cljs.react"
                :className "hover:text-koi-orange transition-colors"
                :target "_blank"}
              "GitHub")
            (Span "•")
            (A {:href "https://clojurescript.org"
                :className "hover:text-koi-orange transition-colors"
                :target "_blank"}
              "ClojureScript")
            (Span "•")
            (A {:href "https://react.dev"
                :target "_blank"}
              "React")))))))

(defonce root (atom nil))

(defn ^:dev/after-load reload []
  (when @root
    (dom/render @root (App {}))))

(defn ^:export init
  "Initialize the React application using React 18+ createRoot API"
  []
  (when-let [root-el (.getElementById js/document "app")]
    (reset! root (dom/create-root root-el))
    (dom/render @root (App {}))))
