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

  A nil :tag throws ex-info with :type :cljs.react.component/missing-tag.

  `Element` honors `component/*create-element*`, which defaults to
  `react/createElement` but can be rebound to use alternative renderers
  (for example a custom JSX runtime). The escape-hatch helpers
  `make-element-fn` / `make-create-cljs-element-fn` build standalone
  Element-shaped fns bound to a specific renderer; they do not read this
  dynamic var."
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
(def ^{:doc "Extract the raw React ref object from a RefAtom; use when passing refs to DOM elements or JS components."}
  react-ref hook/react-ref)
(def ^{:doc "Wrap a CLJS component fn with React.forwardRef. The forwarded ref is wrapped in a RefAtom and injected as :ref in the props map."}
  forward-ref component/forward-ref)

;; Re-export hooks
(def ^{:doc "React.useEffect. Pass effect-fn and optional deps vector; structural equality is used on deps."}
  use-effect hook/use-effect)
(def ^{:doc "React.useCallback. Memoize a fn across renders using CLJS structural equality on deps."}
  use-callback hook/use-callback)
(def ^{:doc "React.useMemo. Memoize a computed value across renders using CLJS structural equality on deps."}
  use-memo hook/use-memo)
(def ^{:doc "React.useLayoutEffect. Like use-effect but runs synchronously after DOM mutations."}
  use-layout-effect hook/use-layout-effect)
(def ^{:doc "React.useRef wrapped as a RefAtom (deref / reset! / swap!). 0-arity initializes to nil."}
  use-ref hook/use-ref)
(def ^{:doc "Customize the handle exposed to parent components when using forward-ref.

  Usage inside a forward-ref'd component:
    (use-imperative-handle ref
      (fn [] #js {:focus (fn [] (.focus @input-ref))})
      [])

  Args: a RefAtom (the forwarded ref), a 0-arity fn returning the handle
  object, and an optional deps vector. Deps follow the standard hook rules
  (CLJS structural equality)."}
  use-imperative-handle hook/use-imperative-handle)
(def ^{:doc "React.useContext. Reads the current value of a React context.
  The argument is the context object returned by `create-context`."}
  use-context hook/use-context)
(def ^{:doc "React.useId. Generates a unique, stable id suitable for accessibility attributes."}
  use-id hook/use-id)
(def ^{:doc "Local component state. Returns a StateAtom that supports deref, reset!, and swap!."}
  use-state hook/use-state)
(def ^{:doc "Subscribe to a ClojureScript atom. Returns the current value and re-renders only when the value changes; structurally-equal swaps are no-ops."}
  use-atom hook/use-atom)
(def ^{:doc "React.useSyncExternalStore. Subscribe to an external store with (fn subscribe [cb]) and (fn get-snapshot [])."}
  use-sync-external-store hook/use-sync-external-store)
(def ^{:doc "Subscribe to an IWatchable source with a custom diff?/select pair. See cljs.react.hook/use-selector for details."}
  use-selector hook/use-selector)
(def ^{:doc "Returns [is-pending? start-transition] for marking updates as non-urgent.

  Usage:
    (let [[pending? start-transition] (use-transition)]
      (Element {:tag \"button\"
                :onClick #(start-transition (fn [] (reset! state :slow)))}
        (if pending? \"…\" \"Go\")))

  Wrap expensive state updates in start-transition to keep the UI responsive."}
  use-transition hook/use-transition)
(def ^{:doc "React.useDeferredValue. Returns a deferred version of the supplied value that lags slightly behind during expensive updates."}
  use-deferred-value hook/use-deferred-value)

;; React primitives
(defn create-context
  "Create a React context with an optional default value."
  ([] (react/createContext nil))
  ([default-value] (react/createContext default-value)))

(def ^{:doc "React.Fragment — group children without adding a DOM wrapper."}
  Fragment react/Fragment)

(def ^{:doc "React.Suspense — render a fallback while descendants suspend."}
  Suspense react/Suspense)

;; Re-export ErrorBoundary
(def ^{:doc "Render children inside a React error boundary. Props: :fallback (element or (fn [err] element)) and optional :on-error (fn [err info])."}
  ErrorBoundary error-boundary/ErrorBoundary)

;; Re-export db utilities
(def ^{:doc "Provide a database context for child components. Usage: (DBProvider {:initial-value {...}} child1 child2 ...)"}
  DBProvider db/DBProvider)
(def ^{:doc "Subscribe to the db. Returns a Cursor (deref / reset! / swap!) that re-renders only when the value at path changes. 0-arity is the root cursor; 1-arity takes a vector path."}
  use-db db/use-db)
(def ^{:doc "Return the raw db atom from context. Does not subscribe — use use-db for reactive reads."}
  use-db-atom db/use-db-atom)

;; Re-export form utilities
(def ^{:doc "Create a form handle. Returns a FormHandle — pass it to use-field /
  use-form-meta / on-submit to drive a form.

  opts map:
    :values      - initial values map, or a watchable (atom/cursor) for reactive defaults
    :validate    - fn(values) -> errors-map or Promise<errors-map>
    :on-submit   - fn(values) -> nil or Promise
    :validate-on - nil | :submit | :blur. :submit (the default) validates only
                   on submit; :blur additionally re-validates when a field blurs.

  When :values is a plain map, it is captured ONCE on first render — later
  re-renders with a new map literal do not reset the form. Pass an atom/cursor
  if you want un-dirtied fields to follow external changes.

  Throws ex-info :type :cljs.react.form/invalid-validate-on if :validate-on
  is anything other than nil, :submit, or :blur."}
  use-form form/use-form)
(def ^{:doc "Subscribe to a single field. Re-renders only when this field's
  value, error, dirty, or touched state changes.

  Returns a map with keys:
    :value    — current value (omitted when opts :checkbox? is true)
    :checked  — boolean (only when opts :checkbox? is true)
    :error    — error string, or nil while the field is untouched
    :dirty    — boolean: has the user changed this field since reset?
    :onChange — DOM change handler (extracts e.target.value / .checked)
    :onBlur   — DOM blur handler (marks the field touched)

  opts map (optional): :checkbox? — reads e.target.checked and returns :checked
  instead of :value."}
  use-field form/use-field)
(def ^{:doc "Subscribe to form meta state. Returns {:validating? :submitting? :submitted? :errors :submit-error}."}
  use-form-meta form/use-form-meta)
(def ^{:doc "Return an onSubmit event handler for the form. 1-arity uses :on-submit from opts; 2-arity overrides with a submit fn."}
  on-submit form/on-submit)
(def ^{:doc "Reset the form to its initial values, clearing errors, dirty/touched, and submit state. 2-arity resets to supplied values."}
  reset-form! form/reset-form!)
(def ^{:doc "Replace the form's :values map. Does not clear errors or flags."}
  set-values! form/set-values!)
(def ^{:doc "Replace the form's :errors map. Keys with errors are also marked touched so use-field surfaces them."}
  set-errors! form/set-errors!)
(def ^{:doc "Clear all field errors and any :submit-error."}
  clear-errors! form/clear-errors!)
(def ^{:doc "Mark a field as touched so its error becomes visible to use-field."}
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
