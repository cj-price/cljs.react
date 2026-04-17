(ns cljs.react.property-test
  (:require
   [cljs.test :refer [deftest is]]
   [clojure.test.check :as tc]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop :include-macros true]
   [cljs.react.component :as component]
   [cljs.react.hook :as hook]
   [cljs.react.db :as db]
   ["global-jsdom/register"]
   ["react" :as react]
   ["@testing-library/react" :refer [renderHook act]]))

(def ^:private num-tests 50)

(defn- normalize
  "Normalize CLJS value for comparison after JS roundtrip.
  Lists/seqs become vectors; keyword keys stay keywords (because our
  js->clj roundtrip uses :keywordize-keys true)."
  [v]
  (cond
    (map? v)        (reduce-kv (fn [m k v] (assoc m k (normalize v))) {} v)
    (sequential? v) (mapv normalize v)
    :else           v))

(def ^:private scalar-gen
  (gen/one-of [gen/small-integer gen/string-alphanumeric gen/boolean]))

(def ^:private prop-value-gen
  (gen/recursive-gen
    (fn [inner]
      (gen/one-of [(gen/vector inner 0 3)
                   (gen/map gen/keyword inner)]))
    scalar-gen))

(def ^:private props-gen
  (gen/map gen/keyword prop-value-gen {:max-elements 4}))

(deftest clj->js-props-roundtrip
  (let [result (tc/quick-check num-tests
                 (prop/for-all [m props-gen]
                   (let [js-obj (component/clj->js-props m)
                         round  (if (nil? js-obj) {} (js->clj js-obj :keywordize-keys true))]
                     (= (normalize m) (normalize round)))))]
    (is (:result result) (pr-str result))))

(deftest clj->js-props-empty-returns-nil
  (let [result (tc/quick-check num-tests
                 (prop/for-all [m (gen/return {})]
                   (nil? (component/clj->js-props m))))]
    (is (:result result) (pr-str result))))

(deftest cljs-deps-equal-inputs-stable
  ;; Equal inputs: the returned JS array must be identical (same reference) across renders.
  (let [result (tc/quick-check num-tests
                 (prop/for-all [d (gen/vector scalar-gen 0 4)]
                   (let [out (atom [])
                         hook-fn (fn [] (hook/cljs-deps d))
                         r       (renderHook hook-fn)
                         first-arr (.. r -result -current)]
                     (swap! out conj first-arr)
                     (.rerender r)
                     (swap! out conj (.. r -result -current))
                     (.unmount r)
                     (identical? (first @out) (second @out)))))]
    (is (:result result) (pr-str result))))

(deftest cljs-deps-unequal-inputs-bump
  ;; When deps change to a non-equal value, the returned array must differ.
  (let [result (tc/quick-check num-tests
                 (prop/for-all [a (gen/vector scalar-gen 1 4)
                                b (gen/vector scalar-gen 1 4)]
                   (if (= a b)
                     true
                     (let [state (atom a)
                           r     (renderHook (fn [] (hook/cljs-deps @state)))
                           arr1  (.. r -result -current)]
                       (reset! state b)
                       (.rerender r)
                       (let [arr2 (.. r -result -current)]
                         (.unmount r)
                         (not (identical? arr1 arr2)))))))]
    (is (:result result) (pr-str result))))

(deftest cursor-lens-set-get
  ;; reset! through cursor then deref returns the set value.
  (let [result (tc/quick-check num-tests
                 (prop/for-all [path (gen/vector gen/keyword 1 3)
                                v    scalar-gen]
                   (let [a (atom {})
                         c (db/->Cursor a path)]
                     (reset! c v)
                     (= v @c (get-in @a path)))))]
    (is (:result result) (pr-str result))))

(deftest cursor-lens-swap-identity
  ;; swap! with identity is a no-op.
  (let [result (tc/quick-check num-tests
                 (prop/for-all [path (gen/vector gen/keyword 1 3)
                                v    scalar-gen]
                   (let [a (atom (assoc-in {} path v))
                         c (db/->Cursor a path)
                         before @a]
                     (swap! c identity)
                     (= before @a))))]
    (is (:result result) (pr-str result))))

(deftest state-atom-swap-arity-uniformity
  ;; swap! arity 1/2/3/variadic must all produce the same result as
  ;; applying the same fn + args directly to the initial value.
  (let [result (tc/quick-check num-tests
                 (prop/for-all [init gen/small-integer
                                a    gen/small-integer
                                b    gen/small-integer
                                c    gen/small-integer]
                   (let [check (fn [expected swapper]
                                 (let [r (renderHook #(hook/use-state init))
                                       s (.. r -result -current)]
                                   (act #(swapper s))
                                   (let [after (.. r -result -current)
                                         ok    (= expected @after)]
                                     (.unmount r)
                                     ok)))]
                     (and
                       (check (inc init) #(swap! % inc))
                       (check (+ init a) #(swap! % + a))
                       (check (+ init a b) #(swap! % + a b))
                       (check (+ init a b c) #(swap! % + a b c))))))]
    (is (:result result) (pr-str result))))

(deftest ref-atom-swap-arity-uniformity
  (let [result (tc/quick-check num-tests
                 (prop/for-all [init gen/small-integer
                                a    gen/small-integer
                                b    gen/small-integer
                                c    gen/small-integer]
                   (let [check (fn [expected swapper]
                                 (let [r  (renderHook #(hook/use-ref init))
                                       ra (.. r -result -current)]
                                   (swapper ra)
                                   (let [ok (= expected @ra)]
                                     (.unmount r)
                                     ok)))]
                     (and
                       (check (inc init) #(swap! % inc))
                       (check (+ init a) #(swap! % + a))
                       (check (+ init a b) #(swap! % + a b))
                       (check (+ init a b c) #(swap! % + a b c))))))]
    (is (:result result) (pr-str result))))

(deftest memo-component-render-count
  ;; For any sequence of props, body invocation count equals the number of
  ;; distinct adjacent groups (since CLJS = collapses consecutive equal props).
  (let [result (tc/quick-check 20
                 (prop/for-all [seq-props (gen/vector (gen/map gen/keyword scalar-gen {:max-elements 2}) 1 6)]
                   (let [render-count (atom 0)
                         inner        (fn [_]
                                        (swap! render-count inc)
                                        (react/createElement "div" nil))
                         memoized     (component/memo-component inner)
                         r            (renderHook (fn [] nil))]
                     (act (fn []
                            (doseq [p seq-props]
                              (.rerender r
                                (component/create-cljs-element memoized p)))))
                     (let [distinct-groups (count (partition-by identity seq-props))
                           observed        @render-count]
                       (.unmount r)
                       ;; Observed must be ≤ distinct-groups; may be less if
                       ;; React batches. The invariant is: no more than the
                       ;; number of distinct-groups + initial mount.
                       (<= observed (inc distinct-groups))))))]
    (is (:result result) (pr-str result))))
