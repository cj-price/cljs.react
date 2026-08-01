(ns cljs.react.demo.concurrent
  (:require [cljs.react.core :refer [use-state use-transition use-deferred-value]]
            [cljs.react.sx :refer [use-sx]]
            [cljs.react.demo.ui :refer [CodeAndOutput Row Stack TextInput
                                        SectionTitle Caption PulseDot]]
            [cljs.react.demo.util :refer [Div Span Section]])
  (:require-macros [cljs.react.core :refer [defnc]]
                   [cljs.react.sx :refer [defstyle]]))

;; A deliberately large list so projecting it is heavy enough that the
;; concurrent features visibly help keep the input responsive.
(def ^:private items
  (mapv (fn [i] (str "item-" i)) (range 3000)))

(defn- matches [q]
  (if (empty? q)
    items
    (filterv #(re-find (re-pattern q) %) items)))

(defstyle result-list
  {:max-height "12rem" :overflow :auto :p 1 :border-radius 1
   :border "1px solid" :border-color :palette.divider
   :bgcolor :palette.surface.sunken
   :font-family :typography.font-family-mono :font-size "0.75rem"})

(defnc ResultList
  [{:keys [rows]}]
  ;; `row-cls` is computed here, not in the loop: `for` is lazy, so React
  ;; realizes it after this render returns and a `use-sx` in the body would
  ;; run with no hook dispatcher installed.
  (let [row-cls   (use-sx {:px 1 :py 0.25 :color :palette.text.secondary})
        empty-cls (use-sx {:p 1 :font-style :italic
                           :color :palette.text.disabled})]
    (Div {:className (use-sx result-list)}
      (if (empty? rows)
        (Div {:className empty-cls} "no matches")
        (for [row rows]
          (Div {:key row :className row-cls} row))))))

(defnc StatusLine
  [{:keys [busy? label]}]
  ;; Both classes unconditionally: the branch below must not change how many
  ;; hooks this render calls.
  (let [dot-cls   (use-sx {:display :inline-block
                           :width "0.5rem" :height "0.5rem"
                           :border-radius "50%"
                           :bgcolor :palette.success.main})
        label-cls (use-sx {:font-size "0.75rem" :text-transform :uppercase
                           :letter-spacing "0.06em"
                           :color :palette.text.secondary})]
    (Row {:gap 1}
      (if busy?
        (PulseDot {:tone :warning})
        (Span {:aria-hidden "true" :className dot-cls}))
      (Span {:role "status" :aria-live "polite" :className label-cls}
        label))))

;;;; use-transition — keep the input snappy while a heavy list update lags

(defnc TransitionDemo
  []
  (let [query            (use-state "")
        rows             (use-state items)
        [pending? start] (use-transition)]
    (Stack {:gap 1.5}
      (TextInput {:aria-label "Filter 3000 items"
                  :value @query
                  :placeholder "Filter 3000 items (e.g. \"1\")…"
                  :onChange (fn [e]
                              (let [v (-> e .-target .-value)]
                                ;; Urgent: the controlled input updates immediately.
                                (reset! query v)
                                ;; Non-urgent: the expensive list re-projects inside
                                ;; a transition, so typing never blocks on it.
                                (start (fn [] (reset! rows (matches v))))))})
      (StatusLine {:busy? pending?
                   :label (if pending?
                            "updating list…"
                            (str (count @rows) " matches"))})
      (ResultList {:rows @rows})
      (Caption {:className (use-sx {:font-style :italic})}
        "The input stays responsive because the list update runs inside start-transition."))))

;;;; use-deferred-value — render a lagging copy of a fast-changing value

(defnc DeferredDemo
  []
  (let [text     (use-state "")
        deferred (use-deferred-value @text)
        stale?   (not= @text deferred)
        rows     (matches deferred)]
    (Stack {:gap 1.5}
      (TextInput {:aria-label "Filter items (deferred)"
                  :value @text
                  :placeholder "Type to filter — the list lags behind…"
                  :onChange #(reset! text (-> % .-target .-value))})
      (StatusLine {:busy? stale?
                   :label (if stale?
                            "list catching up…"
                            (str (count rows) " matches"))})
      (Div {:className (use-sx [{:transition "opacity 160ms ease"}
                                (when stale? {:opacity 0.5})])}
        (ResultList {:rows rows}))
      (Caption {:className (use-sx {:font-style :italic})}
        "use-deferred-value feeds the list a value that lags the input, dimming while it catches up."))))

(defnc ConcurrentTab
  []
  (Section
    (SectionTitle {} "⏱️ Concurrent — Transitions & Deferred Values")

    (CodeAndOutput
     {:title "useTransition — non-urgent updates"
      :code "(let [query (use-state \"\")\n      rows  (use-state items)\n      [pending? start] (use-transition)]\n  (Input\n    {:value @query\n     :onChange\n     (fn [e]\n       (let [v (target-value e)]\n         (reset! query v)            ; urgent\n         (start                      ; non-urgent\n           #(reset! rows\n              (filter-items v)))))})\n  (when pending? (Span \"updating…\")))"}
     (TransitionDemo))

    (CodeAndOutput
     {:title "useDeferredValue — lagging copy"
      :code "(let [text     (use-state \"\")\n      deferred (use-deferred-value @text)\n      stale?   (not= @text deferred)]\n  (Input {:value @text\n          :onChange #(reset! text\n                       (target-value %))})\n  (Div {:className (when stale? \"opacity-50\")}\n    (ResultList\n      {:rows (filter-items deferred)})))"}
     (DeferredDemo))))
