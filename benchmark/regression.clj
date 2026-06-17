#!/usr/bin/env bb
;; Compare benchmark results for the library at a base ref vs the current tree
;; and fail on a reproducible per-bench regression.
;;
;; Each side is measured over SEVERAL runs (separate process invocations). For
;; each bench we take the MIN of its per-run median — benchmark noise is
;; one-sided (interference only ever slows a run down), so the fastest run is
;; the cleanest signal and min-of-N cancels transient spikes. This is the
;; "3+ runs, reproducible" discipline from CLAUDE.md, made mechanical.
;;
;; A bench is a regression when its best current median exceeds its best base
;; median by more than max(REL-FLOOR, CV-MULT × CV) — so normal variance (the
;; suite's own CV) never trips the gate.
;;
;; The 15% floor is empirically calibrated, not arbitrary: even with best-of-5,
;; sub-µs micro-benches (e.g. :props/shallow, ~300ns) show ~10-11% spread
;; between the *best* runs of each side on a quiet machine — a 10% floor
;; false-positives on unchanged code. 15% sits just above that noise. The
;; deliberate, accepted tradeoff: reproducible regressions below ~15% on
;; low-CV benches are invisible; this gate targets the architectural / 2x-class
;; regressions that matter, not sub-15% creep. Tighten the floor only alongside
;; more runs and a quieter (dedicated) runner.
;;
;; Usage: bb benchmark/regression.clj <base1.edn,base2.edn,...> <cur1.edn,cur2.edn,...>
;; Exit 0 if nothing regressed beyond tolerance, 1 otherwise.
(ns regression
  (:require [clojure.edn :as edn]
            [clojure.string :as str]))

(def rel-floor 0.15) ;; always tolerate at least +15% (calibrated to machine noise)
(def cv-mult   3.0)  ;; plus 3× the noisier side's CV (CV stored as a percentage)

(defn- read-runs [list-arg]
  (->> (str/split list-arg #",")
       (remove str/blank?)
       (mapv (fn [p] (edn/read-string (slurp p))))))

(defn- best
  "For each bench id seen across `runs`, the min per-run median and the max
  per-run CV (the conservative noise estimate)."
  [runs]
  (reduce
    (fn [acc run]
      (reduce-kv
        (fn [a id {:keys [median-ns cv]}]
          (-> a
              (update-in [id :median] (fnil min ##Inf) median-ns)
              (update-in [id :cv] (fnil max 0) (or cv 0))))
        acc run))
    {} runs))

(defn- assess [b c]
  (let [bm (:median b) cm (:median c)
        cv (/ (max (:cv b 0) (:cv c 0)) 100.0)
        tol (max rel-floor (* cv-mult cv))]
    {:base bm :cur cm
     :ratio (if (and bm (pos? bm)) (/ cm bm) ##Inf)
     :tol tol
     :regressed (boolean (and bm (pos? bm) (> cm (* bm (+ 1.0 tol)))))}))

(let [[base-arg cur-arg] *command-line-args*]
  (when (or (nil? base-arg) (nil? cur-arg))
    (println "usage: bb benchmark/regression.clj <base1.edn,...> <cur1.edn,...>")
    (System/exit 2))
  (let [base (best (read-runs base-arg))
        cur  (best (read-runs cur-arg))
        ids  (filter #(contains? base %) (keys cur))
        rows (sort-by :ratio > (map (fn [id] (assoc (assess (base id) (cur id)) :id id)) ids))
        regressions (filter :regressed rows)
        only-cur (remove #(contains? base %) (keys cur))
        only-base (remove #(contains? cur %) (keys base))]
    (println (format "%-36s %11s %11s %8s %6s  %s" "bench (best-of-N)" "base(ns)" "cur(ns)" "ratio" "tol" "status"))
    (println (apply str (repeat 84 \-)))
    (doseq [r rows]
      (println (format "%-36s %11.1f %11.1f %7.2fx %5.0f%%  %s"
                       (str (:id r)) (double (:base r)) (double (:cur r))
                       (double (:ratio r)) (* 100.0 (:tol r))
                       (if (:regressed r) "← REGRESSION" "ok"))))
    (when (seq only-cur)
      (println (str "\nNew benches (no baseline, not gated): " (mapv str only-cur))))
    (when (seq only-base)
      (println (str "Dropped benches (in baseline only): " (mapv str only-base))))
    (if (seq regressions)
      (do (println (format "\n%d bench(es) regressed beyond tolerance (reproducible across runs)." (count regressions)))
          (System/exit 1))
      (do (println "\nNo regressions beyond per-bench tolerance.")
          (System/exit 0)))))
