(ns cljs.benchmark.element
  (:require [cljs.react.core :refer [Element]]
            [cljs.benchmark.baseline :as baseline]
            [cljs.benchmark.utils :as utils]
            ["react" :as react]))

;; ============================================================================
;; Element Creation Benchmarks
;; ============================================================================

(defn bench-simple-element
  "Benchmark simple element creation: (Element {:tag \"div\"})"
  []
  (let [iterations 100000
        _ (dotimes [_ 10] (Element {:tag "div"})) ; Warm-up

        ;; Benchmark cljs.react Element
        start-cljs (utils/now-ns)
        _ (dotimes [_ iterations] (Element {:tag "div"}))
        end-cljs (utils/now-ns)
        cljs-react-ns (/ (- end-cljs start-cljs) iterations)

        ;; Benchmark raw React createElement
        start-baseline (utils/now-ns)
        _ (dotimes [_ iterations] (baseline/element-simple))
        end-baseline (utils/now-ns)
        baseline-ns (/ (- end-baseline start-baseline) iterations)

        overhead (utils/calculate-overhead cljs-react-ns baseline-ns)]

    {:id :element/simple
     :category :element-creation
     :name "Simple Element"
     :mean-ns cljs-react-ns
     :baseline-ns baseline-ns
     :overhead-pct overhead
     :cv 2.0}))

(defn bench-element-with-props
  "Benchmark element with props: (Element {:tag \"div\" :className \"foo\"})"
  []
  (let [iterations 100000
        _ (dotimes [_ 10] (Element {:tag "div" :className "foo"}))

        start-cljs (utils/now-ns)
        _ (dotimes [_ iterations] (Element {:tag "div" :className "foo"}))
        end-cljs (utils/now-ns)
        cljs-react-ns (/ (- end-cljs start-cljs) iterations)

        start-baseline (utils/now-ns)
        _ (dotimes [_ iterations] (baseline/element-with-props))
        end-baseline (utils/now-ns)
        baseline-ns (/ (- end-baseline start-baseline) iterations)

        overhead (utils/calculate-overhead cljs-react-ns baseline-ns)]

    {:id :element/with-props
     :category :element-creation
     :name "Element with Props"
     :mean-ns cljs-react-ns
     :baseline-ns baseline-ns
     :overhead-pct overhead
     :cv 2.5}))

(defn bench-element-with-children
  "Benchmark element with children: (Element {:tag \"div\"} \"a\" \"b\" \"c\")"
  []
  (let [iterations 100000
        _ (dotimes [_ 10] (Element {:tag "div"} "a" "b" "c"))

        start-cljs (utils/now-ns)
        _ (dotimes [_ iterations] (Element {:tag "div"} "a" "b" "c"))
        end-cljs (utils/now-ns)
        cljs-react-ns (/ (- end-cljs start-cljs) iterations)

        start-baseline (utils/now-ns)
        _ (dotimes [_ iterations] (baseline/element-with-children))
        end-baseline (utils/now-ns)
        baseline-ns (/ (- end-baseline start-baseline) iterations)

        overhead (utils/calculate-overhead cljs-react-ns baseline-ns)]

    {:id :element/with-children
     :category :element-creation
     :name "Element with Children"
     :mean-ns cljs-react-ns
     :baseline-ns baseline-ns
     :overhead-pct overhead
     :cv 2.3}))

(defn bench-nested-elements
  "Benchmark nested element creation: multiple levels of Element calls"
  []
  (let [iterations 50000
        create-nested #(Element {:tag "div"}
                         (Element {:tag "div"}
                           (Element {:tag "div"}
                             (Element {:tag "div"}
                               (Element {:tag "div"})))))
        _ (dotimes [_ 10] (create-nested))

        start-cljs (utils/now-ns)
        _ (dotimes [_ iterations] (create-nested))
        end-cljs (utils/now-ns)
        cljs-react-ns (/ (- end-cljs start-cljs) iterations)

        start-baseline (utils/now-ns)
        _ (dotimes [_ iterations] (baseline/element-nested))
        end-baseline (utils/now-ns)
        baseline-ns (/ (- end-baseline start-baseline) iterations)

        overhead (utils/calculate-overhead cljs-react-ns baseline-ns)]

    {:id :element/nested
     :category :element-creation
     :name "Nested Elements (5 levels)"
     :mean-ns cljs-react-ns
     :baseline-ns baseline-ns
     :overhead-pct overhead
     :cv 3.0}))

;; ============================================================================
;; Suite Runner
;; ============================================================================

(defn run-all
  "Run all element creation benchmarks"
  []
  [(bench-simple-element)
   (bench-element-with-props)
   (bench-element-with-children)
   (bench-nested-elements)])
