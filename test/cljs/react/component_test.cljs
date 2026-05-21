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

(deftest clj->js-props-namespaced-keyword-test
  (testing "namespaced keywords use their fully-qualified name"
    (let [js-obj (component/clj->js-props {:foo/bar "x" :a.b/c "y"})]
      (is (= "x" (gobj/get js-obj "foo/bar")))
      (is (= "y" (gobj/get js-obj "a.b/c"))))))

(deftest clj->js-props-non-keyword-key-test
  (testing "non-keyword, non-string keys are coerced via str"
    (let [js-obj (component/clj->js-props {42 "n" 'sym "s"})]
      (is (= "n" (gobj/get js-obj "42")))
      (is (= "s" (gobj/get js-obj "sym"))))))

(deftest clj->js-props-nil-and-false-values-test
  (testing "nil and false values are preserved (not dropped)"
    (let [js-obj (component/clj->js-props {:a nil :b false :c 0})]
      (is (true? (gobj/containsKey js-obj "a")))
      (is (nil? (gobj/get js-obj "a")))
      (is (false? (gobj/get js-obj "b")))
      (is (= 0 (gobj/get js-obj "c"))))))

(deftest clj->js-props-nested-empty-map-test
  (testing "nested empty maps round-trip as empty JS objects (not nil)"
    (let [js-obj (component/clj->js-props {:style {}})
          style (gobj/get js-obj "style")]
      (is (object? style))
      (is (zero? (alength (gobj/getKeys style)))))))

(deftest clj->js-props-nested-ref-test
  (testing ":ref inside a nested map is also unwrapped"
    (let [result (renderHook #(hook/use-ref "init"))
          ref-atom (.. result -result -current)
          js-obj (component/clj->js-props {:inner {:ref ref-atom}})
          inner (gobj/get js-obj "inner")]
      (is (= (hook/react-ref ref-atom) (gobj/get inner "ref")))
      (cleanup))))

(deftest clj->js-props-list-and-seq-test
  (testing "lists and lazy seqs become JS arrays"
    (let [js-obj (component/clj->js-props {:list '(1 2 3)
                                            :seq  (map inc [9 8 7])})
          list-arr (gobj/get js-obj "list")
          seq-arr  (gobj/get js-obj "seq")]
      (is (array? list-arr))
      (is (= [1 2 3] (vec list-arr)))
      (is (array? seq-arr))
      (is (= [10 9 8] (vec seq-arr))))))

(deftest clj->js-props-vector-shallow-test
  (testing "vector conversion is shallow — elements are not recursively converted"
    ;; React arrays carry React elements (already JS) or primitives, never CLJS
    ;; maps; `to-array` skips the recursive walk for speed. This test pins that
    ;; contract so a future change doesn't quietly add deep conversion.
    (let [inner  {:id 1}
          js-obj (component/clj->js-props {:rows [inner]})
          rows   (gobj/get js-obj "rows")]
      (is (array? rows))
      (is (identical? inner (aget rows 0))
          "inner CLJS map is preserved by reference, not converted to a JS object"))))

(deftest clj->js-props-hashmap-path-test
  (testing "PersistentHashMap (>8 entries) takes the reduce-kv fallback path"
    (let [big (zipmap (map #(keyword (str "k" %)) (range 12)) (range 12))]
      ;; sanity: confirm we actually exercise the non-ArrayMap branch
      (is (not (instance? PersistentArrayMap big)))
      (let [js-obj (component/clj->js-props big)]
        (is (= 11 (gobj/get js-obj "k11")))
        (is (= 0  (gobj/get js-obj "k0")))
        (is (= 12 (alength (gobj/getKeys js-obj))))))))

(deftest clj->js-props-skip-key-arraymap-test
  (testing "2-arity skip-key drops a top-level key (PersistentArrayMap path)"
    (let [m {:tag "div" :id "x" :className "c"}
          _ (is (instance? PersistentArrayMap m))
          js-obj (component/clj->js-props m :tag)]
      (is (nil? (gobj/get js-obj "tag")))
      (is (false? (gobj/containsKey js-obj "tag")))
      (is (= "x" (gobj/get js-obj "id")))
      (is (= "c" (gobj/get js-obj "className"))))))

(deftest clj->js-props-skip-key-hashmap-test
  (testing "2-arity skip-key drops a top-level key (PersistentHashMap path)"
    (let [big (assoc (zipmap (map #(keyword (str "k" %)) (range 12)) (range 12))
                :tag "div")
          _ (is (not (instance? PersistentArrayMap big)))
          js-obj (component/clj->js-props big :tag)]
      (is (false? (gobj/containsKey js-obj "tag")))
      (is (= 11 (gobj/get js-obj "k11"))))))

(deftest clj->js-props-skip-key-only-top-level-test
  (testing "skip-key only applies at the top level, not in nested maps"
    (let [js-obj (component/clj->js-props {:tag "div" :nested {:tag "keep"}} :tag)
          nested (gobj/get js-obj "nested")]
      (is (false? (gobj/containsKey js-obj "tag")))
      (is (= "keep" (gobj/get nested "tag"))))))

(deftest clj->js-props-skip-key-missing-test
  (testing "skip-key for a key not in the map is a no-op"
    (let [js-obj (component/clj->js-props {:id "x" :className "c"} :tag)]
      (is (= "x" (gobj/get js-obj "id")))
      (is (= "c" (gobj/get js-obj "className")))
      (is (= 2 (alength (gobj/getKeys js-obj)))))))

(deftest clj->js-props-skip-key-empty-after-drop-test
  (testing "dropping the only key produces an empty JS object (not nil)"
    ;; has-entries? gates on the input being non-empty, so the empty-map → nil
    ;; short-circuit doesn't fire here — callers get an empty #js {}.
    (let [js-obj (component/clj->js-props {:tag "div"} :tag)]
      (is (object? js-obj))
      (is (zero? (alength (gobj/getKeys js-obj)))))))

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
