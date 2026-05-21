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

(defn bench-component-instantiation []
  (let [props {:name "Alice" :age 30}
        baseline-props #js {:name "Alice" :age 30}]
    (merge {:id :component/instantiation
            :category :component-performance
            :name "Component Instantiation"}
           (utils/bench-compare
             #(create-cljs-element memoized-cljs-component props)
             #(react/createElement baseline/simple-component-memoized baseline-props)
             {:iterations 100000}))))

(defn bench-component-tree-deep []
  (merge {:id :component/tree-deep
          :category :component-performance
          :name "Deep Component Tree (5 levels)"}
         (utils/bench-compare
           #(create-cljs-element "div" {}
              (create-cljs-element "div" {}
                (create-cljs-element "div" {}
                  (create-cljs-element "div" {}
                    (create-cljs-element "div" {})))))
           #(react/createElement "div" nil
              (react/createElement "div" nil
                (react/createElement "div" nil
                  (react/createElement "div" nil
                    (react/createElement "div" nil)))))
           {:iterations 20000})))

(defn bench-component-tree-wide []
  (merge {:id :component/tree-wide
          :category :component-performance
          :name "Wide Component Tree (10 siblings)"}
         (utils/bench-compare
           #(create-cljs-element "div" {}
              (create-cljs-element "span" {})
              (create-cljs-element "span" {})
              (create-cljs-element "span" {})
              (create-cljs-element "span" {})
              (create-cljs-element "span" {})
              (create-cljs-element "span" {})
              (create-cljs-element "span" {})
              (create-cljs-element "span" {})
              (create-cljs-element "span" {})
              (create-cljs-element "span" {}))
           #(react/createElement "div" nil
              (react/createElement "span" nil)
              (react/createElement "span" nil)
              (react/createElement "span" nil)
              (react/createElement "span" nil)
              (react/createElement "span" nil)
              (react/createElement "span" nil)
              (react/createElement "span" nil)
              (react/createElement "span" nil)
              (react/createElement "span" nil)
              (react/createElement "span" nil))
           {:iterations 20000})))

;; ============================================================================
;; Suite Runner
;; ============================================================================

(defn run-all
  "Run all component performance benchmarks"
  []
  [(bench-component-instantiation)
   (bench-component-tree-deep)
   (bench-component-tree-wide)])
