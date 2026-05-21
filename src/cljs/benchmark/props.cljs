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

(defn bench-props-empty []
  (merge {:id :props/empty
          :category :props-conversion
          :name "Empty Props"}
         (utils/bench-compare
           #(clj->js-props props-empty)
           #(baseline/manual-props-empty)
           {:iterations 100000})))

(defn bench-props-shallow []
  (merge {:id :props/shallow
          :category :props-conversion
          :name "Shallow Props (5 keys)"}
         (utils/bench-compare
           #(clj->js-props props-shallow)
           #(baseline/manual-props-shallow)
           {:iterations 100000})))

(defn bench-props-deep []
  (merge {:id :props/deep
          :category :props-conversion
          :name "Deep Props (3 levels)"}
         (utils/bench-compare
           #(clj->js-props props-deep)
           #(baseline/manual-props-deep)
           {:iterations 50000})))

(defn bench-props-with-vectors []
  (merge {:id :props/with-vectors
          :category :props-conversion
          :name "Props with Vectors"}
         (utils/bench-compare
           #(clj->js-props props-with-vectors)
           #(baseline/manual-props-with-array)
           {:iterations 50000})))

(defn bench-props-large []
  (merge {:id :props/large
          :category :props-conversion
          :name "Large Props (50 keys)"}
         (utils/bench-compare
           #(clj->js-props props-large)
           #(baseline/manual-props-large)
           {:iterations 10000})))

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
