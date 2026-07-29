(ns cljs.react.demo.ui
  "The demo's design system, built on `cljs.react.sx`.

  Every pattern the tabs used to repeat as a literal class string lives here
  once. Variants are plain sx maps composed into a `defstyle` base — `use-sx`
  deep-merges a composition vector into ONE class, so a variant's `&:hover`
  merges with the base's instead of clobbering it."
  (:require ["prismjs" :as Prism]
            ["prismjs/components/prism-clojure"]
            [cljs.react.core :refer [Element]]
            [cljs.react.sx :as sx :refer [use-sx]]
            [cljs.react.demo.util :refer [Div Span Pre Code]])
  (:require-macros [cljs.react.core :refer [defnc]]
                   [cljs.react.sx :refer [defstyle]]))

;; ---------------------------------------------------------------------------
;; Animations

(def spin
  (sx/keyframes {:from {:transform "rotate(0deg)"}
                 :to   {:transform "rotate(360deg)"}}))

(def pulse
  (sx/keyframes {[0 100] {:opacity 1}
                 50      {:opacity 0.35}}))

(def rise
  (sx/keyframes {:from {:opacity 0 :transform "translateY(4px)"}
                 :to   {:opacity 1 :transform "translateY(0)"}}))

(def calm
  "Compose into anything that animates. WCAG 2.2.2 (Pause, Stop, Hide) is a
  LEVEL A requirement and every animation here is `:infinite`, so without this
  the page moves forever with no way to stop it — the sustained motion that
  triggers vestibular symptoms.

  `0.01ms` rather than `:none`: a duration of zero would skip the animation's
  final frame, so a component that animates INTO its resting state would be
  left in its `from` state. One iteration at an imperceptible duration lands on
  `to` instead. A raw at-rule, which the sx dialect passes through — the
  library needs no special support for this."
  {"@media (prefers-reduced-motion: reduce)"
   {:animation-duration "0.01ms"
    :animation-iteration-count 1
    :transition-duration "0.01ms"}})

;; ---------------------------------------------------------------------------

(defn- tint
  "A translucent wash of a theme colour.

  `color-mix` rather than a per-tone theme token: the alpha belongs to the
  component, not to the palette, and this way a tone added later needs no
  matching pair of entries in both theme maps."
  [token pct]
  (str "color-mix(in srgb, " (sx/theme-var token) " " pct "%, transparent)"))

;; ---------------------------------------------------------------------------
;; Layout

(defn- pass
  "Props to forward to the DOM, plus the tag and class we computed.

  A caller's `:className` is appended rather than overwritten — otherwise every
  component here would silently swallow it. Note the ordering caveat that
  applies to any two sx classes on one element: they resolve by stylesheet
  insertion order, not by the order written here, so use this for properties
  the component does not already set."
  [props extra drop-keys]
  (let [base     (apply dissoc props :children drop-keys)
        own      (:className props)
        computed (:className extra)]
    (cond-> (merge base extra)
      (and own computed) (assoc :className (str computed " " own)))))

(defnc Stack
  [{:keys [gap align children] :as props}]
  (apply Element (pass props
                 {:tag "div"
                  :className (use-sx {:display :flex :flex-direction :column
                                      :gap (or gap 2)
                                      :align-items (or align :stretch)})}
                 [:gap :align])
    children))

(defnc Row
  [{:keys [gap align justify wrap? children] :as props}]
  (apply Element (pass props
                 {:tag "div"
                  :className (use-sx {:display :flex
                                      :gap (or gap 1)
                                      :align-items (or align :center)
                                      :justify-content (or justify :flex-start)
                                      :flex-wrap (if (false? wrap?) :nowrap :wrap)})}
                 [:gap :align :justify :wrap?])
    children))

(defnc Grid
  [{:keys [min-width gap children] :as props}]
  (apply Element (pass props
                 {:tag "div"
                  :className (use-sx
                               {:display :grid
                                :gap (or gap 2)
                                :grid-template-columns
                                (str "repeat(auto-fit, minmax(min("
                                     (or min-width "180px")
                                     ", 100%), 1fr))")})}
                 [:min-width :gap])
    children))

;; ---------------------------------------------------------------------------
;; Type

(defstyle section-title
  {:display :flex :align-items :center :gap 1.5
   :font-size "1.75rem" :font-weight :typography.font-weight-bold
   :color :palette.text.primary :letter-spacing "-0.02em" :mb 3})

(defstyle demo-title
  {:display :flex :align-items :center :gap 1
   :font-size "1.05rem" :font-weight 600 :color :palette.text.primary :mb 2
   "&::before" {:content "" :width "0.5rem" :height "0.5rem" :flex-shrink 0
                :border-radius "50%" :bgcolor :palette.primary.main}})

(defnc SectionTitle
  [{:keys [children] :as props}]
  (apply Element (pass props {:tag "h2" :className (use-sx section-title)} []) children))

(defnc DemoTitle
  [{:keys [children] :as props}]
  (apply Element (pass props {:tag "h4" :className (use-sx demo-title)} []) children))

(defstyle caption
  {:font-size "0.8125rem" :color :palette.text.secondary :line-height 1.5})

(defstyle muted
  {:font-size "0.875rem" :color :palette.text.secondary})

(defstyle mono
  {:font-family :typography.font-family-mono :font-size "0.8125rem"
   :color :palette.text.primary})

(defnc Caption
  [{:keys [children] :as props}]
  (apply Element (pass props {:tag "p" :className (use-sx caption)} []) children))

(defnc Muted
  [{:keys [children] :as props}]
  (apply Element (pass props {:tag "p" :className (use-sx muted)} []) children))

(defnc Mono
  [{:keys [children] :as props}]
  (apply Element (pass props {:tag "code" :className (use-sx mono)} []) children))

;; ---------------------------------------------------------------------------
;; Buttons

(defstyle btn-base
  {:display :inline-flex :align-items :center :justify-content :center :gap 1
   :border "1px solid transparent" :border-radius 1 :cursor :pointer
   :font-family :inherit :font-weight 500 :line-height 1.2
   :white-space :nowrap :text-decoration :none
   :transition "background-color 140ms ease, border-color 140ms ease, box-shadow 140ms ease, transform 140ms ease"
   :&:focus-visible {:outline "2px solid" :outline-color :palette.primary.main
                     :outline-offset "2px"}
   :&:active {:transform "translateY(1px)"}
   "&:disabled" {:opacity 0.5 :cursor :not-allowed :transform :none
                 :box-shadow :none}})

(def ^:private btn-sizes
  {:sm {:px 1.5 :py 0.75 :font-size "0.8125rem"}
   :md {:px 2 :py 1 :font-size "0.875rem"}
   :lg {:px 3 :py 1.5 :font-size "1rem"}})

(def ^:private btn-variants
  {:primary
   {:bgcolor :palette.primary.main :color :palette.primary.contrast-text
    :box-shadow 1
    "&:hover:not(:disabled)"{:bgcolor :palette.primary.dark :box-shadow 2}}

   :secondary
   {:bgcolor :palette.background.paper :color :palette.text.primary
    :border-color :palette.divider
    "&:hover:not(:disabled)"{:bgcolor :palette.grey.100
                             :border-color :palette.grey.300}}

   :ghost
   {:bgcolor :transparent :color :palette.text.secondary
    "&:hover:not(:disabled)"{:bgcolor :palette.surface.tint
                             :color :palette.primary.main}}

   :danger
   {:bgcolor :palette.error.main :color :palette.error.contrast-text
    :box-shadow 1
    "&:hover:not(:disabled)"{:bgcolor :palette.error.dark :box-shadow 2}}

   :success
   {:bgcolor :palette.success.main :color :palette.success.contrast-text
    :box-shadow 1
    "&:hover:not(:disabled)"{:bgcolor :palette.success.dark :box-shadow 2}}})

;; :variant is :primary/:secondary/:ghost/:danger/:success, :size is
;; :sm/:md/:lg. Everything else passes through to the element.
(defnc Btn
  [{:keys [variant size full? children] :as props}]
  (apply Element (pass props
                 {:tag "button"
                  :type (or (:type props) "button")
                  :className (use-sx [btn-base
                                      (btn-sizes (or size :md))
                                      (btn-variants (or variant :primary))
                                      (when full? {:width "100%"})])}
                 [:variant :size :full?])
    children))

;; ---------------------------------------------------------------------------
;; Inputs

(defstyle field-base
  {:width "100%" :px 1.5 :py 1.25 :border-radius 1
   :border "2px solid" :font-family :inherit :font-size "0.875rem"
   :color :palette.text.primary :bgcolor :palette.background.paper
   :outline :none
   :transition "border-color 140ms ease, box-shadow 140ms ease"
   "&::placeholder" {:color :palette.text.disabled}
   "&:disabled" {:opacity 0.6 :cursor :not-allowed
                 :bgcolor :palette.surface.sunken}})

(def ^:private focus-ring
  (str "0 0 0 3px " (sx/theme-var :palette.focus.ring)))

(def ^:private field-ok
  {:border-color :palette.divider
   :&:focus {:border-color :palette.primary.main :box-shadow focus-ring}})

(def ^:private field-bad
  {:border-color :palette.error.main
   :bgcolor (tint :palette.error.main 8)
   :&:focus {:box-shadow (str "0 0 0 3px "
                              (tint :palette.error.main 30))}})

(defn field-class
  "The field class as a hook, for callers that must build their own element —
  a `:forward-ref` component, for instance, which cannot go through
  [[TextInput]]."
  ([] (field-class false))
  ([error?] (use-sx [field-base (if error? field-bad field-ok)])))

(defnc TextInput
  [{:keys [error? children] :as props}]
  (apply Element (pass props
                 {:tag "input"
                  :type (or (:type props) "text")
                  :className (use-sx [field-base (if error? field-bad field-ok)])}
                 [:error?])
    children))

(defnc TextArea
  [{:keys [error? children] :as props}]
  (apply Element (pass props
                 {:tag "textarea"
                  :className (use-sx [field-base (if error? field-bad field-ok)
                                      {:resize :vertical :min-height "5rem"}])}
                 [:error?])
    children))

(defnc Select
  [{:keys [error? children] :as props}]
  (apply Element (pass props
                 {:tag "select"
                  :className (use-sx [field-base (if error? field-bad field-ok)
                                      {:cursor :pointer}])}
                 [:error?])
    children))

(defnc FieldLabel
  ;; `tight?` rather than a caller-supplied `{:mb 0}` class. Two single-class
  ;; selectors of equal specificity resolve by STYLESHEET INSERTION ORDER, not
  ;; by the order written, so the override silently lost to this component's
  ;; own rule and every label kept its 0.75 margin. `pass`'s docstring warns
  ;; about exactly this; a variation the component owns has no such hazard.
  [{:keys [tight? children] :as props}]
  (apply Element (pass props
                 {:tag "label"
                  :className (use-sx [{:display :block :mb 0.75
                                       :font-size "0.8125rem" :font-weight 500
                                       :color :palette.text.secondary}
                                      (when tight? {:mb 0})])}
                 [:tight?])
    children))

(defnc FieldError
  [{:keys [children] :as props}]
  (apply Element (pass props
                 {:tag "p"
                  :role "alert"
                  :className (use-sx {:mt 0.75 :font-size "0.75rem"
                                      :color :palette.error.main})}
                 [])
    children))

;; ---------------------------------------------------------------------------
;; Surfaces

(defstyle card
  {:bgcolor :palette.background.paper
   :border "1px solid" :border-color :palette.divider
   :border-radius 1.5 :p 2 :box-shadow 1})

(defstyle panel
  {:bgcolor :palette.surface.sunken :border-radius 1.5 :p 2})

(defnc Card
  [{:keys [children] :as props}]
  (apply Element (pass props {:tag "div" :className (use-sx card)} []) children))

(defnc Panel
  [{:keys [children] :as props}]
  (apply Element (pass props {:tag "div" :className (use-sx panel)} []) children))

(defnc CardTitle
  [{:keys [children] :as props}]
  (apply Element (pass props
                 {:tag "h5"
                  :className (use-sx {:font-size "0.9375rem" :font-weight 600
                                      :color :palette.text.primary :mb 0.5})}
                 [])
    children))

;; A labelled number — the demo's most repeated shape by far.
(defnc Stat
  [{:keys [label value tone]}]
  (Div {:className (use-sx [panel {:display :flex :flex-direction :column
                                   :gap 0.25 :align-items :center :py 1.5}])}
    (Span {:className (use-sx {:font-size "1.5rem" :font-weight 700
                               :line-height 1.1
                               :font-family :typography.font-family-mono
                               :color (case tone
                                        :primary :palette.primary.main
                                        :success :palette.success.main
                                        :error   :palette.error.main
                                        :palette.text.primary)})}
      value)
    (Span {:className (use-sx {:font-size "0.6875rem" :text-transform :uppercase
                               :letter-spacing "0.06em"
                               :color :palette.text.secondary})}
      label)))

;; ---------------------------------------------------------------------------
;; Feedback

(def ^:private tone-token
  (into {:neutral {:fg :palette.text.secondary :bg :palette.surface.sunken}}
        (for [t [:primary :secondary :error :warning :info :success]]
          (let [token (keyword (str "palette." (name t) ".main"))]
            [t {:fg token :bg (tint token 12)}]))))

(defstyle badge-base
  {:display :inline-flex :align-items :center :gap 0.5
   :px 1 :py 0.25 :border-radius "999px"
   :font-size "0.6875rem" :font-weight 600 :line-height 1.6
   :text-transform :uppercase :letter-spacing "0.04em" :white-space :nowrap})

;; :tone replaces the old runtime-built `badge-<type>` class names.
(defnc Badge
  [{:keys [tone children] :as props}]
  (let [{:keys [fg bg]} (tone-token (or tone :neutral))]
    (apply Element (pass props
                   {:tag "span"
                    :className (use-sx [badge-base {:color fg :bgcolor bg}])}
                   [:tone])
      children)))

;; A softer badge for inline annotations — sentence case, no tracking.
(defnc Pill
  [{:keys [tone children] :as props}]
  (let [{:keys [fg bg]} (tone-token (or tone :neutral))]
    (apply Element (pass props
                   {:tag "span"
                    :className (use-sx [badge-base
                                        {:color fg :bgcolor bg
                                         :text-transform :none
                                         :letter-spacing :normal
                                         :font-weight 500}])}
                   [:tone])
      children)))

(defnc Spinner
  [{:keys [size]}]
  (Span {:role "status"
         :aria-label "Loading"
         :className (use-sx [{:display :inline-block :flex-shrink 0
                             :width (or size "1rem") :height (or size "1rem")
                             :border "2px solid"
                             :border-color :palette.divider
                             :border-top-color :palette.primary.main
                             :border-radius "50%"
                             :animation-name spin
                             :animation-duration "700ms"
                             :animation-timing-function :linear
                             :animation-iteration-count :infinite} calm])}))

(defnc PulseDot
  [{:keys [tone]}]
  (Span {:aria-hidden "true"
         :className (use-sx [{:display :inline-block :flex-shrink 0
                             :width "0.5rem" :height "0.5rem"
                             :border-radius "50%"
                             :bgcolor (:fg (tone-token (or tone :primary)))
                             :animation-name pulse
                             :animation-duration "1400ms"
                             :animation-timing-function :ease-in-out
                             :animation-iteration-count :infinite} calm])}))

;; A pulsing placeholder bar, for pending states with a known shape.
(defnc Skeleton
  [{:keys [width height]}]
  (Span {:aria-hidden "true"
         :className (use-sx [{:display :block :border-radius 0.5
                             :width (or width "100%")
                             :height (or height "0.75rem")
                             :bgcolor :palette.surface.sunken
                             :animation-name pulse
                             :animation-duration "1400ms"
                             :animation-timing-function :ease-in-out
                             :animation-iteration-count :infinite} calm])}))

(defnc Alert
  [{:keys [tone children] :as props}]
  (let [{:keys [fg bg]} (tone-token (or tone :info))]
    (apply Element (pass props
                   {:tag "div"
                    :className (use-sx [{:display :flex :gap 1 :align-items :center
                                         :px 1.5 :py 1.25 :border-radius 1
                                         :border "1px solid" :border-color fg
                                         :bgcolor bg :color fg
                                         :font-size "0.875rem"
                                         :animation-name rise
                                         :animation-duration "180ms"
                                         :animation-timing-function :ease-out}
                                        calm])}
                   [:tone])
      children)))

(defnc Divider
  [_]
  (Div {:className (use-sx {:height "1px" :bgcolor :palette.divider
                            :my 1 :flex-shrink 0})}))

;; ---------------------------------------------------------------------------
;; The code/output split every tab renders through

(defstyle code-block
  {:position :relative :height "100%" :m 0
   :px 2 :pt 4 :pb 2 :border-radius 1.5
   :bgcolor :palette.surface.code :color :palette.code.plain
   :font-family :typography.font-family-mono
   :font-size {:xs "0.75rem" :sm "0.8125rem"}
   :line-height 1.7 :overflow-x :auto :box-shadow 2
   ;; Not `:palette.grey.800`: the grey scale deliberately inverts between
   ;; themes while `:surface.code` deliberately does NOT, so grey.800 resolved
   ;; to a near-white #e2e8f0 border around a #0b1220 pane in dark mode. Two
   ;; separately-correct decisions colliding; this surface needs its own token.
   :border "1px solid" :border-color :palette.surface.code-border

   "&::before" {:content "ClojureScript" :position :absolute
                :top "0.85rem" :right "1rem" :font-size "0.6875rem"
                :font-weight 500 :letter-spacing "0.04em"
                :color :palette.code.label}

   ;; Prism writes this markup into the block, so descendant selectors are the
   ;; only way to reach it — and the one place in the demo that genuinely needs
   ;; them. The colours are tokens, so a theme swap recolours syntax for free.
   "& .token.comment"     {:color :palette.code.comment :font-style :italic}
   "& .token.keyword"     {:color :palette.code.keyword :font-weight 600}
   "& .token.string"      {:color :palette.code.string}
   "& .token.number"      {:color :palette.code.number}
   "& .token.function"    {:color :palette.code.function}
   "& .token.punctuation" {:color :palette.code.punctuation}
   "& .token.operator"    {:color :palette.code.punctuation}
   "& .token.boolean"     {:color :palette.code.number}})

(defstyle output-pane
  {:display :flex :flex-direction :column :justify-content :center :gap 2
   :min-height "9rem" :p 2 :border-radius 1.5
   :bgcolor :palette.background.paper
   :border "1px solid" :border-color :palette.divider
   ;; The old sheet said `.demo-output > * { min-width: 0 }` to stop wide
   ;; output blowing out the grid track. sx's selector charset has no `*` (it
   ;; is deliberately narrow), so the pane scrolls its own overflow instead —
   ;; which keeps the grid intact without reaching for a descendant selector.
   :overflow-x :auto})

(defstyle demo-block
  {:mb 4})

(defstyle demo-columns
  {:display :grid :gap 2 :align-items :stretch
   :grid-template-columns "minmax(0, 1fr)"
   ;; A raw at-rule rather than the `:lg` breakpoint: the code pane needs about
   ;; 1024px before two columns beat one, which is not where `:lg` sits.
   "@media (min-width: 1024px)"
   {:grid-template-columns "minmax(0, 1fr) minmax(0, 1fr)"}})

(defnc CodeAndOutput
  [{:keys [code title children]}]
  (Div {:className (use-sx demo-block)}
    (when title (DemoTitle {} title))
    (Div {:className (use-sx demo-columns)}
      (Div
        (Pre {:className (use-sx code-block)}
          (Code {:dangerouslySetInnerHTML
                 {:__html (.highlight Prism code
                            (.-clojure (.-languages Prism))
                            "clojure")}})))
      (Div {:className (use-sx output-pane)} children))))
