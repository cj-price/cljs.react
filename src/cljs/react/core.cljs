(ns cljs.react.core
  (:require ["react" :as react]
            [cljs.react.component :as component]))

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

;; Export for library build
(def default #js {:Element Element})