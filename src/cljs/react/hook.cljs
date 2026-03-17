(ns cljs.react.hook
  (:require
   ["react" :as react]))

(deftype StateAtom [arr]
  IDeref
  (-deref [_] (aget arr 0))

  IReset
  (-reset! [_ v] ((aget arr 1) v))

  ISwap
  (-swap! [_ f] ((aget arr 1) #(f %)))
  (-swap! [_ f a] ((aget arr 1) #(f % a)))
  (-swap! [_ f a b] ((aget arr 1) #(f % a b)))
  (-swap! [_ f a b xs] ((aget arr 1) #(apply f % a b xs))))

(defn cljs-deps
  "Compare ClojureScript deps using structural equality.
  Returns a JS array with a counter that increments only when deps change.
  Must be called from within a hook context (uses useRef internally)."
  [deps]
  (let [ref (react/useRef #js {:deps deps :counter 0})
        state (.-current ref)]
    (when-not (= deps (.-deps state))
      (set! (.-deps state) deps)
      (set! (.-counter state) (inc (.-counter state))))
    #js [(.-counter state)]))

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
  (use-sync-external-store
    (fn [callback]
      (let [key (gensym "use-atom")]
        (add-watch atom key (fn [_ _ _ _] (callback)))
        #(remove-watch atom key)))
    (fn [] @atom)))

(defn use-state
  "Local component state. Returns a StateAtom that supports deref, reset!, and swap!."
  [initial]
  (StateAtom. (react/useState initial)))