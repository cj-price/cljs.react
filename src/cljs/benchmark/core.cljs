(ns cljs.benchmark.core
  (:require [clojure.string :as string]
            [cljs.benchmark.element :as element]
            [cljs.benchmark.props :as props]
            [cljs.benchmark.component :as component]
            [cljs.benchmark.memoization :as memoization]
            [cljs.benchmark.perf-extra :as perf-extra]
            [cljs.benchmark.ssr :as ssr]
            [cljs.benchmark.sx :as sx]
            [cljs.benchmark.utils :as utils]))

;; ============================================================================
;; CLI Argument Parsing
;; ============================================================================

(defn- arg-value
  "Return the value following `flag` in args, or nil. Supports both
  `--emit path` and `--emit=path` forms."
  [args flag]
  (loop [[a & more] args]
    (cond
      (nil? a) nil
      (= a flag) (first more)
      (string/starts-with? a (str flag "=")) (subs a (inc (count flag)))
      :else (recur more))))

(defn parse-args
  "Parse command line arguments"
  [args]
  (let [args-set (set args)]
    {:save-baseline? (contains? args-set "--save-baseline")
     :compare-baseline? (contains? args-set "--compare-baseline")
     :fail-on-warning? (contains? args-set "--fail-on-warning")
     :emit-path (arg-value args "--emit")
     :help? (or (contains? args-set "--help")
                (contains? args-set "-h"))}))

(defn print-help
  "Print usage information"
  []
  (println "cljs.react Benchmark Suite")
  (println)
  (println "Usage:")
  (println "  node target/benchmark.js [OPTIONS]")
  (println)
  (println "Options:")
  (println "  --save-baseline      Save current results as new baseline")
  (println "  --compare-baseline   Compare against saved baseline (TODO)")
  (println "  --emit <path>        Write machine-readable results (EDN) to <path>")
  (println "  --fail-on-warning    Exit with code 1 on warnings")
  (println "  --help, -h           Show this help message")
  (println))

;; ============================================================================
;; Main Entry Point
;; ============================================================================

(defn run-all-benchmarks
  "Run all benchmark suites and collect results"
  []
  (println "\nRunning benchmarks...\n")

  ;; Run all benchmark suites
  (let [results (concat
                  (element/run-all)
                  (props/run-all)
                  (component/run-all)
                  (memoization/run-all)
                  (perf-extra/run-all)
                  (ssr/run-all)
                  (sx/run-all))]
    results))

(defn -main
  "Main entry point for benchmark CLI"
  [& args]
  (let [opts (parse-args args)]
    (cond
      (:help? opts)
      (print-help)

      :else
      (let [thresholds (utils/read-thresholds)
            results    (run-all-benchmarks)]
        (utils/display-results results thresholds)

        ;; Save baseline if requested
        (when (:save-baseline? opts)
          (utils/write-baselines
            (into {} (map (fn [r] [(:id r) (select-keys r [:mean-ns :baseline-ns :overhead-pct])]) results))))

        ;; Emit machine-readable results for the HEAD-vs-HEAD~1 regression gate.
        (when-let [path (:emit-path opts)]
          (utils/write-edn-file
            path
            (into {} (map (fn [r] [(:id r) (select-keys r [:median-ns :mean-ns :cv])]) results)))
          (println (str "\nResults written to " path)))

        ;; Exit code. In --emit mode the run feeds the relative HEAD~1 regression
        ;; gate, so the absolute :max-time-ms thresholds (tuned for a fast dev
        ;; machine) must NOT gate — a slow CI runner would otherwise abort the
        ;; whole comparison with a non-regression failure. Exit 0 and let
        ;; regression.clj decide. Standalone `bb bench` keeps the threshold gate.
        (if (:emit-path opts)
          (js/process.exit 0)
          (utils/exit-with-status results thresholds (:fail-on-warning? opts)))))))

;; Set main function for Node.js execution
(set! *main-cli-fn* -main)
