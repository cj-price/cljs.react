(ns cljs.benchmark.perf-extra
  "Additional benchmarks covering hot paths that the initial baseline missed:
   - Element fast-path for 1/10 children
   - clj->js-props :ref (IReactRef) branch
   - Cursor -swap! at different path depths."
  (:require [cljs.react.core :refer [Element]]
            [cljs.react.component :as component]
            [cljs.react.db :as db]
            [cljs.react.hook :as hook]
            [cljs.benchmark.utils :as utils]
            ["react" :as react]))

;;; Element with varying child counts

(defn bench-element-one-child []
  (merge {:id :element/with-1-child
          :category :element-creation
          :name "Element with 1 child (fast path)"}
         (utils/bench-compare
           #(Element {:tag "div"} "only")
           #(react/createElement "div" nil "only")
           {:iterations 100000})))

(defn bench-element-ten-children []
  (let [kids ["a" "b" "c" "d" "e" "f" "g" "h" "i" "j"]]
    (merge {:id :element/with-10-children
            :category :element-creation
            :name "Element with 10 children (fallback)"}
           (utils/bench-compare
             #(apply Element {:tag "div"} kids)
             #(react/createElement "div" nil "a" "b" "c" "d" "e" "f" "g" "h" "i" "j")
             {:iterations 50000}))))

;;; clj->js-props :ref branch (hot path for :forward-ref components)

(deftype FakeRef [target]
  hook/IReactRef
  (-react-ref [_] target))

(defn bench-props-with-ref []
  (let [ref-atom (->FakeRef #js {:current nil})
        props {:className "x" :ref ref-atom :id "y"}]
    (merge {:id :props/with-ref
            :category :props-conversion
            :name "Props with :ref (IReactRef branch)"}
           (utils/bench-compare
             #(component/clj->js-props props)
             #(let [o #js {}]
                (aset o "className" "x")
                (aset o "ref" ref-atom)
                (aset o "id" "y")
                o)
             {:iterations 100000}))))

;;; Cursor -swap! latency by path depth (post-Tier-4 single-traversal fix)

(defn- make-nested-db [depth leaf-value]
  (reduce (fn [acc _] {:n acc}) leaf-value (range depth)))

(defn bench-cursor-swap-shallow []
  (let [a (atom (make-nested-db 1 0))
        c (db/->Cursor a [:n])
        a2 (atom {:n 0})]
    (merge {:id :cursor/swap-depth-1
            :category :db-performance
            :name "Cursor swap! (depth 1)"}
           (utils/bench-compare
             #(swap! c inc)
             #(swap! a2 update :n inc)
             {:iterations 100000}))))

(defn bench-cursor-swap-deep []
  (let [depth 6
        a (atom (make-nested-db depth 0))
        path (vec (repeat depth :n))
        c (db/->Cursor a path)
        a2 (atom (make-nested-db depth 0))]
    (merge {:id :cursor/swap-depth-6
            :category :db-performance
            :name "Cursor swap! (depth 6)"}
           (utils/bench-compare
             #(swap! c inc)
             #(swap! a2 update-in path inc)
             {:iterations 50000}))))

(defn run-all []
  [(bench-element-one-child)
   (bench-element-ten-children)
   (bench-props-with-ref)
   (bench-cursor-swap-shallow)
   (bench-cursor-swap-deep)])
