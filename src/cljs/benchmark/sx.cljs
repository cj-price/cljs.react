(ns cljs.benchmark.sx
  "sx styling hot paths.

  `use-sx`'s steady state is a cache probe, not a compile — so the numbers that
  matter are the warm ones. `:sx/compile-cold` is included for scale, not as a
  per-render cost.

  The registry writes into `<head>`, so this namespace pulls in jsdom the same
  way the test suite does. It is required for its side effect at load time,
  before any benchmark runs."
  (:require ["global-jsdom/register"]
            [clojure.string :as str]
            [cljs.benchmark.utils :as utils]
            [cljs.react.sx.compile :as sxc]
            [cljs.react.sx.sheet :as sheet]
            [cljs.react.sx.theme :as theme]))

(def ^:private bps (theme/breakpoint-values theme/default-theme-normalized))
(def ^:private bpk (:cx/bp-key theme/default-theme-normalized))

(def ^:private simple-sx
  {:p 2 :color :palette.primary.main :display :flex})

(def ^:private rich-sx
  {:p 2 :m 1 :color :palette.primary.main :bgcolor :palette.background.paper
   :border-radius 1 :display :flex :width {:xs "100%" :md 300}
   :&:hover {:box-shadow 2 :color :palette.primary.dark}})

(defn- hand-rolled-css
  "Reference point for the compiler: the same rule assembled by string
  concatenation, with no normalization, ordering or token resolution."
  []
  (str ".c{padding:calc(var(--cx-spacing) * 2)"
       ";color:var(--cx-palette-primary-main)"
       ";display:flex}"))

;; ============================================================================
;; Compilation
;; ============================================================================

(defn bench-compile-cold []
  (merge {:id :sx/compile-cold
          :category :sx
          :name "Compile sx map to CSS (cold, no cache)"}
         (utils/bench-compare
           #(sxc/rules->css (sxc/sx->rules simple-sx bps) ".c")
           #(hand-rolled-css)
           {:iterations 20000})))

(defn bench-compile-cold-rich []
  (merge {:id :sx/compile-cold-rich
          :category :sx
          :name "Compile sx map to CSS (nested + responsive)"}
         (utils/bench-compare
           #(sxc/rules->css (sxc/sx->rules rich-sx bps) ".c")
           #(hand-rolled-css)
           {:iterations 10000})))

;; ============================================================================
;; Cache hits — the steady-state cost of use-sx
;; ============================================================================

(defn bench-class-for-warm []
  ;; The number that matters: what a mounted component pays when its sx map is
  ;; already registered.
  (sheet/class-for simple-sx bpk bps)
  (let [probe #js {"k" "cx-warm"}]
    (merge {:id :sx/class-for-warm
            :category :sx
            :name "class-for (warm cache hit)"}
           (utils/bench-compare
             #(sheet/class-for simple-sx bpk bps)
             #(aget probe "k")
             {:iterations 100000}))))

(defn bench-static-style-identity []
  ;; defstyle's payoff: the probe collapses to a generation compare plus one
  ;; property lookup, with no structural `=` on the sx map at all.
  (let [ss (sheet/static-style rich-sx)
        probe #js {"k" "cx-static"}]
    (sheet/ensure-static! ss bpk bps)
    (merge {:id :sx/static-style-identity
            :category :sx
            :name "defstyle var (warm identity hit)"}
           (utils/bench-compare
             #(sheet/ensure-static! ss bpk bps)
             #(aget probe "k")
             {:iterations 100000}))))

(defn bench-compose-2 []
  (let [parts [{:p 1 :color "red"} {:color "blue"}]]
    (sheet/class-for-composed parts bpk bps)
    (merge {:id :sx/compose-2
            :category :sx
            :name "compose 2 sx maps (warm)"}
           (utils/bench-compare
             #(sheet/class-for-composed parts bpk bps)
             #(merge (first parts) (second parts))
             {:iterations 50000}))))

;; ============================================================================
;; Theme
;; ============================================================================

(defn bench-theme-vars-flatten []
  (merge {:id :sx/theme-vars-flatten
          :category :sx
          :name "theme -> CSS custom properties"}
         (utils/bench-compare
           #(theme/theme->css-vars theme/default-theme-normalized)
           #(str/join "," (keys (:palette theme/default-theme)))
           {:iterations 5000})))

;; ============================================================================
;; Suite Runner
;; ============================================================================

(defn run-all
  "Run all sx styling benchmarks"
  []
  [(bench-compile-cold)
   (bench-compile-cold-rich)
   (bench-class-for-warm)
   (bench-static-style-identity)
   (bench-compose-2)
   (bench-theme-vars-flatten)])
