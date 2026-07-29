(ns cljs.react.sx
  "MUI-`sx`-style styling for cljs.react: write a Clojure map of declarations
  next to the component, get back a class name.

  Opt-in — `cljs.react.core` never requires this namespace, so a consumer that
  doesn't require it pays nothing.

  Theme values are indirected through CSS custom properties. Swapping a theme
  rewrites the `:root` block and nothing else: not one style rule is
  regenerated and every component keeps the class it already had. Components
  that read the theme do re-render — they are context subscribers by
  construction — but that render is a memo hit returning the same class and
  mutating no DOM.

      (defnc Card [{:keys [children]}]
        (let [cls (use-sx {:p 2
                           :border-radius :shape.border-radius
                           :bgcolor :palette.background.paper
                           :&:hover {:box-shadow 2}})]
          (Element {:tag \"div\" :className cls} children)))

  There is deliberately no `:sx` prop on `Element`: `:sx` is already meaningful
  as a passthrough prop to real MUI components."
  (:require
   [clojure.string :as str]
   ["react" :as react]
   [cljs.react.hook :as hook]
   [cljs.react.sx.sheet :as sheet]
   [cljs.react.sx.theme :as theme]))

;; defonce so hot-reload preserves context identity — otherwise existing
;; <Provider> instances and their consumers would orphan on every reload.
(defonce ^:private theme-context (react/createContext nil))

(defonce ^:private root-provider-count (atom 0))

(defn use-theme
  "The merged theme map, for things that need values rather than vars —
  breakpoint math, `(= :dark (get-in theme [:palette :mode]))`, feeding canvas
  or chart APIs.

  Outside a `ThemeProvider` this returns `default-theme` rather than throwing
  — unlike `use-db`, which has no sensible default. Opting in to sx should not
  secretly mean opting in to restructuring your root."
  []
  (or (hook/use-context theme-context) theme/default-theme-normalized))

(defn use-theme-class
  "The scope class installed by the nearest nested `ThemeProvider`, or nil at
  the root. Only needed with `:as :none`, where you place the class yourself."
  []
  (:cx/class (use-theme)))

(defn use-sx
  "Compile `sx` and return its class name.

  `sx` may be a map, a `defstyle` var, or a vector of either (plus nils, which
  are dropped) — vectors deep-merge right-wins into a single class.

  Note what is absent from the memo dependencies: the theme. That is the
  CSS-variable payoff made structural — a theme change that isn't a breakpoint
  change cannot invalidate this memo, because the generated CSS provably does
  not depend on theme values.

  Values are a trust boundary: a value that could escape its own declaration
  (`;`, `{`, `}`, `<`, a CSS comment, an unbalanced quote) is rejected with
  `ex-info`, not escaped, so untrusted input cannot inject a rule."
  [sx]
  (let [theme (use-theme)
        bpk   (:cx/bp-key theme)]
    (hook/use-memo
      (fn [] (sheet/class-for-any sx bpk (theme/breakpoint-values theme)))
      [sx bpk])))

(defn style
  "Compile `sx` to a class name outside React. Uses `default-theme`'s
  breakpoints unless a theme is supplied.

  Accepts and rejects exactly what [[use-sx]] does, values included — a
  hand-written theme map is normalized first, so its breakpoints reach the
  cache key rather than colliding in the nil bucket with every other
  hand-written theme."
  ([sx] (style sx theme/default-theme-normalized))
  ([sx theme]
   (let [t (if (:cx/bp-key theme)
             theme
             (theme/deep-merge-theme theme/default-theme-normalized theme))]
     (sheet/class-for-any sx (:cx/bp-key t) (theme/breakpoint-values t)))))

(defn theme-var
  "A `var(--cx-…)` reference for a theme path, for hand-written CSS strings.

      (theme-var [:palette :primary :main]) ;=> \"var(--cx-palette-primary-main)\"
      (theme-var :palette.primary.main)     ;=> same
      (theme-var \"palette.primary.main\")    ;=> same"
  [path]
  (theme/var-ref
    (cond
      (vector? path)  path
      (string? path)  (str/split path #"\.")
      (keyword? path) (str/split (name path) #"\.")
      :else (throw (ex-info
                     (str "cljs.react.sx: theme-var takes a vector of segments,"
                          " a dotted keyword or a dotted string. Got "
                          (pr-str path) ".")
                     {:type ::invalid-theme-path :got path})))))

(defn keyframes
  "Define an animation. Returns a value to put under `:animation-name`.

      (def spin (keyframes {:from {:transform \"rotate(0deg)\"}
                            :to   {:transform \"rotate(360deg)\"}}))

      (use-sx {:animation-name spin
               :animation-duration \"900ms\"
               :animation-timing-function :linear
               :animation-iteration-count :infinite})

  Offsets are `:from`, `:to`, a number 0-100, or a percentage string; a vector
  key shares one block between offsets (`{[0 100] {:opacity 1}}`). Each frame
  is an ordinary sx map — shorthands, the spacing scale and theme tokens all
  work inside one — but declarations only: no nested selectors, at-rules or
  responsive maps. For a responsive animation, define two and switch
  `:animation-name` in a breakpoint map.

  Pass the returned value itself, not its name. The name is derived on every
  compile, which is what keeps it correct across hot reload; a name captured
  into a map goes stale silently, leaving an element that renders perfectly and
  never animates. Use [[keyframes-name]] only where a string is unavoidable.

  Prefer the `animation-*` longhands over the `animation` shorthand: sx
  composition deep-merges per property, so the shorthand resets every
  sub-property a later part meant to keep. Note also that `:animation-name`
  alone animates nothing — `animation-duration` defaults to `0s`.

  The frames map is compiled eagerly, so a malformed one throws here rather
  than at whichever render first touches it. Registration is lazy."
  [frames]
  (sheet/keyframes frames))

(defn keyframes-name
  "Register `kf` if needed and return its generated global name, for the
  `animation` shorthand, a `:style` prop or interop.

  An escape hatch, and a function rather than a deref because it is effectful.
  The name it returns is a snapshot: store it in a var or an sx map and it
  outlives the rule it names across hot reload, silently. `@kf` returns the
  frames map instead, which is what a deref should do."
  [kf]
  (sheet/ensure-keyframes! kf))

;; ---------------------------------------------------------------------------
;; ThemeProvider

(defn- theme-provider-inner
  [^js props]
  (let [parent   (hook/use-context theme-context)
        override (.-theme props)
        as       (.-as props)
        children (.-children props)
        nested?  (or (some? parent) (boolean (.-scoped props)))
        base     (or parent theme/default-theme-normalized)
        merged   (hook/use-memo #(theme/deep-merge-theme base override)
                                [base override])
        vars     (hook/use-memo #(theme/theme->css-vars merged) [merged])
        cls      (hook/use-memo #(when nested? (sheet/scoped-theme-class vars))
                                [nested? vars])
        value    (hook/use-memo #(assoc merged :cx/class cls) [merged cls])]

    ;; Root vars are mutable state, so they are REPLACED in a dedicated node
    ;; rather than appended to the content-hashed registry — see the ns
    ;; docstring in cljs.react.sx.sheet for why append-only breaks A -> B -> A.
    ;;
    ;; useInsertionEffect runs in the commit's mutation phase: before every
    ;; layout effect and before paint, so a child measuring itself still reads
    ;; styled DOM. Unlike a render-phase write it only fires for renders that
    ;; actually commit, so a theme change inside a discarded transition cannot
    ;; leave the sheet describing a tree that was never shown.
    (react/useInsertionEffect
      (fn []
        (when-not nested? (sheet/write-theme-vars! vars))
        js/undefined)
      (hook/cljs-deps [nested? vars]))

    (hook/use-effect
      (fn []
        (if (and ^boolean goog/DEBUG (not nested?))
          (do
            (when (> (swap! root-provider-count inc) 1)
              (js/console.warn
                (str "cljs.react.sx: more than one root ThemeProvider is "
                     "mounted. They share one :root custom-property node and "
                     "will overwrite each other. Pass :scoped? true to all "
                     "but one.")))
            (fn [] (swap! root-provider-count dec)))
          js/undefined))
      [nested?])

    (react/createElement
      (.-Provider theme-context)
      #js {:value value}
      ;; `as` is normalized to a string by ThemeProvider, so the sentinel is
      ;; compared as one — `:none` and `"none"` mean the same thing here.
      (if (and nested? (not= as "none"))
        (react/createElement
          as
          #js {:className (str/join " " (remove str/blank? [cls (.-className props)]))
               :style #js {:display "contents"}}
          children)
        children))))

(defn ThemeProvider
  "Install a theme.

  At the root this registers `:root { --cx-…: … }` and renders NO DOM node.
  Nested, it registers a scoped `.cx-theme-…` rule and wraps children in a
  `display: contents` element. That rule redefines custom properties ONLY: the
  wrapper generates no box and cannot paint a background (the inline
  `display: contents` also beats `:className`), so a dark scope must set its
  own surface on an element inside it —
  `(use-sx {:bgcolor :palette.background.default :color :palette.text.primary})`.

  Props map (all keys optional):
    :theme    - a (partial) theme map, deep-merged onto the parent
    :scoped?  - force the scoped path at the root (e.g. two React roots on one
                page, where both would otherwise fight over `:root`)
    :as       - wrapper tag for the scoped path, default \"div\"; keywords and
                strings are equivalent. `:none` renders no wrapper, and then
                placing `(use-theme-class)` yourself is MANDATORY — without it
                the scoped theme has no effect at all, silently.
                Keep this a non-semantic element: the wrapper is forced to
                `display: contents`, which generates no box and can strip a
                semantic tag's structure (list markers, landmark boxes) from
                the accessibility tree. It also breaks flex/grid item
                relationships and percentage-height chains; `:as :none` is the
                escape hatch for both.
    :className - extra classes for the scoped wrapper

  Swapping the root theme rewrites custom properties only: not one style rule
  is regenerated and every component keeps the class it already had. Theme
  readers do re-render, returning the same class and mutating no DOM.

  Like `BaselineProvider`, the `:root` block has no unmount cleanup: a torn-down
  React root leaves the last theme's custom properties in place for the rest of
  the session."
  [{:keys [theme scoped? as className]} & children]
  (apply react/createElement
         theme-provider-inner
         #js {:theme (or theme {})
              :scoped (boolean scoped?)
              ;; Normalized once, here: `:none` was compared as a raw keyword
              ;; while every other legal value arrived as a string tag, so
              ;; `:as :section` crashed and `:as \"none\"` rendered a literal
              ;; <none> element.
              :as (if (nil? as) "div" (name as))
              :className className}
         children))

;; ---------------------------------------------------------------------------
;; BaselineProvider

(def ^:private reset-css
  (str "*,*::before,*::after{box-sizing:border-box}"
       "html{-webkit-text-size-adjust:100%}"
       "h1,h2,h3,h4,h5,h6,p,figure,blockquote,dl,dd{margin:0}"
       "ul,ol{margin:0;padding:0}"
       "img,picture,video,canvas,svg{display:block;max-width:100%}"
       "input,button,textarea,select{font:inherit}"
       "button{cursor:pointer}"
       "a{color:inherit}"))

(def ^:private body-css
  (str "body{margin:0;"
       "background-color:var(--cx-palette-background-default);"
       "color:var(--cx-palette-text-primary);"
       "font-family:var(--cx-typography-font-family);"
       "font-size:var(--cx-typography-font-size);"
       "line-height:var(--cx-typography-line-height);"
       "-webkit-font-smoothing:antialiased;"
       "-moz-osx-font-smoothing:grayscale}"))

(def ^:private color-scheme-css
  "html{color-scheme:var(--cx-palette-mode)}")

(defn- baseline-inner
  [^js props]
  (let [css (.-css props)]
    (react/useInsertionEffect
      (fn [] (sheet/write-baseline! css) js/undefined)
      (hook/cljs-deps [css]))
    (react/createElement react/Fragment nil (.-children props))))

(defn BaselineProvider
  "Install a minimal modern reset, written to a `<style>` node that always
  precedes generated class rules.

  Renders no DOM node of its own.

  Props map (all keys optional):
    :body?                - also style `body` from theme vars (margin,
                            background, colour, typography). Default false.
    :enable-color-scheme? - set `color-scheme` from `:palette.mode`, so native
                            form controls and scrollbars follow the theme.
                            `:palette.mode` carries no colours of its own and
                            the default theme has no dark variant, so a `:dark`
                            mode must ship its own `:palette.background` and
                            `:palette.text` — otherwise this paints the UA
                            canvas dark under still-dark text. The library
                            dev-warns when it sees that combination.

  IMPORTANT: this is GLOBAL CSS. Its selectors are element-level (`a`, `img`,
  `button`), and the sheet is append-only with no unmount cleanup — mounting
  it anywhere styles the whole document for the rest of the session. Mount it
  once at your app root, or not at all. There is no scoped baseline."
  [{:keys [body? enable-color-scheme?]} & children]
  (apply react/createElement
         baseline-inner
         #js {:css (str reset-css
                        (when body? body-css)
                        (when enable-color-scheme? color-scheme-css))}
         children))
