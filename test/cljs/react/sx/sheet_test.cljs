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
    (is (= "cx-beef-1" (sheet/intern-class! "cx-" "Y{}" "beef")))))

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
