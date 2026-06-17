(ns cljs.react.demo.boundaries
  (:require ["react" :as react]
            [cljs.react.core :refer [Element ErrorBoundary Suspense
                                     use-state]]
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

;;;; Suspense — show a fallback while a lazily-loaded component resolves

(defnc LoadedPanel
  [_]
  (Div {:className "p-3 bg-emerald-50 border border-emerald-200 rounded-lg text-emerald-700 text-sm"}
    "✅ Module resolved after a simulated 1.2s dynamic import."))

(defn- make-lazy-panel
  "Build a fresh React.lazy component whose import resolves after 1.2s, so each
  load re-suspends and the fallback is visible."
  []
  (react/lazy
    (fn []
      (js/Promise.
        (fn [resolve _reject]
          (js/setTimeout
            (fn [] (resolve #js {:default LoadedPanel}))
            1200))))))

(defnc SuspenseDemo
  []
  ;; Hold the lazy component in state so each "Load" click creates a fresh one
  ;; (React.lazy caches resolved modules, so a brand-new lazy is what re-shows
  ;; the fallback).
  (let [lazy-comp (use-state nil)]
    (Div {:className "w-full space-y-3"}
      (Button {:className "px-4 py-2 bg-koi-orange text-white rounded-lg font-medium text-sm shadow hover:bg-orange-600 transition-colors"
               :onClick #(reset! lazy-comp (make-lazy-panel))}
        (if @lazy-comp "Reload module" "Load module"))
      (when @lazy-comp
        (Element {:tag Suspense
                  :fallback (Div {:role "status"
                                  :className "flex items-center gap-2 p-3 bg-gray-50 border border-gray-200 rounded-lg text-sm text-gray-500"}
                              (Span {:aria-hidden true
                                     :className "inline-block w-3 h-3 rounded-full border-2 border-koi-orange border-t-transparent animate-spin"})
                              "Loading module…")}
          (Element {:tag @lazy-comp})))
      (P {:className "text-xs text-gray-500 italic"}
        "Suspense renders the fallback while the lazy component's import promise is pending."))))

(defnc BoundariesTab
  []
  (Section
    (H2 "🛡️ Boundaries — Error & Suspense")

    (CodeAndOutput
     {:title "ErrorBoundary — catch & recover"
      :code "(defnc Bomb [{:keys [boom?]}]\n  (when boom?\n    (throw (js/Error. \"Boom!\")))\n  (Div \"Healthy\"))\n\n(ErrorBoundary\n  {:fallback (fn [err]\n               (Div {:role \"alert\"}\n                 \"Caught: \"\n                 (ex-message err)))}\n  (Bomb {:boom? @boom?}))\n\n;; :key isn't forwarded to ErrorBoundary —\n;; remount via a keyed wrapper to reset it."}
     (ErrorBoundaryDemo))

    (CodeAndOutput
     {:title "Suspense — fallback while lazy loads"
      :code "(def LazyPanel\n  (react/lazy\n    (fn []\n      (js/Promise.\n        (fn [resolve _]\n          (js/setTimeout\n            #(resolve #js {:default Panel})\n            1200))))))\n\n(Element {:tag Suspense\n          :fallback (Div \"Loading…\")}\n  (Element {:tag LazyPanel}))"}
     (SuspenseDemo))))
