(ns cljs.react.sx.compile-test
  (:require
   [cljs.test :refer [deftest testing is]]
   [cljs.react.sx.compile :as sxc]))

(def bps [0 600 900 1200 1536])

(defn css
  "Compile an sx map to its CSS strings under the default breakpoints."
  ([sx] (css sx ".c"))
  ([sx cls] (sxc/rules->css (sxc/sx->rules sx bps) cls)))

(defn css1 [sx] (first (css sx)))

(defn- ex-type
  "The `:type` of the ex-info `sx` throws, or nil if it compiles."
  [sx]
  (try (css sx) nil (catch :default e (:type (ex-data e)))))

(deftest declarations-test
  (testing "a plain property/value pair"
    (is (= ".c{display:flex}" (css1 {:display :flex}))))

  (testing "camelCase, kebab-case and string keys all normalize"
    (is (= ".c{background-color:red}" (css1 {:backgroundColor "red"})))
    (is (= ".c{background-color:red}" (css1 {:background-color "red"})))
    (is (= ".c{background-color:red}" (css1 {"background-color" "red"}))))

  (testing "strings pass through verbatim"
    (is (= ".c{border:1px solid red}" (css1 {:border "1px solid red"})))
    (is (= ".c{width:100%}" (css1 {:width "100%"}))))

  (testing "dotless keywords are literal CSS identifiers, kebab-cased"
    (is (= ".c{position:absolute}" (css1 {:position :absolute})))
    (is (= ".c{align-items:flex-start}" (css1 {:alignItems :flexStart}))))

  (testing "nil values drop the declaration entirely"
    (is (= [] (css {:color nil})))
    (is (= ".c{color:red}" (css1 {:color "red" :width nil})))))

(deftest tokens-test
  (testing "dotted keywords are theme tokens, never inlined values"
    (is (= ".c{color:var(--cx-palette-primary-main)}"
           (css1 {:color :palette.primary.main}))))

  (testing "token path segments kebab-case, so either spelling resolves"
    (is (= ".c{color:var(--cx-palette-primary-contrast-text)}"
           (css1 {:color :palette.primary.contrastText})))
    (is (= ".c{color:var(--cx-palette-primary-contrast-text)}"
           (css1 {:color :palette.primary.contrast-text}))))

  (testing "numeric token segments work"
    (is (= ".c{box-shadow:var(--cx-shadows-2)}"
           (css1 {:boxShadow :shadows.2})))))

(deftest numbers-test
  (testing "spacing properties multiply the theme spacing unit"
    (is (= ".c{padding:calc(var(--cx-spacing) * 2)}" (css1 {:p 2})))
    (is (= ".c{margin-top:calc(var(--cx-spacing) * 3)}" (css1 {:mt 3})))
    (is (= ".c{gap:calc(var(--cx-spacing) * 1)}" (css1 {:gap 1}))))

  (testing "negative spacing works"
    (is (= ".c{margin-top:calc(var(--cx-spacing) * -1)}" (css1 {:mt -1}))))

  (testing "zero folds to a bare 0 for every property class"
    (is (= ".c{padding:0}" (css1 {:p 0})))
    (is (= ".c{width:0}" (css1 {:width 0}))))

  (testing "box-shadow 0 is elevation 0, not a bare 0"
    ;; `box-shadow:0` is invalid CSS — the property needs at least two lengths
    ;; — and elevation 0 is the natural way to write a flat variant.
    (is (= ".c{box-shadow:var(--cx-shadows-0)}" (css1 {:box-shadow 0}))))

  (testing "a longhand that starts with a unitless property still gets px"
    ;; Prefix matching is right for THEME PATHS (:font-weight-bold) and wrong
    ;; for CSS PROPERTIES: real parsers discard `flex-basis:200`.
    (is (= ".c{flex-basis:200px}" (css1 {:flex-basis 200})))
    (is (= ".c{flex:1}" (css1 {:flex 1}))))

  (testing "the legacy grid gap aliases scale like their modern spellings"
    (is (= ".c{grid-row-gap:calc(var(--cx-spacing) * 2)}"
           (css1 {:grid-row-gap 2}))))

  (testing "border-radius uses the shape scale"
    (is (= ".c{border-radius:calc(var(--cx-shape-border-radius) * 1)}"
           (css1 {:borderRadius 1}))))

  (testing "box-shadow is an elevation index"
    (is (= ".c{box-shadow:var(--cx-shadows-2)}" (css1 {:boxShadow 2}))))

  (testing "unitless properties stay raw"
    (is (= ".c{font-weight:500}" (css1 {:fontWeight 500})))
    (is (= ".c{opacity:0.5}" (css1 {:opacity 0.5})))
    (is (= ".c{z-index:10}" (css1 {:zIndex 10})))
    (is (= ".c{line-height:1.5}" (css1 {:lineHeight 1.5}))))

  (testing "everything else is pixels"
    (is (= ".c{width:300px}" (css1 {:width 300})))
    (is (= ".c{font-size:14px}" (css1 {:fontSize 14}))))

  (testing "strings bypass the scale entirely"
    (is (= ".c{padding:1.5rem}" (css1 {:p "1.5rem"})))))

(deftest shorthands-test
  (testing "multi-property aliases emit multiple declarations"
    (is (= ".c{padding-left:calc(var(--cx-spacing) * 2);padding-right:calc(var(--cx-spacing) * 2)}"
           (css1 {:px 2})))
    (is (= ".c{margin-bottom:calc(var(--cx-spacing) * 1);margin-top:calc(var(--cx-spacing) * 1)}"
           (css1 {:my 1}))))

  (testing ":bgcolor maps to background-color"
    (is (= ".c{background-color:var(--cx-palette-primary-main)}"
           (css1 {:bgcolor :palette.primary.main}))))

  (testing "shorthand sorts before the longhand it would be overridden by"
    ;; {:p 1 :pt 2} must mean 'pad 1 everywhere, 2 on top' regardless of map
    ;; iteration order.
    (is (= ".c{padding:calc(var(--cx-spacing) * 1);padding-top:calc(var(--cx-spacing) * 2)}"
           (css1 {:p 1 :pt 2})))
    (is (= ".c{padding:calc(var(--cx-spacing) * 1);padding-top:calc(var(--cx-spacing) * 2)}"
           (css1 {:pt 2 :p 1}))))

  (testing "the more specific key wins a CSS property two keys both write"
    ;; `{:mx 2 :ml 0}` is idiomatic MUI. Both keys expand onto margin-left, and
    ;; add-decls is last-write-wins, so the write ORDER is the whole semantics.
    (is (= ".c{margin-left:calc(var(--cx-spacing) * 5);margin-right:calc(var(--cx-spacing) * 1)}"
           (css1 {:mx 1 :ml 5})))
    (is (= ".c{margin-left:calc(var(--cx-spacing) * 5);margin-right:calc(var(--cx-spacing) * 1)}"
           (css1 {:ml 5 :mx 1})))
    (is (= ".c{margin-left:calc(var(--cx-spacing) * 5);margin-right:calc(var(--cx-spacing) * 1)}"
           (css1 {:mx 1 :margin-left 5})))
    (is (= ".c{margin-left:calc(var(--cx-spacing) * 5);margin-right:calc(var(--cx-spacing) * 1)}"
           (css1 {:margin-left 5 :mx 1})))))

(deftest nested-selectors-test
  (testing "& is replaced by the class"
    (is (= [".c{color:red}" ".c:hover{color:blue}"]
           (css {:color "red" :&:hover {:color "blue"}}))))

  (testing "string keys are the primary spelling"
    (is (= [".c:hover{color:blue}"] (css {"&:hover" {:color "blue"}}))))

  (testing "descendant selectors"
    (is (= [".c .child{color:red}"] (css {"& .child" {:color "red"}}))))

  (testing "pseudo-elements"
    (is (= [".c::before{content:\"x\"}"] (css {"&::before" {:content "x"}}))))

  (testing "child combinator"
    (is (= [".c>div{margin:0}"] (css {"&>div" {:margin 0}}))))

  (testing "nesting composes"
    (is (= [".c .child:hover{color:red}"]
           (css {"& .child" {"&:hover" {:color "red"}}}))))

  (testing "a bare selector key is rejected rather than implicitly prefixed"
    ;; Rejecting this is what removes the ambiguity with responsive maps.
    (let [e (try (css {:.child {:color "red"}}) nil
                 (catch :default e e))]
      (is (some? e))
      (is (= :cljs.react.sx.compile/invalid-nested-key (:type (ex-data e))))))

  (testing "a nested selector key with a non-map value is rejected"
    (let [e (try (css {:&:hover "red"}) nil (catch :default e e))]
      (is (some? e))
      (is (= :cljs.react.sx.compile/invalid-nested-value (:type (ex-data e)))))))

(deftest content-quoting-test
  (testing "plain strings are auto-quoted — the #1 CSS-in-JS gotcha"
    (is (= ".c::before{content:\"hi\"}" (css1 {"&::before" {:content "hi"}}))))

  (testing "CSS keywords are not quoted"
    (doseq [kw ["none" "normal" "inherit" "initial" "unset" "open-quote"]]
      (is (= (str ".c::before{content:" kw "}")
             (css1 {"&::before" {:content kw}})))))

  (testing "functional values are not quoted"
    (is (= ".c::before{content:attr(data-x)}"
           (css1 {"&::before" {:content "attr(data-x)"}})))
    (is (= ".c::before{content:var(--x)}"
           (css1 {"&::before" {:content "var(--x)"}})))
    (is (= ".c::before{content:url(a.png)}"
           (css1 {"&::before" {:content "url(a.png)"}}))))

  (testing "already-quoted values are left alone"
    (is (= ".c::before{content:\"hi\"}"
           (css1 {"&::before" {:content "\"hi\""}}))))

  (testing "keyword content is quoted too, not just string content"
    ;; quote-content used to hang off the string branch, so a keyword took the
    ;; generic path and emitted `content:hi`, which the parser discards.
    (is (= ".c::before{content:\"hi\"}" (css1 {"&::before" {:content :hi}})))
    (is (= ".c::before{content:none}" (css1 {"&::before" {:content :none}}))))

  (testing "embedded quotes and backslashes are escaped, not emitted raw"
    (is (= ".c::before{content:\"he said \\\"hi\\\"\"}"
           (css1 {"&::before" {:content "he said \"hi\""}})))
    (is (= ".c::before{content:\"a\\\\b\"}"
           (css1 {"&::before" {:content "a\\b"}}))))

  (testing "a value that merely STARTS with a quote is not passed through"
    ;; `\"a\";color:red` is two declarations and insertRule accepts it, so the
    ;; bypass has to mean 'one complete CSS string', not 'begins with a quote'.
    (is (= :cljs.react.sx.compile/unsafe-value
           (ex-type {"&::before" {:content "\"a\";color:red"}})))))

(deftest unsafe-values-test
  (testing "a value that could escape its declaration is rejected"
    ;; A `;`-only payload stays one valid rule and insertRule accepts it in
    ;; RELEASE; the `}` shape is accepted by the dev text-node path and
    ;; rejected by insertRule, so it works in dev and vanishes in production.
    (doseq [v ["red}#victim{display:none"
               "red;display:none"
               "red<style>"
               "red/*x*/"
               "\"unbalanced"]]
      (is (= :cljs.react.sx.compile/unsafe-value (ex-type {:color v}))
          (pr-str v))))

  (testing "ordinary values with punctuation still compile"
    (is (= ".c{font-family:'Segoe UI', sans-serif}"
           (css1 {:font-family "'Segoe UI', sans-serif"})))
    (is (= ".c{background:url(a.png) no-repeat}"
           (css1 {:background "url(a.png) no-repeat"}))))

  (testing "a hostile KEYWORD value is rejected, exactly like a hostile string"
    (doseq [s ["flex}x{color:red" "flex;color:red" "flex<style>"]]
      (is (= :cljs.react.sx.compile/unsafe-value (ex-type {:display (keyword s)}))
          (str (pr-str s) " — the check used to hang off the string branch only,"
               " so (keyword s) walked past it and emitted a live injected rule"
               " in dev that insertRule discards wholesale in release")))
    (is (= :cljs.react.sx.compile/unsafe-value
           (ex-type {:display (keyword "a" "flex/*x*/")}))
        (str "a `/` payload only survives the 2-arity constructor: (keyword s)"
             " splits on `/` and keeps the first segment, so the 1-arity form"
             " truncates to a harmless `flex` before the compiler sees it")))

  (testing "a hostile TOKEN keyword is rejected before it becomes a var()"
    (doseq [s ["a.b}victim{color:red" "a.b;color:red" "a.b<style>"]]
      (is (= :cljs.react.sx.theme/invalid-var-name
             (ex-type {:color (keyword s)}))
          (str (pr-str s) " — the dotted-keyword branch split on `.` and spliced"
               " the segments into var(--cx-…) unchecked"))))

  (testing "ordinary keyword values still compile"
    (is (= ".c{display:flex}" (css1 {:display :flex})))
    (is (= ".c{color:var(--cx-palette-primary-main)}"
           (css1 {:color :palette.primary.main})))))

(deftest non-scalar-values-test
  (testing "a value that is not nil/keyword/number/string is rejected"
    ;; `(str v)` used to pr-str these straight into the declaration —
    ;; `width:[1 2]`, `display:true` — where a parser silently drops them, and
    ;; `{:width [{:a 1}]}` put extra braces inside ONE rule.
    (doseq [v [[1 2] [{:a 1}] true false #{1} 'sym]]
      (is (= :cljs.react.sx.compile/invalid-value (ex-type {:width v}))
          (pr-str v))))

  (testing "a map in value position is still responsive, not a value"
    (is (= [".c{width:1px}"] (css {:width {:xs 1}})))
    (is (= :cljs.react.sx.compile/invalid-value
           (ex-type {:width {:md {:color :red}}})))))

(deftest unsafe-keys-test
  (testing "a selector key that could escape the generated class is rejected"
    ;; `{\"&, body\" {…}}` compiles to ONE syntactically valid rule that
    ;; insertRule accepts and that styles `body` — the containment promise.
    (doseq [k ["&, body" "&{}body" "&:hover;body" "&<x"]]
      (is (= :cljs.react.sx.compile/invalid-selector (ex-type {k {:margin 0}}))
          (pr-str k))))

  (testing "the selector shapes the dialect documents still work"
    (doseq [k ["&:hover" "& .child" "&::before" "&>div" "&:not(.x)"
               "&[disabled]" "& + &"]]
      (is (nil? (ex-type {k {:margin 0}})) (pr-str k))))

  (testing "at-rule keys are an allowlist, not a passthrough"
    (is (nil? (ex-type {"@media print" {:color "red"}})))
    (is (nil? (ex-type {"@supports (display: grid)" {:display :grid}})))
    (is (nil? (ex-type {"@layer base" {:color "red"}})))
    (is (= :cljs.react.sx.compile/invalid-at-rule
           (ex-type {"@import url(evil.css)" {:color "red"}})))
    (is (= :cljs.react.sx.compile/invalid-at-rule
           (ex-type {"@media print{}body" {:color "red"}}))))

  (testing "a property name that is not a property name is rejected"
    (is (= :cljs.react.sx.compile/invalid-property
           (ex-type {"color:red;background" "blue"})))
    (is (nil? (ex-type {"--my-var" "blue"})))))

(deftest one-at-rule-per-rule-test
  (testing "an at-rule inside an at-rule is rejected, not silently flattened"
    ;; ctx carries a single :at slot and nothing composes preludes, so the
    ;; inner one used to OVERWRITE the outer and escape it.
    (let [e (try (css {"@media print" {"@supports (display: grid)"
                                       {:color "red"}}})
                 nil (catch :default e e))]
      (is (some? e))
      (is (= :cljs.react.sx.compile/nested-at-rule (:type (ex-data e))))
      (is (= "@media print" (:outer (ex-data e))))))

  (testing "responsive breakpoints inside an at-rule are rejected too"
    ;; This one shipped as `{\"@media print\" {:width {:xs 1 :md 2}}}` emitting
    ;; the :md declaration OUTSIDE the print block, so it applied on screen.
    (is (= :cljs.react.sx.compile/nested-at-rule
           (ex-type {"@media print" {:width {:xs 1 :md 2}}})))
    (is (= :cljs.react.sx.compile/nested-at-rule
           (ex-type {"@media print" {:&:hover {:width {:md 2}}}}))))

  (testing ":xs under an at-rule is fine — it adds no prelude of its own"
    (is (= ["@media print{.c{width:1px}}"]
           (css {"@media print" {:width {:xs 1}}})))))

(deftest responsive-test
  (testing ":xs lands in the base rule, never in a media query"
    (is (= [".c{width:100%}"] (css {:width {:xs "100%"}}))))

  (testing "other breakpoints emit mobile-first min-width queries"
    (is (= [".c{width:100%}" "@media (min-width: 900px){.c{width:300px}}"]
           (css {:width {:xs "100%" :md 300}}))))

  (testing "media blocks sort ascending regardless of authoring order"
    (is (= ["@media (min-width: 600px){.c{width:1px}}"
            "@media (min-width: 900px){.c{width:2px}}"
            "@media (min-width: 1200px){.c{width:3px}}"]
           (css {:width {:lg 3 :sm 1 :md 2}}))))

  (testing "responsive values inside a nested selector"
    (is (= [".c:hover{color:red}" "@media (min-width: 900px){.c:hover{color:blue}}"]
           (css {:&:hover {:color {:xs "red" :md "blue"}}}))))

  (testing "a non-breakpoint key in a map value is rejected"
    (let [e (try (css {:width {:small 1}}) nil (catch :default e e))]
      (is (some? e))
      (is (= :cljs.react.sx.compile/invalid-nested-key (:type (ex-data e)))))))

(deftest at-rules-test
  (testing "raw at-rules pass through and sort after responsive blocks"
    (is (= [".c{color:red}"
            "@media (min-width: 900px){.c{color:blue}}"
            "@media print{.c{color:black}}"]
           (css {:color {:xs "red" :md "blue"}
                 "@media print" {:color "black"}}))))

  (testing "@supports"
    (is (= ["@supports (display: grid){.c{display:grid}}"]
           (css {"@supports (display: grid)" {:display :grid}})))))

(deftest ordering-test
  (testing "base rule precedes nested rules precede media blocks"
    (is (= [".c{color:red}"
            ".c:hover{color:green}"
            "@media (min-width: 900px){.c{color:blue}}"]
           (css {:&:hover {:color "green"}
                 :color {:xs "red" :md "blue"}}))))

  (testing "declarations within a rule sort shorthand-first then alphabetically"
    ;; One-segment properties (color, margin, width) sort alphabetically ahead
    ;; of two-segment ones (margin-top), so a shorthand can never be emitted
    ;; after the longhand that should override it.
    (is (= ".c{color:red;margin:0;width:1px;margin-top:calc(var(--cx-spacing) * 1)}"
           (css1 {:margin-top 1 :width 1 :margin 0 :color "red"}))))

  (testing "shorthands whose longhands do not share their name still sort first"
    ;; Hyphen count is only a PROXY for shorthand-ness: `place-items` has two
    ;; segments like `align-items` and sorts after it alphabetically, so the
    ;; shorthand would clobber the longhand it should lose to.
    (is (= ".c{place-items:start;align-items:center}"
           (css1 {:align-items "center" :place-items "start"})))
    (is (= ".c{flex-flow:column wrap;flex-direction:row}"
           (css1 {:flex-direction :row :flex-flow "column wrap"}))))

  (testing "the inset family sorts before the longhands it would clobber"
    (is (= ".c{inset:0;bottom:auto}" (css1 {:bottom "auto" :inset 0}))
        (str "inset expands to top/right/bottom/left, which share its hyphen"
             " count, so the heuristic emitted bottom first and let the"
             " shorthand overwrite it; the other three longhands only looked"
             " correct because they sort after inset alphabetically"))
    (is (= ".c{inset:0;bottom:auto}" (css1 {:inset 0 :bottom "auto"})))
    (is (= ".c{inset-block:0;top:auto}" (css1 {:top "auto" :inset-block 0})))
    (is (= ".c{inset-inline:0;left:auto}"
           (css1 {:left "auto" :inset-inline 0})))))

(deftest determinism-test
  ;; CLJS maps with <=8 entries are PersistentArrayMap (insertion-ordered);
  ;; larger ones are PersistentHashMap (unordered). Two `=` maps can iterate
  ;; differently, and the style cache is keyed by `=` — so equal maps that
  ;; compiled differently would make the registry serve the wrong rules.
  (testing "authoring order does not affect output"
    (is (= (css {:p 1 :m 2 :color "red"})
           (css {:color "red" :m 2 :p 1}))))

  (testing "PersistentArrayMap and PersistentHashMap agree"
    ;; array-map never promotes, so this stays insertion-ordered at 10 entries
    ;; while the same content as a literal would already be a hash map.
    (let [m   (array-map :z-index 5 :p 1 :m 2 :color "red" :width 10
                         :height 20 :display :flex :position :absolute
                         :top 0 :left 0)
          phm (into (hash-map) m)]
      (is (= m phm) "test setup: the two maps must be equal")
      (is (not (identical? (type m) (type phm)))
          "test setup: the two maps must have different implementations")
      (is (= (css m) (css phm)))))

  (testing "nested and responsive sections are order-stable too"
    (let [a {:&:hover {:color "a" :p 1} :width {:md 2 :xs 1} :m 3}
          b {:m 3 :width {:xs 1 :md 2} :&:hover {:p 1 :color "a"}}]
      (is (= (css a) (css b))))))

(defn- kf-type
  "The `:type` of the ex-info `frames` throws, or nil if it compiles."
  [frames]
  (try (sxc/keyframes->body frames) nil (catch :default e (:type (ex-data e)))))

(deftest keyframes-test
  (testing "from and to canonicalize to offsets"
    (is (= "0%{opacity:0}100%{opacity:1}"
           (sxc/keyframes->body {:from {:opacity 0} :to {:opacity 1}}))))

  (testing "numbers, percentage strings and decimals"
    (is (= "0%{opacity:0}50%{opacity:0.5}100%{opacity:1}"
           (sxc/keyframes->body {0 {:opacity 0} 50 {:opacity 0.5}
                                 100 {:opacity 1}})))
    (is (= "33.3%{opacity:1}" (sxc/keyframes->body {"33.3%" {:opacity 1}})))
    (is (= "25%{opacity:1}" (sxc/keyframes->body {:25% {:opacity 1}}))))

  (testing "a vector key is the multi-selector form"
    (is (= "0%,100%{opacity:1}50%{opacity:0}"
           (sxc/keyframes->body {[0 100] {:opacity 1} 50 {:opacity 0}}))))

  (testing "frames emit in ascending offset order regardless of authoring order"
    ;; The body is hashed into the keyframes name, so two `=` frames maps that
    ;; emitted different text would register two names and two rules.
    (is (= (sxc/keyframes->body {:to {:opacity 1} :from {:opacity 0}})
           (sxc/keyframes->body {:from {:opacity 0} :to {:opacity 1}})))
    (let [m   (array-map 90 {:opacity 0.9} 10 {:opacity 0.1} 20 {:opacity 0.2}
                         30 {:opacity 0.3} 40 {:opacity 0.4} 50 {:opacity 0.5}
                         60 {:opacity 0.6} 70 {:opacity 0.7} 80 {:opacity 0.8}
                         100 {:opacity 1})
          phm (into (hash-map) m)]
      (is (= m phm) "test setup: the two maps must be equal")
      (is (not (identical? (type m) (type phm)))
          "test setup: the two maps must have different implementations")
      (is (= (sxc/keyframes->body m) (sxc/keyframes->body phm)))))

  (testing "a frame is a full sx map, not a second dialect"
    (is (= "0%{padding:calc(var(--cx-spacing) * 2)}"
           (sxc/keyframes->body {:from {:p 2}})))
    (is (= "0%{color:var(--cx-palette-primary-main)}"
           (sxc/keyframes->body {:from {:color :palette.primary.main}})))
    (is (= "0%{content:\"x\"}" (sxc/keyframes->body {:from {:content "x"}})))
    (is (= "0%{background-color:red}"
           (sxc/keyframes->body {:from {:bgcolor "red"}})))
    (is (= "0%{padding:calc(var(--cx-spacing) * 1);padding-top:calc(var(--cx-spacing) * 2)}"
           (sxc/keyframes->body {:from {:p 1 :pt 2}}))
        "shorthand still emits before longhand")
    (is (= "0%{opacity:1}"
           (sxc/keyframes->body {:from {:opacity 1 :color nil}}))
        "a nil declaration is dropped, as everywhere else"))

  (testing "values inside a frame hit the same trust boundary"
    (is (= :cljs.react.sx.compile/unsafe-value
           (kf-type {:from {:color "red}#victim{color:blue"}})))
    (is (= :cljs.react.sx.compile/invalid-value
           (kf-type {:from {:opacity true}}))))

  (testing "an empty body is rejected here, because nothing downstream will"
    ;; `@keyframes x{}` is syntactically valid, so insertRule accepts it in
    ;; release exactly as the dev text node does.
    (is (= :cljs.react.sx.compile/empty-keyframes (kf-type {})))
    (is (= :cljs.react.sx.compile/empty-keyframes (kf-type {:from {}})))
    (is (= :cljs.react.sx.compile/empty-keyframes
           (kf-type {:from {:color nil}}))))

  (testing "invalid selectors"
    (is (= :cljs.react.sx.compile/invalid-keyframe-selector (kf-type {"50" {:opacity 1}})))
    (is (= :cljs.react.sx.compile/invalid-keyframe-selector (kf-type {"110%" {:opacity 1}})))
    (is (= :cljs.react.sx.compile/invalid-keyframe-selector (kf-type {110 {:opacity 1}})))
    (is (= :cljs.react.sx.compile/invalid-keyframe-selector (kf-type {-10 {:opacity 1}})))
    (is (= :cljs.react.sx.compile/invalid-keyframe-selector (kf-type {"from, to" {:opacity 1}})))
    (is (= :cljs.react.sx.compile/invalid-keyframe-selector (kf-type {:hover {:opacity 1}})))
    (is (= :cljs.react.sx.compile/invalid-keyframe-selector (kf-type {[] {:opacity 1}}))))

  (testing "duplicate offsets after canonicalization"
    (is (= :cljs.react.sx.compile/duplicate-keyframe
           (kf-type {:from {:opacity 0} "0%" {:opacity 1}})))
    (is (= :cljs.react.sx.compile/duplicate-keyframe
           (kf-type {:to {:opacity 0} 100 {:opacity 1}})))
    (is (= :cljs.react.sx.compile/duplicate-keyframe
           (kf-type {[0 0] {:opacity 1}}))))

  (testing "a frame holds declarations only"
    (is (= :cljs.react.sx.compile/invalid-keyframe-body
           (kf-type {:from {:&:hover {:opacity 1}}})))
    (is (= :cljs.react.sx.compile/invalid-keyframe-body
           (kf-type {:from {"@media print" {:opacity 1}}})))
    (is (= :cljs.react.sx.compile/invalid-keyframe-body
           (kf-type {:from {:width {:xs 1 :md 2}}})))
    (is (= :cljs.react.sx.compile/invalid-keyframe-body
           (kf-type {:from "opacity:1"})))
    (is (= :cljs.react.sx.compile/invalid-keyframes (kf-type "from{}")))))

(deftest time-value-test
  ;; `<time>` has no unitless form, zero included — so every bare number here
  ;; would emit `Npx` and be discarded, the same silent-drop shape the
  ;; box-shadow and content cases already guard.
  (testing "a bare number is refused rather than guessed at"
    (is (= :cljs.react.sx.compile/invalid-time-value (ex-type {:animation-duration 1})))
    (is (= :cljs.react.sx.compile/invalid-time-value (ex-type {:animation-delay 0})))
    (is (= :cljs.react.sx.compile/invalid-time-value (ex-type {:transitionDuration 2})))
    (is (= :cljs.react.sx.compile/invalid-time-value (ex-type {:transition-delay 0})))
    (is (= :cljs.react.sx.compile/invalid-time-value
           (kf-type {:from {:animation-duration 1}}))
        "inside a keyframe too — same emitter"))

  (testing "strings are the supported spelling"
    (is (= ".c{animation-duration:200ms}" (css1 {:animation-duration "200ms"})))
    (is (= ".c{animation-delay:0s}" (css1 {:animation-delay "0s"}))))

  (testing "the shorthand and the iteration count are untouched"
    (is (= ".c{animation:spin 1s linear}" (css1 {:animation "spin 1s linear"})))
    (is (= ".c{animation-iteration-count:3}"
           (css1 {:animation-iteration-count 3})))))

(deftest vars-rule-test
  (testing "renders a declaration block from a sorted var map"
    (is (= ":root{--cx-a:1px;--cx-b:2px}"
           (sxc/vars-rule ":root" (sorted-map "--cx-a" "1px" "--cx-b" "2px"))))))
