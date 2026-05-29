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
  (-swap! [_ f]        ((aget arr 1) f))
  (-swap! [_ f a]      ((aget arr 1) #(f % a)))
  (-swap! [_ f a b]    ((aget arr 1) #(f % a b)))
  (-swap! [_ f a b xs] ((aget arr 1) #(apply f % a b xs)))

  ;; A fresh StateAtom is allocated each render, but two wrappers backed by
  ;; the same useState slot share the same setter — React guarantees setter
  ;; identity is stable per hook position. So we use setter identity as the
  ;; equivalence key: this gives callers stable `=` across renders without
  ;; the hazards of caching a wrapper that mutates a render-spanning ref.
  IEquiv
  (-equiv [_ other]
    (and (instance? StateAtom other)
         (identical? (aget arr 1) (aget (.-arr ^StateAtom other) 1))))

  IHash
  (-hash [_] (goog/getUid (aget arr 1))))

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
  "React.useEffect with CLJS structural equality on the deps vector.

  Args:
    effect-fn - 0-arity fn run after commit; may return a cleanup fn (or nil)
    deps      - optional vector of dependencies; the effect re-runs only when
                `=` on the deps vector changes.

  Returns nil. Pass `[]` to run once on mount, omit to run after every render."
  ([effect-fn]
   (react/useEffect effect-fn))
  ([effect-fn deps]
   (react/useEffect effect-fn (cljs-deps deps))))

(defn use-callback
  "React.useCallback with CLJS structural equality on deps. Returns the same
  fn reference across renders until `deps` changes.

  Args:
    f    - the function to memoize
    deps - optional vector of dependencies"
  ([f] (react/useCallback f))
  ([f deps] (react/useCallback f (cljs-deps deps))))

(defn use-memo
  "React.useMemo with CLJS structural equality on deps. Calls `f` and caches
  its return value; re-invokes only when `deps` changes.

  Args:
    f    - 0-arity fn producing the memoized value
    deps - optional vector of dependencies"
  ([f] (react/useMemo f))
  ([f deps] (react/useMemo f (cljs-deps deps))))

(defn use-layout-effect
  "React.useLayoutEffect — like `use-effect` but fires synchronously after
  DOM mutations and before the browser paints. Use for reading layout or
  imperatively positioning elements; prefer `use-effect` otherwise.

  Args:
    effect-fn - 0-arity fn; may return a cleanup fn (or nil)
    deps      - optional vector of dependencies"
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
  (-swap! [o f]            (-reset! o (f (.-current ref))))
  (-swap! [o f a]          (-reset! o (f (.-current ref) a)))
  (-swap! [o f a b]        (-reset! o (f (.-current ref) a b)))
  (-swap! [o f a b xs]     (-reset! o (apply f (.-current ref) a b xs)))
  IReactRef
  (-react-ref [_] ref)

  ;; Same rationale as StateAtom: a fresh RefAtom is built per render, but
  ;; two wrappers around the same underlying React ref are considered equal.
  IEquiv
  (-equiv [_ other]
    (and (instance? RefAtom other)
         (identical? ref (.-ref ^RefAtom other))))

  IHash
  (-hash [_] (goog/getUid ref)))

(defn use-ref
  "React.useRef wrapped as a RefAtom (supports `deref` / `reset!` / `swap!`).
  RefAtoms compare equal across renders when they wrap the same underlying
  React ref, so they're safe to place into hook deps.

  Args:
    initial - optional initial value (defaults to nil)

  Returns a RefAtom. Use `react-ref` to extract the raw React ref before
  handing to a DOM element or JS component."
  ([]        (RefAtom. (react/useRef nil)))
  ([initial] (RefAtom. (react/useRef initial))))

(defn react-ref
  "Extract the raw React ref object from a RefAtom.
  Use this when passing refs to DOM elements or JS components."
  [ref-atom]
  (-react-ref ref-atom))

(defn use-imperative-handle
  "Customize the handle exposed to parent components when using `forward-ref`.

  Args:
    ref           - the RefAtom received via `forward-ref` props
    create-handle - 0-arity fn returning the object exposed to the parent
    deps          - optional vector of dependencies

  Usage inside a forward-ref'd component:
    (use-imperative-handle ref
      (fn [] #js {:focus (fn [] (.focus @input-ref))})
      [])"
  ([ref create-handle]
   (react/useImperativeHandle (-react-ref ref) create-handle))
  ([ref create-handle deps]
   (react/useImperativeHandle (-react-ref ref) create-handle (cljs-deps deps))))

;; Re-exports — see cljs.react.core for user-facing docs.
(def ^:no-doc use-context react/useContext)
(def ^:no-doc use-id      react/useId)

(defn use-sync-external-store
  "React.useSyncExternalStore — subscribe to an external store and return the
  current snapshot.

  Args:
    subscribe           - (fn [callback]) invoked once per subscription; must
                          call `callback` whenever the store changes and return
                          an unsubscribe fn.
    get-snapshot        - 0-arity fn returning the current value. Must be
                          referentially stable for unchanged state — React tears
                          on identity comparison, so structurally-equal but
                          freshly-allocated returns will cause infinite renders.
    get-server-snapshot - optional 0-arity fn returning the SSR snapshot.

  Returns the current snapshot. Prefer `use-atom` / `use-selector` for
  Clojure-atom sources; reach for this only for external (non-atom) stores."
  ([subscribe get-snapshot]
   (react/useSyncExternalStore subscribe get-snapshot))
  ([subscribe get-snapshot get-server-snapshot]
   (react/useSyncExternalStore subscribe get-snapshot get-server-snapshot)))

(defn use-transition
  "React.useTransition. Returns `[pending? start-transition]`:
    - pending?         - true while a transition is in flight
    - start-transition - (fn [thunk]) marks the updates run inside `thunk` as
                         non-urgent so React can keep the UI responsive.

  Usage:
    (let [[pending? start-transition] (use-transition)]
      (Element {:tag \"button\"
                :onClick #(start-transition (fn [] (reset! state :slow)))}
        (if pending? \"…\" \"Go\")))"
  []
  (let [arr (react/useTransition)]
    [(aget arr 0) (aget arr 1)]))

(def ^{:doc "React.useDeferredValue — returns a deferred version of the supplied value that lags slightly behind during expensive updates."}
  use-deferred-value react/useDeferredValue)

(defn use-state
  "Local component state. Returns a StateAtom that supports deref, reset!, and swap!.

  `initial` is stored verbatim — including functions. (React.useState treats a
  function argument as a lazy initializer; this wrapper protects fn values by
  passing them through a thunk, so `(use-state my-handler)` stores `my-handler`
  itself, not its return value.) Pass `:lazy? true` to opt back into lazy init:
  `initial` is then expected to be a 0-arity fn called once on mount, and its
  return value is stored.

  A fresh StateAtom value is returned each render with the current
  [value setter] tuple captured (matching React's snapshot semantics — `@s`
  in a closure reads the value at the render the closure was created in,
  NOT the latest committed value). If you need the latest value inside an
  async callback or interval, mirror it into a `use-ref` and deref the ref.
  StateAtoms backed by the same useState slot compare equal under `=` (setter
  identity is stable across renders), so a StateAtom is safe to place into
  cljs.react use-effect / use-memo / use-callback deps.

  NOTE: unlike a regular CLJS atom (and unlike RefAtom), `reset!`/`swap!` on a
  StateAtom do NOT synchronously return the new value — React's setter is
  asynchronous and batched, so the next value isn't known until the following
  render. `reset!` returns the value you passed in; `swap!` returns nil. Read
  the updated value via `@s` on the next render, not from the `swap!` result."
  [initial & {:keys [lazy?]}]
  (StateAtom.
    (cond
      lazy?         (react/useState initial)
      (fn? initial) (react/useState (fn [] initial))
      :else         (react/useState initial))))

(defn use-selector
  "Subscribe to an IWatchable `source`, re-rendering only when `diff?` returns
  truthy between old/new states.

  Args:
    source - any IWatchable (atom, cursor, ratom-like) whose value the
             component depends on.
    diff?  - (fn [old-state new-state]) — return truthy to schedule a re-render.
             Cheap-but-correct beats deep `not=` for hot paths.
    select - (fn [state]) projects a snapshot out of the current state.
             Equal projections (`=`) return the cached reference; this is
             required by React's tearing checks under useSyncExternalStore.
    deps   - vector of selector parameters whose change should rebuild the
             subscription. Use `[]` for permanent subscriptions.

  Returns the most recent `(select state)`."
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
                                 ;; In-place advance of cached.state is safe:
                                 ;; React's useSyncExternalStore identity-checks
                                 ;; on the *returned snapshot*, not on the cache
                                 ;; object — and we return (.-snap cached) here,
                                 ;; which is unchanged. The mutation only updates
                                 ;; our identity-shortcut for the next call.
                                 (do (set! (.-state cached) state)
                                     (.-snap cached))
                                 (do (reset! cache-ref
                                             #js {:state state :snap new-snap})
                                     new-snap))))))
                       deps)]
    ;; Pass get-snapshot as get-server-snapshot too: CLJS atoms hold the
    ;; same data on server and client, and the snapshot path is pure (no
    ;; subscription side-effects). Without this, useSyncExternalStore throws
    ;; during hydration of any component using use-atom / use-db.
    (use-sync-external-store subscribe get-snapshot get-snapshot)))

(defn use-atom
  "Subscribe to a ClojureScript atom. Returns the current dereffed value and
  re-renders only when the value changes by `=`. Structurally-equal updates
  are treated as no-ops — a `swap!` that produces an `=`-equal map won't
  re-render consumers.

  Args:
    source - any IWatchable (`atom`, Cursor, etc.)"
  [source]
  (use-selector source not= identity [source]))