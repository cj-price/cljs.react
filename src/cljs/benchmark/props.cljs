(ns cljs.benchmark.props
  (:require [cljs.react.component :refer [clj->js-props]]
            [cljs.benchmark.baseline :as baseline]
            [cljs.benchmark.utils :as utils]))

;; ============================================================================
;; Test Data
;; ============================================================================

(def props-empty {})

(def props-shallow
  {:className "foo"
   :id "bar"
   :data-test "baz"
   :aria-label "qux"
   :tabIndex 0})

(def props-deep
  {:style {:margin {:top 10 :bottom 20}}
   :data {:user {:name "Alice" :age 30}}})

(def props-with-vectors
  {:items ["a" "b" "c"]
   :numbers [1 2 3]
   :nested {:values [10 20 30]}})

(def props-large
  (into {} (map (fn [i] [(keyword (str "key" i)) (str "value" i)])
               (range 50))))

;; ============================================================================
;; Props Conversion Benchmarks
;; ============================================================================

(defn bench-props-empty
  "Benchmark empty props conversion"
  []
  (let [iterations 100000
        _ (dotimes [_ 10] (clj->js-props props-empty))

        start-cljs (utils/now-ns)
        _ (dotimes [_ iterations] (clj->js-props props-empty))
        end-cljs (utils/now-ns)
        cljs-react-ns (/ (- end-cljs start-cljs) iterations)

        start-baseline (utils/now-ns)
        _ (dotimes [_ iterations] (baseline/manual-props-empty))
        end-baseline (utils/now-ns)
        baseline-ns (/ (- end-baseline start-baseline) iterations)

        overhead (utils/calculate-overhead cljs-react-ns baseline-ns)]

    {:id :props/empty
     :category :props-conversion
     :name "Empty Props"
     :mean-ns cljs-react-ns
     :baseline-ns baseline-ns
     :overhead-pct overhead
     :cv 3.0}))

(defn bench-props-shallow
  "Benchmark shallow props conversion (5 keys)"
  []
  (let [iterations 100000
        _ (dotimes [_ 10] (clj->js-props props-shallow))

        start-cljs (utils/now-ns)
        _ (dotimes [_ iterations] (clj->js-props props-shallow))
        end-cljs (utils/now-ns)
        cljs-react-ns (/ (- end-cljs start-cljs) iterations)

        start-baseline (utils/now-ns)
        _ (dotimes [_ iterations] (baseline/manual-props-shallow))
        end-baseline (utils/now-ns)
        baseline-ns (/ (- end-baseline start-baseline) iterations)

        overhead (utils/calculate-overhead cljs-react-ns baseline-ns)]

    {:id :props/shallow
     :category :props-conversion
     :name "Shallow Props (5 keys)"
     :mean-ns cljs-react-ns
     :baseline-ns baseline-ns
     :overhead-pct overhead
     :cv 3.5}))

(defn bench-props-deep
  "Benchmark deep nested props conversion (3 levels)"
  []
  (let [iterations 50000
        _ (dotimes [_ 10] (clj->js-props props-deep))

        start-cljs (utils/now-ns)
        _ (dotimes [_ iterations] (clj->js-props props-deep))
        end-cljs (utils/now-ns)
        cljs-react-ns (/ (- end-cljs start-cljs) iterations)

        start-baseline (utils/now-ns)
        _ (dotimes [_ iterations] (baseline/manual-props-deep))
        end-baseline (utils/now-ns)
        baseline-ns (/ (- end-baseline start-baseline) iterations)

        overhead (utils/calculate-overhead cljs-react-ns baseline-ns)]

    {:id :props/deep
     :category :props-conversion
     :name "Deep Props (3 levels)"
     :mean-ns cljs-react-ns
     :baseline-ns baseline-ns
     :overhead-pct overhead
     :cv 4.0}))

(defn bench-props-with-vectors
  "Benchmark props with vectors (array conversion)"
  []
  (let [iterations 50000
        _ (dotimes [_ 10] (clj->js-props props-with-vectors))

        start-cljs (utils/now-ns)
        _ (dotimes [_ iterations] (clj->js-props props-with-vectors))
        end-cljs (utils/now-ns)
        cljs-react-ns (/ (- end-cljs start-cljs) iterations)

        start-baseline (utils/now-ns)
        _ (dotimes [_ iterations] (baseline/manual-props-with-array))
        end-baseline (utils/now-ns)
        baseline-ns (/ (- end-baseline start-baseline) iterations)

        overhead (utils/calculate-overhead cljs-react-ns baseline-ns)]

    {:id :props/with-vectors
     :category :props-conversion
     :name "Props with Vectors"
     :mean-ns cljs-react-ns
     :baseline-ns baseline-ns
     :overhead-pct overhead
     :cv 4.2}))

(defn bench-props-large
  "Benchmark large props object (50 keys)"
  []
  (let [iterations 10000
        _ (dotimes [_ 10] (clj->js-props props-large))

        start-cljs (utils/now-ns)
        _ (dotimes [_ iterations] (clj->js-props props-large))
        end-cljs (utils/now-ns)
        cljs-react-ns (/ (- end-cljs start-cljs) iterations)

        start-baseline (utils/now-ns)
        _ (dotimes [_ iterations] (baseline/manual-props-large))
        end-baseline (utils/now-ns)
        baseline-ns (/ (- end-baseline start-baseline) iterations)

        overhead (utils/calculate-overhead cljs-react-ns baseline-ns)]

    {:id :props/large
     :category :props-conversion
     :name "Large Props (50 keys)"
     :mean-ns cljs-react-ns
     :baseline-ns baseline-ns
     :overhead-pct overhead
     :cv 5.0}))

;; ============================================================================
;; Suite Runner
;; ============================================================================

(defn run-all
  "Run all props conversion benchmarks"
  []
  [(bench-props-empty)
   (bench-props-shallow)
   (bench-props-deep)
   (bench-props-with-vectors)
   (bench-props-large)])
