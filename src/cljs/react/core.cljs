(ns cljs.react.core
  (:require
   ["react" :as react]
   [cljs.react.component :as component]
   [cljs.react.hook :as hook]
   [cljs.react.db :as db]
   [cljs.react.form :as form]))

(defn Element
  "Create a React element from ClojureScript data structures.

  Usage:
    (Element {:tag \"div\" :className \"container\"} child1 child2 ...)

  The first argument should be a map with at least a :tag key.
  All other keys become props passed to React.createElement.
  Remaining arguments are children.

  Uses component/*create-element* dynamic var which defaults to react/createElement
  but can be rebound to use alternative renderers like emotion/jsx."
  ([{:keys [tag] :as props}]
   (when (nil? tag) (throw (js/Error. "Element requires a :tag prop")))
   (component/*create-element* tag (component/clj->js-props (dissoc props :tag))))
  ([{:keys [tag] :as props} c1]
   (when (nil? tag) (throw (js/Error. "Element requires a :tag prop")))
   (component/*create-element* tag (component/clj->js-props (dissoc props :tag)) c1))
  ([{:keys [tag] :as props} c1 c2]
   (when (nil? tag) (throw (js/Error. "Element requires a :tag prop")))
   (component/*create-element* tag (component/clj->js-props (dissoc props :tag)) c1 c2))
  ([{:keys [tag] :as props} c1 c2 c3]
   (when (nil? tag) (throw (js/Error. "Element requires a :tag prop")))
   (component/*create-element* tag (component/clj->js-props (dissoc props :tag)) c1 c2 c3))
  ([{:keys [tag] :as props} c1 c2 c3 & more]
   (when (nil? tag) (throw (js/Error. "Element requires a :tag prop")))
   (apply component/*create-element* tag (component/clj->js-props (dissoc props :tag)) c1 c2 c3 more)))

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

(def ^{:doc "React.Fragment — group children without adding a DOM wrapper."}
  Fragment react/Fragment)

(def ^{:doc "React.Suspense — render a fallback while descendants suspend."}
  Suspense react/Suspense)

;; Re-export db utilities
(def DBProvider db/DBProvider)
(def use-db db/use-db)
(def use-db-atom db/use-db-atom)
(def use-cursor db/use-cursor)

;; Re-export form utilities
(def use-form form/use-form)
(def use-field form/use-field)
(def use-form-meta form/use-form-meta)
(def on-submit form/on-submit)
