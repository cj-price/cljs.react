(ns cljs.react.core
  (:require
   ["react" :as react]
   ["react-dom" :as react-dom]
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
  (when (nil? tag)
    (throw (js/Error. "Element requires a :tag prop")))
  (let [react-props (component/clj->js-props (dissoc props :tag))
        js-children (to-array children)]
    (apply component/*create-element* tag react-props js-children)))

;; Re-export custom renderer utilities
(def make-element-fn component/make-element-fn)
(def make-create-cljs-element-fn component/make-create-cljs-element-fn)

;; Re-export ref utilities
(def react-ref hook/react-ref)
(def forward-ref component/forward-ref)

;; Re-export hooks
(def use-effect hook/use-effect)
(def use-callback hook/use-callback)
(def use-memo hook/use-memo)
(def use-layout-effect hook/use-layout-effect)
(def use-ref hook/use-ref)
(def use-imperative-handle hook/use-imperative-handle)
(def use-context hook/use-context)
(def use-id hook/use-id)
(def use-state hook/use-state)
(def use-atom hook/use-atom)
(def use-sync-external-store hook/use-sync-external-store)
(def use-transition hook/use-transition)
(def use-deferred-value hook/use-deferred-value)

;; React primitives
(defn create-context
  "Create a React context with an optional default value."
  ([] (react/createContext nil))
  ([default-value] (react/createContext default-value)))

(def Fragment react/Fragment)
(def Suspense react/Suspense)

(defn create-portal
  "Render children into a different DOM node."
  [child container]
  (react-dom/createPortal child container))

;; Re-export db utilities
(def DBProvider db/DBProvider)
(def use-db db/use-db)
(def use-cursor db/use-cursor)
