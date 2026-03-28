(ns cljs.react.demo
  (:require ["react" :as react]
            ["react-dom/client" :as react-dom]
            [cljs.react.core :refer [Element]]
            [cljs.react.demo.basics :refer [BasicsTab]]
            [cljs.react.demo.state :refer [StateTab]]
            [cljs.react.demo.effects :refer [EffectsTab]]
            [cljs.react.demo.advanced :refer [AdvancedTab]]
            [cljs.react.demo.db :refer [DBTab]]
            [cljs.react.demo.forms :refer [FormsTab]]
            [cljs.react.demo.mui :refer [MUITab]])
  (:require-macros [cljs.react.core :refer [defnc]]))

(defnc App
  []
  (let [[selected-section set-selected-section] (react/useState :basics)
        sections [{:id :basics :title "Basic Components" :emoji "🧱"}
                  {:id :state :title "State Management" :emoji "📊"}
                  {:id :effects :title "Side Effects" :emoji "⚡"}
                  {:id :advanced :title "Advanced" :emoji "🚀"}
                  {:id :db :title "Global State" :emoji "🗄️"}
                  {:id :forms :title "Forms" :emoji "📝"}
                  {:id :mui :title "MUI" :emoji "🎨"}]]
    (Element {:tag "div"}
      ;; Header
      (Element {:tag "header"}
        (Element {:tag "div" :className "container"}
          (Element {:tag "h1"}
            (Element {:tag "span" :className "accent"} "cljs.react")
            " Comprehensive Demo")
          (Element {:tag "p" :className "subtitle"}
            "A modern React wrapper for ClojureScript")))

      ;; Navigation
      (Element {:tag "nav"}
        (Element {:tag "div" :className "container"}
          (for [section sections]
            (Element {:tag "button"
                      :key (:id section)
                      :className (if (= selected-section (:id section)) "active" "")
                      :onClick #(set-selected-section (:id section))}
              (:emoji section) " " (:title section)))))

      ;; Main Content
      (Element {:tag "main"}
        (when (= selected-section :basics)   (BasicsTab))
        (when (= selected-section :state)    (StateTab))
        (when (= selected-section :effects)  (EffectsTab))
        (when (= selected-section :advanced) (AdvancedTab))
        (when (= selected-section :db)       (DBTab))
        (when (= selected-section :forms)    (FormsTab))
        (when (= selected-section :mui)      (MUITab)))

      ;; Footer
      (Element {:tag "footer"}
        (Element {:tag "div" :className "container"}
          (Element {:tag "p"}
            "Built with "
            (Element {:tag "strong"} "cljs.react")
            " - A modern React wrapper for ClojureScript")
          (Element {:tag "div" :style {:display "flex" :justifyContent "center" :gap "1rem" :marginTop "1rem" :fontSize "0.875rem"}}
              (Element {:tag "a"
                        :href "https://github.com"
                        :className "hover:text-koi-orange transition-colors"
                        :target "_blank"}
                "GitHub")
              (Element {:tag "span"} "•")
              (Element {:tag "a"
                        :href "https://clojurescript.org"
                        :className "hover:text-koi-orange transition-colors"
                        :target "_blank"}
                "ClojureScript")
              (Element {:tag "span"} "•")
              (Element {:tag "a"
                        :href "https://react.dev"
                        :target "_blank"}
                "React")))))))

(defonce root (atom nil))

(defn ^:dev/after-load reload []
  (when @root
    (.render @root (App {}))))

(defn ^:export init
  "Initialize the React application using React 18+ createRoot API"
  []
  (when-let [root-el (.getElementById js/document "app")]
    (reset! root (react-dom/createRoot root-el))
    (.render @root (App {}))))
