(ns cljs.benchmark.utils
  (:require [clojure.string :as string]
            [cljs.reader :as reader]
            ["fs" :as fs]
            ["path" :as path]))

;; ============================================================================
;; Number Formatting
;; ============================================================================

(defn format-number
  "Format number with thousand separators (e.g. 100000 -> \"100,000\")."
  [n]
  ;; Lookahead inserts a comma between digit pairs where 3-digit groups follow
  ;; up to end-of-number. Works for integers and decimals; only the integer
  ;; portion gets commas.
  (string/replace (str n) #"\B(?=(\d{3})+(?!\d))" ","))

(defn format-duration
  "Convert nanoseconds to appropriate unit (ns, μs, ms)"
  [ns-time]
  (cond
    (< ns-time 1000) (str (format-number (.toFixed ns-time 1)) " ns/op")
    (< ns-time 1000000) (str (.toFixed (/ ns-time 1000) 1) " μs/op")
    :else (str (.toFixed (/ ns-time 1000000) 1) " ms/op")))

(defn format-percentage
  "Format percentage with 1 decimal place"
  [pct]
  (str (.toFixed pct 1) "%"))

;; ============================================================================
;; Statistics
;; ============================================================================

(defn calculate-mean
  "Calculate mean of a sequence of numbers"
  [nums]
  (/ (reduce + nums) (count nums)))

(defn calculate-std-dev
  "Calculate standard deviation"
  [nums]
  (let [mean (calculate-mean nums)
        variance (/ (reduce + (map #(* (- % mean) (- % mean)) nums))
                    (count nums))]
    (Math/sqrt variance)))

(defn calculate-median
  "Median of a sequence (linear-ish: sorts the seq)"
  [nums]
  (let [sorted (vec (sort nums))
        n (count sorted)
        mid (quot n 2)]
    (if (odd? n)
      (nth sorted mid)
      (/ (+ (nth sorted (dec mid)) (nth sorted mid)) 2))))

(defn calculate-stats
  "Calculate mean, median, std dev, and coefficient of variation from samples"
  [samples]
  (let [mean (calculate-mean samples)
        std-dev (calculate-std-dev samples)
        cv (if (zero? mean) 0 (* 100 (/ std-dev mean)))]
    {:mean mean
     :median (calculate-median samples)
     :std-dev std-dev
     :cv cv
     :min (apply min samples)
     :max (apply max samples)}))

(defn calculate-overhead
  "Calculate percentage overhead: ((cljs-react - baseline) / baseline) * 100"
  [cljs-react-time baseline-time]
  (if (zero? baseline-time)
    0
    (* 100 (/ (- cljs-react-time baseline-time) baseline-time))))

;; ============================================================================
;; DOM Helpers (for React rendering tests)
;; ============================================================================

(defn create-container
  "Create a DOM container for React rendering tests"
  []
  (let [container (js/document.createElement "div")]
    (.setAttribute container "id" (str "benchmark-" (random-uuid)))
    (.appendChild js/document.body container)
    container))

(defn cleanup-container
  "Remove DOM container after test"
  [container]
  (when container
    (.removeChild js/document.body container)))

;; ============================================================================
;; File I/O
;; ============================================================================

(defn read-edn-file
  "Read and parse EDN file, return nil if file doesn't exist"
  [file-path]
  (try
    (let [content (.readFileSync fs file-path "utf8")]
      (reader/read-string content))
    (catch js/Error e
      (when-not (= (.-code e) "ENOENT")
        (println "Error reading" file-path ":" (.-message e)))
      nil)))

(defn write-edn-file
  "Write ClojureScript data structure to EDN file"
  [file-path data]
  (try
    (let [dir-path (path/dirname file-path)]
      ;; Ensure directory exists
      (.mkdirSync fs dir-path #js {:recursive true})
      ;; Write file
      (.writeFileSync fs file-path (pr-str data) "utf8"))
    (catch js/Error e
      (println "Error writing" file-path ":" (.-message e)))))

(defn read-thresholds
  "Load threshold configuration from benchmark/thresholds.edn"
  []
  (or (read-edn-file "benchmark/thresholds.edn")
      {}))

(defn read-baselines
  "Load baseline measurements from benchmark/baselines.edn"
  []
  (read-edn-file "benchmark/baselines.edn"))

(defn write-baselines
  "Save baseline measurements to benchmark/baselines.edn"
  [results]
  (let [baseline-data {:version "0.1.0"
                       :timestamp (js/Date.)
                       :node-version js/process.version
                       :react-version "19.0.0"
                       :results results}]
    (write-edn-file "benchmark/baselines.edn" baseline-data)
    (println "\nBaseline saved to benchmark/baselines.edn")))

;; ============================================================================
;; Result Display
;; ============================================================================

(def colors
  {:reset "\033[0m"
   :red "\033[31m"
   :green "\033[32m"
   :yellow "\033[33m"
   :blue "\033[34m"
   :gray "\033[90m"
   :bold "\033[1m"})

(defn colorize
  "Apply ANSI color to text"
  [color text]
  (str (get colors color "") text (:reset colors)))

(defn status-symbol
  "Return colored status symbol based on result"
  [status]
  (case status
    :pass (colorize :green "✓ PASS")
    :fail (colorize :red "✗ FAIL")
    :warn (colorize :yellow "⚠ WARN")
    (colorize :gray "• SKIP")))

(defn print-separator
  "Print horizontal separator line"
  ([] (print-separator 80))
  ([width] (println (apply str (repeat width "-")))))

(defn print-header
  "Print section header"
  [title]
  (println)
  (print-separator)
  (println (colorize :bold title))
  (print-separator))

(defn check-threshold
  "Check if result passes threshold, return {:status :pass/:fail/:warn :reason <str>}"
  [benchmark-id result thresholds]
  (let [threshold (get thresholds benchmark-id)
        {:keys [max-overhead-pct max-time-ms]} threshold
        {:keys [overhead-pct mean-ns]} result
        time-ms (/ mean-ns 1000000.0)]
    (cond
      (nil? threshold)
      {:status :skip :reason "No threshold defined"}

      (and max-overhead-pct (> overhead-pct max-overhead-pct))
      {:status :warn
       :reason (str "Overhead " (format-percentage overhead-pct)
                   " exceeds threshold " (format-percentage max-overhead-pct))}

      (and max-time-ms (> time-ms max-time-ms))
      {:status :fail
       :reason (str "Time " (.toFixed time-ms 3) "ms"
                   " exceeds threshold " max-time-ms "ms")}

      :else
      {:status :pass})))

(defn display-benchmark-result
  "Display a single benchmark result"
  [benchmark-id result thresholds indent]
  (let [{:keys [name mean-ns baseline-ns overhead-pct cv baseline-cv
                samples iterations min-ns max-ns]} result
        threshold-check (check-threshold benchmark-id result thresholds)
        status (:status threshold-check)]
    (println (str indent name))
    (when (and samples iterations)
      (println (str indent "  (n=" samples " × " (format-number iterations) " iters)")))
    (println (str indent "  cljs.react:  " (format-duration mean-ns)
                 "  (±" (format-percentage cv) ")"
                 (when (and min-ns max-ns)
                   (str "  [" (format-duration min-ns) " – " (format-duration max-ns) "]"))))
    (when baseline-ns
      (println (str indent "  raw React:   " (format-duration baseline-ns)
                   "  (±" (format-percentage (or baseline-cv 0)) ")")))
    (when overhead-pct
      (println (str indent "  Overhead:    " (format-percentage overhead-pct))))
    (println (str indent "  Status:      " (status-symbol status)))
    (when (:reason threshold-check)
      (println (str indent "  " (colorize :yellow (:reason threshold-check)))))
    (println)))

(defn display-results
  "Display all benchmark results with formatted output"
  [results thresholds]
  (println)
  (println (colorize :bold "cljs.react Performance Benchmark Suite"))
  (println (apply str (repeat 80 "=")))
  (println "Environment:")
  (println "  Node.js:     " js/process.version)
  (println "  React:        19.0.0")
  (println "  cljs.react:   0.1.0")
  (println "  Date:        " (.toLocaleString (js/Date.)))

  ;; Group results by category
  (doseq [[category benchmarks] (group-by :category results)]
    (print-header (clojure.string/upper-case (name category)))
    (doseq [benchmark benchmarks]
      (display-benchmark-result (:id benchmark) benchmark thresholds "  ")))

  ;; Summary
  (print-separator)
  (println (colorize :bold "SUMMARY"))
  (print-separator)
  (let [checks (map #(check-threshold (:id %) % thresholds) results)
        total (count results)
        passed (count (filter #(= :pass (:status %)) checks))
        failed (count (filter #(= :fail (:status %)) checks))
        warnings (count (filter #(= :warn (:status %)) checks))]
    (println "  Total:       " total)
    (println "  Passed:      " (colorize :green passed))
    (println "  Failed:      " (colorize :red failed))
    (println "  Warnings:    " (colorize :yellow warnings))
    (println)
    (if (> failed 0)
      (println "  Overall:     " (status-symbol :fail))
      (if (> warnings 0)
        (println "  Overall:     " (status-symbol :warn) "(with warnings)")
        (println "  Overall:     " (status-symbol :pass))))
    (println)
    {:total total :passed passed :failed failed :warnings warnings}))

(defn exit-with-status
  "Exit process with appropriate status code based on results"
  [results thresholds fail-on-warning?]
  (let [checks (map #(check-threshold (:id %) % thresholds) results)
        has-failures (some #(= :fail (:status %)) checks)
        has-warnings (some #(= :warn (:status %)) checks)]
    (cond
      has-failures (js/process.exit 1)
      (and fail-on-warning? has-warnings) (js/process.exit 1)
      :else (js/process.exit 0))))

;; ============================================================================
;; Timing Utilities
;; ============================================================================

(defn now-ns
  "Get current time in nanoseconds (using hrtime for precision)"
  []
  (let [hr-time (js/process.hrtime)]
    (+ (* (aget hr-time 0) 1e9) (aget hr-time 1))))

(defn time-operation
  "Time an operation and return duration in nanoseconds"
  [f]
  (let [start (now-ns)
        _ (f)
        end (now-ns)]
    (- end start)))

;; ============================================================================
;; Bench Harness
;; ============================================================================
;;
;; Each measurement takes `samples` independent samples of `iterations` calls,
;; preceded by `warmup` full-size passes for JIT settling. Per-op times are
;; computed per-sample, then aggregated into mean / median / stddev so the CV
;; reflects actual run-to-run variance rather than a fabricated constant.

(def ^:private default-bench-opts
  {:iterations 100000
   :samples    10
   :warmup     2})

(defn- one-sample-per-op-ns
  "Run f for `iterations` and return mean ns/op for this sample."
  [iterations f]
  (let [start (now-ns)]
    (dotimes [_ iterations] (f))
    (/ (- (now-ns) start) iterations)))

(defn bench
  "Take repeated samples of (f). Returns the stats map from calculate-stats
  augmented with :samples and :iterations."
  ([f] (bench f nil))
  ([f opts]
   (let [{:keys [iterations samples warmup]} (merge default-bench-opts opts)]
     (dotimes [_ warmup]
       (dotimes [_ iterations] (f)))
     (assoc (calculate-stats
              (vec (for [_ (range samples)]
                     (one-sample-per-op-ns iterations f))))
            :samples samples
            :iterations iterations))))

(defn bench-compare
  "Bench `target-fn` and `baseline-fn` under the same opts. Returns a result map
  shaped for display: :mean-ns / :cv from target, :baseline-ns / :baseline-cv
  from baseline, plus :median-ns, :min-ns, :max-ns and :overhead-pct."
  ([target-fn baseline-fn] (bench-compare target-fn baseline-fn nil))
  ([target-fn baseline-fn opts]
   (let [t (bench target-fn opts)
         b (bench baseline-fn opts)]
     {:mean-ns     (:mean t)
      :median-ns   (:median t)
      :min-ns      (:min t)
      :max-ns      (:max t)
      :cv          (:cv t)
      :baseline-ns (:mean b)
      :baseline-cv (:cv b)
      :overhead-pct (calculate-overhead (:mean t) (:mean b))
      :samples     (:samples t)
      :iterations  (:iterations t)})))
