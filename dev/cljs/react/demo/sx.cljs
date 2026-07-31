(ns cljs.react.demo.sx
  (:require [cljs.react.core :refer [use-state use-atom]]
            [cljs.react.sx :as sx :refer [use-sx use-theme ThemeProvider]]
            ;; Internal — the demo reads it only to show that a theme swap
            ;; regenerates no CSS. Application code never needs this.
            [cljs.react.sx.sheet :as sheet]
            [cljs.react.demo.ui :refer [CodeAndOutput Btn Row Stack
                                        SectionTitle DemoTitle Caption Muted calm]]
            [cljs.react.demo.util :refer [Div Button Section Span Strong]])
  (:require-macros [cljs.react.core :refer [defnc]]
                   [cljs.react.sx :refer [defstyle]]))

;; ============================================================================
;; Basics
;; ============================================================================

(def basics-code
  "(let [cls (use-sx {:p 2
                   :bgcolor :palette.primary.main
                   :color :palette.primary.contrastText
                   :border-radius 1
                   :font-weight 500})]
  (Element {:tag \"div\" :className cls} \"Styled by sx\"))")

(defnc BasicsDemo
  []
  (Div {:className (use-sx {:p 2
                            :bgcolor :palette.primary.main
                            :color :palette.primary.contrast-text
                            :border-radius 1
                            :font-weight 500})}
    "Styled by sx"))

;; ============================================================================
;; Numbers by property class
;; ============================================================================

(def scale-code
  ";; numbers mean different things per property
{:p 2}             ; calc(var(--cx-spacing) * 2)
{:width 300}       ; 300px — plain length
{:font-weight 500} ; 500 — unitless, raw
{:box-shadow 2}    ; var(--cx-shadows-2)
{:p \"1.5rem\"}      ; string, verbatim

;; the compiler refuses these (no dead CSS):
{:animation-duration 1} ; throws — needs \"1s\"
{:box-shadow 0}         ; var(--cx-shadows-0)")

(defnc ScaleBox
  [{:keys [n]}]
  (Div {:className (use-sx {:p n
                            :bgcolor :palette.background.paper
                            :border "1px solid"
                            :border-color :palette.divider
                            :color :palette.text.primary
                            :border-radius 1
                            :box-shadow 1
                            :font-size 12})}
    (str ":p " n)))

(defnc ScaleDemo
  []
  ;; use-sx is a hook, so it lives in ScaleBox rather than in the loop body.
  (Row {:gap 1}
    (for [n [1 2 3 4]]
      (ScaleBox {:key n :n n}))))

;; ============================================================================
;; The CSS-variable payoff
;; ============================================================================

(def theme-code
  "(ThemeProvider {:theme {:spacing spacing
                        :palette {:primary {:main colour}}}}
  ...)

;; A theme swap rewrites the :root --cx-* block
;; and regenerates no style rules — every class
;; references var(--cx-…), so the compile counter
;; below never moves.")

(defnc ThemedBox
  [{:keys [label]}]
  (Div {:className (use-sx {:p 2
                            :bgcolor :palette.primary.main
                            :color :palette.primary.contrast-text
                            :border-radius 1
                            :font-size 13
                            :font-weight 500})}
    label))

(defnc ThemeInfo
  []
  (let [theme (use-theme)]
    (Caption {}
      "use-theme sees spacing " (Strong (str (:spacing theme)))
      " and primary "
      (Strong (get-in theme [:palette :primary :main])))))

;; Values and callbacks, not the StateAtoms: `defnc` memoizes with CLJS `=`,
;; and a StateAtom is `=` to itself on every render, so a memoized child given
;; one would never see its value change.
(defnc ThemeToggleDemo
  [{:keys [spacing on-spacing colour on-colour]}]
  (let [compiles (use-atom sheet/compile-count)
        ;; Hoisted out of the lazy `for` below: React realizes that seq after
        ;; this render returns, and a hook called then has no dispatcher.
        swatch-cls (use-sx {:px 1.5 :py 0.75 :border-radius 1
                            :border "1px solid transparent"
                            :cursor :pointer :font-size "0.8125rem"
                            :font-family :typography.font-family-mono
                            :bgcolor "var(--swatch)"
                            :color "#fff"})
        count-cls  (use-sx {:font-weight 700 :color :palette.primary.main})]
    (Stack {:gap 2}
      ;; :aria-pressed carries the selected state, which colour alone does not,
      ;; and each swatch gets a human name — its accessible name would
      ;; otherwise be a raw hex code.
      (Row {:gap 1}
        (for [n [4 8 12]]
          (Btn {:key n
                :size :sm
                :variant (if (= spacing n) :primary :secondary)
                :aria-pressed (= spacing n)
                :onClick #(on-spacing n)}
            "spacing " n))
        ;; The swatch colour varies with the palette, so it rides in a custom
        ;; property rather than minting a class per colour — the pattern the
        ;; registry's growth warning exists to steer you toward.
        (for [[c label] [["#ea580c" "orange"]
                         ["#1976d2" "blue"]
                         ["#2e7d32" "green"]]]
          (Button {:key c
                   :type "button"
                   :aria-pressed (= colour c)
                   :aria-label (str "primary colour " label " " c)
                   :style #js {"--swatch" c}
                   :className swatch-cls
                   :onClick #(on-colour c)}
            c)))

      (Row {:gap 1}
        (ThemedBox {:label "one"})
        (ThemedBox {:label "two"})
        (ThemedBox {:label "three"}))
      (ThemeInfo)

      (Caption {}
        "Total sx compiles since page load: "
        (Span {:className count-cls} (str compiles))
        " — click every button above, and the site's dark toggle too, and watch it stay put."))))

;; ============================================================================
;; Responsive + nested selectors
;; ============================================================================

(def responsive-code
  "{:width {:xs \"100%\" :md 320}  ; :xs base, then
                              ; min-width media
 :&:hover {:box-shadow 3}     ; nested needs &
 \"& .tag\" {:opacity 0.6}}     ; string keys too

;; Raw at-rules also work, beyond breakpoints:
\"@media (min-width: 1024px)\" {...}
\"@supports (display: grid)\"  {...}")

(defnc ResponsiveDemo
  []
  (Div {:className (use-sx {:width {:xs "100%" :md 320}
                            :p 2
                            :bgcolor :palette.background.paper
                            :border "1px solid"
                            :border-color :palette.divider
                            :border-radius 1
                            :box-shadow 1
                            :transition "box-shadow 150ms"
                            :&:hover {:box-shadow 3}
                            "& .tag" {:opacity 0.6 :font-size 11}})}
    (Div "Hover me — resize the window to see the width change.")
    (Span {:className "tag"} "nested selector, dimmed")))

;; ============================================================================
;; Keyframes
;; ============================================================================

(def keyframes-code
  "(def spin
  (sx/keyframes {:from {:transform \"rotate(0deg)\"}
                 :to   {:transform \"rotate(360deg)\"}}))

;; Pass the OBJECT, not its name — it is
;; re-derived each compile, so it survives
;; hot reload.
(use-sx {:animation-name spin
         :animation-duration \"900ms\"
         :animation-timing-function :linear
         :animation-iteration-count :infinite})

;; Offsets: :from, :to, 0-100, or \"33.3%\".
;; A vector key shares one block.
;; Frames are ordinary sx maps.")

(def swing
  (sx/keyframes {0   {:transform "translateX(0)"}
                 50  {:transform "translateX(28px)"}
                 100 {:transform "translateX(0)"}}))

(def glow
  (sx/keyframes {[0 100] {:box-shadow 1}
                 50      {:box-shadow 4}}))

(defnc KeyframesDemo
  []
  (Stack {:gap 2}
    (Row {:gap 2}
      (Div {:className (use-sx [{:width "2rem" :height "2rem" :border-radius 1
                                :bgcolor :palette.primary.main
                                :animation-name swing
                                :animation-duration "1600ms"
                                :animation-timing-function :ease-in-out
                                :animation-iteration-count :infinite} calm])})
      (Div {:className (use-sx [{:px 2 :py 1 :border-radius 1
                                :bgcolor :palette.background.paper
                                :border "1px solid" :border-color :palette.divider
                                :font-size "0.8125rem"
                                :animation-name glow
                                :animation-duration "2200ms"
                                :animation-timing-function :ease-in-out
                                :animation-iteration-count :infinite} calm])}
        "content-addressed @keyframes"))
    (Caption {}
      "Identical frames share one rule, and the generated name is a hash of the "
      "body — so two components that animate the same way cannot collide, and "
      "neither can they duplicate.")))

;; ============================================================================
;; defstyle + composition
;; ============================================================================

(defstyle card-style
  {:p 2
   :border-radius 1
   :bgcolor :palette.background.paper
   :border "1px solid"
   :border-color :palette.divider
   :box-shadow 1
   :font-size 13})

(def defstyle-code
  "(defstyle card-style
  {:p 2 :border-radius 1 :box-shadow 1 ...})

;; A var has stable identity — use-sx compares
;; deps by identical?, skipping the map walk.
(use-sx card-style)

;; Vectors deep-merge, right wins → one class.
;; nils are dropped.
(use-sx [card-style (when selected? {:bgcolor :palette.primary.main})])")

(defnc SelectableCard
  ;; A button, not a div with :onClick: the demo is the copy-paste surface, and
  ;; a clickable div is not in the tab order, has no role and announces no
  ;; pressed state. `:font :inherit` and `:text-align :left` undo the UA button
  ;; styling that the div never had.
  [{:keys [i selected? on-click]}]
  (Button {:type "button"
           :aria-pressed selected?
           :onClick on-click
           :className (use-sx [card-style
                               {:cursor :pointer
                                :text-align :left
                                :font :inherit}
                               (when selected?
                                 {:bgcolor :palette.primary.main
                                  :color :palette.primary.contrast-text
                                  :box-shadow 3})])}
    (str "card " i)))

(defnc CompositionDemo
  []
  (let [selected (use-state 1)]
    (Row {:gap 1}
      (for [i [0 1 2]]
        (SelectableCard {:key i
                         :i i
                         :selected? (= i @selected)
                         :on-click #(reset! selected i)})))))

;; ============================================================================
;; Scoped theming
;; ============================================================================

(def scoped-code
  "(ThemeProvider {}                  ; root: no DOM
  (ThemedBox {:label \"page theme\"})
  (ThemeProvider {:theme violet}   ; nested scope
    (ThemedBox {:label \"scoped\"})))

;; A nested provider emits .cx-theme-<hash>
;; instead of rewriting :root, so a scoped panel
;; works. Only one root provider may mount; this
;; tab is inside the site's, so all here nest.")

(defnc ScopedDemo
  []
  ;; The scope's accent is chosen to read against BOTH page backgrounds. An
  ;; earlier near-black one demonstrated the mechanism perfectly and was
  ;; invisible the moment the site switched to dark.
  (Row {:gap 1.5}
    (ThemedBox {:label "page theme"})
    (ThemeProvider {:theme {:palette {:primary {:main "#7c3aed"
                                                :contrast-text "#ffffff"}}}}
      (ThemedBox {:label "scoped theme"}))))

;; ============================================================================
;; Baseline
;; ============================================================================

(def baseline-code
  "(BaselineProvider {:body? true :enable-color-scheme? true}
  (App))

;; Mounted once here at the app root: box-sizing
;; reset, themed body background, and native
;; controls that follow the dark toggle.
;;
;; NOTE: GLOBAL CSS. Element-level selectors, an
;; append-only sheet with no cleanup — mount it
;; once at the app root, or not at all.")

;; ============================================================================
;; Tab
;; ============================================================================

(defnc SXTab
  []
  (let [spacing (use-state 8)
        colour  (use-state "#ea580c")]
    ;; Nested, not root: the site's App already mounts the one root provider.
    ;; This one therefore scopes its overrides to a class on a wrapper.
    (ThemeProvider {:theme {:spacing @spacing
                            :palette {:primary {:main @colour}}}}
      (Section
        (SectionTitle {} "💅 Styling (sx)")
        (Muted {:style {:marginBottom "1.5rem" :maxWidth "60ch"}}
          "MUI-sx-style styling in pure ClojureScript. Write a map of declarations, "
          "get a class name back. Theme values are indirected through CSS custom "
          "properties, so a theme swap rewrites one :root block and regenerates "
          "no CSS — every component keeps the class it already had. "
          "Every pixel of this site is styled this way.")

        (CodeAndOutput {:title "Basics — a map in, a class out" :code basics-code}
          (BasicsDemo))

        (CodeAndOutput {:title "Numbers mean different things per property"
                        :code scale-code}
          (ScaleDemo))

        (CodeAndOutput {:title "Theme swaps regenerate no style CSS"
                        :code theme-code}
          (ThemeToggleDemo {:spacing    @spacing
                            :on-spacing #(reset! spacing %)
                            :colour     @colour
                            :on-colour  #(reset! colour %)}))

        (CodeAndOutput {:title "Responsive values and nested selectors"
                        :code responsive-code}
          (ResponsiveDemo))

        (CodeAndOutput {:title "Keyframes" :code keyframes-code}
          (KeyframesDemo))

        (CodeAndOutput {:title "defstyle and composition" :code defstyle-code}
          (CompositionDemo))

        (CodeAndOutput {:title "Scoped theming" :code scoped-code}
          (ScopedDemo))

        (Div
          (DemoTitle {} "BaselineProvider — mounted once at the app root")
          (CodeAndOutput {:code baseline-code}
            (Caption {}
              "The reset behind this page: box-sizing, margin zeroing, a themed "
              "body background, and color-scheme so native scrollbars and form "
              "controls follow the dark toggle.")))))))
