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

(defn bench-equality-shallow-hit []
  (let [prev-js #js {:cljsProps props-shallow-1 :children nil}
        next-js #js {:cljsProps props-shallow-2 :children nil}
        prev-js-baseline #js {:name "Alice" :age 30 :city "NYC"}
        next-js-baseline #js {:name "Alice" :age 30 :city "NYC"}]
    (merge {:id :memoization/equality-shallow-hit
            :category :memoization
            :name "Equality Check - Shallow (Hit)"}
           (utils/bench-compare
             #(cljs-are-equal? prev-js next-js)
             #(baseline/equality-check-shallow prev-js-baseline next-js-baseline)
             {:iterations 100000}))))

(defn bench-equality-shallow-miss []
  (let [prev-js #js {:cljsProps props-shallow-1 :children nil}
        next-js #js {:cljsProps props-shallow-3 :children nil}
        prev-js-baseline #js {:name "Alice" :age 30 :city "NYC"}
        next-js-baseline #js {:name "Bob" :age 25 :city "SF"}]
    (merge {:id :memoization/equality-shallow-miss
            :category :memoization
            :name "Equality Check - Shallow (Miss)"}
           (utils/bench-compare
             #(cljs-are-equal? prev-js next-js)
             #(baseline/equality-check-shallow prev-js-baseline next-js-baseline)
             {:iterations 100000}))))

(defn bench-equality-deep-hit []
  (let [prev-js #js {:cljsProps props-deep-1 :children nil}
        next-js #js {:cljsProps props-deep-2 :children nil}
        prev-js-baseline #js {:user #js {:name "Alice" :profile #js {:age 30}}}
        next-js-baseline #js {:user #js {:name "Alice" :profile #js {:age 30}}}]
    (merge {:id :memoization/equality-deep-hit
            :category :memoization
            :name "Equality Check - Deep (Hit)"}
           (utils/bench-compare
             #(cljs-are-equal? prev-js next-js)
             #(baseline/equality-check-shallow prev-js-baseline next-js-baseline)
             {:iterations 50000}))))

(defn bench-equality-deep-miss []
  (let [prev-js #js {:cljsProps props-deep-1 :children nil}
        next-js #js {:cljsProps props-deep-3 :children nil}
        prev-js-baseline #js {:user #js {:name "Alice" :profile #js {:age 30}}}
        next-js-baseline #js {:user #js {:name "Bob" :profile #js {:age 25}}}]
    (merge {:id :memoization/equality-deep-miss
            :category :memoization
            :name "Equality Check - Deep (Miss)"}
           (utils/bench-compare
             #(cljs-are-equal? prev-js next-js)
             #(baseline/equality-check-shallow prev-js-baseline next-js-baseline)
             {:iterations 50000}))))

(defn bench-equality-identity []
  (let [same-props props-shallow-1
        prev-js #js {:cljsProps same-props :children nil}
        next-js #js {:cljsProps same-props :children nil}
        same-js #js {:name "Alice" :age 30}]
    (merge {:id :memoization/equality-identity
            :category :memoization
            :name "Equality Check - Identity (Optimal)"}
           (utils/bench-compare
             #(cljs-are-equal? prev-js next-js)
             #(baseline/equality-check-shallow same-js same-js)
             {:iterations 100000}))))

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
