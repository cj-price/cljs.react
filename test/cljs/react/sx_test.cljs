(ns cljs.react.sx-test
  (:require
   [cljs.test :refer [deftest testing is use-fixtures]]
   [clojure.string :as str]
   ["react" :as react]
   ["global-jsdom/register"]
   ["@testing-library/react" :refer [render renderHook act cleanup]]
   [cljs.react.core :refer [Element]]
   [cljs.react.sx :as sx :refer-macros [defstyle]]
   [cljs.react.sx.sheet :as sheet]))

(use-fixtures :each {:before (fn [] (sheet/reset-sheet!))
                     :after  (fn [] (cleanup))})

(defn- node [attr] (.querySelector js/document (str "[" attr "]")))
(defn- node-text [attr] (some-> (node attr) .-textContent))
(defn- sheet-text [] (or (node-text "data-cljs-react-sx") ""))
(defn- theme-text [] (or (node-text "data-cljs-react-sx-theme") ""))

(defn- theme-wrapper
  ([] (theme-wrapper {}))
  ([props]
   (fn [^js p] (sx/ThemeProvider props (.-children p)))))

;; ---------------------------------------------------------------------------

(deftest use-sx-test
  (testing "returns a class name and injects the rule"
    (let [^js r (renderHook #(sx/use-sx {:color "red"}))
          cls   (.. r -result -current)]
      (is (re-matches #"cx-[0-9a-z]+" cls))
      (is (str/includes? (sheet-text) (str "." cls "{color:red}")))))

  (testing "works with no ThemeProvider mounted"
    (let [^js r (renderHook #(sx/use-sx {:p 2}))]
      (is (some? (.. r -result -current)))
      (is (str/includes? (theme-text) "--cx-spacing:8px"))))

  (testing "nil sx yields nil rather than an empty class"
    (let [^js r (renderHook #(sx/use-sx nil))]
      (is (nil? (.. r -result -current)))))

  (testing "two components with equal sx maps share one class"
    (let [^js a (renderHook #(sx/use-sx {:m 1}))
          ^js b (renderHook #(sx/use-sx {:m 1}))]
      (is (= (.. a -result -current) (.. b -result -current)))))

  (testing "a vector composes its parts into a single class"
    (let [^js r (renderHook #(sx/use-sx [{:p 1 :color "red"} {:color "blue"}]))
          ^js s (renderHook #(sx/use-sx {:p 1 :color "blue"}))]
      (is (= (.. s -result -current) (.. r -result -current)))))

  (testing "nils inside a composition vector are dropped"
    (let [active? false
          ^js r (renderHook #(sx/use-sx [{:p 1} (when active? {:color "red"})]))
          ^js s (renderHook #(sx/use-sx {:p 1}))]
      (is (= (.. s -result -current) (.. r -result -current)))))

  (testing "re-rendering with an equal literal does not recompile"
    (let [^js r (renderHook (fn [] (sx/use-sx {:padding-left 3})))
          after @sheet/compile-count]
      (dotimes [_ 5] (.rerender r))
      (is (= after @sheet/compile-count))
      (is (= 1 (count (distinct [(.. r -result -current)])))))))

(deftest use-theme-test
  (testing "outside a provider it returns default-theme and does NOT throw"
    ;; Deliberately unlike use-db's ::no-provider: there IS a sensible default
    ;; theme, so opting in to sx must not mean restructuring your root.
    (let [^js r (renderHook #(sx/use-theme))
          t     (.. r -result -current)]
      (is (= 8 (:spacing t)))
      (is (= "#1976d2" (get-in t [:palette :primary :main])))))

  (testing "inside a provider it returns the merged theme"
    ;; Every render in a deftest stays mounted until the :after fixture, so a
    ;; block that mounts a ROOT provider unmounts the previous one first —
    ;; otherwise the suite trips the library's own multi-root warning and
    ;; trains the reader to ignore it.
    (cleanup)
    (let [^js r (renderHook #(sx/use-theme)
                            #js {:wrapper (theme-wrapper
                                            {:theme {:spacing 4
                                                     :palette {:primary {:main "#abcdef"}}}})})
          t     (.. r -result -current)]
      (is (= 4 (:spacing t)))
      (is (= "#abcdef" (get-in t [:palette :primary :main])))
      (is (= "#42a5f5" (get-in t [:palette :primary :light]))
          "unspecified branches survive the merge")))

  (testing "use-theme-class is nil at the root"
    (cleanup)
    (let [^js r (renderHook #(sx/use-theme-class)
                            #js {:wrapper (theme-wrapper {:theme {:spacing 4}})})]
      (is (nil? (.. r -result -current))))))

(deftest theme-var-test
  (testing "vector paths"
    (is (= "var(--cx-palette-primary-main)"
           (sx/theme-var [:palette :primary :main]))))
  (testing "dotted keyword paths"
    (is (= "var(--cx-palette-primary-main)"
           (sx/theme-var :palette.primary.main))))
  (testing "dotted string paths"
    ;; A string used to be treated as a seq of characters, yielding
    ;; var(--cx-p-a-l-e-t-t-e-…).
    (is (= "var(--cx-palette-primary-main)"
           (sx/theme-var "palette.primary.main"))))
  (testing "camelCase segments kebab-case"
    (is (= "var(--cx-shape-border-radius)"
           (sx/theme-var [:shape :borderRadius]))))
  (testing "numeric segments work"
    (is (= "var(--cx-shadows-2)" (sx/theme-var [:shadows 2]))))
  (testing "anything else is rejected by name"
    (let [e (try (sx/theme-var 42) nil (catch :default e e))]
      (is (some? e))
      (is (= :cljs.react.sx/invalid-theme-path (:type (ex-data e)))))))

(deftest style-fn-test
  (testing "compiles outside React and matches the hook's class"
    (let [cls   (sx/style {:color "seagreen"})
          ^js r (renderHook #(sx/use-sx {:color "seagreen"}))]
      (is (= cls (.. r -result -current)))))

  (testing "accepts every shape use-sx does, and rejects the same ones"
    (is (nil? (sx/style nil)))
    (is (= (sx/style {:p 1}) (sx/style [{:p 1}])))
    (is (= (sx/style {:p 1}) (sx/style [nil {:p 1}])))
    (is (= (sx/style {:p 1}) (sx/style (sheet/static-style {:p 1}))))
    (let [e (try (sx/style "p-1") nil (catch :default e e))]
      (is (= :cljs.react.sx.sheet/invalid-sx (:type (ex-data e))))))

  (testing "a hand-written theme's breakpoints reach the cache key"
    ;; `:cx/bp-key` is only attached by deep-merge-theme, so the hand-written
    ;; map the docstring invites used to land in the single nil bucket —
    ;; every such theme sharing one class and the second one's media query
    ;; never being emitted.
    (let [a (sx/style {:width {:md 10}} {:breakpoints {:md 700}})
          b (sx/style {:width {:md 10}} {:breakpoints {:md 1100}})]
      (is (not= a b))
      (is (str/includes? (sheet-text) (str "@media (min-width: 700px){." a)))
      (is (str/includes? (sheet-text) (str "@media (min-width: 1100px){." b))))))

(deftest root-provider-renders-no-dom-test
  (testing "the root provider adds no wrapper element"
    (cleanup)
    (let [^js r (render (sx/ThemeProvider {:theme {:spacing 4}}
                          (Element {:tag "span" :id "kid"} "hi")))
          ^js c (.-container r)]
      (is (= 1 (.. c -childNodes -length)))
      (is (= "SPAN" (.. c -firstChild -tagName)))
      (is (= "kid" (.. c -firstChild -id)))))

  (testing "the root provider writes :root custom properties"
    (cleanup)
    (render (sx/ThemeProvider {:theme {:spacing 4}}
              (Element {:tag "span"} "hi")))
    (is (str/starts-with? (theme-text) ":root{"))
    (is (str/includes? (theme-text) "--cx-spacing:4px"))))

(deftest nested-provider-test
  (testing "a nested provider emits a scoped class on a display:contents wrapper"
    (cleanup)
    (let [^js r (render
                  (sx/ThemeProvider {}
                    ;; A dark scope carries its own surface and text: :mode on
                    ;; its own only drives color-scheme, and the library
                    ;; dev-warns when it is flipped without them.
                    (sx/ThemeProvider {:theme {:palette
                                               {:mode :dark
                                                :background {:default "#121212"}
                                                :text {:primary "#ffffff"}}}}
                      (Element {:tag "span" :id "kid"} "hi"))))
          ^js c (.-container r)
          ^js w (.-firstChild c)]
      (is (= "DIV" (.-tagName w)))
      (is (re-find #"cx-theme-[0-9a-z]+" (.-className w)))
      (is (= "contents" (.. w -style -display)))
      (is (= "kid" (.. w -firstChild -id)))))

  (testing "the scoped rule carries the overridden vars, not :root"
    (cleanup)
    (let [^js r (render
                  (sx/ThemeProvider {}
                    (sx/ThemeProvider {:theme {:spacing 3}}
                      (Element {:tag "span"} "hi"))))
          cls   (.. r -container -firstChild -className)
          scoped (str/trim (first (str/split cls #" ")))]
      (is (str/includes? (sheet-text) (str "." scoped "{")))
      (is (str/includes? (sheet-text) "--cx-spacing:3px"))
      (is (not (str/includes? (theme-text) "--cx-spacing:3px")))))

  (testing "two nested providers with = themes share one class"
    (cleanup)
    (let [^js r (render
                  (sx/ThemeProvider {}
                    (Element {:tag "div"}
                      (sx/ThemeProvider {:theme {:spacing 5}}
                        (Element {:tag "span"} "a"))
                      (sx/ThemeProvider {:theme {:spacing 5}}
                        (Element {:tag "span"} "b")))))
          ^js kids (.. r -container -firstChild -childNodes)]
      (is (= (.-className (aget kids 0)) (.-className (aget kids 1))))))

  (testing ":as :none renders no wrapper — the class is yours to place"
    (cleanup)
    (let [^js r (render
                  (sx/ThemeProvider {}
                    (sx/ThemeProvider {:theme {:spacing 3} :as :none}
                      (Element {:tag "span" :id "kid"} "hi"))))]
      (is (= "SPAN" (.. r -container -firstChild -tagName)))
      (is (= "kid" (.. r -container -firstChild -id)))))

  (testing ":as picks the wrapper tag, as a keyword or a string"
    ;; `:none` was compared as a raw KEYWORD while every other legal value
    ;; arrived as a string tag, so `:as :section` crashed on an object tag and
    ;; `:as \"none\"` rendered a literal <none> element.
    (doseq [as [:section "section"]]
      (cleanup)
      (let [^js r (render
                    (sx/ThemeProvider {}
                      (sx/ThemeProvider {:theme {:spacing 3} :as as}
                        (Element {:tag "span"} "hi"))))]
        (is (= "SECTION" (.. r -container -firstChild -tagName)) (pr-str as))))

    (doseq [as [:none "none"]]
      (cleanup)
      (let [^js r (render
                    (sx/ThemeProvider {}
                      (sx/ThemeProvider {:theme {:spacing 3} :as as}
                        (Element {:tag "span"} "hi"))))]
        (is (= "SPAN" (.. r -container -firstChild -tagName)) (pr-str as)))))

  (testing ":scoped? forces the scoped path at the root"
    (cleanup)
    (let [^js r (render (sx/ThemeProvider {:theme {:spacing 3} :scoped? true}
                          (Element {:tag "span"} "hi")))
          ^js w (.. r -container -firstChild)]
      (is (= "DIV" (.-tagName w)))
      (is (re-find #"cx-theme-" (.-className w)))))

  (testing "use-theme-class returns the scope class under a nested provider"
    (cleanup)
    (let [^js r (renderHook
                  #(sx/use-theme-class)
                  #js {:wrapper (fn [^js p]
                                  (sx/ThemeProvider {}
                                    (sx/ThemeProvider {:theme {:spacing 6}}
                                      (.-children p))))})]
      (is (re-find #"cx-theme-" (.. r -result -current))))))

(deftest theme-toggle-test
  (testing "toggling root theme A -> B -> A restores A's variables"
    (cleanup)
    ;; The regression guard for R1: with the theme vars in the append-only
    ;; content-hashed registry, returning to A is a dedup hit while B's later
    ;; rule still wins at equal specificity, and the page stays on B.
    (let [mode  (atom :a)
          set!* (atom nil)
          Root  (fn [_]
                  (let [s (react/useState 0)]
                    (reset! set!* (aget s 1))
                    (sx/ThemeProvider
                      {:theme (if (= :a @mode)
                                {:palette {:primary {:main "#aaaaaa"}}}
                                {:palette {:primary {:main "#bbbbbb"}}})}
                      (Element {:tag "span"} "hi"))))]
      (render (react/createElement Root nil))
      (is (str/includes? (theme-text) "#aaaaaa"))

      (reset! mode :b)
      (act (fn [] (@set!* 1)))
      (is (str/includes? (theme-text) "#bbbbbb"))
      (is (not (str/includes? (theme-text) "#aaaaaa")))

      (reset! mode :a)
      (act (fn [] (@set!* 2)))
      (is (str/includes? (theme-text) "#aaaaaa"))
      (is (not (str/includes? (theme-text) "#bbbbbb")))))

  (testing "a theme swap regenerates no CSS"
    (cleanup)
    ;; The CSS-variable thesis, demonstrated rather than asserted: generated
    ;; rules reference var(--cx-…), so only the :root block changes.
    (let [mode  (atom :a)
          set!* (atom nil)
          Root  (fn [_]
                  (let [s (react/useState 0)]
                    (reset! set!* (aget s 1))
                    (sx/ThemeProvider
                      {:theme (if (= :a @mode)
                                {:palette {:primary {:main "#aaaaaa"}}}
                                {:palette {:primary {:main "#bbbbbb"}}})}
                      (Element {:tag "span"
                                :className (sx/use-sx {:color :palette.primary.main})}
                        "hi"))))]
      (render (react/createElement Root nil))
      (let [after @sheet/compile-count
            css   (sheet-text)]
        (reset! mode :b)
        (act (fn [] (@set!* 1)))
        (is (= after @sheet/compile-count) "no recompile on theme change")
        (is (= css (sheet-text)) "no new rules on theme change")))))

(deftest strict-mode-test
  (testing "double-invoked renders inject the rule exactly once"
    ;; Without this the render-phase-injection argument is unverified: React 19
    ;; dev double-invokes component bodies and useMemo factories.
    (let [Comp (fn [_]
                 (Element {:tag "div"
                           :className (sx/use-sx {:letter-spacing "0.31em"})}
                   "hi"))]
      (render (react/createElement react/StrictMode nil
                                   (react/createElement Comp nil)))
      (is (= 1 (count (re-seq #"letter-spacing:0\.31em" (sheet-text)))))))

  (testing "the class is the same under StrictMode as without it"
    (let [seen (atom [])
          Comp (fn [_]
                 (let [c (sx/use-sx {:letter-spacing "0.32em"})]
                   (swap! seen conj c)
                   (Element {:tag "div" :className c} "hi")))]
      (render (react/createElement react/StrictMode nil
                                   (react/createElement Comp nil)))
      (is (= 1 (count (distinct @seen))))))

  (testing "a StrictMode-mounted provider writes one theme node"
    (cleanup)
    (render (react/createElement
              react/StrictMode nil
              (sx/ThemeProvider {:theme {:spacing 9}}
                (Element {:tag "span"} "hi"))))
    (is (= 1 (.-length (.querySelectorAll js/document
                                          "[data-cljs-react-sx-theme]"))))
    (is (str/includes? (theme-text) "--cx-spacing:9px"))))

(deftest baseline-provider-test
  (testing "renders no DOM node of its own"
    (let [^js r (render (sx/BaselineProvider {}
                          (Element {:tag "span" :id "kid"} "hi")))]
      (is (= 1 (.. r -container -childNodes -length)))
      (is (= "SPAN" (.. r -container -firstChild -tagName)))))

  (testing "writes the reset into the baseline node"
    (render (sx/BaselineProvider {} (Element {:tag "span"} "hi")))
    (is (str/includes? (node-text "data-cljs-react-sx-baseline")
                       "box-sizing:border-box")))

  (testing ":body? is opt-in"
    (render (sx/BaselineProvider {} (Element {:tag "span"} "hi")))
    (is (not (str/includes? (node-text "data-cljs-react-sx-baseline") "body{")))
    (cleanup)
    (render (sx/BaselineProvider {:body? true} (Element {:tag "span"} "hi")))
    (is (str/includes? (node-text "data-cljs-react-sx-baseline") "body{"))
    (is (str/includes? (node-text "data-cljs-react-sx-baseline")
                       "var(--cx-palette-background-default)")))

  (testing ":enable-color-scheme? tracks the palette mode through a var"
    (render (sx/BaselineProvider {:enable-color-scheme? true}
              (Element {:tag "span"} "hi")))
    (is (str/includes? (node-text "data-cljs-react-sx-baseline")
                       "color-scheme:var(--cx-palette-mode)")))

  (testing "mounting twice leaves one baseline node, ordered before the sheet"
    (render (react/createElement
              "div" nil
              (sx/BaselineProvider {} (Element {:tag "span"} "a"))
              (sx/BaselineProvider {} (Element {:tag "span"} "b"))))
    (renderHook #(sx/use-sx {:color "red"}))
    (is (= 1 (.-length (.querySelectorAll js/document
                                          "[data-cljs-react-sx-baseline]"))))
    (let [kids (to-array (array-seq (.. js/document -head -childNodes)))]
      (is (< (.indexOf kids (node "data-cljs-react-sx-baseline"))
             (.indexOf kids (node "data-cljs-react-sx")))))))

(defstyle card-style {:p 2 :border-radius :shape.border-radius})

(deftest defstyle-test
  (testing "a defstyle var resolves to the same class as its literal map"
    (let [^js a (renderHook #(sx/use-sx card-style))
          ^js b (renderHook #(sx/use-sx {:p 2 :border-radius :shape.border-radius}))]
      (is (= (.. b -result -current) (.. a -result -current)))))

  (testing "it works with no provider mounted, compiling lazily on first use"
    (sheet/reset-sheet!)
    (is (zero? @sheet/compile-count))
    (let [^js r (renderHook #(sx/use-sx card-style))]
      (is (some? (.. r -result -current)))
      (is (= 1 @sheet/compile-count))))

  (testing "reuse does not recompile"
    (let [after @sheet/compile-count]
      (dotimes [_ 5] (renderHook #(sx/use-sx card-style)))
      (is (= after @sheet/compile-count))))

  (testing "the var derefs to its sx map"
    (is (= {:p 2 :border-radius :shape.border-radius} @card-style)))

  (testing "composes with inline overrides"
    (let [^js a (renderHook #(sx/use-sx [card-style {:p 4}]))
          ^js b (renderHook #(sx/use-sx {:p 4 :border-radius :shape.border-radius}))]
      (is (= (.. b -result -current) (.. a -result -current))))))

;; ---------------------------------------------------------------------------

(deftest keyframes-test
  (testing "a component animates: both rules land and the class points at them"
    (let [spin (sx/keyframes {:from {:transform "rotate(0deg)"}
                              :to   {:transform "rotate(360deg)"}})
          ^js r (renderHook #(sx/use-sx {:animation-name spin
                                         :animation-duration "900ms"}))
          cls   (.. r -result -current)
          nm    (sx/keyframes-name spin)
          t     (sheet-text)]
      (is (str/includes? t (str "@keyframes " nm
                                "{0%{transform:rotate(0deg)}"
                                "100%{transform:rotate(360deg)}}")))
      (is (str/includes? t (str "." cls "{animation-duration:900ms;"
                                "animation-name:" nm "}")))))

  (testing "the keyframes rule is injected exactly once under StrictMode"
    (cleanup)
    (let [pulse (sx/keyframes {:from {:opacity 0.4} :to {:opacity 1}})
          Comp  (fn [_]
                  (Element {:tag "div"
                            :className (sx/use-sx {:animation-name pulse
                                                   :animation-duration "1s"})}
                    "hi"))]
      (render (react/createElement react/StrictMode nil
                                   (react/createElement Comp nil)))
      (is (= 1 (count (re-seq (re-pattern (str "@keyframes "
                                               (sx/keyframes-name pulse) "\\{"))
                              (sheet-text)))))))

  (testing "two definitions with identical frames share one rule"
    (let [a (sx/keyframes {:from {:opacity 0} :to {:opacity 1}})
          b (sx/keyframes {"0%" {:opacity 0} "100%" {:opacity 1}})]
      (is (= (sx/keyframes-name a) (sx/keyframes-name b)))))

  (testing "deref gives back the frames map, not the registered name"
    ;; Symmetric with defstyle: a deref is a read. Registration is a named,
    ;; visibly effectful call.
    (let [frames {:from {:opacity 0} :to {:opacity 1}}]
      (is (= frames @(sx/keyframes frames)))))

  (testing "a theme swap still regenerates no CSS"
    (cleanup)
    (sheet/reset-sheet!)
    (let [fade  (sx/keyframes {:from {:opacity 0} :to {:opacity 1}})
          mode  (atom :a)
          set!* (atom nil)
          Root  (fn [_]
                  (let [s (react/useState 0)]
                    (reset! set!* (aget s 1))
                    (sx/ThemeProvider
                      {:theme (if (= :a @mode)
                                {:palette {:primary {:main "#aaaaaa"}}}
                                {:palette {:primary {:main "#bbbbbb"}}})}
                      (Element {:tag "span"
                                :className (sx/use-sx
                                             {:color :palette.primary.main
                                              :animation-name fade
                                              :animation-duration "1s"})}
                        "hi"))))]
      (render (react/createElement Root nil))
      (let [before   (sheet-text)
            compiles @sheet/compile-count]
        (reset! mode :b)
        (act (fn [] (@set!* 1)))
        (is (= before (sheet-text)))
        (is (= compiles @sheet/compile-count)))))

  (testing "a frames map is validated at definition, not at first render"
    (is (thrown? js/Error (sx/keyframes {:from {:opacity 0} "0%" {:opacity 1}})))
    (is (thrown? js/Error (sx/keyframes {:from {}}))))

  (testing "a Keyframes anywhere but :animation-name is refused"
    (let [kf (sx/keyframes {:from {:opacity 0} :to {:opacity 1}})]
      (is (thrown? js/Error (sx/style {:color kf}))))))
