(ns cljs.react.sx.compile
  "Normalizes an sx map to a flat, deterministic vector of rules, and emits
  those rules as CSS text.

  The dialect partitions value types with no heuristics:

    dotted keyword  -> theme token   `:palette.primary.main` -> var(--cx-…)
    dotless keyword -> CSS keyword   `:flex`, `:absolute`, `:none`
    string          -> literal value `\"1px solid red\"`, verbatim (except
                       `content`, which is quoted unless already a complete
                       CSS string, a CSS-wide keyword or a function call)
    number          -> theme scale or px, decided by the property
    map (value pos) -> responsive breakpoints
    map (& key)     -> nested selector

  Rules carry the sentinel selector `&`; [[rules->css]] substitutes the real
  class. Hashing runs over the sentinel form, which is what makes the class
  name derivable from its own CSS without circularity.

  This namespace never sees a theme — only breakpoint pixels. Theme values
  reach CSS exclusively as `var(--cx-…)` references, so generated CSS provably
  cannot depend on theme values."
  (:require
   [clojure.string :as str]
   [cljs.react.sx.theme :as theme]))

(def ^:private bp-index
  (zipmap theme/bp-order (range)))

(def ^:private at-rule-order
  "Sort weight for raw at-rules, above any plausible breakpoint pixel so they
  land after responsive blocks."
  1000000)

(def ^:private shorthands
  "MUI system props, keyed by kebab-cased name. Values are the properties each
  expands to; multi-property aliases emit multiple declarations."
  {"m"  [:margin]  "mt" [:margin-top]  "mr" [:margin-right]
   "mb" [:margin-bottom] "ml" [:margin-left]
   "mx" [:margin-left :margin-right] "my" [:margin-top :margin-bottom]
   "p"  [:padding] "pt" [:padding-top] "pr" [:padding-right]
   "pb" [:padding-bottom] "pl" [:padding-left]
   "px" [:padding-left :padding-right] "py" [:padding-top :padding-bottom]
   "bgcolor" [:background-color]})

(def ^:private spacing-props
  "Properties whose numeric values multiply the theme spacing unit."
  #{"margin" "margin-top" "margin-right" "margin-bottom" "margin-left"
    "margin-block" "margin-block-start" "margin-block-end"
    "margin-inline" "margin-inline-start" "margin-inline-end"
    "padding" "padding-top" "padding-right" "padding-bottom" "padding-left"
    "padding-block" "padding-block-start" "padding-block-end"
    "padding-inline" "padding-inline-start" "padding-inline-end"
    "gap" "row-gap" "column-gap"
    ;; The legacy grid-* aliases, so `:grid-row-gap 2` scales like `:row-gap 2`
    ;; instead of falling through to a bare number.
    "grid-gap" "grid-row-gap" "grid-column-gap"})

(def ^:private content-literals
  "`content` values that must not be auto-quoted."
  #{"none" "normal" "inherit" "initial" "unset" "revert" "revert-layer"
    "open-quote" "close-quote" "no-open-quote" "no-close-quote"})

(defn- key-name
  "The raw name of an sx key. Strings pass through; keywords/symbols use their
  name so `:&:hover` and `\"&:hover\"` are the same key."
  [k]
  (if (string? k) k (name k)))

(defn- token?
  [v]
  (and (keyword? v) (str/includes? (name v) ".")))

(defn- token->var
  "`:palette.primary.main` -> `var(--cx-palette-primary-main)`."
  [k]
  (theme/var-ref (str/split (name k) #"\.")))

(def ^:private css-string-re
  "One complete CSS string and nothing else: a single quoted run, backslash
  escapes allowed, no trailing text. `\"a\";color:red` deliberately fails —
  merely STARTING with a quote is what let a second declaration ride along."
  #"^(?:\"(?:[^\"\\]|\\.)*\"|'(?:[^'\\]|\\.)*')$")

(defn- quote-content
  [v]
  (let [t (str/trim v)]
    (if (or (contains? content-literals t)
            (re-matches css-string-re t)
            (re-find #"^[a-z-]+\(" t))
      v
      ;; Backslash first, or the escapes added for `"` get escaped in turn.
      (str "\"" (-> v
                    (str/replace "\\" "\\\\")
                    (str/replace "\"" "\\\""))
           "\""))))

(defn- number->value
  [prop n]
  (cond
    ;; Before the zero case: elevation 0 is a real shadow level (`none`), and
    ;; a bare `box-shadow:0` is invalid CSS the parser discards.
    (= "box-shadow" prop)          (str "var(--cx-shadows-" n ")")
    (zero? n)                      "0"
    (contains? spacing-props prop) (str "calc(var(--cx-spacing) * " n ")")
    (= "border-radius" prop)       (str "calc(var(--cx-shape-border-radius) * " n ")")
    (theme/unitless-prop? prop)    (str n)
    :else                          (str n "px")))

(def ^:private unsafe-value
  "Characters that would let a value terminate its own declaration or rule.
  Rejected rather than escaped: `;` alone still parses as one valid rule, so
  `insertRule` accepts it in release, and the `}` shape is accepted by the dev
  text-node path but rejected in release — styles that work in dev and vanish
  in production."
  #"[{};<]|/\*|\*/")

(defn- check-value!
  [prop v]
  (when (or (re-find unsafe-value v)
            (odd? (count (re-seq #"\"" v)))
            (odd? (count (re-seq #"'" v))))
    (throw (ex-info
             (str "cljs.react.sx: value for " (pr-str prop) " is not a safe CSS"
                  " value. `{`, `}`, `;`, `<`, CSS comments and unbalanced"
                  " quotes are rejected, because they let a value escape its"
                  " own declaration. Got " (pr-str v) ".")
             {:type ::unsafe-value :prop prop :value v})))
  v)

(defn- resolve-value
  "Resolve an sx value for `prop` (a kebab-cased property name) to CSS text.
  Returns nil for nil, which drops the declaration."
  [prop v]
  (cond
    (nil? v)   nil
    (token? v) (token->var v)

    ;; Above the type dispatch: `content` needs quoting for every scalar type,
    ;; not just strings — a bare keyword would emit `content:hi`, which the
    ;; parser discards.
    (= "content" prop)
    (quote-content (check-value! prop (if (keyword? v) (name v) (str v))))

    (keyword? v) (check-value! prop (theme/kebab v))
    (number? v)  (number->value prop v)
    (string? v)  (check-value! prop v)

    :else
    (throw (ex-info
             (str "cljs.react.sx: value for " (pr-str prop) " must be nil, a "
                  "keyword, a number or a string. Got " (pr-str v) ". A map "
                  "value is only meaningful under a property key (responsive "
                  "breakpoints) or a `&`/`@` key (a nested rule).")
             {:type ::invalid-value :prop prop :value v}))))

(defn- nest-selector
  "Splice `nested` (which contains `&`) into the enclosing `parent` selector."
  [parent nested]
  (str/replace nested "&" parent))

(defn- entry-sort-key
  "Write order for one sx key.

  An alias is written BEFORE anything more specific, so the more specific key
  wins the shared CSS property: `{:mx 1 :ml 5}` must mean margin-left 5, and
  `{:mx 2 :ml 0}` — idiomatic MUI — must not silently do nothing. Aliases
  expanding to more properties are the less specific ones, so the expansion
  count sorts descending. Ties break on the name, then on the printed key (two
  spellings of the same name)."
  [k]
  (let [ks (key-name k)]
    [(- (count (get shorthands (theme/kebab ks) [ks]))) ks (pr-str k)]))

(defn- sorted-entries
  "sx entries in a total, map-type-independent order.

  CLJS maps with <=8 entries are PersistentArrayMap (insertion-ordered);
  larger ones are PersistentHashMap (unordered), so two `=` maps can iterate
  differently. Since the style cache is keyed by `=`, iterating in a fixed
  order is a correctness requirement, not a tidiness one.

  Decorate-sort-undecorate: CLJS `sort-by` calls its key fn twice per
  COMPARISON, and the key includes a `pr-str`."
  [sx]
  (->> sx
       (map (fn [[k _ :as e]] [(entry-sort-key k) e]))
       (sort-by first)
       (map second)))

(def ^:private prop-name-re
  "A CSS property name, custom properties included. Property keys are spliced
  into rule text, so they are validated rather than trusted."
  #"^-{0,2}[a-zA-Z][a-zA-Z0-9-]*$")

(defn- check-prop!
  [raw-key prop]
  (when-not (re-matches prop-name-re prop)
    (throw (ex-info
             (str "cljs.react.sx: " (pr-str raw-key) " is not a usable CSS "
                  "property name (" (pr-str prop) "). Property keys are "
                  "spliced into rule text verbatim.")
             {:type ::invalid-property :key raw-key :prop prop})))
  prop)

(defn- add-decls
  [acc {:keys [at order selector]} raw-key v]
  (let [ks    (theme/kebab raw-key)
        props (or (get shorthands ks) [ks])
        rk    [at selector]]
    (reduce
      (fn [a p]
        (let [prop (check-prop! raw-key (name p))
              val  (resolve-value prop v)]
          (if (nil? val)
            a
            (update-in a [rk] (fn [rule]
                                (-> (or rule {:at at :order order
                                              :selector selector :decls {}})
                                    (assoc-in [:decls prop] val)))))))
      acc props)))

(def ^:private selector-re
  "A nested selector key. Anchored on `&` and deliberately narrow: a top-level
  `,` would let `{\"&, body\" {…}}` compile to ONE syntactically valid rule
  that `insertRule` accepts and that escapes the generated class entirely,
  which is exactly the containment promise the library makes."
  #"^&[a-zA-Z0-9 &.:\[\]()>+~_-]*$")

(def ^:private at-rule-re
  "An at-rule key: an allowlisted prelude plus a restricted charset. Unknown
  at-rules are rejected rather than passed through, since the prelude is
  spliced into rule text ahead of the generated class."
  #"^@(?:media|supports|container|layer)(?:[ (][a-zA-Z0-9 ()\[\]:,.%/_-]*)?$")

(defn- check-selector!
  [k ks]
  (when-not (re-matches selector-re ks)
    (throw (ex-info
             (str "cljs.react.sx: " (pr-str k) " is not a usable nested "
                  "selector. It must start with `&` and use only identifier "
                  "characters, combinators and pseudo/attribute syntax — a "
                  "comma or a brace would let the rule escape the generated "
                  "class.")
             {:type ::invalid-selector :key k})))
  ks)

(defn- check-at-rule!
  [k ks]
  (when-not (re-matches at-rule-re ks)
    (throw (ex-info
             (str "cljs.react.sx: " (pr-str k) " is not a usable at-rule. "
                  "Only @media, @supports, @container and @layer preludes are "
                  "accepted, and only with identifier, whitespace and "
                  "parenthesis characters.")
             {:type ::invalid-at-rule :key k})))
  ks)

(defn- check-one-at-rule!
  "One rule carries one at-rule prelude. `ctx` has a single `:at` slot and
  nothing composes preludes, so nesting would OVERWRITE the outer one and let
  the inner declarations escape it — `{\"@media print\" {:width {:md 1}}}`
  applying on screen. Loud rejection, in this ns's habit."
  [what inner outer]
  (when outer
    (throw (ex-info
             (str "cljs.react.sx: " what " is nested inside " (pr-str outer)
                  "; one rule carries one at-rule prelude. Write the two "
                  "conditions as a single prelude instead.")
             {:type ::nested-at-rule :outer outer :inner inner}))))

(defn- collect
  [acc sx ctx bps]
  (reduce
    (fn [a [k v]]
      (let [ks (key-name k)]
        (when (and (or (str/starts-with? ks "&") (str/starts-with? ks "@"))
                   (not (map? v)))
          (throw (ex-info
                   (str "cljs.react.sx: " (pr-str k) " is a nested selector or "
                        "at-rule, so its value must be an sx map. Got "
                        (pr-str v) ".")
                   {:type ::invalid-nested-value :key k :value v})))
        (cond
          (str/starts-with? ks "&")
          (collect a v (update ctx :selector nest-selector (check-selector! k ks))
                   bps)

          (str/starts-with? ks "@")
          (do
            (check-at-rule! k ks)
            (check-one-at-rule! (pr-str k) ks (:at ctx))
            (collect a v (assoc ctx :at ks :order at-rule-order) bps))

          (map? v)
          (do
            (when-let [bad (seq (remove #(contains? bp-index %) (keys v)))]
              (throw (ex-info
                       (str "cljs.react.sx: map value for " (pr-str k)
                            " is neither a responsive breakpoint map nor a "
                            "nested selector. Nested selectors must start with"
                            " `&` (e.g. \"&:hover\"); responsive maps may only"
                            " use " (pr-str theme/bp-order) ". Got "
                            (pr-str (vec bad)) ".")
                       {:type ::invalid-nested-key :key k :invalid (vec bad)})))
            ;; A breakpoint is itself an at-rule prelude, so it cannot compose
            ;; with an enclosing one either — see check-one-at-rule!.
            (when-let [inner (seq (remove #(zero? (get bp-index %)) (keys v)))]
              (let [bpks (vec (sort-by bp-index inner))]
                (check-one-at-rule!
                  (str "responsive breakpoint(s) " (pr-str bpks) " under "
                       (pr-str k))
                  bpks (:at ctx))))
            (reduce
              (fn [a2 [bpk bpv]]
                (let [idx (get bp-index bpk)
                      px  (nth bps idx 0)]
                  (if (zero? idx)
                    (add-decls a2 ctx k bpv)
                    (add-decls a2
                               (assoc ctx
                                      :at (str "@media (min-width: " px "px)")
                                      :order px)
                               k bpv))))
              a (sort-by (comp bp-index first) v)))

          :else
          (add-decls a ctx k v))))
    acc (sorted-entries sx)))

(defn sx->rules
  "Normalize `sx` to a flat, deterministic vector of rules.

  Each rule is `{:at <at-rule prelude or nil> :order <sort weight>
  :selector <string containing the `&` sentinel> :decls <prop->value map>}`
  and compiles to exactly one CSS rule.

  Order is base -> nested -> media (ascending) -> raw at-rules; declarations
  sort shorthand-before-longhand then alphabetically, so `{:p 1 :pt 2}`
  behaves as authored regardless of map type. Two `=` sx maps must produce
  identical output — the style cache depends on it.

  `breakpoints` is a 5-vector of pixel numbers; the theme is deliberately not
  passed, so output provably cannot depend on theme values."
  [sx breakpoints]
  (->> (collect {} sx {:at nil :order 0 :selector "&"} breakpoints)
       (vals)
       (remove #(empty? (:decls %)))
       (sort-by (fn [{:keys [at order selector]}]
                  [(if at 1 0) (or order 0) (or at "") selector]))
       (vec)))

(def ^:private hyphen-re #"-")

(def ^:private decl-rank
  "Emission rank for shorthands whose longhands do NOT start with the
  shorthand's own name, where the hyphen-count heuristic below cannot see the
  relationship — `place-items` would otherwise sort AFTER `align-items` and
  clobber it. The `inset` family is the same shape: its longhands are `top` /
  `right` / `bottom` / `left`, which share its hyphen count."
  {"place-items" 0 "place-content" 0 "place-self" 0 "flex-flow" 0
   "inset" 0 "inset-block" 0 "inset-inline" 0})

(defn- emit-decls
  "Declarations as text, shorthand before longhand. Within a rule the cascade
  resolves by source order at equal specificity, so a shorthand emitted last
  would silently win. Hyphen count is the heuristic (`margin` before
  `margin-top`); [[decl-rank]] carries the families it cannot see."
  [decls]
  (->> decls
       (map (fn [[prop _ :as d]]
              [[(or (decl-rank prop) (count (str/split prop hyphen-re))) prop] d]))
       (sort-by first)
       (map (fn [[_ [prop v]]] (str prop ":" v)))
       (str/join ";")))

(defn- emit-rule
  "Render one rule as a single CSS rule string from its already-rendered
  declaration `body`, substituting `cls` for the `&` sentinel."
  [{:keys [at selector]} body cls]
  (let [r (str (str/replace selector "&" cls) "{" body "}")]
    (if at (str at "{" r "}") r)))

(defn render-rules
  "Pair each rule with its rendered declaration body.

  The body is identical in sentinel and final form, so rendering it once here
  lets a caller that needs both (hash the sentinel, insert the real class) pay
  for the declarations once."
  [rules]
  (mapv (fn [rule] [rule (emit-decls (:decls rule))]) rules))

(defn rendered->css
  "Render the output of [[render-rules]] to a vector of CSS strings, one rule
  each (required by `insertRule`, which accepts exactly one rule per call)."
  [rendered cls]
  (mapv (fn [[rule body]] (emit-rule rule body cls)) rendered))

(defn rules->css
  "Render `rules` to a vector of CSS strings, one rule each. Pass `\"&\"` as
  `cls` to get the hashable sentinel form."
  [rules cls]
  (rendered->css (render-rules rules) cls))

(defn vars-decls
  "Render a sorted var map as a declaration body. The one encoding of custom
  properties as text: the `:root` block and the scoped-theme class both use it,
  so escaping added to one cannot miss the other."
  [vars]
  (str/join ";" (map (fn [[k v]] (str k ":" v)) vars)))

(defn vars-rule
  "Render a `selector { --a: x; --b: y }` rule from a sorted var map."
  [selector vars]
  (str selector "{" (vars-decls vars) "}"))
