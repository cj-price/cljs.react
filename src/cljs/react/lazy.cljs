(ns cljs.react.lazy
  "Bridge shadow-cljs code-split modules (`shadow.lazy`) to React Suspense.

  `use-lazy-loadable` turns a `shadow.lazy/loadable` (or any 0-arg loader returning a
  promise of a component) into a stable, callable component. Render it inside a
  `Suspense` boundary: the fallback shows while the module's chunk is fetched,
  then the resolved component renders with its props/children intact.

  All `shadow.lazy` / `shadow.loader` interaction is contained in this namespace.

  Notes:
  - Loaded content is client-only: under server rendering (`hydrate-root`) the
    Suspense fallback is what renders on the server; the chunk is fetched and the
    component mounts on the client.
  - `React.lazy` caches the settled result (success or failure) on the lazy
    component. To retry after a failed load, remount the component that calls
    `use-lazy-loadable` (e.g. bump a `:key`); the cached error can't be cleared in place."
  (:require
   ["react" :as react]
   [shadow.lazy :as lazy]
   [cljs.react.component :as component]))

(defn- shim
  "Wrap a loaded ClojureScript component as a plain React function component.

  React renders the resolved `:default` with our `{:cljsProps …}` wrapper, so we
  unwrap it (which also merges `:children`) and call the loaded component with a
  real CLJS props map — props and children flow exactly as for an eager defnc.
  Carries the loaded component's displayName through for DevTools/stack traces."
  [loaded]
  (let [c (fn [js-props] (loaded (component/unwrap-cljs-props js-props)))]
    (when-let [dn (.-displayName loaded)]
      (set! (.-displayName c) dn))
    c))

(defn- ->thunk
  "Normalize `src` to a 0-arg fn returning a Promise of `#js {:default Component}`
  — the shape React.lazy expects."
  [src]
  (let [wrap (fn [loaded] #js {:default (shim loaded)})]
    (cond
      (instance? lazy/Loadable src) (fn [] (lazy/load src wrap))
      (fn? src)                     (fn [] (.then (src) wrap))
      :else
      (throw (ex-info "use-lazy-loadable expects a shadow.lazy Loadable or a 0-arg loader fn"
                      {:src src})))))

(defn use-lazy-loadable
  "Lazily load a component and return a stable, callable wrapper to render under
  Suspense.

  `src` is either a `shadow.lazy/loadable` (real code-split module) or a 0-arg fn
  returning a `js/Promise` of a ClojureScript component. Hold `src` in a
  module-level `def` so its identity is stable across renders — passing a fresh
  value each render (e.g. an inline `(lazy/loadable …)`) rebuilds the lazy
  component and re-suspends on every render. A dev build warns when that happens.

  The returned value is used like any defnc component — `(Panel {:label \"x\"})` —
  but must sit inside a `Suspense` boundary that supplies the loading fallback:

    (def panel (shadow.lazy/loadable my.app.panel/Panel))

    (defnc View []
      (let [Panel (use-lazy-loadable panel)]
        (Element {:tag Suspense :fallback (Element {:tag \"div\"} \"Loading…\")}
          (Panel {:label \"hi\"}))))"
  [src]
  ;; Dev-only stability check; the whole block (incl. the useRef) DCEs out of
  ;; :advanced release builds since goog/DEBUG folds to false.
  (when ^boolean goog/DEBUG
    (let [prev (react/useRef src)]
      (when-not (identical? (.-current prev) src)
        (js/console.warn
         (str "use-lazy-loadable: `src` changed identity between renders, which rebuilds the "
              "lazy component and re-suspends every render. Hold the loadable/loader "
              "in a module-level def. (Ignore if you are intentionally swapping modules.)")))
      (set! (.-current prev) src)))
  (react/useMemo
   (fn []
     (let [Lazy (react/lazy (->thunk src))]
       (fn
         ([]              (component/create-cljs-element Lazy {}))
         ([props]         (component/create-cljs-element Lazy props))
         ([props & more]  (apply component/create-cljs-element Lazy props more)))))
   #js [src]))
