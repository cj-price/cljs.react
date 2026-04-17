(ns cljs.react.component-test
  (:require
   [cljs.test :refer [deftest testing is]]
   [cljs.react.component :as component]
   [cljs.react.hook :as hook]
   [goog.object :as gobj]
   ["react" :as react]
   ["global-jsdom/register"]
   ["@testing-library/react" :refer [render renderHook cleanup]]))

;;; clj->js-props

(deftest clj->js-props-nil-test
  (testing "nil → nil"
    (is (nil? (component/clj->js-props nil)))))

(deftest clj->js-props-empty-test
  (testing "empty map → nil (React accepts nil props; saves an allocation)"
    (is (nil? (component/clj->js-props {})))))

(deftest clj->js-props-keyword-keys-test
  (testing "keyword keys become string properties"
    (let [js-obj (component/clj->js-props {:className "foo" :id "bar"})]
      (is (= "foo" (gobj/get js-obj "className")))
      (is (= "bar" (gobj/get js-obj "id"))))))

(deftest clj->js-props-string-keys-test
  (testing "string keys are preserved as-is"
    (let [js-obj (component/clj->js-props {"className" "x" "id" "y"})]
      (is (= "x" (gobj/get js-obj "className")))
      (is (= "y" (gobj/get js-obj "id"))))))

(deftest clj->js-props-nested-map-test
  (testing "nested maps are converted recursively"
    (let [js-obj (component/clj->js-props {:style {:color "red" :margin 10}})
          style (gobj/get js-obj "style")]
      (is (= "red" (gobj/get style "color")))
      (is (= 10 (gobj/get style "margin"))))))

(deftest clj->js-props-sequential-test
  (testing "sequentials become JS arrays"
    (let [js-obj (component/clj->js-props {:items [1 2 3]})
          items (gobj/get js-obj "items")]
      (is (array? items))
      (is (= 3 (alength items)))
      (is (= 1 (aget items 0)))
      (is (= 3 (aget items 2))))))

(deftest clj->js-props-mixed-test
  (testing "handles a mix of scalars, maps, and sequences"
    (let [js-obj (component/clj->js-props
                   {:id "x" :n 42 :flag true
                    :style {:color "red"}
                    :items [:a :b]})]
      (is (= "x" (gobj/get js-obj "id")))
      (is (= 42 (gobj/get js-obj "n")))
      (is (true? (gobj/get js-obj "flag")))
      (is (= "red" (gobj/get (gobj/get js-obj "style") "color")))
      (is (array? (gobj/get js-obj "items"))))))

(deftest clj->js-props-ref-refatom-test
  (testing ":ref with a RefAtom is unwrapped to the raw React ref"
    (let [result (renderHook #(hook/use-ref "init"))
          ref-atom (.. result -result -current)
          js-obj (component/clj->js-props {:ref ref-atom})
          raw-ref (gobj/get js-obj "ref")]
      (is (some? raw-ref))
      (is (= (hook/react-ref ref-atom) raw-ref))
      (cleanup))))

(deftest clj->js-props-ref-passthrough-test
  (testing ":ref with a non-IReactRef value is passed through unchanged"
    (let [callback (fn [_x] :called)
          js-obj (component/clj->js-props {:ref callback})]
      (is (identical? callback (gobj/get js-obj "ref"))))))

;;; make-element-fn (custom renderer)

(deftest make-element-fn-happy-path-test
  (testing "make-element-fn dispatches to the provided renderer"
    (let [calls (atom [])
          mock-renderer (fn [& args] (swap! calls conj args) :fake-element)
          my-element (component/make-element-fn mock-renderer)
          result (my-element {:tag "section" :className "x"} "hi")]
      (is (= :fake-element result))
      (is (= 1 (count @calls)))
      (let [[tag js-props child] (first @calls)]
        (is (= "section" tag))
        (is (= "x" (gobj/get js-props "className")))
        (is (nil? (gobj/get js-props "tag")))
        (is (= "hi" child))))))

(deftest make-element-fn-nil-tag-throws-test
  (testing "make-element-fn with nil :tag throws (matches Element contract)"
    (let [my-element (component/make-element-fn (fn [& _] :never))]
      (is (thrown? js/Error (my-element {:tag nil})))
      (is (thrown? js/Error (my-element {}))))))

;;; make-create-cljs-element-fn

(deftest make-create-cljs-element-fn-test
  (testing "for string types it passes js props (converted)"
    (let [calls (atom [])
          mock-renderer (fn [& args] (swap! calls conj args) :fake-element)
          create (component/make-create-cljs-element-fn mock-renderer)
          _ (create "div" {:id "x"} "a")]
      (let [[type js-props child] (first @calls)]
        (is (= "div" type))
        (is (= "x" (gobj/get js-props "id")))
        (is (= "a" child)))))

  (testing "for component types it wraps props in cljsProps"
    (let [calls (atom [])
          mock-renderer (fn [& args] (swap! calls conj args) :fake-element)
          create (component/make-create-cljs-element-fn mock-renderer)
          my-comp (fn [_] nil)
          _ (create my-comp {:x 1 :key "k"} "child")]
      (let [[type js-props child] (first @calls)]
        (is (identical? my-comp type))
        (is (= {:x 1 :key "k"} (gobj/get js-props "cljsProps")))
        (is (= "k" (gobj/get js-props "key"))
            ":key is hoisted to the top level so React can see it")
        (is (= "child" child))))))

;;; memo-component-js

(defn- inner-js [^js js-props]
  (react/createElement "span" nil (.-label js-props)))

(def MemoJS (component/memo-component-js inner-js))

(deftest memo-component-js-test
  (testing "memo-component-js accepts raw JS props"
    (let [result (render (react/createElement MemoJS #js {:label "raw-memo"}))
          span (.. result -container -firstChild)]
      (is (= "SPAN" (.-tagName span)))
      (is (= "raw-memo" (.-textContent span)))
      (cleanup))))
