(ns cljs.react.dom
  "React DOM entry points: mount/hydrate a root, render into it, tear it down,
  and render into a portal."
  (:require ["react-dom" :as react-dom]
            ["react-dom/client" :as react-dom-client]))

(defn create-root
  "Create a React root for `container` (a DOM element). Call [[render]] on the
  result to attach the tree, and [[unmount]] to tear it down.
  Wraps react-dom/client createRoot."
  [container]
  (react-dom-client/createRoot container))

(defn hydrate-root
  "Hydrate `container` with `element` — use when the initial markup was
  server-rendered. Returns a root that [[render]] and [[unmount]] accept.
  Wraps react-dom/client hydrateRoot."
  [container element]
  (react-dom-client/hydrateRoot container element))

(defn render
  "Render `element` into an existing root created by [[create-root]].
  Returns nil — react-dom returns undefined here, do not chain on the result."
  [root element]
  (.render ^js root element)
  nil)

(defn unmount
  "Detach and clean up a root created by [[create-root]] or [[hydrate-root]].
  Returns nil."
  [root]
  (.unmount ^js root)
  nil)

(defn create-portal
  "Render `child` into `container` (a DOM node outside the current tree)."
  [child container]
  (react-dom/createPortal child container))

(defn mount!
  "Create a root for `container` and render `element` into it in one step,
  returning the root (hand it to [[unmount]] to tear down). Convenience for the
  common [[create-root]] + [[render]] pair:

    (defonce root (mount! (js/document.getElementById \"app\") (App {})))"
  [container element]
  (let [root (create-root container)]
    (render root element)
    root))

(defn flush-sync
  "Force React to flush any state updates performed inside `thunk` (a 0-arity
  fn) synchronously, committing their DOM changes before returning. Opts out of
  React's batching — use sparingly, e.g. when you must read updated layout
  immediately after a state change. Wraps react-dom/flushSync. Returns `thunk`'s
  value."
  [thunk]
  (react-dom/flushSync thunk))
