(ns cljs.react.demo.concurrent
  (:require [cljs.react.core :refer [use-state use-transition use-deferred-value]]
            [cljs.react.demo.util :refer [CodeAndOutput Div P Span H2
                                          Input Section]])
  (:require-macros [cljs.react.core :refer [defnc]]))

;; A deliberately large list so projecting it is heavy enough that the
;; concurrent features visibly help keep the input responsive.
(def ^:private items
  (mapv (fn [i] (str "item-" i)) (range 3000)))

(defn- matches [q]
  (if (empty? q)
    items
    (filterv #(re-find (re-pattern q) %) items)))

(defnc ResultList
  [{:keys [rows]}]
  (Div {:className "max-h-48 overflow-auto rounded-lg border border-gray-200 bg-gray-50 p-2 font-mono text-xs"}
    (if (empty? rows)
      (Div {:className "text-gray-400 italic p-2"} "no matches")
      (for [row rows]
        (Div {:key row :className "px-2 py-0.5 text-gray-700"} row)))))

;;;; use-transition — keep the input snappy while a heavy list update lags

(defnc TransitionDemo
  []
  (let [query            (use-state "")
        rows             (use-state items)
        [pending? start] (use-transition)]
    (Div {:className "w-full space-y-3"}
      (Input {:type "text"
              :aria-label "Filter 3000 items"
              :value @query
              :placeholder "Filter 3000 items (e.g. \"1\")…"
              :className "w-full px-4 py-2.5 border-2 border-gray-200 rounded-lg focus:border-koi-orange focus:ring-2 focus:ring-koi-orange/20 outline-none transition-all"
              :onChange (fn [e]
                          (let [v (-> e .-target .-value)]
                            ;; Urgent: the controlled input updates immediately.
                            (reset! query v)
                            ;; Non-urgent: the expensive list re-projects inside
                            ;; a transition, so typing never blocks on it.
                            (start (fn [] (reset! rows (matches v))))))})
      (Div {:className "flex items-center gap-2 text-xs"}
        (Span {:aria-hidden true
               :className (str "inline-block w-2 h-2 rounded-full "
                               (if pending? "bg-amber-500 animate-pulse" "bg-emerald-500"))})
        (Span {:role "status" :aria-live "polite"
               :className "uppercase tracking-wide text-gray-500"}
          (if pending? "updating list…" (str (count @rows) " matches"))))
      (ResultList {:rows @rows})
      (P {:className "text-xs text-gray-500 italic"}
        "The input stays responsive because the list update runs inside start-transition."))))

;;;; use-deferred-value — render a lagging copy of a fast-changing value

(defnc DeferredDemo
  []
  (let [text     (use-state "")
        deferred (use-deferred-value @text)
        stale?   (not= @text deferred)
        rows     (matches deferred)]
    (Div {:className "w-full space-y-3"}
      (Input {:type "text"
              :aria-label "Filter items (deferred)"
              :value @text
              :placeholder "Type to filter — the list lags behind…"
              :className "w-full px-4 py-2.5 border-2 border-gray-200 rounded-lg focus:border-koi-orange focus:ring-2 focus:ring-koi-orange/20 outline-none transition-all"
              :onChange #(reset! text (-> % .-target .-value))})
      (Div {:className "flex items-center gap-2 text-xs"}
        (Span {:aria-hidden true
               :className (str "inline-block w-2 h-2 rounded-full "
                               (if stale? "bg-amber-500 animate-pulse" "bg-emerald-500"))})
        (Span {:role "status" :aria-live "polite"
               :className "uppercase tracking-wide text-gray-500"}
          (if stale? "list catching up…" (str (count rows) " matches"))))
      (Div {:className (str "transition-opacity " (when stale? "opacity-50"))}
        (ResultList {:rows rows}))
      (P {:className "text-xs text-gray-500 italic"}
        "use-deferred-value feeds the list a value that lags the input, dimming while it catches up."))))

(defnc ConcurrentTab
  []
  (Section
    (H2 "⏱️ Concurrent — Transitions & Deferred Values")

    (CodeAndOutput
     {:title "useTransition — non-urgent updates"
      :code "(let [query (use-state \"\")\n      rows  (use-state items)\n      [pending? start] (use-transition)]\n  (Input\n    {:value @query\n     :onChange\n     (fn [e]\n       (let [v (target-value e)]\n         (reset! query v)            ; urgent\n         (start                      ; non-urgent\n           #(reset! rows\n              (filter-items v)))))})\n  (when pending? (Span \"updating…\")))"}
     (TransitionDemo))

    (CodeAndOutput
     {:title "useDeferredValue — lagging copy"
      :code "(let [text     (use-state \"\")\n      deferred (use-deferred-value @text)\n      stale?   (not= @text deferred)]\n  (Input {:value @text\n          :onChange #(reset! text\n                       (target-value %))})\n  (Div {:className (when stale? \"opacity-50\")}\n    (ResultList\n      {:rows (filter-items deferred)})))"}
     (DeferredDemo))))
