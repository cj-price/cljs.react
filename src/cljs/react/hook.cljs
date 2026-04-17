(ns cljs.react.hook
  (:require
   ["react" :as react]))

(deftype StateAtom [arr]
  IDeref
  (-deref [_] (aget arr 0))

  IReset
  (-reset! [_ v] ((aget arr 1) v))

  ISwap
  (-swap! [_ f] ((aget arr 1) f))
  (-swap! [_ f a] ((aget arr 1) #(f % a)))
  (-swap! [_ f a b] ((aget arr 1) #(f % a b)))
  (-swap! [_ f a b xs] ((aget arr 1) #(apply f % a b xs))))

(defn cljs-deps
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

(defprotocol IReactRef
  (-react-ref [this]))

(deftype RefAtom [ref]
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

(defn use-atom
  "Subscribe to a ClojureScript atom. Returns the current value and re-renders on changes."
  [atom]
  (let [subscribe    (use-callback
                       (fn [callback]
                         (let [key (gensym "use-atom")]
                           (add-watch atom key (fn [_ _ _ _] (callback)))
                           #(remove-watch atom key)))
                       [atom])
        get-snapshot (use-callback (fn [] @atom) [atom])]
    (use-sync-external-store subscribe get-snapshot)))

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
  (let [snap-ref     (use-ref nil)
        subscribe    (use-callback
                       (fn [callback]
                         (let [key (gensym "use-selector")]
                           (add-watch source key
                             (fn [_ _ old new]
                               (when (diff? old new) (callback))))
                           #(remove-watch source key)))
                       deps)
        get-snapshot (use-callback
                       (fn []
                         (let [new-snap (select @source)
                               cached   @snap-ref]
                           (if (= new-snap cached)
                             cached
                             (do (reset! snap-ref new-snap) new-snap))))
                       deps)]
    (use-sync-external-store subscribe get-snapshot)))