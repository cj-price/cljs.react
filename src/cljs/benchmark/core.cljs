(ns cljs.benchmark.core
  (:require [cljs.benchmark.element :as element]
            [cljs.benchmark.props :as props]
            [cljs.benchmark.component :as component]
            [cljs.benchmark.memoization :as memoization]
            [cljs.benchmark.utils :as utils]))

;; ============================================================================
;; CLI Argument Parsing
;; ============================================================================

(defn parse-args
  "Parse command line arguments"
  [args]
  (let [args-set (set args)]
    {:save-baseline? (contains? args-set "--save-baseline")
     :compare-baseline? (contains? args-set "--compare-baseline")
     :fail-on-warning? (contains? args-set "--fail-on-warning")
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
                  (memoization/run-all))]
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
            results (run-all-benchmarks)
            summary (utils/display-results results thresholds)]

        ;; Save baseline if requested
        (when (:save-baseline? opts)
          (utils/write-baselines
            (into {} (map (fn [r] [(:id r) (select-keys r [:mean-ns :baseline-ns :overhead-pct])]) results))))

        ;; Exit with appropriate code
        (utils/exit-with-status results thresholds (:fail-on-warning? opts))))))

;; Set main function for Node.js execution
(set! *main-cli-fn* -main)
