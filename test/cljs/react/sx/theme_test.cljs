(ns cljs.react.sx.theme-test
  (:require
   [cljs.test :refer [deftest testing is]]
   [clojure.string :as str]
   [cljs.react.sx.theme :as theme]))

(deftest kebab-test
  (testing "camelCase becomes kebab-case"
    (is (= "contrast-text" (theme/kebab :contrastText)))
    (is (= "border-radius" (theme/kebab :borderRadius)))
    (is (= "background-color" (theme/kebab :backgroundColor))))

  (testing "already-kebab names pass through"
    (is (= "border-radius" (theme/kebab :border-radius)))
    (is (= "main" (theme/kebab :main))))

  (testing "strings work too"
    (is (= "font-size" (theme/kebab "fontSize")))))

(deftest unitless-name-test
  (testing "exact matches"
    (is (theme/unitless-name? "line-height"))
    (is (theme/unitless-name? "z-index"))
    (is (theme/unitless-name? "opacity"))
    (is (theme/unitless-name? "font-weight")))

  (testing "prefix matches — theme keys extend a unitless property"
    (is (theme/unitless-name? "font-weight-bold"))
    (is (theme/unitless-name? "flex-grow")))

  (testing "non-unitless properties"
    (is (not (theme/unitless-name? "font-size")))
    (is (not (theme/unitless-name? "border-radius")))
    (is (not (theme/unitless-name? "spacing")))
    (is (not (theme/unitless-name? "width")))))

(deftest unitless-prop-test
  (testing "exact matches, same as unitless-name?"
    (is (theme/unitless-prop? "flex"))
    (is (theme/unitless-prop? "grid-row"))
    (is (theme/unitless-prop? "line-height")))

  (testing "a longhand that merely STARTS with a unitless property is not one"
    ;; The whole point of the split: `flex-basis:200` (no unit) is discarded by
    ;; a real parser, while `font-weight-bold` as a THEME PATH must stay raw.
    (is (not (theme/unitless-prop? "flex-basis")))
    (is (not (theme/unitless-prop? "grid-row-gap")))
    (is (theme/unitless-name? "flex-basis"))))

(deftest var-name-test
  (testing "one encoding of a theme path as a custom property"
    (is (= "--cx-palette-primary-main"
           (theme/var-name [:palette :primary :main])))
    (is (= "--cx-shape-border-radius" (theme/var-name [:shape :borderRadius]))))

  (testing "non-named segments (a vector index) render via str"
    (is (= "--cx-shadows-2" (theme/var-name [:shadows 2])))
    (is (= "var(--cx-shadows-2)" (theme/var-ref [:shadows 2]))))

  (testing "strings work as segments, so the flattener can share it"
    (is (= "--cx-z-index-app-bar" (theme/var-name ["z-index" "app-bar"]))))

  (testing "a segment that cannot form a property name is rejected here"
    (doseq [segs [["a" "b}victim{color:red"] ["a" "b;c"] ["a" "b<style>"]]]
      (let [e (try (theme/var-name segs) nil (catch :default e e))]
        (is (some? e)
            (str (pr-str segs) " — validation lives in var-name rather than in"
                 " the flattener so all three callers inherit it; the"
                 " compiler's token path used to splice this into a rule"))
        (is (= :cljs.react.sx.theme/invalid-var-name (:type (ex-data e))))))))

(deftest unsafe-theme-values-test
  (testing "a value that could escape the :root block is rejected"
    ;; The :root block is written with .textContent, so — unlike a class rule
    ;; under insertRule — nothing downstream re-parses and rejects a payload.
    (doseq [v ["red}#victim{outline-style:dotted"
               "red;outline:1px solid red"
               "red<script>"
               "red/*x*/"
               "'unbalanced"]]
      (let [e (try (theme/theme->css-vars
                     (theme/deep-merge-theme theme/default-theme
                                             {:palette {:primary {:main v}}}))
                   nil
                   (catch :default e e))]
        (is (some? e) (pr-str v))
        (is (= :cljs.react.sx.theme/unsafe-theme-value (:type (ex-data e)))))))

  (testing "delimiters are checked in ORDER, not by count"
    ;; `)(` has one of each, so the old total-comparison predicate passed it.
    ;; The stray `)` invalidates its own declaration and the `(` then swallows
    ;; every LATER declaration in the :root block to EOF — verified in a real
    ;; engine, --cx-spacing and the text colours all resolved to "".
    (doseq [v [")(" "a)b(c" "][" "([)]" "[(])"]]
      (let [e (try (theme/theme->css-vars
                     (theme/deep-merge-theme theme/default-theme
                                             {:palette {:primary {:main v}}}))
                   nil
                   (catch :default e e))]
        (is (some? e) (pr-str v))
        (is (= :cljs.react.sx.theme/unsafe-theme-value (:type (ex-data e)))))))

  (testing "a raw newline inside a quoted run is rejected"
    (doseq [v ["\"a\nb\"" "'a\nb'" "\"a\rb\""]]
      (let [e (try (theme/theme->css-vars
                     (theme/deep-merge-theme theme/default-theme
                                             {:palette {:primary {:main v}}}))
                   nil
                   (catch :default e e))]
        (is (some? e) (pr-str v))
        (is (= :cljs.react.sx.theme/unsafe-theme-value (:type (ex-data e)))))))

  (testing "the default theme's own values pass — parens and quotes balance"
    (is (map? (theme/theme->css-vars theme/default-theme-normalized))))

  (testing "a theme key that cannot form a custom property name is rejected"
    (let [e (try (theme/theme->css-vars
                   (theme/deep-merge-theme theme/default-theme
                                           {"colour: red; --x" {:a 1}}))
                 nil
                 (catch :default e e))]
      (is (some? e))
      (is (= :cljs.react.sx.theme/invalid-var-name (:type (ex-data e)))))))

(deftest theme->css-vars-test
  (let [vars (theme/theme->css-vars theme/default-theme-normalized)]
    (testing "nested palette paths flatten to kebab-cased custom properties"
      (is (= "#1976d2" (get vars "--cx-palette-primary-main")))
      (is (= "#ffffff" (get vars "--cx-palette-primary-contrast-text")))
      (is (= "#ffffff" (get vars "--cx-palette-background-default"))))

    (testing "spacing carries a unit — it is consumed inside calc()"
      (is (= "8px" (get vars "--cx-spacing"))))

    (testing "unitless theme values do not gain px"
      (is (= "1.5" (get vars "--cx-typography-line-height")))
      (is (= "700" (get vars "--cx-typography-font-weight-bold")))
      (is (= "1100" (get vars "--cx-z-index-app-bar"))))

    (testing "other numbers gain px"
      (is (= "4px" (get vars "--cx-shape-border-radius"))))

    (testing "string values pass through verbatim"
      ;; The body scale is a string on purpose: BaselineProvider {:body? true}
      ;; writes it onto `body`, and a px value there overrides the reader's
      ;; own browser font-size default.
      (is (= "1rem" (get vars "--cx-typography-font-size"))))

    (testing "vectors index by position"
      (is (= "none" (get vars "--cx-shadows-0")))
      (is (= "0 2px 6px rgba(0, 0, 0, 0.14)" (get vars "--cx-shadows-2"))))

    (testing "keyword values render as their name"
      (is (= "light" (get vars "--cx-palette-mode"))))

    (testing "every palette pairing clears 4.5:1 for body text"
      ;; White on #ed6c02 is 3.11:1 and on #0288d1 3.86:1 — both fail, and the
      ;; theme is what declares the pairing, so the library has to own it.
      (is (= "rgba(0, 0, 0, 0.87)"
             (get vars "--cx-palette-warning-contrast-text")))
      (is (= "#0277bd" (get vars "--cx-palette-info-main")))
      (is (= "#ffffff" (get vars "--cx-palette-info-contrast-text"))))

    (testing ":breakpoints is excluded — media params cannot use var()"
      ;; Load-bearing: this is why the style cache keys on bp-key rather than
      ;; on the theme.
      (is (empty? (filter #(str/includes? % "breakpoint") (keys vars)))))

    (testing "namespaced bookkeeping keys are skipped"
      (is (empty? (filter #(str/includes? % "bp-key") (keys vars)))))

    (testing "output is sorted, so equal themes give byte-identical text"
      (is (= (seq (keys vars)) (sort (keys vars)))))))

(deftest var-name-injectivity-test
  (testing "no two theme paths flatten to the same --cx-* name"
    ;; Catches a real bug class: sibling :fontSize and :font-size keys both
    ;; kebab to --cx-…-font-size and one would silently win.
    (let [names (keys (theme/theme->css-vars theme/default-theme-normalized))]
      (is (= (count names) (count (distinct names)))))))

(deftest bp-key-test
  (testing "bp-key summarizes the five breakpoints in ascending order"
    (is (= "0|600|900|1200|1536" (theme/bp-key theme/default-theme))))

  (testing "default-theme-normalized carries bp-key precomputed"
    (is (= "0|600|900|1200|1536" (:cx/bp-key theme/default-theme-normalized))))

  (testing "overriding a breakpoint changes bp-key"
    (let [t (theme/deep-merge-theme theme/default-theme
                                    {:breakpoints {:md 1000}})]
      (is (= "0|600|1000|1200|1536" (:cx/bp-key t))))))

(deftest breakpoint-values-test
  (is (= [0 600 900 1200 1536]
         (theme/breakpoint-values theme/default-theme-normalized))))

(deftest deep-merge-theme-test
  (testing "nested maps merge rather than clobber"
    (let [t (theme/deep-merge-theme theme/default-theme
                                    {:palette {:primary {:main "#ff0000"}}})]
      (is (= "#ff0000" (get-in t [:palette :primary :main])))
      (is (= "#42a5f5" (get-in t [:palette :primary :light])))
      (is (= "#9c27b0" (get-in t [:palette :secondary :main])))))

  (testing "vectors replace wholesale"
    (let [t (theme/deep-merge-theme theme/default-theme {:shadows ["a" "b"]})]
      (is (= ["a" "b"] (:shadows t)))))

  (testing "camelCase overrides normalize onto their kebab-case slot"
    (let [t (theme/deep-merge-theme theme/default-theme
                                    {:shape {:borderRadius 12}})]
      (is (= 12 (get-in t [:shape :border-radius])))
      (is (nil? (get-in t [:shape :borderRadius])))
      (is (= "12px" (get (theme/theme->css-vars t)
                         "--cx-shape-border-radius")))))

  (testing "camelCase override does not create a colliding sibling var"
    (let [t     (theme/deep-merge-theme theme/default-theme
                                        {:typography {:fontSize 20}})
          names (keys (theme/theme->css-vars t))]
      (is (= (count names) (count (distinct names))))
      (is (= "20px" (get (theme/theme->css-vars t)
                         "--cx-typography-font-size")))))

  (testing "merging preserves unrelated branches"
    (let [t (theme/deep-merge-theme theme/default-theme {:spacing 4})]
      (is (= 4 (:spacing t)))
      (is (= "1rem" (get-in t [:typography :font-size]))))))
