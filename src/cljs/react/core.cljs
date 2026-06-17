(ns cljs.react.core
  "Public entry point for the `cljs.react` library. Re-exports the Element DSL,
  hooks, memoized components, global-state (`db` + `Cursor`), form helpers, and
  the error boundary.

  Prefer requiring this namespace in consumer code; everything under
  `cljs.react.{component,hook,db,form,error-boundary}` is considered
  implementation detail and may change between minor versions. The companion
  namespace `cljs.react.dom` provides the mount API (`create-root`, `render`,
  `hydrate-root`, `unmount`) and `create-portal`.

  See `defnc` in `cljs.react.core` (macro) for the typical way to author
  components."
  (:require
   ["react" :as react]
   [cljs.react.component :as component]
   [cljs.react.hook :as hook]
   [cljs.react.db :as db]
   [cljs.react.form :as form]
   [cljs.react.error-boundary :as error-boundary]))

(defn Element
  "Create a React element from a ClojureScript props map and child elements.

  Usage:
    (Element {:tag \"div\" :className \"container\"} child1 child2 ...)

  The first argument must be a map with a :tag key (a string for DOM elements
  or a JS React component such as a MUI export). All other keys become props on
  the React element; remaining positional args are children.

  Do NOT use `Element` to call `defnc` components — call them directly:
    (MyComponent {:key \"k\" :foo 1})        ; correct
    (Element {:tag MyComponent :foo 1})    ; wrong — drops :key and cljsProps wrapper
  Calling a defnc through `Element` hands the component raw JS props instead of
  a CLJS map; the component body will see nothing usable.

  A nil :tag throws ex-info with :type :cljs.react.component/missing-tag."
  ([{:keys [tag] :as props}]
   (component/*create-element* tag (component/element-props tag props)))
  ([{:keys [tag] :as props} c1]
   (component/*create-element* tag (component/element-props tag props) c1))
  ([{:keys [tag] :as props} c1 c2]
   (component/*create-element* tag (component/element-props tag props) c1 c2))
  ([{:keys [tag] :as props} c1 c2 c3]
   (component/*create-element* tag (component/element-props tag props) c1 c2 c3))
  ([{:keys [tag] :as props} c1 c2 c3 & more]
   (apply component/*create-element* tag (component/element-props tag props) c1 c2 c3 more)))

;; Re-export ref utilities
(def ^{:doc "Extract the raw React ref object from a RefAtom. Hand the raw ref to DOM
  elements (`:ref (react-ref my-ref)`) or to JS components that don't speak
  the RefAtom protocols; the RefAtom itself is still useful for `deref` /
  `reset!` reads from CLJS."}
  react-ref hook/react-ref)
(def ^{:doc "Wrap a CLJS component fn with React.forwardRef. The forwarded ref is
  wrapped in a RefAtom and injected as `:ref` in the props map, so the body
  can `deref` / `reset!` it like any other RefAtom.

  Callers may supply the ref via either path:
    - Direct CLJS call: (MyComp {:ref some-ref ...}) — :ref lives inside the
      CLJS props map.
    - Raw createElement: (react/createElement MyComp #js {:ref some-ref ...}).

  React's top-level ref takes precedence; the cljsProps :ref is the fallback
  path that makes the direct-call API work."}
  forward-ref component/forward-ref)

;; Re-export hooks
(def ^{:doc "React.useEffect with CLJS structural equality on the deps vector.

  Args:
    effect-fn - 0-arity fn run after commit; may return a cleanup fn (or nil)
    deps      - optional vector of dependencies; the effect re-runs only when
                `=` on the deps vector changes.

  Returns nil. Pass `[]` to run once on mount, omit to run after every render."}
  use-effect hook/use-effect)
(def ^{:doc "React.useCallback with CLJS structural equality on deps. Returns the same
  fn reference across renders until `deps` changes.

  Args:
    f    - the function to memoize
    deps - optional vector of dependencies"}
  use-callback hook/use-callback)
(def ^{:doc "React.useMemo with CLJS structural equality on deps. Calls `f` and caches
  its return value; re-invokes only when `deps` changes.

  Args:
    f    - 0-arity fn producing the memoized value
    deps - optional vector of dependencies"}
  use-memo hook/use-memo)
(def ^{:doc "React.useLayoutEffect — like `use-effect` but fires synchronously after
  DOM mutations and before the browser paints. Use for reading layout or
  imperatively positioning elements; prefer `use-effect` otherwise.

  Args:
    effect-fn - 0-arity fn; may return a cleanup fn (or nil)
    deps      - optional vector of dependencies"}
  use-layout-effect hook/use-layout-effect)
(def ^{:doc "React.useRef wrapped as a RefAtom (supports `deref` / `reset!` / `swap!`).
  RefAtoms compare equal across renders when they wrap the same underlying
  React ref, so they're safe to place into hook deps.

  Args:
    initial - optional initial value (defaults to nil)

  Returns a RefAtom. Use `react-ref` to extract the raw React ref before
  handing to a DOM element or JS component."}
  use-ref hook/use-ref)
(def ^{:doc "Customize the handle exposed to parent components when using `forward-ref`.

  Args:
    ref           - the RefAtom received via `forward-ref` props
    create-handle - 0-arity fn returning the object exposed to the parent
    deps          - optional vector of dependencies

  Usage inside a forward-ref'd component:
    (use-imperative-handle ref
      (fn [] #js {:focus (fn [] (.focus @input-ref))})
      [])"}
  use-imperative-handle hook/use-imperative-handle)
(def ^{:doc "React.useContext. Reads the current value of a React context.

  Args:
    context - the context object returned by `create-context`

  Returns the nearest provided value (or the context's default if no provider
  is mounted above this component)."}
  use-context hook/use-context)
(def ^{:doc "React.useId. Returns a stable string id suitable for accessibility
  attributes (e.g. pairing <label htmlFor> with <input id>). Stable across
  renders of the same component instance; opaque format."}
  use-id hook/use-id)
(def ^{:doc "Local component state. Returns a StateAtom that supports `deref`, `reset!`,
  and `swap!`.

  Args:
    initial      - initial value. Stored verbatim, including functions — to
                   store a fn as state, just pass it.
    :lazy? true  - opt into React's lazy-init semantics: `initial` must be a
                   0-arity fn called once on mount, and its return value is
                   stored. Use for expensive initial computations.

  A fresh StateAtom value is returned each render with the current
  [value setter] tuple captured (matching React's snapshot semantics — `@s`
  in a closure reads the value at the render the closure was created in,
  NOT the latest committed value). If you need the latest value inside an
  async callback or interval, mirror it into a `use-ref` and deref the ref.
  StateAtoms backed by the same useState slot compare `=`, so they're safe
  to place into hook deps."}
  use-state hook/use-state)
(def ^{:doc "Subscribe to a ClojureScript atom. Returns the current dereffed value and
  re-renders only when the value changes by `=`. Structurally-equal updates
  are treated as no-ops — a `swap!` that produces an `=`-equal map won't
  re-render consumers.

  Args:
    source - any IWatchable (`atom`, Cursor, etc.)"}
  use-atom hook/use-atom)
(def ^{:doc "React.useSyncExternalStore — subscribe to an external store and return the
  current snapshot.

  Args:
    subscribe           - (fn [callback]) invoked once per subscription; must
                          call `callback` whenever the store changes and return
                          an unsubscribe fn.
    get-snapshot        - 0-arity fn returning the current value. Must be
                          referentially stable for unchanged state — React
                          tears on identity comparison.
    get-server-snapshot - optional 0-arity fn returning the SSR snapshot.

  Prefer `use-atom` / `use-selector` for Clojure-atom sources; reach for this
  only for external (non-atom) stores."}
  use-sync-external-store hook/use-sync-external-store)
(def ^{:doc "Subscribe to an IWatchable `source`, re-rendering only when `diff?` returns
  truthy between old/new states.

  Args:
    source - any IWatchable (atom, cursor, ratom-like) whose value the
             component depends on.
    diff?  - (fn [old-state new-state]) — return truthy to schedule a re-render.
    select - (fn [state]) projects a snapshot out of the current state.
             Equal projections (`=`) return the cached reference; required by
             React's tearing checks under useSyncExternalStore.
    deps   - vector of selector parameters whose change should rebuild the
             subscription. Use `[]` for permanent subscriptions.

  Returns the most recent `(select state)`."}
  use-selector hook/use-selector)
(def ^{:doc "React.useTransition. Returns `[pending? start-transition]`:
    - pending?         - true while a transition is in flight
    - start-transition - (fn [thunk]) marks the updates run inside `thunk` as
                         non-urgent so React can keep the UI responsive.

  Usage:
    (let [[pending? start-transition] (use-transition)]
      (Element {:tag \"button\"
                :onClick #(start-transition (fn [] (reset! state :slow)))}
        (if pending? \"…\" \"Go\")))"}
  use-transition hook/use-transition)
(def ^{:doc "React.useDeferredValue. Returns a deferred version of `value` that lags
  slightly behind during expensive updates, letting the UI stay responsive
  while the deferred copy catches up. Useful for piping a fast-changing input
  into an expensive list/visualisation."}
  use-deferred-value hook/use-deferred-value)

(defn start-transition
  "React.startTransition — the standalone counterpart to `use-transition`'s
  start-transition. Marks the state updates performed inside `thunk` (a 0-arity
  fn) as a non-urgent transition so React can keep the UI responsive. Reach for
  this form when you don't need the pending flag; use `use-transition` inside a
  component when you do."
  [thunk]
  (react/startTransition thunk))

;; React primitives
(defn create-context
  "Create a React context.

  Args:
    default-value - optional value returned by `use-context` when no
                    `<Context.Provider>` is mounted above the consumer.
                    Defaults to nil.

  Returns the context object — hand it to `use-context` to read, or use its
  `.-Provider` to install a value (most callers prefer `DBProvider` /
  ad-hoc Providers built on top of `create-context`)."
  ([] (react/createContext nil))
  ([default-value] (react/createContext default-value)))

(def ^{:doc "React.Fragment — render children without adding a DOM wrapper. Use as the
  :tag on an Element when you need to return multiple siblings without an
  enclosing <div>: (Element {:tag Fragment} child-1 child-2)."}
  Fragment react/Fragment)

(def ^{:doc "React.Suspense — render a fallback while descendants suspend (e.g. while a
  lazy-loaded component or a suspending data hook is pending).

  Props map:
    :fallback - element rendered while any descendant is suspended.

  Usage:
    (Element {:tag Suspense :fallback (Element {:tag \"div\"} \"Loading…\")}
      (LazyChild))"}
  Suspense react/Suspense)

;; Re-export ErrorBoundary
(def ^{:doc "Render children inside a React error boundary.

  Props map:
    :fallback  — element value, or a 1-arity fn (fn [error] -> element) called
                 with the thrown error. **Required, must be non-nil** — a missing
                 or nil `:fallback` throws ex-info `:type ::missing-fallback` /
                 `::nil-fallback` at element-creation time so a typo'd key
                 (e.g. `:fall-back`) is loud rather than silently swallowing the
                 error. If the fallback fn itself throws (e.g. wrong arity, or
                 the rendered element is malformed), the boundary catches that
                 too and renders a `<pre>` sentinel with both errors logged to
                 the console.

                 NOTE: a `defnc` component is itself a function, so passing one
                 directly will invoke it with the error as its props (almost
                 never what you want). Wrap it:
                 `:fallback (fn [err] (MyFallback {:error err}))`.
    :on-error  — optional (fn [error info] ...) invoked in componentDidCatch,
                 useful for logging/telemetry.

  Usage:
    (ErrorBoundary
      {:fallback (fn [err]
                   (Element {:tag \"div\" :role \"alert\"}
                     (ex-message err)))}
      (RiskyChild))

  Tip: give the fallback markup `:role \"alert\"` so screen readers announce
  the error when it appears."}
  ErrorBoundary error-boundary/ErrorBoundary)

;; Re-export db utilities
(def ^{:doc "Provide a database context for child components — installs a single
  CLJS atom that descendants can read/write via `use-db`.

  Props map (all keys optional):
    :value - the db. Accepts either:
             • an atom — used as-is so the caller owns it and can watch or
               snapshot it from outside React
             • a plain CLJS value — atom-ified internally on first render
             • omitted/nil — defaults to (atom {})
             Captured once on mount; later re-renders with a different :value
             do not replace the running atom.

  Usage:
    (DBProvider {} (App))                                ; defaults to (atom {})
    (DBProvider {:value {:user nil :todos []}} (App))    ; plain value
    (DBProvider {:value my-atom} (App))                  ; caller-owned atom"}
  DBProvider db/DBProvider)
(def ^{:doc "Subscribe to the db and return a Cursor scoped to `path`.

  Arities:
    0-arity     - root cursor; `@cursor` is the whole db; `reset!`/`swap!`
                  replace the root value.
    (use-db path) - cursor scoped to a vector path; an empty vector is
                  equivalent to the 0-arity root cursor.

  Path must be a vector; non-vector paths throw ex-info
  `:type :cljs.react.db/invalid-cursor-path`.

  Cursor identity is memoized on `path` — `=`-equal paths return the same
  Cursor across renders, so the result is safe in `use-effect` / `use-memo`
  deps. The component re-renders only when the value at `path` changes.

  Throws ex-info `:type :cljs.react.db/no-provider` if called outside a
  `DBProvider`."}
  use-db db/use-db)

;; Re-export form utilities
(def ^{:doc "Create a form handle owning the form-state atom. Pass the returned
  FormHandle to `use-field` / `use-form-meta` to subscribe to slices, and to
  `on-submit` to build the DOM submit handler.

  opts map:
    :values      - initial values map, or a watchable (atom/cursor) for reactive defaults
    :validate    - fn(values) -> errors-map or Promise<errors-map>
    :on-submit   - fn(values) -> nil or Promise
    :validate-on - nil | :submit | :blur. :submit (the default) validates only
                   when the form is submitted; :blur additionally re-validates
                   when a field blurs.

  When :values is a plain map, it is captured once on first render — later
  changes to the same key (e.g. props re-rendering with a new :values map)
  do NOT reset the form. Pass an atom/cursor if you need reactive defaults;
  un-dirtied fields will then follow changes to the watchable.

  Validator and submit failures (both sync throws and async rejections) are
  funneled into :submit-error and reset :submitting? / :validating? to false.
  :submit-error is cleared at the start of each new submit.

  Throws ex-info `:type :cljs.react.form/invalid-validate-on` if :validate-on
  is anything other than nil, :submit, or :blur."}
  use-form form/use-form)
(def ^{:doc "Subscribe to a single field. Returns a map with:
    :value    — current value (use for text/select inputs)
    :checked  — true only when :value is the literal boolean `true`
                (use for checkbox inputs)
    :error    — error string, or nil while the field is untouched
    :dirty    — boolean: has the user changed this field since reset?
    :onChange — DOM change handler (extracts e.target.value / .checked)
    :onBlur   — DOM blur handler (marks the field touched)

  Both :value and :checked are always present so callers can destructure
  uniformly; only one is meaningful per field type. Re-renders only when this
  specific field's value, error, dirty, or touched state changes.

  opts map (optional):
    :checkbox? - true for checkbox fields. Switches :onChange to read
                 e.target.checked instead of e.target.value.

  Radio groups: no dedicated mode — use the field as a string and set each
  input's :checked to (= field-value option) and :value to option, e.g.
  (Element {:tag \"input\" :type \"radio\" :name \"color\" :value \"red\"
            :checked (= (:value field) \"red\") :onChange (:onChange field)})."}
  use-field form/use-field)
(def ^{:doc "Subscribe to form meta state (everything except :values).
  Returns `{:validating? :submitting? :submitted? :errors :submit-error}`.
  Re-renders only when meta state changes."}
  use-form-meta form/use-form-meta)
(def ^{:doc "Returns an onSubmit event handler.

  Arities:
    (on-submit handle)            - uses :on-submit from opts (read at event
                                    time, always fresh).
    (on-submit handle submit-fn)  - override with a specific submit fn.

  Both arities call `.preventDefault` on the event, run validation, and then
  invoke the submit fn with the current :values. While a submit is in flight
  (:submitting? true), further submits are ignored — clicking the submit
  button twice will not run on-submit twice."}
  on-submit form/on-submit)
(def ^{:doc "Reset the form to its initial values, clearing errors, dirty/touched flags,
  and submit state.

  Arities:
    (reset-form! handle)         - reset to the initial :values from opts.
    (reset-form! handle values)  - reset to the supplied values map instead."}
  reset-form! form/reset-form!)
(def ^{:doc "Replace the form's `:values` map. Does not touch errors or any flags
  (`:dirty`, `:touched`, `:submitting?`, ...) — call `reset-form!` for that."}
  set-values! form/set-values!)
(def ^{:doc "Replace the form's `:errors` map. Field-keys with non-nil error values
  are also added to `:touched` so the errors are surfaced by `use-field`
  without the user having to blur each field first. Nil-valued keys are not
  touched — touched-ness tracks user interaction, not error presence."}
  set-errors! form/set-errors!)
(def ^{:doc "Clear all field errors and any `:submit-error`. Does not change `:values`,
  `:dirty`, or `:touched`."}
  clear-errors! form/clear-errors!)
(def ^{:doc "Mark a field as touched so its error becomes visible to `use-field`.
  Useful for surfacing a server-side error tied to a specific field, in
  combination with `set-errors!`."}
  touch-field! form/touch-field!)

;; Form escape hatches — for testing, devtools, or custom integrations.
;; Most app code should stick to use-form / use-field / use-form-meta.
(def ^{:doc "Return the raw form-state atom for direct inspection/mutation.

  Dereferencing yields a map with the following keys (this shape is part of
  the public contract):
    :values        — current values map
    :errors        — map of field-key → error
    :dirty         — set of field-keys the user has changed
    :touched       — set of field-keys that have been blurred or submitted
    :validating?   — true while an async validator is in flight
    :submitting?   — true while a submit is in flight
    :submitted?    — true after a submit completes successfully
    :submit-error  — error from the most recent failed submit, or nil

  Advanced — prefer use-form-meta / use-field for reactive reads."}
  form-atom form/form-atom)
(def ^{:doc "Return the current :use-form opts map (always fresh)."}
  form-opts form/form-opts)
