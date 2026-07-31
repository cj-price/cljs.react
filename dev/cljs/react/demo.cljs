(ns cljs.react.demo
  (:require [cljs.react.core :refer [use-state use-sync-external-store]]
            [cljs.react.dom :as dom]
            [cljs.react.sx :as sx :refer [use-sx]]
            [cljs.react.demo.theme :as theme]
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
            [cljs.react.demo.util :refer [Div Span H1 Button Header
                                          Nav Main]])
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

(def ^:private dark-media
  (when (exists? js/window)
    (.matchMedia js/window "(prefers-color-scheme: dark)")))

(defn- subscribe-scheme
  [callback]
  (if dark-media
    (do (.addEventListener dark-media "change" callback)
        #(.removeEventListener dark-media "change" callback))
    (fn [])))

(defn- system-dark?
  []
  (boolean (and dark-media (.-matches dark-media))))

(defn- use-system-dark?
  []
  (use-sync-external-store subscribe-scheme system-dark?))

(defn- resolve-dark?
  [pref system-dark?]
  (case pref
    :dark   true
    :light  false
    :system system-dark?))

(def ^:private theme-options
  [{:value :light  :icon "☀️" :label "Light"}
   {:value :dark   :icon "🌙" :label "Dark"}
   {:value :system :icon "🖥️" :label "System"}])

(def ^:private pref-storage-key "cljs-react-demo.theme-pref")

(def ^:private pref-values (into #{} (map :value) theme-options))

(defn- load-pref
  []
  (or (try
        (some-> (.-localStorage js/window)
                (.getItem pref-storage-key)
                keyword
                pref-values)
        (catch :default _ nil))
      :system))

(defn- store-pref!
  [pref]
  (try
    (some-> (.-localStorage js/window)
            (.setItem pref-storage-key (name pref)))
    (catch :default _ nil)))

(defstyle switch-group
  {:display :inline-flex :align-items :center :gap 0.25
   :p 0.25 :border-radius 1.25
   :bgcolor :palette.background.paper
   :border "1px solid" :border-color :palette.divider})

(defstyle switch-option
  {:display :inline-flex :align-items :center :gap 0.5
   :px 1.25 :py 0.75 :border-radius 1
   :border "1px solid transparent"
   :font-family :inherit :font-size "0.8125rem" :font-weight 500
   :cursor :pointer :bgcolor :transparent
   :color :palette.text.secondary
   :transition "background-color 140ms ease, color 140ms ease, box-shadow 140ms ease"
   :&:hover {:color :palette.text.primary}
   :&:focus-visible {:outline "2px solid"
                     :outline-color :palette.primary.main
                     :outline-offset "2px"}})

(def ^:private switch-option-active
  {:bgcolor :palette.primary.main
   :color :palette.primary.contrast-text
   :box-shadow 1
   :&:hover {:color :palette.primary.contrast-text}})

(defnc ThemeSwitch
  [{:keys [pref on-pref]}]
  (let [idle   (use-sx switch-option)
        active (use-sx [switch-option switch-option-active])]
    (Div {:className (use-sx switch-group)
          :role "group" :aria-label "Colour theme"}
      (for [{:keys [value icon label]} theme-options
            :let [selected? (= pref value)]]
        (Button {:key value
                 :aria-pressed (if selected? "true" "false")
                 :onClick #(on-pref value)
                 :className (if selected? active idle)}
          (Span {:aria-hidden "true"} icon) label)))))

(defnc SiteHeader
  [{:keys [pref on-pref]}]
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
          " demo"))
      (ThemeSwitch {:pref pref :on-pref on-pref}))))

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

(defnc Page
  [{:keys [pref on-pref selected on-select]}]
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
      (SiteHeader {:pref pref :on-pref on-pref})
      (SiteNav {:selected selected :on-select on-select})
      (Main {:className (use-sx [container {:flex 1 :py 4}])}
        (view {})))))

(defnc App
  []
  (let [pref     (use-state (load-pref))
        selected (use-state :basics)
        dark?    (resolve-dark? @pref (use-system-dark?))]
    ;; Root ThemeProvider renders no DOM node, so the page surface below is a
    ;; child: it has to read the theme this provider installs, not the default.
    (sx/ThemeProvider {:theme (theme/for-mode dark?)}
      (sx/BaselineProvider {:body? true :enable-color-scheme? true})
      (Page {:pref      @pref
             :on-pref   #(do (store-pref! %) (reset! pref %))
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
