(ns cljs.react.demo.boundaries
  (:require [shadow.lazy :as lazy]
            [cljs.react.core :refer [Element ErrorBoundary Suspense
                                     use-lazy-loadable use-state]]
            [cljs.react.demo.util :refer [CodeAndOutput Div P Span H2
                                          Button Section]])
  (:require-macros [cljs.react.core :refer [defnc]]))

;;;; ErrorBoundary — catch a render-phase throw and recover

(defnc Bomb
  [{:keys [boom?]}]
  (when boom?
    (throw (js/Error. "💥 Boom! Bomb exploded during render.")))
  (Div {:className "p-3 bg-emerald-50 border border-emerald-200 rounded-lg text-emerald-700 text-sm"}
    "✅ Bomb is healthy — flip the switch to make it throw."))

(defnc ErrorBoundaryDemo
  []
  (let [boom? (use-state false)
        ;; ErrorBoundary keeps its caught-error state until the boundary
        ;; remounts. We can't pass :key to ErrorBoundary (it builds its own
        ;; JS props), so we remount via a keyed wrapper: bumping `gen` gives
        ;; the wrapper a fresh key, unmounting the errored boundary and
        ;; mounting a clean one.
        gen   (use-state 0)]
    (Div {:className "w-full space-y-3"}
      (Div {:className "flex gap-2"}
        (Button {:className "flex-1 px-3 py-2 bg-koi-orange text-white rounded-lg font-medium text-sm shadow hover:bg-orange-600 transition-colors"
                 :onClick #(reset! boom? true)}
          "Trigger error")
        (Button {:className "flex-1 px-3 py-2 bg-gray-200 text-gray-700 rounded-lg font-medium text-sm hover:bg-gray-300 transition-colors"
                 :onClick (fn [] (reset! boom? false) (swap! gen inc))}
          "Reset boundary"))
      (Div {:key @gen}
        (ErrorBoundary
          {:fallback (fn [err]
                       (Div {:className "p-3 bg-red-50 border border-red-200 rounded-lg"
                             :role "alert"}
                         (Span {:className "text-sm font-semibold text-red-700"}
                           "Caught by ErrorBoundary: ")
                         (Span {:className "text-sm text-red-600 font-mono"}
                           (ex-message err))))}
          (Bomb {:boom? @boom?})))
      (P {:className "text-xs text-gray-500 italic"}
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
        Panel (use-lazy-loadable panel-loadable)]
    (Div {:className "w-full space-y-3"}
      (Button {:className "px-4 py-2 bg-koi-orange text-white rounded-lg font-medium text-sm shadow hover:bg-orange-600 transition-colors"
               :onClick #(reset! show? true)}
        "Load module")
      (when @show?
        (Element {:tag Suspense
                  :fallback (Div {:role "status"
                                  :className "flex items-center gap-2 p-3 bg-gray-50 border border-gray-200 rounded-lg text-sm text-gray-500"}
                              (Span {:aria-hidden true
                                     :className "inline-block w-3 h-3 rounded-full border-2 border-koi-orange border-t-transparent animate-spin"})
                              "Loading module…")}
          (Panel {:label "✅ Real chunk fetched on demand via shadow.loader."})))
      (P {:className "text-xs text-gray-500 italic"}
        "First click fetches lazy-panel.js while Suspense shows the fallback; the chunk is then cached."))))

(defnc BoundariesTab
  []
  (Section
    (H2 "🛡️ Boundaries — Error & Suspense")

    (CodeAndOutput
     {:title "ErrorBoundary — catch & recover"
      :code "(defnc Bomb [{:keys [boom?]}]\n  (when boom?\n    (throw (js/Error. \"Boom!\")))\n  (Div \"Healthy\"))\n\n(ErrorBoundary\n  {:fallback (fn [err]\n               (Div {:role \"alert\"}\n                 \"Caught: \"\n                 (ex-message err)))}\n  (Bomb {:boom? @boom?}))\n\n;; :key isn't forwarded to ErrorBoundary —\n;; remount via a keyed wrapper to reset it."}
     (ErrorBoundaryDemo))

    (CodeAndOutput
     {:title "Suspense — fallback while a code-split module loads"
      :code "(def panel\n  (lazy/loadable my.app.panel/Panel))\n\n(defnc View []\n  (let [Panel (use-lazy-loadable panel)]\n    (Element {:tag Suspense\n              :fallback (Div \"Loading…\")}\n      (Panel {:label \"hi\"}))))\n\n;; `panel` is referenced via loadable (not :require),\n;; so shadow splits it into its own chunk, fetched\n;; on demand. use-lazy-loadable returns a callable component."}
     (SuspenseDemo))))
