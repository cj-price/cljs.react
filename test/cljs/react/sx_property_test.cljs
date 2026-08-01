(ns cljs.react.sx-property-test
  (:require
   [cljs.test :refer [deftest is]]
   [clojure.string :as str]
   [clojure.test.check :as tc]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop :include-macros true]
   ["global-jsdom/register"]
   [cljs.react.sx.compile :as sxc]
   [cljs.react.sx.sheet :as sheet]
   [cljs.react.sx.theme :as theme]))

(def ^:private num-tests 100)

(def bps (theme/breakpoint-values theme/default-theme-normalized))
(def bpk (:cx/bp-key theme/default-theme-normalized))

;; Property keys drawn from a fixed pool so generated maps collide on keys
;; often enough to exercise shorthand expansion and last-wins resolution.
(def ^:private prop-key-gen
  (gen/elements [:p :px :py :m :mt :mx :color :backgroundColor :bgcolor
                 :width :height :display :fontWeight :borderRadius :boxShadow
                 :gap :zIndex :opacity :position :border]))

;; Hostile values are drawn rarely on purpose: every one of them must be
;; REJECTED, so at a higher weight most generated maps would compile to
;; nothing and the structural properties below would pass vacuously.
(def ^:private hostile-value-gen
  (gen/elements [true false [1 2] [{:a 1}] {:color :red} #{:a}
                 "red}#victim{display:none" "red;color:blue" "red/*x*/"
                 "\"unbalanced"]))

(def ^:private prop-value-gen
  (gen/frequency
    [[8 gen/small-integer]
     [8 (gen/elements ["red" "100%" "1px solid black" "0.5rem"])]
     [8 (gen/elements [:flex :none :absolute :inherit])]
     [8 (gen/elements [:palette.primary.main :shape.borderRadius
                       :palette.text.secondary])]
     [1 hostile-value-gen]]))

(def ^:private rejected ::rejected)

(defn- css*
  "Compiled CSS for `sx`, or `::rejected` if the compiler refused it. The
  properties below hold either way: a rejection is a legitimate, deterministic
  outcome — silently emitting garbage is not."
  [sx]
  (try (sxc/rules->css (sxc/sx->rules sx bps) ".c")
       (catch :default _ rejected)))

(def ^:private responsive-gen
  (gen/fmap #(into {} %)
            (gen/not-empty
              (gen/vector-distinct-by
                first
                (gen/tuple (gen/elements theme/bp-order) prop-value-gen)
                {:max-elements 3}))))

(def ^:private nested-gen
  (gen/fmap (fn [[sel decls]] [sel (into {} decls)])
            (gen/tuple
              (gen/elements ["&:hover" "&:focus" "& .child" "&::before" "&>div"])
              (gen/not-empty
                (gen/vector-distinct-by
                  first
                  (gen/tuple prop-key-gen prop-value-gen)
                  {:max-elements 3})))))

(def ^:private sx-gen
  (gen/fmap
    (fn [[decls responsives nesteds]]
      (merge (into {} decls) (into {} responsives) (into {} nesteds)))
    (gen/tuple
      (gen/vector-distinct-by first
                              (gen/tuple prop-key-gen prop-value-gen)
                              {:max-elements 6})
      (gen/vector-distinct-by first
                              (gen/tuple prop-key-gen responsive-gen)
                              {:max-elements 2})
      (gen/vector-distinct-by first nested-gen {:max-elements 2}))))

;; ---------------------------------------------------------------------------

(deftest compile-is-order-independent
  ;; CLJS maps with <=8 entries are PersistentArrayMap (insertion-ordered);
  ;; larger ones are PersistentHashMap (unordered), so two `=` maps can iterate
  ;; differently. The style cache is keyed by `=`, so nondeterministic output
  ;; would make the registry serve one map's rules under another map's class.
  (let [result (tc/quick-check num-tests
                 (prop/for-all [sx sx-gen]
                   (let [shuffled (into (hash-map) (shuffle (seq sx)))
                         arrayed  (apply array-map (mapcat identity (shuffle (seq sx))))]
                     (= (css* sx) (css* shuffled) (css* arrayed)))))]
    (is (:result result) (pr-str result))))

(deftest compile-is-pure
  (let [result (tc/quick-check num-tests
                 (prop/for-all [sx sx-gen]
                   (= (css* sx) (css* sx))))]
    (is (:result result) (pr-str result))))

(deftest hostile-values-are-rejected
  ;; The reason the structural properties below were green: prop-value-gen drew
  ;; from a fixed safe pool, so no generated value could contain `}` or `;` or
  ;; be a non-scalar. Emitting one is not an option — `width:[1 2]` and
  ;; `display:true` are discarded by the parser, and `{:width [{:a 1}]}` put
  ;; extra braces inside one rule.
  (let [result (tc/quick-check num-tests
                 (prop/for-all [k prop-key-gen v hostile-value-gen]
                   (= rejected (css* {k v}))))]
    (is (:result result) (pr-str result))))

(deftest every-rule-is-exactly-one-css-rule
  ;; insertRule accepts exactly one rule per call, so each emitted string must
  ;; have balanced braces and describe a single rule (or a single at-block).
  (let [result (tc/quick-check num-tests
                 (prop/for-all [sx sx-gen]
                   (let [out (css* sx)]
                     (or (= rejected out)
                         (every? (fn [s]
                                   (and (= (count (re-seq #"\{" s))
                                           (count (re-seq #"\}" s)))
                                        (str/ends-with? s "}")
                                        (if (str/starts-with? s "@")
                                          (= 2 (count (re-seq #"\{" s)))
                                          (= 1 (count (re-seq #"\{" s))))))
                                 out)))))]
    (is (:result result) (pr-str result))))

(deftest class-is-a-function-of-the-css
  ;; Equal sx maps must yield one class; sx maps whose CSS differs must not
  ;; share one. This exercises the collision chain in the content registry.
  (let [result (tc/quick-check num-tests
                 (prop/for-all [a sx-gen b sx-gen]
                   (let [cls (fn [sx] (try (sheet/class-for sx bpk bps)
                                           (catch :default _ rejected)))
                         ca  (cls a)
                         cb  (cls b)]
                     (and (= ca (cls a))
                          (if (= (css* a) (css* b))
                            (= ca cb)
                            (not= ca cb))))))]
    (is (:result result) (pr-str result))))

(deftest theme-var-names-are-injective
  ;; Catches a real bug class: sibling :fontSize and :font-size keys both
  ;; kebab to --cx-…-font-size and one would silently win.
  (let [branch-gen (gen/elements [[:palette :primary] [:palette :text]
                                  [:typography] [:shape] [:z-index]])
        key-gen    (gen/elements [:fontSize :font-size :borderRadius
                                  :border-radius :main :contrastText
                                  :contrast-text :lineHeight])
        result
        (tc/quick-check num-tests
          (prop/for-all [overrides (gen/vector (gen/tuple branch-gen key-gen
                                                          gen/small-integer)
                                               0 6)]
            (let [t     (reduce (fn [acc [branch k v]]
                                  (theme/deep-merge-theme
                                    acc (assoc-in {} (conj branch k) v)))
                                theme/default-theme overrides)
                  names (keys (theme/theme->css-vars t))]
              (= (count names) (count (distinct names))))))]
    (is (:result result) (pr-str result))))

(deftest generated-css-never-embeds-theme-values
  ;; The structural guarantee behind caching on bp-key rather than the theme:
  ;; a theme token must compile to a var() reference, never to the value it
  ;; currently holds.
  (let [result (tc/quick-check num-tests
                 (prop/for-all [k prop-key-gen
                                token (gen/elements [:palette.primary.main
                                                     :palette.text.secondary
                                                     :shape.borderRadius])]
                   (let [out (str/join (sxc/rules->css
                                         (sxc/sx->rules {k token} bps) ".c"))]
                     (and (str/includes? out "var(--cx-")
                          (not (str/includes? out "#1976d2"))
                          (not (str/includes? out "rgba"))))))]
    (is (:result result) (pr-str result))))

;; ---------------------------------------------------------------------------
;; Keyframes

(def ^:private kf-offset-gen
  (gen/elements [:from :to 0 25 50 75 100 "12.5%" "80%" [0 100] [25 75]]))

(def ^:private frames-gen
  (gen/fmap #(into {} %)
            (gen/not-empty
              (gen/vector-distinct-by
                first
                (gen/tuple kf-offset-gen
                           (gen/fmap #(into {} %)
                                     (gen/not-empty
                                       (gen/vector-distinct-by
                                         first
                                         (gen/tuple prop-key-gen prop-value-gen)
                                         {:max-elements 3}))))
                {:max-elements 4}))))

(defn- kf-body*
  [frames]
  (try (sxc/keyframes->body frames) (catch :default _ rejected)))

(deftest keyframes-emission-is-order-independent
  ;; The body is content-hashed into the keyframes NAME, so two `=` frames maps
  ;; that emitted different text would register two names and two rules — the
  ;; same requirement compile-is-order-independent covers for classes.
  (let [result (tc/quick-check num-tests
                 (prop/for-all [frames frames-gen]
                   (let [shuffled (into (hash-map) (shuffle (seq frames)))
                         arrayed  (apply array-map
                                         (mapcat identity (shuffle (seq frames))))]
                     (= (kf-body* frames) (kf-body* shuffled) (kf-body* arrayed)))))]
    (is (:result result) (pr-str result))))

(deftest hostile-values-inside-a-frame-are-rejected
  ;; Proves the frame emitter routes through the same value checks a rule body
  ;; does, rather than having quietly grown a dialect of its own.
  (let [result (tc/quick-check num-tests
                 (prop/for-all [offset kf-offset-gen
                                k prop-key-gen
                                v hostile-value-gen]
                   (= rejected (kf-body* {offset {k v}}))))]
    (is (:result result) (pr-str result))))

(deftest every-keyframes-body-is-one-insertable-rule
  ;; The strongest statement of the dev/release-divergence invariant: the dev
  ;; text node accepts anything, insertRule accepts exactly one rule, and the
  ;; :test build runs the dev path — so this is the only thing asking a real
  ;; parser whether every generated @keyframes rule survives release.
  (sheet/set-insert-mode! :cssom)
  (let [result (tc/quick-check num-tests
                 (prop/for-all [frames frames-gen]
                   (let [body (kf-body* frames)]
                     (or (= rejected body)
                         (let [nm (sheet/keyframes-name! body)]
                           (some (fn [^js r] (= nm (.-name r)))
                                 (some-> (.querySelector
                                           js/document "[data-cljs-react-sx]")
                                         .-sheet .-cssRules array-seq)))))))]
    (sheet/set-insert-mode! :text)
    (is (:result result) (pr-str result))))
