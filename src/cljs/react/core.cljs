(ns cljs.react.core
  (:require
   [cljs.react.component :as component]
   [cljs.react.hook :as hook]
   [cljs.react.db :as db]))

(defn Element
  "Create a React element from ClojureScript data structures.

  Usage:
    (Element {:tag \"div\" :className \"container\"} child1 child2 ...)

  The first argument should be a map with at least a :tag key.
  All other keys become props passed to React.createElement.
  Remaining arguments are children.

  Uses component/*create-element* dynamic var which defaults to react/createElement
  but can be rebound to use alternative renderers like emotion/jsx."
  [{:keys [tag] :as props} & children]
  (let [element-type (or tag "div")
        react-props (component/clj->js-props (dissoc props :tag))
        js-children (to-array children)]
    (apply component/*create-element* element-type react-props js-children)))

;; Re-export custom renderer utilities
(def make-element-fn component/make-element-fn)
(def make-create-cljs-element-fn component/make-create-cljs-element-fn)

;; Re-export ref utilities
(def react-ref hook/react-ref)
(def forward-ref component/forward-ref)

;; Re-export db utilities
(def DBProvider db/DBProvider)
(def use-db db/use-db)
(def use-cursor db/use-cursor)
