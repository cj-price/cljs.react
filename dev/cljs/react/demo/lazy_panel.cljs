(ns cljs.react.demo.lazy-panel
  "Code-split chunk loaded on demand by the Suspense demo. Nothing in the :main
  module statically requires this namespace — it is referenced only through
  `shadow.lazy/loadable`, so shadow emits it as its own `lazy-panel.js` file that
  the loader fetches when the user clicks \"Load module\"."
  (:require [cljs.react.demo.util :refer [Div Span P]])
  (:require-macros [cljs.react.core :refer [defnc]]))

(defnc Panel
  [{:keys [label]}]
  (Div {:className "p-3 bg-emerald-50 border border-emerald-200 rounded-lg space-y-1"}
    (P {:className "text-emerald-700 text-sm font-medium"} label)
    (P {:className "text-emerald-600 text-xs"}
      "This component lives in a separate "
      (Span {:className "font-mono"} "lazy-panel.js")
      " chunk, fetched only when first rendered.")))
