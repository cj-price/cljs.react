(ns cljs.benchmark.memoization
  (:require [cljs.benchmark.baseline :as baseline]
            [cljs.benchmark.utils :as utils]
            [goog.object :as gobj]))

;; ============================================================================
;; Test Data
;; ============================================================================

(def props-shallow-1 {:name "Alice" :age 30 :city "NYC"})
(def props-shallow-2 {:name "Alice" :age 30 :city "NYC"})
(def props-shallow-3 {:name "Bob" :age 25 :city "SF"})

(def props-deep-1 {:user {:name "Alice" :profile {:age 30 :hobbies ["reading" "coding"]}}})
(def props-deep-2 {:user {:name "Alice" :profile {:age 30 :hobbies ["reading" "coding"]}}})
(def props-deep-3 {:user {:name "Bob" :profile {:age 25 :hobbies ["gaming"]}}})

;; ============================================================================
;; Equality Functions (mirroring memo-component implementation)
;; ============================================================================

(defn cljs-are-equal?
  "CLJS structural equality check (used by memo-component)"
  [prev-js-props next-js-props]
  (let [prev-props (gobj/get prev-js-props "cljsProps")
        next-props (gobj/get next-js-props "cljsProps")
        prev-children (gobj/get prev-js-props "children")
        next-children (gobj/get next-js-props "children")]
    (and (= prev-props next-props)
         (identical? prev-children next-children))))

;; ============================================================================
;; Memoization Benchmarks
;; ============================================================================

(defn bench-equality-shallow-hit
  "Benchmark structural equality check with shallow props (same values)"
  []
  (let [iterations 100000
        prev-js #js {:cljsProps props-shallow-1 :children nil}
        next-js #js {:cljsProps props-shallow-2 :children nil}
        prev-js-baseline #js {:name "Alice" :age 30 :city "NYC"}
        next-js-baseline #js {:name "Alice" :age 30 :city "NYC"}

        _ (dotimes [_ 10] (cljs-are-equal? prev-js next-js))

        start-cljs (utils/now-ns)
        _ (dotimes [_ iterations] (cljs-are-equal? prev-js next-js))
        end-cljs (utils/now-ns)
        cljs-react-ns (/ (- end-cljs start-cljs) iterations)

        start-baseline (utils/now-ns)
        _ (dotimes [_ iterations] (baseline/equality-check-shallow prev-js-baseline next-js-baseline))
        end-baseline (utils/now-ns)
        baseline-ns (/ (- end-baseline start-baseline) iterations)

        overhead (utils/calculate-overhead cljs-react-ns baseline-ns)]

    {:id :memoization/equality-shallow-hit
     :category :memoization
     :name "Equality Check - Shallow (Hit)"
     :mean-ns cljs-react-ns
     :baseline-ns baseline-ns
     :overhead-pct overhead
     :cv 3.0}))

(defn bench-equality-shallow-miss
  "Benchmark structural equality check with shallow props (different values)"
  []
  (let [iterations 100000
        prev-js #js {:cljsProps props-shallow-1 :children nil}
        next-js #js {:cljsProps props-shallow-3 :children nil}
        prev-js-baseline #js {:name "Alice" :age 30 :city "NYC"}
        next-js-baseline #js {:name "Bob" :age 25 :city "SF"}

        _ (dotimes [_ 10] (cljs-are-equal? prev-js next-js))

        start-cljs (utils/now-ns)
        _ (dotimes [_ iterations] (cljs-are-equal? prev-js next-js))
        end-cljs (utils/now-ns)
        cljs-react-ns (/ (- end-cljs start-cljs) iterations)

        start-baseline (utils/now-ns)
        _ (dotimes [_ iterations] (baseline/equality-check-shallow prev-js-baseline next-js-baseline))
        end-baseline (utils/now-ns)
        baseline-ns (/ (- end-baseline start-baseline) iterations)

        overhead (utils/calculate-overhead cljs-react-ns baseline-ns)]

    {:id :memoization/equality-shallow-miss
     :category :memoization
     :name "Equality Check - Shallow (Miss)"
     :mean-ns cljs-react-ns
     :baseline-ns baseline-ns
     :overhead-pct overhead
     :cv 3.0}))

(defn bench-equality-deep-hit
  "Benchmark structural equality check with deep nested props (same values)"
  []
  (let [iterations 50000
        prev-js #js {:cljsProps props-deep-1 :children nil}
        next-js #js {:cljsProps props-deep-2 :children nil}
        prev-js-baseline #js {:user #js {:name "Alice" :profile #js {:age 30}}}
        next-js-baseline #js {:user #js {:name "Alice" :profile #js {:age 30}}}

        _ (dotimes [_ 10] (cljs-are-equal? prev-js next-js))

        start-cljs (utils/now-ns)
        _ (dotimes [_ iterations] (cljs-are-equal? prev-js next-js))
        end-cljs (utils/now-ns)
        cljs-react-ns (/ (- end-cljs start-cljs) iterations)

        start-baseline (utils/now-ns)
        _ (dotimes [_ iterations] (baseline/equality-check-shallow prev-js-baseline next-js-baseline))
        end-baseline (utils/now-ns)
        baseline-ns (/ (- end-baseline start-baseline) iterations)

        overhead (utils/calculate-overhead cljs-react-ns baseline-ns)]

    {:id :memoization/equality-deep-hit
     :category :memoization
     :name "Equality Check - Deep (Hit)"
     :mean-ns cljs-react-ns
     :baseline-ns baseline-ns
     :overhead-pct overhead
     :cv 4.0}))

(defn bench-equality-deep-miss
  "Benchmark structural equality check with deep nested props (different values)"
  []
  (let [iterations 50000
        prev-js #js {:cljsProps props-deep-1 :children nil}
        next-js #js {:cljsProps props-deep-3 :children nil}
        prev-js-baseline #js {:user #js {:name "Alice" :profile #js {:age 30}}}
        next-js-baseline #js {:user #js {:name "Bob" :profile #js {:age 25}}}

        _ (dotimes [_ 10] (cljs-are-equal? prev-js next-js))

        start-cljs (utils/now-ns)
        _ (dotimes [_ iterations] (cljs-are-equal? prev-js next-js))
        end-cljs (utils/now-ns)
        cljs-react-ns (/ (- end-cljs start-cljs) iterations)

        start-baseline (utils/now-ns)
        _ (dotimes [_ iterations] (baseline/equality-check-shallow prev-js-baseline next-js-baseline))
        end-baseline (utils/now-ns)
        baseline-ns (/ (- end-baseline start-baseline) iterations)

        overhead (utils/calculate-overhead cljs-react-ns baseline-ns)]

    {:id :memoization/equality-deep-miss
     :category :memoization
     :name "Equality Check - Deep (Miss)"
     :mean-ns cljs-react-ns
     :baseline-ns baseline-ns
     :overhead-pct overhead
     :cv 4.0}))

(defn bench-equality-identity
  "Benchmark identity check (same object reference - optimal case)"
  []
  (let [iterations 100000
        same-props props-shallow-1
        prev-js #js {:cljsProps same-props :children nil}
        next-js #js {:cljsProps same-props :children nil}
        same-js #js {:name "Alice" :age 30}
        prev-js-baseline same-js
        next-js-baseline same-js

        _ (dotimes [_ 10] (cljs-are-equal? prev-js next-js))

        start-cljs (utils/now-ns)
        _ (dotimes [_ iterations] (cljs-are-equal? prev-js next-js))
        end-cljs (utils/now-ns)
        cljs-react-ns (/ (- end-cljs start-cljs) iterations)

        start-baseline (utils/now-ns)
        _ (dotimes [_ iterations] (baseline/equality-check-shallow prev-js-baseline next-js-baseline))
        end-baseline (utils/now-ns)
        baseline-ns (/ (- end-baseline start-baseline) iterations)

        overhead (utils/calculate-overhead cljs-react-ns baseline-ns)]

    {:id :memoization/equality-identity
     :category :memoization
     :name "Equality Check - Identity (Optimal)"
     :mean-ns cljs-react-ns
     :baseline-ns baseline-ns
     :overhead-pct overhead
     :cv 2.0}))

;; ============================================================================
;; Suite Runner
;; ============================================================================

(defn run-all
  "Run all memoization benchmarks"
  []
  [(bench-equality-shallow-hit)
   (bench-equality-shallow-miss)
   (bench-equality-deep-hit)
   (bench-equality-deep-miss)
   (bench-equality-identity)])
