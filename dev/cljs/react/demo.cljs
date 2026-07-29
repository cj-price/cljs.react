(ns cljs.react.demo
  (:require [cljs.react.core :refer [use-state use-effect]]
            [cljs.react.dom :as dom]
            [cljs.react.sx :as sx :refer [use-sx]]
            [cljs.react.demo.theme :as theme]
            [cljs.react.demo.ui :as ui :refer [Btn Row]]
            [cljs.react.demo.basics :refer [BasicsTab]]
            [cljs.react.demo.state :refer [StateTab]]
            [cljs.react.demo.effects :refer [EffectsTab]]
            [cljs.react.demo.advanced :refer [AdvancedTab]]
            [cljs.react.demo.db :refer [DBTab]]
            [cljs.react.demo.forms :refer [FormsTab]]
            [cljs.react.demo.boundaries :refer [BoundariesTab]]
            [cljs.react.demo.concurrent :refer [ConcurrentTab]]
            [cljs.react.demo.interop :refer [InteropTab]]
            [cljs.react.demo.mui :refer [MUITab]]
            [cljs.react.demo.sx :refer [SXTab]]
            [cljs.react.demo.util :refer [Div Span P H1 Button Header
                                          Nav Main Footer A Strong]])
  (:require-macros [cljs.react.core :refer [defnc]]
                   [cljs.react.sx :refer [defstyle]]))

;; One list, used for both the nav and the body. The body used to be an
;; eleven-way `when` ladder sitting directly under this vector, so adding a tab
;; meant editing three places and forgetting one was silent.
(def sections
  [{:id :basics     :title "Basic Components" :emoji "🧱" :view BasicsTab}
   {:id :state      :title "State Management" :emoji "📊" :view StateTab}
   {:id :effects    :title "Side Effects"     :emoji "⚡" :view EffectsTab}
   {:id :advanced   :title "Advanced"         :emoji "🚀" :view AdvancedTab}
   {:id :db         :title "Global State"     :emoji "🗄️" :view DBTab}
   {:id :forms      :title "Forms"            :emoji "📝" :view FormsTab}
   {:id :boundaries :title "Boundaries"       :emoji "🛡️" :view BoundariesTab}
   {:id :concurrent :title "Concurrent"       :emoji "⏱️" :view ConcurrentTab}
   {:id :interop    :title "Interop"          :emoji "🔌" :view InteropTab}
   {:id :mui        :title "MUI"              :emoji "🎨" :view MUITab}
   {:id :sx         :title "Styling"          :emoji "💅" :view SXTab}])

;; ---------------------------------------------------------------------------

(defstyle container
  {:width "100%" :max-width "1200px" :mx :auto :px 2})

(defstyle bar
  {:position :sticky :top 0 :z-index :z-index.app-bar
   :bgcolor :palette.surface.overlay
   :backdrop-filter "blur(12px)"
   :border-bottom "1px solid" :border-color :palette.divider})

(defn- rule-count
  "Rules currently in the generated stylesheet, read from the CSSOM.

  Deliberately not `sheet/compile-count`: that counter is dev-only, so the
  deployed demo would forever claim zero compiles. This number is real in both
  builds — and holding still across a theme swap is the whole claim."
  []
  (or (some-> (.querySelector js/document "[data-cljs-react-sx]")
              .-sheet .-cssRules .-length)
      0))

;; These take VALUES and callbacks, never the StateAtom itself. `defnc`
;; memoizes with CLJS `=`, and a StateAtom keys equality on its shared
;; underlying object — so an atom prop is `=` on every render and a memoized
;; child holding one never re-renders, however much its value changed.
(defnc ThemeToggle
  [{:keys [dark? on-toggle]}]
  (let [rules (use-state 0)]
    ;; No deps: re-read after every commit and write back only on a change, so
    ;; it settles in one extra render instead of looping.
    (use-effect (fn []
                  (let [n (rule-count)]
                    (when (not= n @rules) (reset! rules n)))
                  js/undefined))
    (Row {:gap 1.5}
      (Span {:className (use-sx {:font-size "0.75rem" :white-space :nowrap
                                 :color :palette.text.secondary
                                 :font-family :typography.font-family-mono})}
        @rules " CSS rules")
      (Btn {:variant :secondary :size :sm
            :aria-pressed (str (boolean dark?))
            :onClick on-toggle}
        (if dark? "☀️ Light" "🌙 Dark")))))

(defnc SiteHeader
  [{:keys [dark? on-toggle]}]
  (Header {:className (use-sx [bar {:position :static}])}
    (Div {:className (use-sx [container {:display :flex :gap 2
                                         :align-items :center
                                         :justify-content :space-between
                                         :flex-wrap :wrap :py 3}])}
      (Div
        (H1 {:className (use-sx {:font-size {:xs "1.6rem" :md "2.25rem"}
                                 :font-weight 700 :letter-spacing "-0.03em"
                                 :color :palette.text.primary})}
          (Span {:className (use-sx {:color :palette.primary.main})} "cljs.react")
          " demo")
        (P {:className (use-sx {:mt 0.5 :font-size "1rem"
                                :color :palette.text.secondary})}
          "Idiomatic React 19 for ClojureScript"))
      (ThemeToggle {:dark? dark? :on-toggle on-toggle}))))

(defstyle tab-button
  {:display :inline-flex :align-items :center :gap 0.75
   :px 1.75 :py 1 :border-radius 1
   :font-family :inherit :font-size "0.875rem"
   :font-weight 500 :cursor :pointer
   :border "1px solid" :border-color :palette.divider
   :bgcolor :palette.background.paper
   :color :palette.text.secondary
   :transition "transform 140ms ease, box-shadow 140ms ease, background-color 140ms ease, color 140ms ease"
   :&:hover {:transform "translateY(-2px)" :box-shadow 2
             :color :palette.text.primary}
   :&:focus-visible {:outline "2px solid"
                     :outline-color :palette.primary.main
                     :outline-offset "2px"}})

(def ^:private tab-button-active
  {:bgcolor :palette.primary.main
   :border-color :palette.primary.main
   :color :palette.primary.contrast-text
   :box-shadow 2
   :&:hover {:color :palette.primary.contrast-text}})

(defnc SiteNav
  [{:keys [selected on-select]}]
  ;; Both classes are computed HERE, not in the loop below. `for` is lazy, and
  ;; React realizes the seq after this function has already returned — a
  ;; `use-sx` in the loop body would run with no dispatcher installed and throw
  ;; "Invalid hook call".
  (let [idle-cls   (use-sx tab-button)
        active-cls (use-sx [tab-button tab-button-active])]
    (Nav {:className (use-sx bar)}
      (Div {:className (use-sx [container {:display :flex :flex-wrap :wrap :gap 1
                                           :justify-content :center :py 1.5}])}
        (for [{:keys [id title emoji]} sections
              :let [active? (= selected id)]]
          (Button {:key id
                   :aria-current (when active? "page")
                   :onClick #(on-select id)
                   :className (if active? active-cls idle-cls)}
            emoji " " title))))))

(defnc SiteFooter
  [_]
  (Footer {:className (use-sx {:mt 6 :py 4
                               :border-top "1px solid"
                               :border-color :palette.divider
                               :bgcolor :palette.surface.overlay})}
    (Div {:className (use-sx [container {:display :flex :flex-direction :column
                                         :align-items :center :gap 1
                                         :text-align :center}])}
      (P {:className (use-sx {:font-size "0.875rem"
                              :color :palette.text.secondary})}
        "Built with " (Strong "cljs.react") " — every pixel on this page is styled by "
        (Strong "cljs.react.sx"))
      (let [link-cls (use-sx {:font-size "0.875rem" :text-decoration :none
                              :color :palette.text.secondary
                              :transition "color 140ms ease"
                              :&:hover {:color :palette.primary.main}})]
        (Row {:gap 2 :justify :center}
          (for [[label href] [["GitHub" "https://github.com/cj-price/cljs.react"]
                              ["ClojureScript" "https://clojurescript.org"]
                              ["React" "https://react.dev"]]]
            (A {:key label :href href :target "_blank" :rel "noreferrer"
                :className link-cls}
              label)))))))

(defnc Page
  [{:keys [dark? on-toggle selected on-select]}]
  (let [{:keys [view]} (first (filter #(= selected (:id %)) sections))]
    (Div {:className (use-sx {:min-height "100vh"
                              :display :flex :flex-direction :column
                              :bgcolor :palette.background.default
                              :color :palette.text.primary
                              ;; The dot grid the old sheet drew on <body>,
                              ;; now themed rather than hardcoded.
                              :background-image
                              (str "radial-gradient(circle, "
                                   (sx/theme-var :palette.dot)
                                   " 1px, transparent 1px)")
                              :background-size "32px 32px"})}
      (SiteHeader {:dark? dark? :on-toggle on-toggle})
      (SiteNav {:selected selected :on-select on-select})
      (Main {:className (use-sx [container {:flex 1 :py 4}])}
        (view {}))
      (SiteFooter {}))))

(defnc App
  []
  (let [dark?    (use-state false)
        selected (use-state :basics)]
    ;; Root ThemeProvider renders no DOM node, so the page surface below is a
    ;; child: it has to read the theme this provider installs, not the default.
    (sx/ThemeProvider {:theme (theme/for-mode @dark?)}
      (sx/BaselineProvider {:body? true :enable-color-scheme? true})
      (Page {:dark?     @dark?
             :on-toggle #(swap! dark? not)
             :selected  @selected
             :on-select #(reset! selected %)}))))

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
