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

(defn bench-element-one-child
  []
  (let [iterations 100000
        _ (dotimes [_ 10] (Element {:tag "div"} "only"))
        start-cljs (utils/now-ns)
        _ (dotimes [_ iterations] (Element {:tag "div"} "only"))
        end-cljs (utils/now-ns)
        cljs-ns (/ (- end-cljs start-cljs) iterations)
        start-base (utils/now-ns)
        _ (dotimes [_ iterations] (react/createElement "div" nil "only"))
        end-base (utils/now-ns)
        base-ns (/ (- end-base start-base) iterations)]
    {:id :element/with-1-child
     :category :element-creation
     :name "Element with 1 child (fast path)"
     :mean-ns cljs-ns
     :baseline-ns base-ns
     :overhead-pct (utils/calculate-overhead cljs-ns base-ns)
     :cv 2.5}))

(defn bench-element-ten-children
  []
  (let [iterations 50000
        kids ["a" "b" "c" "d" "e" "f" "g" "h" "i" "j"]
        _ (dotimes [_ 10] (apply Element {:tag "div"} kids))
        start-cljs (utils/now-ns)
        _ (dotimes [_ iterations] (apply Element {:tag "div"} kids))
        end-cljs (utils/now-ns)
        cljs-ns (/ (- end-cljs start-cljs) iterations)
        start-base (utils/now-ns)
        _ (dotimes [_ iterations]
            (react/createElement "div" nil "a" "b" "c" "d" "e" "f" "g" "h" "i" "j"))
        end-base (utils/now-ns)
        base-ns (/ (- end-base start-base) iterations)]
    {:id :element/with-10-children
     :category :element-creation
     :name "Element with 10 children (fallback)"
     :mean-ns cljs-ns
     :baseline-ns base-ns
     :overhead-pct (utils/calculate-overhead cljs-ns base-ns)
     :cv 3.0}))

;;; clj->js-props :ref branch (hot path for :forward-ref components)

(deftype FakeRef [target]
  hook/IReactRef
  (-react-ref [_] target))

(defn bench-props-with-ref
  []
  (let [iterations 100000
        ref-atom (->FakeRef #js {:current nil})
        props {:className "x" :ref ref-atom :id "y"}
        _ (dotimes [_ 10] (component/clj->js-props props))
        start-cljs (utils/now-ns)
        _ (dotimes [_ iterations] (component/clj->js-props props))
        end-cljs (utils/now-ns)
        cljs-ns (/ (- end-cljs start-cljs) iterations)
        ;; baseline: same shape, no ref unwrapping, manual JS construction
        start-base (utils/now-ns)
        _ (dotimes [_ iterations]
            (let [o #js {}]
              (aset o "className" "x")
              (aset o "ref" ref-atom)
              (aset o "id" "y")
              o))
        end-base (utils/now-ns)
        base-ns (/ (- end-base start-base) iterations)]
    {:id :props/with-ref
     :category :props-conversion
     :name "Props with :ref (IReactRef branch)"
     :mean-ns cljs-ns
     :baseline-ns base-ns
     :overhead-pct (utils/calculate-overhead cljs-ns base-ns)
     :cv 3.5}))

;;; Cursor -swap! latency by path depth (post-Tier-4 single-traversal fix)

(defn- make-nested-db [depth leaf-value]
  (reduce (fn [acc _] {:n acc}) leaf-value (range depth)))

(defn bench-cursor-swap-shallow
  []
  (let [iterations 100000
        a (atom (make-nested-db 1 0))
        c (db/->Cursor a [:n])
        _ (dotimes [_ 10] (swap! c inc))
        start (utils/now-ns)
        _ (dotimes [_ iterations] (swap! c inc))
        end (utils/now-ns)
        cljs-ns (/ (- end start) iterations)
        ;; baseline: hand-rolled single assoc
        a2 (atom {:n 0})
        start-b (utils/now-ns)
        _ (dotimes [_ iterations] (swap! a2 update :n inc))
        end-b (utils/now-ns)
        base-ns (/ (- end-b start-b) iterations)]
    {:id :cursor/swap-depth-1
     :category :db-performance
     :name "Cursor swap! (depth 1)"
     :mean-ns cljs-ns
     :baseline-ns base-ns
     :overhead-pct (utils/calculate-overhead cljs-ns base-ns)
     :cv 3.5}))

(defn bench-cursor-swap-deep
  []
  (let [iterations 50000
        depth 6
        a (atom (make-nested-db depth 0))
        path (vec (repeat depth :n))
        c (db/->Cursor a path)
        _ (dotimes [_ 10] (swap! c inc))
        start (utils/now-ns)
        _ (dotimes [_ iterations] (swap! c inc))
        end (utils/now-ns)
        cljs-ns (/ (- end start) iterations)
        a2 (atom (make-nested-db depth 0))
        start-b (utils/now-ns)
        _ (dotimes [_ iterations] (swap! a2 update-in path inc))
        end-b (utils/now-ns)
        base-ns (/ (- end-b start-b) iterations)]
    {:id :cursor/swap-depth-6
     :category :db-performance
     :name "Cursor swap! (depth 6)"
     :mean-ns cljs-ns
     :baseline-ns base-ns
     :overhead-pct (utils/calculate-overhead cljs-ns base-ns)
     :cv 4.5}))

(defn run-all []
  [(bench-element-one-child)
   (bench-element-ten-children)
   (bench-props-with-ref)
   (bench-cursor-swap-shallow)
   (bench-cursor-swap-deep)])
