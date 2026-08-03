(ns cljs.react.demo
  (:require [cljs.react.core :refer [use-state use-effect
                                     use-sync-external-store
                                     Element Fragment]]
            [cljs.react.sx :as sx :refer [use-sx]]
            [cljs.react.demo.theme :as theme]
            [cljs.react.demo.getting-started :refer [GettingStartedTab]]
            [cljs.react.demo.basics :refer [BasicsTab]]
            [cljs.react.demo.state :refer [StateTab]]
            [cljs.react.demo.effects :refer [EffectsTab]]
            [cljs.react.demo.advanced :refer [AdvancedTab]]
            [cljs.react.demo.db :refer [DBTab]]
            [cljs.react.demo.forms :refer [FormsTab]]
            [cljs.react.demo.boundaries :refer [BoundariesTab]]
            [cljs.react.demo.concurrent :refer [ConcurrentTab]]
            [cljs.react.demo.interop :refer [InteropTab]]
            [cljs.react.demo.reagent :refer [ReagentTab]]
            [cljs.react.demo.mui :refer [MUITab]]
            [cljs.react.demo.sx :refer [SXTab]]
            [cljs.react.demo.util :refer [Div Span H1 Button Header
                                          Nav Main]])
  (:require-macros [cljs.react.core :refer [defnc]]
                   [cljs.react.sx :refer [defstyle]]))

;; Grouped for the nav; flattened for the body. The body used to be an
;; eleven-way `when` ladder, so adding a tab meant editing three places and
;; forgetting one was silent. `sections` stays the single flat lookup both the
;; body and default selection read; `groups` only adds the nav headings.
(def groups
  [{:title nil
    :sections
    [{:id :start      :title "Getting Started"  :emoji "🏁" :view GettingStartedTab}]}
   {:title "Core"
    :sections
    [{:id :basics     :title "Basic Components" :emoji "🧱" :view BasicsTab}
     {:id :state      :title "State Management" :emoji "📊" :view StateTab}
     {:id :effects    :title "Side Effects"     :emoji "⚡" :view EffectsTab}
     {:id :advanced   :title "Advanced"         :emoji "🚀" :view AdvancedTab}
     {:id :boundaries :title "Boundaries"       :emoji "🛡️" :view BoundariesTab}
     {:id :concurrent :title "Concurrent"       :emoji "⏱️" :view ConcurrentTab}]}
   {:title "Interop"
    :sections
    [{:id :interop    :title "Interop"          :emoji "🔌" :view InteropTab}
     {:id :reagent    :title "Reagent"          :emoji "⚛️" :view ReagentTab}
     {:id :mui        :title "MUI"              :emoji "🎨" :view MUITab}]}
   {:title "Extras"
    :sections
    [{:id :db         :title "Global State"     :emoji "🗄️" :view DBTab
      :experimental true}
     {:id :forms      :title "Forms"            :emoji "📝" :view FormsTab
      :experimental true}
     {:id :sx         :title "Styling"          :emoji "💅" :view SXTab
      :experimental true}]}])

(def sections (into [] (mapcat :sections) groups))

;; ---------------------------------------------------------------------------

(def ^:private bar-h "68px")

(defstyle container
  {:width "100%" :max-width "1400px" :mx :auto :px 2})

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

(defstyle hamburger
  {:display {:xs :inline-flex :md :none}
   :align-items :center :justify-content :center
   :width "2.5rem" :height "2.5rem" :p 0 :flex-shrink 0
   :border-radius 1 :cursor :pointer :font-size "1.25rem" :line-height 1
   :border "1px solid" :border-color :palette.divider
   :bgcolor :palette.background.paper :color :palette.text.primary
   :&:focus-visible {:outline "2px solid" :outline-color :palette.primary.main
                     :outline-offset "2px"}})

(defnc SiteHeader
  [{:keys [pref on-pref on-toggle open]}]
  (Header {:className (use-sx bar)}
    (Div {:className (use-sx [container {:display :flex :gap 2
                                         :align-items :center
                                         :justify-content :space-between
                                         :flex-wrap :wrap
                                         :min-height bar-h :py 1.5}])}
      (Div {:className (use-sx {:display :flex :align-items :center :gap 1.5})}
        (Button {:type "button"
                 :aria-label (if open "Close navigation" "Open navigation")
                 :aria-expanded (if open "true" "false") :onClick on-toggle
                 :className (use-sx hamburger)}
          (Span {:aria-hidden "true"} "☰"))
        (H1 {:className (use-sx {:font-size {:xs "1.6rem" :md "2.25rem"}
                                 :font-weight 700 :letter-spacing "-0.03em"
                                 :color :palette.text.primary})}
          (Span {:className (use-sx {:color :palette.primary.main})} "cljs.react")
          " demo"))
      (ThemeSwitch {:pref pref :on-pref on-pref}))))

(defstyle sidebar
  {:position :fixed :top 0 :left 0 :bottom 0 :z-index :z-index.drawer
   :box-sizing :border-box :width "264px"
   :display :flex :flex-direction :column :gap 0.5 :p 2 :overflow-y :auto
   :bgcolor :palette.background.paper
   :border-right "1px solid" :border-color :palette.divider
   :transition "transform 200ms ease, visibility 200ms ease"
   ;; At `:md` the drawer becomes a persistent, in-flow sidebar column. Raw
   ;; at-rule (not `:md` bp map) so the whole override reads as one block, and
   ;; `:transform :none` wins over the `open`-driven base transform composed in
   ;; the component regardless of drawer state after a resize.
   ;; `1rem` past `bar-h` so the stuck column keeps a gap below the header
   ;; instead of butting against it; no border (a full-height rule dangles well
   ;; past the short nav) — the dot-grid gap to the content is the separation.
   "@media (min-width: 900px)"
   {:position :sticky :top (str "calc(" bar-h " + 1rem)") :bottom :auto
    :z-index :auto :width "240px" :flex-shrink 0 :transform :none
    :visibility :visible
    :px 0 :py 1 :max-height (str "calc(100vh - " bar-h " - 2rem)")
    :bgcolor :transparent :border-right :none}})

(defstyle nav-link
  {:display :flex :align-items :center :gap 0.75 :width "100%"
   :px 1.5 :py 1 :border-radius 1 :text-align :left
   :font-family :inherit :font-size "0.875rem" :font-weight 500
   :cursor :pointer :border "1px solid transparent"
   :bgcolor :transparent :color :palette.text.secondary
   :transition "background-color 140ms ease, color 140ms ease"
   :&:hover {:bgcolor :palette.surface.tint :color :palette.text.primary}
   :&:focus-visible {:outline "2px solid"
                     :outline-color :palette.primary.main
                     :outline-offset "2px"}})

(def ^:private nav-link-active
  {:bgcolor :palette.primary.main
   :border-color :palette.primary.main
   :color :palette.primary.contrast-text
   :box-shadow 1
   :&:hover {:color :palette.primary.contrast-text}})

(defstyle nav-badge
  {:display :inline-flex :align-items :center :justify-content :center
   :margin-left :auto :flex-shrink 0
   :px 0.75 :height "1.125rem" :border-radius "999px"
   :font-size "0.5625rem" :font-weight 700 :letter-spacing "0.07em"
   :text-transform :uppercase :line-height 1
   :bgcolor :palette.warning.main :color :palette.warning.contrast-text
   :box-shadow 1})

(defstyle nav-group-label
  {:px 1.5 :pt 2 :pb 0.5
   :font-size "0.6875rem" :font-weight 700 :letter-spacing "0.08em"
   :text-transform :uppercase :color :palette.text.secondary
   ;; A heading first in the nav sits flush with the nav's own top padding.
   ;; `:first-child`, not `:first-of-type`: with the title-less Getting Started
   ;; group first, the "Core" label is the first DIV yet follows a button, and
   ;; must keep its full spacing.
   :&:first-child {:pt 0.5}})

(defnc SiteNav
  [{:keys [selected on-select open]}]
  ;; Every class is computed HERE, not in the loops below. `for` is lazy, and
  ;; React realizes the seq after this function has already returned — a
  ;; `use-sx` in a loop body would run with no dispatcher installed and throw
  ;; "Invalid hook call".
  (let [idle-cls   (use-sx nav-link)
        active-cls (use-sx [nav-link nav-link-active])
        label-cls  (use-sx nav-group-label)
        badge-cls  (use-sx nav-badge)]
    (Nav {:className (use-sx [sidebar {:transform (if open
                                                    "translateX(0)"
                                                    "translateX(-100%)")
                                       :visibility (if open :visible :hidden)}])
          :aria-label "Sections"}
      (for [{group-title :title :keys [sections]} groups]
        (Element {:tag Fragment :key (or group-title (-> sections first :id name))}
          (when group-title
            (Div {:className label-cls} group-title))
          (for [{:keys [id title emoji experimental]} sections
                :let [active? (= selected id)]]
            (Button {:key id
                     :aria-current (when active? "page")
                     :onClick #(on-select id)
                     :className (if active? active-cls idle-cls)}
              (Span {:aria-hidden "true"} emoji) " " title
              (when experimental
                (Span {:className badge-cls} "Experimental")))))))))

(defnc Backdrop
  [{:keys [on-close]}]
  (Div {:aria-hidden "true" :onClick on-close
        :className (use-sx {:position :fixed :inset 0 :z-index 1150
                            :display {:md :none}
                            :bgcolor "rgba(0, 0, 0, 0.5)"})}))

(defnc Page
  [{:keys [pref on-pref selected on-select open on-toggle on-close]}]
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
      (SiteHeader {:pref pref :on-pref on-pref :on-toggle on-toggle :open open})
      ;; Sidebar + main share one centred, max-width row so the content sits
      ;; flush beside the sidebar (not centred in the leftover flex space, which
      ;; drifts far right on wide displays), aligned with the header above.
      (Div {:className (use-sx [container {:display :flex :align-items :flex-start
                                           :flex 1 :gap {:md 3}}])}
        (SiteNav {:selected selected :on-select on-select :open open})
        (when open (Backdrop {:on-close on-close}))
        (Main {:className (use-sx {:flex 1 :min-width 0 :py 4})}
          (view {}))))))

(defnc App
  []
  (let [pref     (use-state (load-pref))
        selected (use-state :start)
        open     (use-state false)
        dark?    (resolve-dark? @pref (use-system-dark?))]
    (use-effect
      (fn []
        (if @open
          (let [handler (fn [e]
                          (when (= (.-key e) "Escape")
                            (reset! open false)))]
            (.addEventListener js/document "keydown" handler)
            (fn [] (.removeEventListener js/document "keydown" handler)))
          js/undefined))
      [@open])
    ;; Root ThemeProvider renders no DOM node, so the page surface below is a
    ;; child: it has to read the theme this provider installs, not the default.
    (sx/ThemeProvider {:theme (theme/for-mode dark?)}
      (sx/BaselineProvider {:body? true :enable-color-scheme? true})
      (Page {:pref      @pref
             :on-pref   #(do (store-pref! %) (reset! pref %))
             :selected  @selected
             :on-select #(do (reset! selected %) (reset! open false))
             :open      @open
             :on-toggle #(swap! open not)
             :on-close  #(reset! open false)}))))

