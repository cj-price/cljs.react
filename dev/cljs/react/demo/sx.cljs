(ns cljs.react.demo.sx
  (:require [cljs.react.core :refer [use-state use-atom]]
            [cljs.react.sx :refer [use-sx use-theme ThemeProvider]]
            ;; Internal — the demo reads it only to show that a theme swap
            ;; regenerates no CSS. Application code never needs this.
            [cljs.react.sx.sheet :as sheet]
            [cljs.react.demo.util :refer [CodeAndOutput Div P H2 H4 Button
                                          Section Span Strong]])
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
  ";; padding/margin/gap multiply the theme spacing unit
{:p 2}          ; calc(var(--cx-spacing) * 2)
{:width 300}    ; 300px          — plain lengths
{:font-weight 500} ; 500         — unitless properties stay raw
{:box-shadow 2} ; var(--cx-shadows-2)
{:p \"1.5rem\"}   ; strings pass through verbatim")

(defnc ScaleBox
  [{:keys [n]}]
  (Div {:className (use-sx {:p n
                            :bgcolor :palette.background.paper
                            :border "1px solid"
                            :color :palette.text.primary
                            :border-radius 1
                            :box-shadow 1
                            :font-size 12})}
    (str ":p " n)))

(defnc ScaleDemo
  []
  ;; use-sx is a hook, so it lives in ScaleBox rather than in the loop body.
  (Div {:className (use-sx {:display :flex :gap 1 :flex-wrap :wrap})}
    (for [n [1 2 3 4]]
      (ScaleBox {:key n :n n}))))

;; ============================================================================
;; The CSS-variable payoff
;; ============================================================================

(def theme-code
  "(ThemeProvider {:theme {:spacing spacing
                        :palette {:primary {:main colour}}}}
  ...)

;; Changing the theme rewrites the :root --cx-* block and nothing else.
;; Not one style rule is regenerated and every component keeps the class it
;; already had — every generated class references var(--cx-…) rather than a
;; baked value, so the compile counter below never moves. Components that
;; read the theme DO re-render (they are context subscribers), but that
;; render is a memo hit returning the same class and mutating no DOM.
;;
;; (A NESTED provider does register one scoped rule per distinct theme it
;;  inherits, since its variables are baked into a .cx-theme-* class.)")

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
    (P {:className "text-xs text-gray-600 mt-2"}
      "use-theme sees spacing " (Strong (str (:spacing theme)))
      " and primary "
      (Strong (get-in theme [:palette :primary :main])))))

(defnc ThemeToggleDemo
  ;; The provider itself lives at the tab root (see SXTab) so this page has
  ;; exactly one root ThemeProvider — two would fight over the same `:root`
  ;; custom-property node, which the library dev-warns about.
  [{:keys [spacing colour]}]
  (let [compiles (use-atom sheet/compile-count)]
    (Div
      ;; :aria-pressed carries the selected state, which colour alone does not,
      ;; and each swatch gets a human name — its accessible name would
      ;; otherwise be a raw hex code. White on #f97316 is 2.80:1, so the
      ;; orange swatch takes black text.
      (Div {:className "flex gap-2 flex-wrap mb-3"}
        (for [n [4 8 12]]
          (Button {:key n
                   :type "button"
                   :aria-pressed (= @spacing n)
                   :className (if (= @spacing n)
                                "px-3 py-1 bg-koi-orange text-black rounded text-sm"
                                "px-3 py-1 bg-gray-200 text-gray-700 rounded text-sm")
                   :onClick #(reset! spacing n)}
            "spacing " n))
        (for [[c label dark-text?] [["#f97316" "orange" true]
                                    ["#1976d2" "blue" false]
                                    ["#2e7d32" "green" false]]]
          (Button {:key c
                   :type "button"
                   :aria-pressed (= @colour c)
                   :aria-label (str "primary colour " label " " c)
                   :className (str "px-3 py-1 rounded text-sm "
                                   (if dark-text? "text-black" "text-white"))
                   :style #js {:backgroundColor c}
                   :onClick #(reset! colour c)}
            c)))

      (Div {:className "flex gap-2 flex-wrap"}
        (ThemedBox {:label "one"})
        (ThemedBox {:label "two"})
        (ThemedBox {:label "three"}))
      (ThemeInfo)

      (P {:className "text-xs text-gray-600 mt-3"}
        "Total sx compiles since page load: "
        (Strong {:className "text-koi-orange"} (str compiles))
        " — click every button above and watch it stay put."))))

;; ============================================================================
;; Responsive + nested selectors
;; ============================================================================

(def responsive-code
  "{:width {:xs \"100%\" :md 320}   ; :xs is the base rule, the rest are
                                ; mobile-first min-width media blocks
 :&:hover {:box-shadow 3}      ; nested selectors need an explicit &
 \"& .tag\" {:opacity 0.6}}      ; string keys are the primary spelling")

(defnc ResponsiveDemo
  []
  (Div {:className (use-sx {:width {:xs "100%" :md 320}
                            :p 2
                            :bgcolor :palette.background.paper
                            :border "1px solid"
                            :border-radius 1
                            :box-shadow 1
                            :transition "box-shadow 150ms"
                            :&:hover {:box-shadow 3}
                            "& .tag" {:opacity 0.6 :font-size 11}})}
    (Div "Hover me — resize the window to see the width change.")
    (Span {:className "tag"} "nested selector, dimmed")))

;; ============================================================================
;; defstyle + composition
;; ============================================================================

(defstyle card-style
  {:p 2
   :border-radius 1
   :bgcolor :palette.background.paper
   :border "1px solid"
   :box-shadow 1
   :font-size 13})

(def defstyle-code
  "(defstyle card-style
  {:p 2 :border-radius 1 :box-shadow 1 ...})

;; A var is a stable identity, so use-sx's deps compare by `identical?`
;; instead of walking the map — and repeated mounts skip the cache probe.
(use-sx card-style)

;; Vectors deep-merge right-wins into ONE class. nils are dropped.
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
    (Div {:className "flex gap-2 flex-wrap"}
      (for [i [0 1 2]]
        (SelectableCard {:key i
                         :i i
                         :selected? (= i @selected)
                         :on-click #(reset! selected i)})))))

;; ============================================================================
;; Scoped theming
;; ============================================================================

(def scoped-code
  "(ThemeProvider {}                              ; root: no DOM node
  (ThemedBox {:label \"page theme\"})
  (ThemeProvider {:theme dark-theme}            ; nested: scoped class on a
    (ThemedBox {:label \"scoped dark\"})))         ; display:contents wrapper

;; A nested provider emits .cx-theme-<hash> rather than rewriting :root,
;; so a dark panel inside a light page works. Exactly one root provider may
;; be mounted — a second would overwrite the first's :root variables, and
;; the library dev-warns when that happens.")

(defnc ScopedDemo
  []
  (Div {:className "flex gap-3 flex-wrap"}
    (ThemedBox {:label "page theme"})
    (ThemeProvider {:theme {:palette {:mode :dark
                                      :primary {:main "#111827"}
                                      :background {:paper "#1f2937"}
                                      :text {:primary "#f9fafb"}}}}
      (ThemedBox {:label "scoped dark"}))))

;; ============================================================================
;; Baseline — documented, deliberately not mounted
;; ============================================================================

(def baseline-code
  "(BaselineProvider {:body? true :enable-color-scheme? true}
  (App))

;; NOTE: this is GLOBAL CSS. Its selectors are element-level (a, img,
;; button) and the sheet is append-only with no unmount cleanup, so
;; mounting it anywhere styles the whole document for the rest of the
;; session. Mount it once at your app root, or not at all — there is no
;; scoped baseline. This demo deliberately does not mount it, since it
;; would restyle every other tab.")

;; ============================================================================
;; Tab
;; ============================================================================

(defnc SXTab
  []
  (let [spacing (use-state 8)
        colour  (use-state "#f97316")]
    ;; ONE root ThemeProvider for the whole tab. It renders no DOM node, and
    ;; every provider below it is therefore nested and scoped.
    (ThemeProvider {:theme {:spacing @spacing
                            :palette {:primary {:main @colour}}}}
      (Section
        (H2 "Styling (sx)")
        (P {:className "section-description"}
          "MUI-sx-style styling in pure ClojureScript. Write a map of declarations,
           get a class name back. Theme values are indirected through CSS custom
           properties, so a theme swap rewrites one :root block and regenerates
           no CSS — every component keeps the class it already had.")

        (CodeAndOutput {:title "Basics — a map in, a class out" :code basics-code}
          (BasicsDemo))

        (CodeAndOutput {:title "Numbers mean different things per property"
                        :code scale-code}
          (ScaleDemo))

        (CodeAndOutput {:title "Theme swaps regenerate no style CSS"
                        :code theme-code}
          (ThemeToggleDemo {:spacing spacing :colour colour}))

        (CodeAndOutput {:title "Responsive values and nested selectors"
                        :code responsive-code}
          (ResponsiveDemo))

        (CodeAndOutput {:title "defstyle and composition" :code defstyle-code}
          (CompositionDemo))

        (CodeAndOutput {:title "Scoped theming" :code scoped-code}
          (ScopedDemo))

        (Div {:className "code-and-output"}
          (H4 "BaselineProvider (not mounted here — see the note)")
          (CodeAndOutput {:code baseline-code}
            (P {:className "text-sm text-gray-600"}
              "Shown as a snippet only. Mounting a global reset inside one tab
               would restyle every other tab for the rest of the session.")))))))
