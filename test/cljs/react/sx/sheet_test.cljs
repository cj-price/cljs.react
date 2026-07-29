(ns cljs.react.sx.sheet-test
  (:require
   [cljs.test :refer [deftest testing is use-fixtures]]
   [clojure.string :as str]
   ["global-jsdom/register"]
   [cljs.react.sx.sheet :as sheet]
   [cljs.react.sx.theme :as theme]))

(use-fixtures :each {:before (fn [] (sheet/reset-sheet!))})

(def bpk (:cx/bp-key theme/default-theme-normalized))
(def bps (theme/breakpoint-values theme/default-theme-normalized))

(defn- node [attr] (.querySelector js/document (str "[" attr "]")))
(defn- node-text [attr] (some-> (node attr) .-textContent))
(defn- sheet-text [] (node-text "data-cljs-react-sx"))
(defn- theme-text [] (node-text "data-cljs-react-sx-theme"))

(defn- cls-for [sx] (sheet/class-for sx bpk bps))

(defn- css-rules
  "The generated sheet's rules as the CSSOM actually parsed them."
  []
  (some-> (node "data-cljs-react-sx") .-sheet .-cssRules array-seq))

(defn- parsed-decls
  "Property -> value for the base rule of `cls`, read back out of the CSSOM
  rather than out of the string the compiler produced. Every string assertion
  in these suites is a comparison against our own output; this is the only
  thing that asks a real parser whether the CSS is CSS."
  [cls]
  (let [^js rule (first (filter #(= (str "." cls) (.-selectorText %))
                                (css-rules)))
        ^js decl (some-> rule .-style)]
    (into {} (for [i (range (.-length decl))
                   :let [p (.item decl i)]]
               [p (.getPropertyValue decl p)]))))

;; ---------------------------------------------------------------------------

(deftest fnv1a-test
  (testing "known vectors"
    ;; A plain `*` here overflows 2^53 and silently loses precision — it would
    ;; produce "1anyso1" for "hello" instead of "m3bicr". These fixed values
    ;; are what make that regression visible.
    (is (= "ztntfp" (sheet/fnv1a "")))
    (is (= "1r9wi7g" (sheet/fnv1a "a")))
    (is (= "m3bicr" (sheet/fnv1a "hello")))
    (is (= "1ci18je" (sheet/fnv1a ".c{color:red}"))))

  (testing "hashes are stable and input-sensitive"
    (is (= (sheet/fnv1a "abc") (sheet/fnv1a "abc")))
    (is (not= (sheet/fnv1a "abc") (sheet/fnv1a "abd"))))

  (testing "output is always a base36 string with no sign"
    (doseq [s ["" "x" "a longer string with punctuation {};:"]]
      (is (re-matches #"[0-9a-z]+" (sheet/fnv1a s))))))

(deftest class-for-test
  (testing "returns a cx- prefixed class"
    (is (re-matches #"cx-[0-9a-z]+" (cls-for {:color "red"}))))

  (testing "equal sx maps share a class"
    (is (= (cls-for {:color "red"}) (cls-for {:color "red"}))))

  (testing "different sx maps get different classes"
    (is (not= (cls-for {:color "red"}) (cls-for {:color "blue"}))))

  (testing "sx maps that compile to identical CSS collapse to one class"
    ;; Hashing the generated CSS rather than the input map buys this.
    (is (= (cls-for {:color "red"}) (cls-for {"color" "red"}))))

  (testing "two sx maps with identical CSS inject the rule only once"
    ;; Insertion is deduped by intern-class! reporting first-interning, with no
    ;; second registry holding a copy of every rule string. Two DISTINCT sx
    ;; maps reaching the same content is the case that needs the dedupe.
    (sheet/reset-sheet!)
    (let [c (cls-for {:letter-spacing "0.41em"})]
      (cls-for {"letter-spacing" "0.41em"})
      (is (= 1 (count (re-seq (re-pattern (str "\\." c "\\{")) (sheet-text)))))))

  (testing "the class in the injected rule is the class returned to the caller"
    ;; Guards the hash-circularity trap: the class name derives from CSS text
    ;; that itself contains the class name, so it must be hashed in sentinel
    ;; form and substituted afterwards.
    (let [c (cls-for {:color "rebeccapurple"})]
      (is (str/includes? (sheet-text) (str "." c "{color:rebeccapurple}")))))

  (testing "nested and responsive rules all carry the class"
    (let [c (cls-for {:color "red" :&:hover {:color "blue"}
                      :width {:xs "100%" :md 300}})
          t (sheet-text)]
      (is (str/includes? t (str "." c "{color:red;width:100%}")))
      (is (str/includes? t (str "." c ":hover{color:blue}")))
      (is (str/includes? t (str "@media (min-width: 900px){." c "{width:300px}}")))))

  (testing "a rule is injected exactly once no matter how often it is requested"
    (let [c (cls-for {:outline "1px solid red"})
          n (count (re-seq (re-pattern (str "\\." c "\\{"))  (sheet-text)))]
      (dotimes [_ 5] (cls-for {:outline "1px solid red"}))
      (is (= 1 n))
      (is (= 1 (count (re-seq (re-pattern (str "\\." c "\\{")) (sheet-text))))))))

(deftest compile-caching-test
  (testing "an equal sx map compiles once"
    (cls-for {:p 1})
    (let [after-first @sheet/compile-count]
      (cls-for {:p 1})
      (cls-for {:p 1})
      (is (= after-first @sheet/compile-count))))

  (testing "a different sx map compiles again"
    (let [before @sheet/compile-count]
      (cls-for {:p 99})
      (is (= (inc before) @sheet/compile-count)))))

(deftest collision-test
  (testing "identical content dedupes to one class"
    (is (= "cx-deadbeef" (sheet/intern-class! "cx-" "A{}" "deadbeef")))
    (is (= "cx-deadbeef" (sheet/intern-class! "cx-" "A{}" "deadbeef"))))

  (testing "a genuine collision takes a probe suffix, losing neither style"
    ;; Injected hash code is the seam that lets this be tested without
    ;; monkeypatching the hash function.
    (let [a (sheet/intern-class! "cx-" "A{}" "cafe")
          b (sheet/intern-class! "cx-" "B{}" "cafe")
          c (sheet/intern-class! "cx-" "C{}" "cafe")]
      (is (= "cx-cafe" a))
      (is (= "cx-cafe-1" b))
      (is (= "cx-cafe-2" c))
      (is (= 3 (count (distinct [a b c]))))))

  (testing "after a collision, each content still maps back to its own class"
    (sheet/intern-class! "cx-" "X{}" "beef")
    (sheet/intern-class! "cx-" "Y{}" "beef")
    (is (= "cx-beef" (sheet/intern-class! "cx-" "X{}" "beef")))
    (is (= "cx-beef-1" (sheet/intern-class! "cx-" "Y{}" "beef"))))

  (testing "the prefix is part of the identity, not just of the name"
    ;; Buckets keyed on the hash alone left prefixes sharing a namespace: the
    ;; same text under two prefixes returned ONE name, and an unrelated
    ;; neighbour could push a probe suffix onto content that never collided.
    ;; The three prefixes are disjoint by construction today, which is exactly
    ;; the kind of invariant that holds until it doesn't.
    (let [a (sheet/intern-class! "cx-" "Z{}" "f00d")
          b (sheet/intern-class! "cx-kf-" "Z{}" "f00d")]
      (is (= "cx-f00d" a))
      (is (= "cx-kf-f00d" b) "a shared bucket would have returned cx-f00d"))))

(deftest theme-vars-test
  (let [light (theme/theme->css-vars theme/default-theme-normalized)
        dark  (theme/theme->css-vars
                (theme/deep-merge-theme theme/default-theme
                                        {:palette {:primary {:main "#000000"}}}))]
    (testing "vars are written into the dedicated theme node"
      (sheet/write-theme-vars! light)
      (is (str/starts-with? (theme-text) ":root{"))
      (is (str/includes? (theme-text) "--cx-palette-primary-main:#1976d2")))

    (testing "writing a new theme REPLACES rather than appends"
      (sheet/write-theme-vars! dark)
      (is (str/includes? (theme-text) "--cx-palette-primary-main:#000000"))
      (is (not (str/includes? (theme-text) "#1976d2"))))

    (testing "toggling back to a previously-used theme restores it"
      ;; The regression this exists for: with an append-only content-hashed
      ;; registry, re-registering `light` is a dedup hit while `dark`'s later
      ;; rule keeps winning at equal specificity — the page stays dark.
      (sheet/write-theme-vars! light)
      (is (str/includes? (theme-text) "--cx-palette-primary-main:#1976d2"))
      (is (not (str/includes? (theme-text) "#000000"))))

    (testing "rewriting the same vars is a no-op"
      (let [before (theme-text)]
        (sheet/write-theme-vars! light)
        (is (= before (theme-text)))))))

(deftest default-vars-test
  (testing "using a style with no provider still installs default vars"
    (cls-for {:color :palette.primary.main})
    (is (str/includes? (theme-text) "--cx-palette-primary-main:#1976d2")))

  (testing "default vars do not clobber vars a provider already wrote"
    (sheet/reset-sheet!)
    (sheet/write-theme-vars!
      (theme/theme->css-vars
        (theme/deep-merge-theme theme/default-theme {:spacing 4})))
    (cls-for {:p 1})
    (is (str/includes? (theme-text) "--cx-spacing:4px"))))

(deftest node-order-test
  (testing "baseline precedes theme precedes generated rules"
    (cls-for {:color "red"})
    (sheet/write-baseline! "a{color:inherit}")
    (let [kids (vec (array-seq (.. js/document -head -childNodes)))
          idx  (fn [attr] (.indexOf (to-array kids) (node attr)))]
      (is (< (idx "data-cljs-react-sx-baseline")
             (idx "data-cljs-react-sx-theme")))
      (is (< (idx "data-cljs-react-sx-theme")
             (idx "data-cljs-react-sx")))))

  (testing "nodes are created once, not per insertion"
    (dotimes [i 5] (cls-for {:z-index i}))
    (is (= 1 (.-length (.querySelectorAll js/document "[data-cljs-react-sx]"))))))

(deftest baseline-test
  (testing "the reset lands in the baseline node"
    (sheet/write-baseline! "button{cursor:pointer}")
    (is (= "button{cursor:pointer}" (node-text "data-cljs-react-sx-baseline"))))

  (testing "writing twice leaves one node and replaces its content"
    (sheet/write-baseline! "a{color:inherit}")
    (is (= 1 (.-length (.querySelectorAll js/document
                                          "[data-cljs-react-sx-baseline]"))))
    (is (= "a{color:inherit}" (node-text "data-cljs-react-sx-baseline")))))

(deftest static-style-test
  (testing "a StaticStyle derefs to its sx map"
    (is (= {:p 2} @(sheet/static-style {:p 2}))))

  (testing "identity, not structure, is the equivalence key"
    (let [a (sheet/static-style {:p 2})
          b (sheet/static-style {:p 2})]
      (is (= a a))
      (is (not= a b))
      ;; a fresh instance on hot reload SHOULD be a different identity, so the
      ;; style recompiles and consumers re-render
      (is (not (identical? a b)))))

  (testing "resolves to the same class as the equivalent inline map"
    (let [ss (sheet/static-style {:p 2})]
      (is (= (cls-for {:p 2}) (sheet/ensure-static! ss bpk bps)))))

  (testing "compiles once, then answers from its own cache"
    (let [ss (sheet/static-style {:m 7})]
      (sheet/ensure-static! ss bpk bps)
      (let [after @sheet/compile-count]
        (dotimes [_ 5] (sheet/ensure-static! ss bpk bps))
        (is (= after @sheet/compile-count))))))

(deftest composed-test
  (testing "parts deep-merge right-wins into a single class"
    (is (= (cls-for {:p 1 :color "blue"})
           (sheet/class-for-composed [{:p 1 :color "red"} {:color "blue"}]
                                     bpk bps))))

  (testing "nil parts are dropped"
    (is (= (sheet/class-for-composed [{:p 1}] bpk bps)
           (sheet/class-for-composed [nil {:p 1} nil] bpk bps))))

  (testing "order matters — merge order, not stylesheet insertion order"
    ;; Concatenating class names instead would make these two render
    ;; identically, since every rule has single-class specificity.
    (is (not= (sheet/class-for-composed [{:color "red"} {:color "blue"}] bpk bps)
              (sheet/class-for-composed [{:color "blue"} {:color "red"}] bpk bps))))

  (testing "nested maps combine rather than clobber"
    (is (= (cls-for {:&:hover {:color "red" :p 1}})
           (sheet/class-for-composed [{:&:hover {:color "red"}}
                                      {:&:hover {:p 1}}]
                                     bpk bps))))

  (testing "StaticStyle parts compose with inline maps"
    (let [base (sheet/static-style {:p 1 :color "red"})]
      (is (= (cls-for {:p 1 :color "blue"})
             (sheet/class-for-composed [base {:color "blue"}] bpk bps)))))

  (testing "composing a StaticStyle does not register its standalone rule"
    (sheet/reset-sheet!)
    (let [base (sheet/static-style {:padding-top "11px"})
          cls  (sheet/class-for-composed [base {:padding-bottom "13px"}]
                                         bpk bps)]
      (is (= 1 @sheet/compile-count)
          (str "the composition is one compile; resolving the part to build a"
               " cache key used to make it two"))
      (is (not (str/includes? (sheet-text) "{padding-top:11px}"))
          (str "the part's standalone class is never put on an element, so"
               " registering it leaked a permanent orphan rule"))
      (is (str/includes? (sheet-text)
                         (str "." cls "{padding-bottom:13px;padding-top:11px}")))))

  (testing "a warm composition skips the merge"
    (let [parts [{:p 3} {:m 4}]]
      (sheet/class-for-composed parts bpk bps)
      (let [after @sheet/compile-count]
        (dotimes [_ 5] (sheet/class-for-composed parts bpk bps))
        (is (= after @sheet/compile-count))))))

(deftest breakpoint-key-test
  (testing "the same sx under different breakpoints yields different CSS"
    (let [alt-theme (theme/deep-merge-theme theme/default-theme
                                            {:breakpoints {:md 1000}})
          sx        {:width {:xs 1 :md 2}}
          a         (sheet/class-for sx bpk bps)
          b         (sheet/class-for sx
                                     (:cx/bp-key alt-theme)
                                     (theme/breakpoint-values alt-theme))]
      (is (not= a b))
      (is (str/includes? (sheet-text) (str "@media (min-width: 900px){." a)))
      (is (str/includes? (sheet-text) (str "@media (min-width: 1000px){." b)))))

  (testing "a style with no responsive values still keys on bp-key"
    ;; Same CSS under both breakpoint sets collapses to one class, because the
    ;; content hash is over the CSS, not over [sx bp-key].
    (let [a (sheet/class-for {:color "goldenrod"} bpk bps)
          b (sheet/class-for {:color "goldenrod"} "0|1|2|3|4" [0 1 2 3 4])]
      (is (= a b)))))

(deftest css-survives-a-real-parser-test
  ;; The gap that let a whole family of bugs ship green: every other CSS
  ;; assertion in these suites compares our output against our own
  ;; expectation, so a declaration that is well-formed to us and garbage to a
  ;; parser (flex-basis:200, content:hi, box-shadow:0) reads as passing.
  (testing "declarations the compiler emits survive the CSSOM"
    (doseq [[sx props] [[{:flex-basis 200}        ["flex-basis"]]
                        [{:width 300 :height 20}  ["width" "height"]]
                        [{:content "say \"hi\""}  ["content"]]
                        [{:content :hi}           ["content"]]
                        [{:opacity 0.5}           ["opacity"]]
                        [{:border "1px solid red"} ["border-color"]]]]
      (let [decls (parsed-decls (cls-for sx))]
        (doseq [p props]
          (is (contains? decls p)
              (str (pr-str sx) " -> parsed " (pr-str decls)))))))

  (testing "var()-valued declarations reach the sheet"
    ;; jsdom's cssstyle DROPS `calc(var(--cx-spacing) * 1)` — valid in every
    ;; browser and the library's most common output — so those are asserted on
    ;; the rule text rather than on the parsed declaration block.
    (let [c (cls-for {:box-shadow 0})]
      (is (str/includes? (sheet-text)
                         (str "." c "{box-shadow:var(--cx-shadows-0)}")))))

  (testing "values a parser would discard never get that far"
    (doseq [sx [{:width [1 2]} {:display true} {:color "red;color:blue"}]]
      (is (some? (try (cls-for sx) nil (catch :default e e)))
          (pr-str sx)))))

(deftest insert-mode-test
  ;; goog.DEBUG is true under the :test build, so the shipping insertRule path
  ;; — the one that REJECTS anything that is not exactly one rule — would
  ;; otherwise be dead code in the suite.
  (doseq [mode [:text :cssom]]
    (testing (str "insertion mode " mode)
      (sheet/reset-sheet!)
      (sheet/set-insert-mode! mode)
      (let [c (cls-for {:color "red" :&:hover {:color "blue"}
                        :width {:xs 1 :md 2}})]
        ;; One CSSOM rule per emitted string in BOTH modes — the invariant
        ;; insertRule enforces and the text-node path does not.
        (is (= 3 (count (css-rules))))
        ;; :cssom keeps the rules in the CSSOM, so the node's text is empty;
        ;; the parsed declarations are what both modes have in common.
        (is (= "red" (get (parsed-decls c) "color"))))))
  (sheet/set-insert-mode! :text))

(deftest class-for-any-test
  (testing "every shape the public API accepts routes to the same class"
    (is (nil? (sheet/class-for-any nil bpk bps)))
    (is (= (cls-for {:p 1}) (sheet/class-for-any {:p 1} bpk bps)))
    (is (= (cls-for {:p 1}) (sheet/class-for-any [{:p 1}] bpk bps)))
    (is (= (cls-for {:p 1})
           (sheet/class-for-any (sheet/static-style {:p 1}) bpk bps))))

  (testing "anything else is rejected by name, not by internal destructuring"
    (doseq [sx ["p-1" 42 :p]]
      (let [e (try (sheet/class-for-any sx bpk bps) nil (catch :default e e))]
        (is (some? e) (pr-str sx))
        (is (= :cljs.react.sx.sheet/invalid-sx (:type (ex-data e))))))))

(deftest composition-parts-test
  (testing "an unmergeable part is rejected rather than reduced away"
    ;; A string part used to collapse the whole composition to the EMPTY class
    ;; — a silently unstyled element — and a nested vector died on `nth`.
    (doseq [parts [[{:p 1} "oops"] [{:p 1} [{:p 2}]] [{:p 1} 3]]]
      (let [e (try (sheet/class-for-composed parts bpk bps) nil
                   (catch :default e e))]
        (is (some? e) (pr-str parts))
        (is (= :cljs.react.sx.sheet/invalid-sx (:type (ex-data e))))))))

(deftest reset-sheet-test
  (testing "reset removes every node and clears every registry together"
    (cls-for {:color "red"})
    (sheet/write-baseline! "a{color:inherit}")
    (sheet/reset-sheet!)
    (is (nil? (node "data-cljs-react-sx")))
    (is (nil? (node "data-cljs-react-sx-baseline")))
    (is (nil? (node "data-cljs-react-sx-theme")))
    (is (zero? @sheet/compile-count))
    ;; and the registry no longer claims a class whose rules are gone
    (cls-for {:color "red"})
    (is (str/includes? (sheet-text) "color:red")))

  (testing "a StaticStyle re-injects its rules after a reset"
    ;; A StaticStyle memoizes its class on itself rather than in a registry,
    ;; so it needs a generation stamp — otherwise it keeps handing out a class
    ;; whose rules were just removed from the document.
    (let [ss (sheet/static-style {:color "tomato"})]
      (is (str/includes? (do (sheet/ensure-static! ss bpk bps) (sheet-text))
                         "color:tomato"))
      (sheet/reset-sheet!)
      (let [c (sheet/ensure-static! ss bpk bps)]
        (is (str/includes? (sheet-text) (str "." c "{color:tomato}")))))))

;; ---------------------------------------------------------------------------
;; Keyframes

(def ^:private fade {:from {:opacity 0} :to {:opacity 1}})

(defn- kf-rules
  "The `@keyframes` rules the CSSOM actually parsed, as {name -> keyTexts}."
  []
  ;; Identified structurally rather than by rule-type constant: a keyframes
  ;; rule is the only one carrying both a name and child rules.
  (into {} (for [^js r (css-rules)
                 :when (and (.-name r) (.-cssRules r))]
             [(.-name r) (mapv #(.-keyText ^js %) (array-seq (.-cssRules r)))])))

(deftest keyframes-name-test
  (testing "returns a cx-kf- prefixed name and injects one rule"
    (let [nm (sheet/keyframes-name! "0%{opacity:0}100%{opacity:1}")]
      (is (re-matches #"cx-kf-[0-9a-z]+" nm))
      (is (str/includes? (sheet-text) (str "@keyframes " nm "{")))))

  (testing "identical bodies share one name and inject one rule"
    (sheet/reset-sheet!)
    (let [a (sheet/keyframes-name! "0%{opacity:0}")
          b (sheet/keyframes-name! "0%{opacity:0}")]
      (is (= a b))
      (is (= 1 (count (re-seq #"@keyframes " (sheet-text)))))))

  (testing "distinct bodies get distinct names"
    (is (not= (sheet/keyframes-name! "0%{opacity:0}")
              (sheet/keyframes-name! "0%{opacity:1}"))))

  (testing "a genuine collision takes a probe suffix"
    (let [a (sheet/keyframes-name! "0%{left:0}" "abcd")
          b (sheet/keyframes-name! "0%{left:9px}" "abcd")]
      (is (= "cx-kf-abcd" a))
      (is (= "cx-kf-abcd-1" b))
      (is (= 2 (count (select-keys (kf-rules) [a b])))))))

(deftest keyframes-insert-mode-test
  ;; The load-bearing one. insertRule parses and accepts EXACTLY one rule, so
  ;; a design emitting the frames as separate rules — or emitting two
  ;; @keyframes in one call — passes the dev text-node path and dies here.
  (doseq [mode [:text :cssom]]
    (testing (str "insertion mode " mode)
      (sheet/reset-sheet!)
      (sheet/set-insert-mode! mode)
      (let [nm (sheet/keyframes-name! "0%{opacity:0}50%,60%{opacity:0.5}100%{opacity:1}")
            ks (get (kf-rules) nm)]
        (is (some? ks) "the CSSOM parsed it as a keyframes rule")
        (is (= 3 (count ks)) "one CSSOM keyframe per emitted frame")
        (is (= "0%" (first ks))))))
  (sheet/set-insert-mode! :text))

(deftest keyframes-value-test
  (testing "a Keyframes under :animation-name registers both rules"
    (let [kf  (sheet/keyframes fade)
          c   (cls-for {:animation-name kf :animation-duration "1s"})
          nm  (get (parsed-decls c) "animation-name")]
      (is (re-matches #"cx-kf-[0-9a-z]+" nm))
      (is (contains? (kf-rules) nm)
          "the class references keyframes that are actually in the document")))

  (testing "the frames map is what it derefs to"
    (is (= fade @(sheet/keyframes fade))))

  (testing "it resolves inside nested and responsive positions"
    (let [kf (sheet/keyframes {:from {:left 0} :to {:left "9px"}})
          c  (cls-for {:&:hover {:animation-name kf}
                       :animationName {:xs kf}})
          t  (sheet-text)
          nm (sheet/ensure-keyframes! kf)]
      (is (str/includes? t (str "." c ":hover{animation-name:" nm "}")))
      (is (str/includes? t (str "." c "{animation-name:" nm "}")))))

  (testing "under any other property it throws rather than emitting a dud"
    ;; `color:cx-kf-abc` is a value the parser silently drops — the failure
    ;; shape this library rejects loudly everywhere else.
    (let [kf (sheet/keyframes fade)
          e  (try (cls-for {:color kf}) nil (catch :default e e))]
      (is (some? e))
      (is (= :cljs.react.sx.sheet/keyframes-not-animation-name
             (:type (ex-data e))))))

  (testing "a malformed frames map fails at definition, not at first render"
    (is (thrown? js/Error (sheet/keyframes {})))))

(deftest keyframes-reset-test
  ;; The design decision made executable. A NAME captured into an sx map would
  ;; survive this reset while the rule it names would not: the class recompiles
  ;; correctly, re-emits the stale name, and nothing re-inserts the keyframes.
  ;; Resolving the object on every compile is what repairs it.
  (testing "after a reset the class and its keyframes are both back, and agree"
    (let [kf (sheet/keyframes fade)]
      (cls-for {:animation-name kf})
      (sheet/reset-sheet!)
      (let [c  (cls-for {:animation-name kf})
            nm (get (parsed-decls c) "animation-name")]
        (is (str/includes? (sheet-text) (str "@keyframes " nm "{")))
        (is (contains? (kf-rules) nm))))))
