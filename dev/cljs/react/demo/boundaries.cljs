(ns cljs.react.demo.boundaries
  (:require [shadow.lazy :as lazy]
            [cljs.react.core :refer [Element ErrorBoundary Suspense
                                     use-lazy-loadable use-state]]
            [cljs.react.sx :refer [use-sx]]
            [cljs.react.demo.ui :refer [CodeAndOutput Btn Row Stack
                                        SectionTitle Caption Alert Spinner]]
            [cljs.react.demo.util :refer [Div Span Section]])
  (:require-macros [cljs.react.core :refer [defnc]]))

;;;; ErrorBoundary — catch a render-phase throw and recover

(defnc Bomb
  [{:keys [boom?]}]
  (when boom?
    (throw (js/Error. "💥 Boom! Bomb exploded during render.")))
  (Alert {:tone :success}
    "✅ Bomb is healthy — flip the switch to make it throw."))

(defnc ErrorBoundaryDemo
  []
  (let [boom? (use-state false)
        ;; ErrorBoundary keeps its caught-error state until the boundary
        ;; remounts. We can't pass :key to ErrorBoundary (it builds its own
        ;; JS props), so we remount via a keyed wrapper: bumping `gen` gives
        ;; the wrapper a fresh key, unmounting the errored boundary and
        ;; mounting a clean one.
        gen   (use-state 0)
        ;; The fallback runs inside ErrorBoundary's CLASS component render,
        ;; where hooks are invalid — so its classes are computed out here.
        bold  (use-sx {:font-weight 600})
        mono  (use-sx {:font-family :typography.font-family-mono})]
    (Stack {:gap 1.5}
      (Row {:gap 1 :wrap? false}
        (Btn {:size :sm :full? true :onClick #(reset! boom? true)}
          "Trigger error")
        (Btn {:variant :secondary :size :sm :full? true
              :onClick (fn [] (reset! boom? false) (swap! gen inc))}
          "Reset boundary"))
      (Div {:key @gen}
        (ErrorBoundary
          {:fallback (fn [err]
                       (Alert {:tone :error :role "alert"}
                         (Span {:className bold} "Caught by ErrorBoundary: ")
                         (Span {:className mono} (ex-message err))))}
          (Bomb {:boom? @boom?})))
      (Caption {:className (use-sx {:font-style :italic})}
        "The fallback receives the thrown error; 'Reset boundary' remounts a clean boundary via a keyed wrapper."))))

;;;; Suspense — show a fallback while a real code-split module resolves

;; `cljs.react.demo.lazy-panel` is NOT in this ns's :require list — referencing
;; its Panel through `loadable` is what tells shadow-cljs to split it into its
;; own `lazy-panel.js` chunk, fetched on demand by the loader.
#_{:clj-kondo/ignore [:unresolved-namespace]}
(def panel-loadable (lazy/loadable cljs.react.demo.lazy-panel/Panel))

(defnc SuspenseDemo
  []
  (let [show? (use-state false)
        Panel (use-lazy-loadable panel-loadable)
        ;; Unconditional: a `use-sx` inside the `when` below is a conditional
        ;; hook, and React counts hooks per render — the first click added one
        ;; and blew up with "Rendered more hooks than during the previous
        ;; render".
        fallback-cls (use-sx {:display :flex :align-items :center
                              :gap 1 :p 1.5 :border-radius 1
                              :bgcolor :palette.surface.sunken
                              :font-size "0.875rem"
                              :color :palette.text.secondary})]
    (Stack {:gap 1.5 :align :flex-start}
      (Btn {:size :sm :onClick #(reset! show? true)} "Load module")
      (when @show?
        (Element {:tag Suspense
                  :fallback (Div {:role "status" :className fallback-cls}
                              (Spinner {:size "0.75rem"})
                              "Loading module…")}
          (Panel {:label "✅ Real chunk fetched on demand via shadow.loader."})))
      (Caption {:className (use-sx {:font-style :italic})}
        "First click fetches lazy-panel.js while Suspense shows the fallback; the chunk is then cached."))))

(defnc BoundariesTab
  []
  (Section
    (SectionTitle {} "🛡️ Boundaries — Error & Suspense")

    (CodeAndOutput
     {:title "ErrorBoundary — catch & recover"
      :code "(defnc Bomb [{:keys [boom?]}]\n  (when boom?\n    (throw (js/Error. \"Boom!\")))\n  (Div \"Healthy\"))\n\n(ErrorBoundary\n  {:fallback (fn [err]\n               (Div {:role \"alert\"}\n                 \"Caught: \"\n                 (ex-message err)))}\n  (Bomb {:boom? @boom?}))\n\n;; :key isn't forwarded — wrap to remount."}
     (ErrorBoundaryDemo))

    (CodeAndOutput
     {:title "Suspense — fallback while a code-split module loads"
      :code "(def panel\n  (lazy/loadable my.app.panel/Panel))\n\n(defnc View []\n  (let [Panel (use-lazy-loadable panel)]\n    (Element {:tag Suspense\n              :fallback (Div \"Loading…\")}\n      (Panel {:label \"hi\"}))))\n\n;; loadable (not :require) → its own chunk,\n;; fetched on demand under Suspense."}
     (SuspenseDemo))))
