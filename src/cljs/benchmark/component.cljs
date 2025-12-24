(ns cljs.benchmark.component
  (:require [cljs.react.component :refer [memo-component create-cljs-element]]
            [cljs.benchmark.baseline :as baseline]
            [cljs.benchmark.utils :as utils]
            ["react" :as react]))

;; ============================================================================
;; Test Components
;; ============================================================================

(defn simple-cljs-component
  "Simple CLJS component for benchmarking"
  [props]
  (create-cljs-element "div" props (str "Hello " (:name props))))

(def memoized-cljs-component
  "Memoized CLJS component"
  (memo-component simple-cljs-component))

;; ============================================================================
;; Component Creation Benchmarks
;;
;; Note: These benchmark component *creation* (instantiation), not rendering,
;; since rendering requires a full DOM environment which is complex in Node.js.
;; Component creation is still a valuable metric as it happens on every render.
;; ============================================================================

(defn bench-component-instantiation
  "Benchmark component element instantiation"
  []
  (let [iterations 100000
        props {:name "Alice" :age 30}
        _ (dotimes [_ 10] (create-cljs-element memoized-cljs-component props))

        start-cljs (utils/now-ns)
        _ (dotimes [_ iterations] (create-cljs-element memoized-cljs-component props))
        end-cljs (utils/now-ns)
        cljs-react-ns (/ (- end-cljs start-cljs) iterations)

        baseline-props #js {:name "Alice" :age 30}
        start-baseline (utils/now-ns)
        _ (dotimes [_ iterations] (react/createElement baseline/simple-component-memoized baseline-props))
        end-baseline (utils/now-ns)
        baseline-ns (/ (- end-baseline start-baseline) iterations)

        overhead (utils/calculate-overhead cljs-react-ns baseline-ns)]

    {:id :component/instantiation
     :category :component-performance
     :name "Component Instantiation"
     :mean-ns cljs-react-ns
     :baseline-ns baseline-ns
     :overhead-pct overhead
     :cv 3.0}))

(defn bench-component-tree-deep
  "Benchmark creating a deep component tree (5 levels)"
  []
  (let [iterations 20000
        create-deep-tree (fn []
                          (create-cljs-element "div" {}
                            (create-cljs-element "div" {}
                              (create-cljs-element "div" {}
                                (create-cljs-element "div" {}
                                  (create-cljs-element "div" {}))))))
        baseline-tree (fn []
                       (react/createElement "div" nil
                         (react/createElement "div" nil
                           (react/createElement "div" nil
                             (react/createElement "div" nil
                               (react/createElement "div" nil))))))

        _ (dotimes [_ 10] (create-deep-tree))

        start-cljs (utils/now-ns)
        _ (dotimes [_ iterations] (create-deep-tree))
        end-cljs (utils/now-ns)
        cljs-react-ns (/ (- end-cljs start-cljs) iterations)

        start-baseline (utils/now-ns)
        _ (dotimes [_ iterations] (baseline-tree))
        end-baseline (utils/now-ns)
        baseline-ns (/ (- end-baseline start-baseline) iterations)

        overhead (utils/calculate-overhead cljs-react-ns baseline-ns)]

    {:id :component/tree-deep
     :category :component-performance
     :name "Deep Component Tree (5 levels)"
     :mean-ns cljs-react-ns
     :baseline-ns baseline-ns
     :overhead-pct overhead
     :cv 4.0}))

(defn bench-component-tree-wide
  "Benchmark creating a wide component tree (10 siblings)"
  []
  (let [iterations 20000
        create-wide-tree (fn []
                          (create-cljs-element "div" {}
                            (create-cljs-element "span" {})
                            (create-cljs-element "span" {})
                            (create-cljs-element "span" {})
                            (create-cljs-element "span" {})
                            (create-cljs-element "span" {})
                            (create-cljs-element "span" {})
                            (create-cljs-element "span" {})
                            (create-cljs-element "span" {})
                            (create-cljs-element "span" {})
                            (create-cljs-element "span" {})))
        baseline-tree (fn []
                       (react/createElement "div" nil
                         (react/createElement "span" nil)
                         (react/createElement "span" nil)
                         (react/createElement "span" nil)
                         (react/createElement "span" nil)
                         (react/createElement "span" nil)
                         (react/createElement "span" nil)
                         (react/createElement "span" nil)
                         (react/createElement "span" nil)
                         (react/createElement "span" nil)
                         (react/createElement "span" nil)))

        _ (dotimes [_ 10] (create-wide-tree))

        start-cljs (utils/now-ns)
        _ (dotimes [_ iterations] (create-wide-tree))
        end-cljs (utils/now-ns)
        cljs-react-ns (/ (- end-cljs start-cljs) iterations)

        start-baseline (utils/now-ns)
        _ (dotimes [_ iterations] (baseline-tree))
        end-baseline (utils/now-ns)
        baseline-ns (/ (- end-baseline start-baseline) iterations)

        overhead (utils/calculate-overhead cljs-react-ns baseline-ns)]

    {:id :component/tree-wide
     :category :component-performance
     :name "Wide Component Tree (10 siblings)"
     :mean-ns cljs-react-ns
     :baseline-ns baseline-ns
     :overhead-pct overhead
     :cv 4.0}))

;; ============================================================================
;; Suite Runner
;; ============================================================================

(defn run-all
  "Run all component performance benchmarks"
  []
  [(bench-component-instantiation)
   (bench-component-tree-deep)
   (bench-component-tree-wide)])
