(ns cljs.react.sx.theme
  "Theme data and CSS custom property generation for `cljs.react.sx`.

  A theme is a plain ClojureScript map. Theme values reach generated CSS only
  as custom properties (`--cx-*`), never inlined — which is what lets a theme
  swap repaint the app by rewriting one `:root` block, regenerating no CSS and
  leaving every component on the class it already had. Components that read the
  theme do re-render (context propagation ignores memo bailouts); that render
  is a memo hit returning the same class and mutating no DOM.

  `:breakpoints` is the single exception. Media query parameters cannot
  reference custom properties in any shipping engine, so breakpoint pixels are
  compiled into rule text and therefore participate in the style cache key
  (see [[bp-key]]).

  Theme map keys are normalized to kebab-case by [[deep-merge-theme]], so a
  camelCase override (`{:shape {:borderRadius 8}}`) and its kebab-case spelling
  address the same slot and the same custom property."
  (:require
   [clojure.string :as str]))

(def bp-order
  "Breakpoint keys, ascending. Mobile-first: `:xs` is the base rule."
  [:xs :sm :md :lg :xl])

(def default-theme
  "The theme used when no `ThemeProvider` is mounted, and the base every
  provider deep-merges onto. Deliberately tighter than MUI's — five
  shadow levels rather than 25, no component overrides."
  {:palette
   {:mode       :light
    :primary    {:main "#1976d2" :light "#42a5f5" :dark "#1565c0" :contrast-text "#ffffff"}
    :secondary  {:main "#9c27b0" :light "#ba68c8" :dark "#7b1fa2" :contrast-text "#ffffff"}
    :error      {:main "#d32f2f" :light "#ef5350" :dark "#c62828" :contrast-text "#ffffff"}
    ;; White on #ed6c02 measures 3.11:1 and on #0288d1 3.86:1, both under the
    ;; 4.5:1 WCAG minimum for body text. The pairing is declared by the theme,
    ;; so the library — not the consumer — has to be the one that fixes it.
    :warning    {:main "#ed6c02" :light "#ff9800" :dark "#e65100" :contrast-text "rgba(0, 0, 0, 0.87)"}
    :info       {:main "#0277bd" :light "#03a9f4" :dark "#01579b" :contrast-text "#ffffff"}
    :success    {:main "#2e7d32" :light "#4caf50" :dark "#1b5e20" :contrast-text "#ffffff"}
    :text       {:primary "rgba(0, 0, 0, 0.87)"
                 :secondary "rgba(0, 0, 0, 0.6)"
                 :disabled "rgba(0, 0, 0, 0.38)"}
    :background {:default "#ffffff" :paper "#ffffff"}
    :divider    "rgba(0, 0, 0, 0.12)"}

   :spacing 8

   :typography
   {:font-family "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif"
    ;; A string, so it passes through verbatim rather than gaining `px`:
    ;; `BaselineProvider {:body? true}` writes this straight onto `body`, and a
    ;; px value there would override a raised browser default.
    :font-size "1rem"
    :line-height 1.5
    :font-weight-light 300
    :font-weight-regular 400
    :font-weight-medium 500
    :font-weight-bold 700}

   :shape {:border-radius 4}

   :shadows ["none"
             "0 1px 2px rgba(0, 0, 0, 0.12)"
             "0 2px 6px rgba(0, 0, 0, 0.14)"
             "0 6px 16px rgba(0, 0, 0, 0.16)"
             "0 12px 32px rgba(0, 0, 0, 0.18)"]

   :z-index {:app-bar 1100 :drawer 1200 :modal 1300 :tooltip 1500}

   :breakpoints {:xs 0 :sm 600 :md 900 :lg 1200 :xl 1536}})

(def ^:private default-branch-keys
  "Top-level [[default-theme]] keys that expand into a family of custom
  properties. Dev-only: the branch-collapse check in [[theme->css-vars]]
  compares a merged theme against these."
  (vec (keep (fn [[k v]] (when (coll? v) k))
             (dissoc default-theme :breakpoints))))

(def ^:private unitless-props
  "CSS properties whose numeric values must not receive a `px` suffix. Matched
  against every kebab-cased path segment as an exact-or-prefix test, so
  `:font-weight-bold` under `:typography` and `:app-bar` under `:z-index` both
  resolve unitless."
  #{"animation-iteration-count" "aspect-ratio" "border-image-outset"
    "border-image-slice" "border-image-width" "box-flex" "box-ordinal-group"
    "column-count" "columns" "fill-opacity" "flex" "flex-grow" "flex-shrink"
    "flood-opacity" "font-weight" "grid-area" "grid-column" "grid-column-end"
    "grid-column-start" "grid-row" "grid-row-end" "grid-row-start" "line-clamp"
    "line-height" "opacity" "order" "orphans" "stop-opacity" "stroke-dasharray"
    "stroke-dashoffset" "stroke-miterlimit" "stroke-opacity" "stroke-width"
    "tab-size" "widows" "z-index" "zoom"})

(defn kebab
  "Normalize a keyword/symbol/string to a kebab-case CSS-ish name.
  `:contrastText` -> \"contrast-text\", `:borderRadius` -> \"border-radius\".
  Already-kebab names pass through unchanged."
  [k]
  (-> (name k)
      (str/replace #"([a-z0-9])([A-Z])" "$1-$2")
      (str/lower-case)))

(defn unitless-name?
  "True when a kebab-cased name denotes a unitless CSS property, by exact match
  or by prefix (`font-weight-bold` matches `font-weight`).

  For THEME PATH SEGMENTS only. A CSS property name must use
  [[unitless-prop?]] — prefix matching there would make `flex-basis` inherit
  `flex`'s unitless status and silently drop the `px`."
  [s]
  (boolean
    (or (contains? unitless-props s)
        (some #(str/starts-with? s (str % "-")) unitless-props))))

(defn unitless-prop?
  "True when a kebab-cased CSS PROPERTY name is unitless. Exact match only:
  every legitimately unitless property is in the set under its own name, so a
  longhand that merely starts with one (`flex-basis`, `grid-row-gap`) still
  gets its unit."
  [s]
  (contains? unitless-props s))

(def ^:private unsafe-var-value
  "Characters that would let a theme value terminate its own declaration and
  inject further declarations or rules. The `:root` block is written with
  `.textContent`, so — unlike a class rule under `insertRule` — nothing
  downstream re-parses and rejects a payload like
  `red}#victim{outline-style:dotted`."
  #"[;{}<>\\]|/\*|\*/")

(def ^:private var-name-re #"^--[a-zA-Z0-9-]+$")

(defn- balanced?
  [s]
  (and (even? (count (re-seq #"\"" s)))
       (even? (count (re-seq #"'" s)))
       (= (count (re-seq #"\(" s)) (count (re-seq #"\)" s)))))

(defn- var-value
  "Render a theme value as a custom property value. Numbers gain `px` unless
  the path is unitless; keywords render as their name; everything else is
  passed through via `str`.

  Rejects anything that could escape the declaration — this is the one place
  every theme value passes through on its way into CSS text."
  [segments v]
  (let [s (cond
            (number? v)  (if (some unitless-name? segments) (str v) (str v "px"))
            (keyword? v) (name v)
            :else        (str v))]
    (when (or (re-find unsafe-var-value s) (not (balanced? s)))
      (throw (ex-info
               (str "cljs.react.sx: theme value at " (pr-str (vec segments))
                    " is not a safe custom property value. `;`, `{`, `}`, `<`,"
                    " `>`, `\\`, CSS comments and unbalanced quotes or"
                    " parentheses are rejected, because the :root block is"
                    " written as text with nothing downstream to re-parse it."
                    " Got " (pr-str s) ".")
               {:type ::unsafe-theme-value :path (vec segments) :value v})))
    s))

(defn- qualified-key?
  [k]
  (and (keyword? k) (some? (namespace k))))

(defn var-name
  "`[:palette :primary :main]` -> \"--cx-palette-primary-main\".

  The single encoding of a theme path as a custom property name — the compiler,
  [[cljs.react.sx/theme-var]] and the flattener all route through here, so they
  cannot drift. Non-named segments (a vector index) render via `str`.

  Validation lives here for the same reason: the name is spliced into rule text
  by all three callers, so a segment that would produce something other than a
  custom property name is rejected rather than escaping its declaration."
  [segments]
  (let [nm (str "--cx-"
                (str/join "-" (map #(if (or (string? %) (keyword? %) (symbol? %))
                                      (kebab %)
                                      (str %))
                                   segments)))]
    (when-not (re-matches var-name-re nm)
      (throw (ex-info
               (str "cljs.react.sx: theme path " (pr-str (vec segments))
                    " produces the invalid custom property name "
                    (pr-str nm) ". A theme key must be a plain identifier.")
               {:type ::invalid-var-name :path (vec segments) :name nm})))
    nm))

(defn var-ref
  "`var(…)` reference for a theme path — [[var-name]] wrapped for value
  position."
  [segments]
  (str "var(" (var-name segments) ")"))

(defn- flatten-vars
  "Walk `node`, accumulating [var-name value] pairs. Vectors index by position,
  so `:shadows` yields `--cx-shadows-0` .. `--cx-shadows-4`."
  [segments node acc]
  (cond
    (map? node)
    (reduce-kv (fn [a k v]
                 (if (qualified-key? k)
                   a
                   (flatten-vars (conj segments (kebab k)) v a)))
               acc node)

    (vector? node)
    (reduce (fn [a [i v]] (flatten-vars (conj segments (str i)) v a))
            acc (map-indexed vector node))

    :else
    (conj acc [(var-name segments) (var-value segments node)])))

(defn theme->css-vars
  "Flatten `theme` to a sorted map of `\"--cx-…\" -> \"value\"`.

  `:breakpoints` is excluded: media query parameters cannot reference custom
  properties, which is the load-bearing assumption behind caching styles on
  [[bp-key]] rather than on the theme. Namespaced keys (`:cx/bp-key`) are
  skipped too.

  Returns a sorted map so callers get byte-identical text for equal themes."
  [theme]
  (let [pairs (flatten-vars [] (dissoc theme :breakpoints) [])]
    (when ^boolean goog/DEBUG
      (let [dupes (->> pairs
                       (map first)
                       (frequencies)
                       (keep (fn [[k n]] (when (> n 1) k))))]
        (when (seq dupes)
          (js/console.warn
            (str "cljs.react.sx: theme paths collide on custom property name(s) "
                 (pr-str (vec dupes))
                 " — one value silently wins. Sibling keys that differ only in "
                 "case (e.g. :fontSize and :font-size) flatten to the same "
                 "--cx-* name."))))

      ;; A scalar override replaces a whole branch, so `{:palette \"red\"}`
      ;; yields --cx-palette and NO --cx-palette-* at all: every :palette.*
      ;; token in every already-compiled class resolves to nothing. Silent
      ;; without this, since the collision check above only sees names that
      ;; are still being produced.
      (let [collapsed (keep (fn [k] (when-not (coll? (get theme k)) k))
                            default-branch-keys)]
        (when (seq collapsed)
          (js/console.warn
            (str "cljs.react.sx: theme branch(es) " (pr-str (vec collapsed))
                 " collapsed to a non-map value, so no "
                 (pr-str (mapv #(str (var-name [%]) "-*") collapsed))
                 " custom properties are emitted and every token under them "
                 "resolves to nothing."))))

      ;; :palette.mode only drives `color-scheme` (see BaselineProvider); it
      ;; carries no colours of its own, so a mode flip that leaves the light
      ;; defaults in place paints dark-on-white text over a dark UA canvas.
      (when (and (= :dark (get-in theme [:palette :mode]))
                 (= (get-in theme [:palette :background :default])
                    (get-in default-theme [:palette :background :default]))
                 (= (get-in theme [:palette :text :primary])
                    (get-in default-theme [:palette :text :primary])))
        (js/console.warn
          (str "cljs.react.sx: :palette.mode is :dark but :palette.background "
               "and :palette.text are still the light defaults. :mode only "
               "drives color-scheme — set the dark surface and text colours "
               "alongside it, or BaselineProvider's :enable-color-scheme? "
               "will paint dark text on a dark canvas."))))
    (into (sorted-map) pairs)))

(defn breakpoint-values
  "Breakpoint pixels as a 5-vector in [[bp-order]], the only slice of the theme
  the compiler is allowed to see."
  [theme]
  (mapv #(get-in theme [:breakpoints %] 0) bp-order))

(defn bp-key
  "A compact string identifying a theme's breakpoints — the cache dimension
  generated CSS actually depends on. Nobody overrides breakpoints in practice,
  so this is one interned constant and styles are shared app-wide."
  [theme]
  (str/join "|" (breakpoint-values theme)))

(defn- normalize-keys
  [node]
  (cond
    (map? node)    (reduce-kv (fn [a k v]
                                (assoc a
                                       (if (qualified-key? k) k (keyword (kebab k)))
                                       (normalize-keys v)))
                              {} node)
    (vector? node) (mapv normalize-keys node)
    :else          node))

(defn deep-merge
  "Right-wins recursive merge. Maps merge; every other value (including
  vectors) replaces wholesale. Shared by theme merging and sx composition, so
  the two cannot drift on what \"deep\" means."
  [a b]
  (if (and (map? a) (map? b))
    (merge-with deep-merge a b)
    b))

(defn deep-merge-theme
  "Deep-merge `override` onto `base`, normalizing both to kebab-case keys, and
  recompute `:cx/bp-key`. Maps merge recursively; every other value (including
  vectors such as `:shadows`) replaces wholesale."
  [base override]
  (let [;; `:cx/bp-key` is only ever attached by this fn, which normalizes as
        ;; it goes — its presence means `base` is already normalized.
        base*  (if (contains? base :cx/bp-key) base (normalize-keys base))
        merged (deep-merge base* (normalize-keys override))]
    (assoc merged :cx/bp-key (bp-key merged))))

(def default-theme-normalized
  "[[default-theme]] with `:cx/bp-key` precomputed. The no-provider path reads
  bp-key off this, not off a provider merge."
  (deep-merge-theme default-theme {}))
