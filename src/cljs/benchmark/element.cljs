(ns cljs.benchmark.element
  (:require [cljs.react.core :refer [Element]]
            [cljs.benchmark.baseline :as baseline]
            [cljs.benchmark.utils :as utils]))

;; ============================================================================
;; Element Creation Benchmarks
;; ============================================================================

(defn bench-simple-element []
  (merge {:id :element/simple
          :category :element-creation
          :name "Simple Element"}
         (utils/bench-compare
           #(Element {:tag "div"})
           #(baseline/element-simple)
           {:iterations 100000})))

(defn bench-element-with-props []
  (merge {:id :element/with-props
          :category :element-creation
          :name "Element with Props"}
         (utils/bench-compare
           #(Element {:tag "div" :className "foo"})
           #(baseline/element-with-props)
           {:iterations 100000})))

(defn bench-element-with-children []
  (merge {:id :element/with-children
          :category :element-creation
          :name "Element with Children"}
         (utils/bench-compare
           #(Element {:tag "div"} "a" "b" "c")
           #(baseline/element-with-children)
           {:iterations 100000})))

(defn bench-nested-elements []
  (merge {:id :element/nested
          :category :element-creation
          :name "Nested Elements (5 levels)"}
         (utils/bench-compare
           #(Element {:tag "div"}
              (Element {:tag "div"}
                (Element {:tag "div"}
                  (Element {:tag "div"}
                    (Element {:tag "div"})))))
           #(baseline/element-nested)
           {:iterations 50000})))

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
