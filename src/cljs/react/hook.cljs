(ns cljs.react.hook
  "ClojureScript-idiomatic wrappers over React hooks. Returns CLJS-friendly
  types (StateAtom, RefAtom, Cursor-compatible selectors) so consumers can
  use `deref`, `reset!`, `swap!` directly instead of React's tuple/ref shapes.

  Most symbols are re-exported from `cljs.react.core`; prefer that namespace
  unless you need something not re-exported (e.g. [[use-selector]])."
  (:require
   ["react" :as react]))

(deftype ^:no-doc StateAtom [arr]
  IDeref
  (-deref [_] (aget arr 0))

  IReset
  (-reset! [_ v] ((aget arr 1) v) v)

  ISwap
  (-swap! [_ f] ((aget arr 1) f))
  (-swap! [_ f a] ((aget arr 1) #(f % a)))
  (-swap! [_ f a b] ((aget arr 1) #(f % a b)))
  (-swap! [_ f a b xs] ((aget arr 1) #(apply f % a b xs))))

(defn ^:no-doc cljs-deps
  "Compare ClojureScript deps using structural equality.
  Returns a JS array whose element[0] is a counter that bumps only when deps change.
  Allocates a fresh JS array only when deps change — stable renders reuse the cached array,
  so React's element-wise Object.is comparison sees no change. Must be called from
  within a hook context (uses useRef internally)."
  [deps]
  (let [ref (react/useRef nil)
        state (or (.-current ref)
                  (let [s #js {:deps deps :counter 0 :arr #js [0]}]
                    (set! (.-current ref) s)
                    s))]
    (when-not (= deps (.-deps state))
      (set! (.-deps state) deps)
      (let [c (inc (.-counter state))]
        (set! (.-counter state) c)
        (set! (.-arr state) #js [c])))
    (.-arr state)))

(defn use-effect
  ([effect-fn]
   (react/useEffect effect-fn))
  ([effect-fn deps]
   (react/useEffect effect-fn (cljs-deps deps))))

(defn use-callback
  ([f] (react/useCallback f))
  ([f deps] (react/useCallback f (cljs-deps deps))))

(defn use-memo
  ([f] (react/useMemo f))
  ([f deps] (react/useMemo f (cljs-deps deps))))

(defn use-layout-effect
  ([effect-fn]
   (react/useLayoutEffect effect-fn))
  ([effect-fn deps]
   (react/useLayoutEffect effect-fn (cljs-deps deps))))

(defprotocol ^:no-doc IReactRef
  (-react-ref [this]))

(deftype ^:no-doc RefAtom [ref]
  IDeref
  (-deref [_] (.-current ref))
  IReset
  (-reset! [_ v] (set! (.-current ref) v) v)
  ISwap
  (-swap! [o f] (-reset! o (f (.-current ref))))
  (-swap! [o f a] (-reset! o (f (.-current ref) a)))
  (-swap! [o f a b] (-reset! o (f (.-current ref) a b)))
  (-swap! [o f a b xs] (-reset! o (apply f (.-current ref) a b xs)))
  IReactRef
  (-react-ref [_] ref))

(defn use-ref
  ([] (RefAtom. (react/useRef nil)))
  ([initial] (RefAtom. (react/useRef initial))))

(defn react-ref
  "Extract the raw React ref object from a RefAtom.
  Use this when passing refs to DOM elements or JS components."
  [ref-atom]
  (-react-ref ref-atom))

(defn use-imperative-handle
  "Customize the handle exposed to parent components when using forward-ref."
  ([ref create-handle]
   (react/useImperativeHandle (-react-ref ref) create-handle))
  ([ref create-handle deps]
   (react/useImperativeHandle (-react-ref ref) create-handle (cljs-deps deps))))

(def use-context react/useContext)

(def use-id react/useId)

(defn use-sync-external-store
  "Subscribe to an external store.

  - subscribe: (fn [callback] ...) - called with a callback that should be invoked
    when the store changes. Must return an unsubscribe function.
  - get-snapshot: (fn [] ...) - returns the current value of the store.
  - get-server-snapshot: (fn [] ...) - optional, returns the snapshot for server rendering."
  ([subscribe get-snapshot]
   (react/useSyncExternalStore subscribe get-snapshot))
  ([subscribe get-snapshot get-server-snapshot]
   (react/useSyncExternalStore subscribe get-snapshot get-server-snapshot)))

(defn use-transition
  "Returns [is-pending start-transition] for marking updates as non-urgent.
  Wrap state updates in start-transition to keep the UI responsive."
  []
  (let [arr (react/useTransition)]
    [(aget arr 0) (aget arr 1)]))

(def use-deferred-value react/useDeferredValue)

(defn use-state
  "Local component state. Returns a StateAtom that supports deref, reset!, and swap!."
  [initial]
  (StateAtom. (react/useState initial)))

(defn use-selector
  "Subscribe to an IWatchable `source`, re-rendering only when `diff?` returns
  truthy between old/new states. `select` projects a snapshot out of the
  current state; equal projections return the cached reference (required for
  useSyncExternalStore render stability).

  `deps` controls when the subscription identity changes — pass [] for a
  permanent subscription, or a vector of selector parameters otherwise."
  [source diff? select deps]
  ;; Cache holds the last (source-state, projected-snapshot) pair. React calls
  ;; getSnapshot multiple times per render (commit + tearing checks). If the
  ;; source-state identity hasn't changed, skip select + structural-= entirely.
  (let [cache-ref    (use-ref nil)
        subscribe    (use-callback
                       (fn [callback]
                         ;; Identity-keyed: a fresh JS object is unique per
                         ;; subscribe call, avoiding gensym's global counter
                         ;; touch and symbol allocation.
                         (let [key #js {}]
                           (add-watch source key
                             (fn [_ _ old new]
                               (when (diff? old new) (callback))))
                           #(remove-watch source key)))
                       deps)
        get-snapshot (use-callback
                       (fn []
                         (let [state  @source
                               ^js cached @cache-ref]
                           (if (and cached (identical? state (.-state cached)))
                             (.-snap cached)
                             (let [new-snap (select state)]
                               (if (and cached (= new-snap (.-snap cached)))
                                 (do (set! (.-state cached) state)
                                     (.-snap cached))
                                 (do (reset! cache-ref
                                             #js {:state state :snap new-snap})
                                     new-snap))))))
                       deps)]
    (use-sync-external-store subscribe get-snapshot)))

(defn use-atom
  "Subscribe to a ClojureScript atom. Returns the current value and re-renders
  only when the value changes. Structurally-equal updates are treated as
  no-ops — a swap! that produces a `=`-equal map won't re-render consumers."
  [atom]
  (use-selector atom not= identity [atom]))