(ns cljs.react.demo.lazy-panel
  "Code-split chunk loaded on demand by the Suspense demo. Nothing in the :main
  module statically requires this namespace — it is referenced only through
  `shadow.lazy/loadable`, so shadow emits it as its own `lazy-panel.js` file that
  the loader fetches when the user clicks \"Load module\"."
  (:require [cljs.react.sx :refer [use-sx]]
            [cljs.react.demo.util :refer [Div Span P]])
  (:require-macros [cljs.react.core :refer [defnc]]))

(defnc Panel
  [{:keys [label]}]
  (Div {:className (use-sx {:display :flex :flex-direction :column :gap 0.5
                            :p 1.5 :border-radius 1
                            :border "1px solid"
                            :border-color :palette.success.main
                            :color :palette.success.main
                            :bgcolor "color-mix(in srgb, var(--cx-palette-success-main) 12%, transparent)"})}
    (P {:className (use-sx {:font-size "0.875rem" :font-weight 500})} label)
    (P {:className (use-sx {:font-size "0.75rem" :opacity 0.85})}
      "This component lives in a separate "
      (Span {:className (use-sx {:font-family :typography.font-family-mono})}
        "lazy-panel.js")
      " chunk, fetched only when first rendered.")))
