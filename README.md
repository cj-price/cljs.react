# cljs.react

Idiomatic ClojureScript bindings for React 19. Element DSL, `defnc` function
components, hooks that return CLJS atoms, a `Cursor`-based global store, and a
per-field form module — all built so persistent data structures flow through
React without constant `clj->js` / `js->clj` churn.

**Status:** v0.x, pre-release. Small breaking changes may land at minor
versions until v1.0. Anything under `cljs.react.{component,hook,db,form,error-boundary}`
is implementation detail — require `cljs.react.core` and `cljs.react.dom` from
application code.

## Install

Git dependency in `deps.edn`:

```clojure
{:deps
 {io.github.cj-price/cljs.react
  {:git/url "https://github.com/cj-price/cljs.react.git"
   :git/sha "<latest commit sha>"}}}
```

Add React as a peer dep in `package.json` (pnpm, npm, or yarn):

```json
{
  "dependencies": {
    "react": "^19.0.0",
    "react-dom": "^19.0.0"
  }
}
```

## Quickstart

```clojure
(ns app.main
  (:require [cljs.react.core :refer [defnc Element use-state]]
            [cljs.react.dom :as dom]))

(defnc Counter [{:keys [initial]}]
  (let [n (use-state initial)]
    (Element {:tag "div"}
      (Element {:tag "p"} "Count: " @n)
      (Element {:tag "button" :onClick #(swap! n inc)} "+1"))))

(defonce root (dom/create-root (js/document.getElementById "app")))

(defn init []
  (dom/render root (Counter {:initial 0})))
```

- `defnc` defines a memoized function component (CLJS props → JS under the hood).
- `Element` creates a React element from a map with a `:tag` key.
- `use-state` returns a `StateAtom` — `deref` / `reset!` / `swap!` all work natively.

## API map

Everything below is re-exported from `cljs.react.core` unless otherwise noted.

### Element DSL

| Symbol | Purpose |
| --- | --- |
| `Element` | Create a React element from `{:tag ... :key ... ...}` + children |
| `Fragment` | `React.Fragment` element type |
| `Suspense` | `React.Suspense` element type |
| `ErrorBoundary` | Catch render-phase errors and render a `:fallback` |
| `create-context` | Build a React context with an optional default value |

### Hooks

| Symbol | Purpose |
| --- | --- |
| `use-state` | Local state → `StateAtom` (deref / reset! / swap!) |
| `use-ref` | Mutable ref → `RefAtom` (deref / reset! / swap!) |
| `react-ref` | Extract the raw JS ref from a `RefAtom` for DOM/JS interop |
| `use-effect` | `React.useEffect` with CLJS-equality deps |
| `use-layout-effect` | `React.useLayoutEffect` with CLJS-equality deps |
| `use-memo` | `React.useMemo` with CLJS-equality deps |
| `use-callback` | `React.useCallback` with CLJS-equality deps |
| `use-context` | Read the current value of a React context |
| `use-id` | `React.useId` — stable id for a11y |
| `use-imperative-handle` | Expose an imperative handle via forwardRef |
| `use-sync-external-store` | `React.useSyncExternalStore` |
| `use-transition` | `[is-pending start-transition]` for non-urgent updates |
| `use-deferred-value` | `React.useDeferredValue` |
| `use-atom` | Subscribe to a CLJS atom (equal swaps are no-ops) |
| `use-selector` | Subscribe to an `IWatchable` with custom `diff?`/`select` (advanced) |

### Components

| Symbol | Purpose |
| --- | --- |
| `defnc` (macro) | Define a memoized function component |
| `forward-ref` | Wrap a CLJS component fn with `React.forwardRef` |

### Global state (db / Cursor)

| Symbol | Purpose |
| --- | --- |
| `DBProvider` | Install an atom at the root of the tree |
| `use-db` | Subscribe to the db; returns a Cursor (deref / reset! / swap!). 0-arity is the root cursor; 1-arity takes a vector path. |
| `use-db-atom` | Return the raw db atom (no subscription) |

### Forms

| Symbol | Purpose |
| --- | --- |
| `use-form` | Create a `FormHandle` from `{:values :validate :on-submit :validate-on}` |
| `use-field` | Subscribe to a single field's slice; returns handlers + value/error |
| `use-form-meta` | Subscribe to `{:validating? :submitting? :submitted? :errors :submit-error}` |
| `on-submit` | Build the `onSubmit` event handler for a `FormHandle` |
| `reset-form!` | Reset values to initial; clear errors / dirty / touched / submit state |
| `set-values!` | Replace the form's `:values` map |
| `set-errors!` | Replace `:errors` (and mark each keyed field touched) |
| `clear-errors!` | Clear all field errors and `:submit-error` |
| `touch-field!` | Mark a single field touched so its error renders |
| `form-atom` | Raw form-state atom (testing/devtools); `@(form-atom h)` for a snapshot |
| `form-opts` | Current `:use-form` opts map |

### DOM mount (cljs.react.dom)

| Symbol | Purpose |
| --- | --- |
| `create-root` | Wrap `ReactDOM.createRoot(container)` |
| `hydrate-root` | Wrap `ReactDOM.hydrateRoot(container, element)` — for SSR-rendered markup |
| `render` | `(.render root element)` |
| `unmount` | `(.unmount root)` |
| `create-portal` | `ReactDOM.createPortal(children, container)` |

For SSR, render server-side with `react-dom/server` (e.g. `renderToString`) and
on the client call `hydrate-root` on the same container; the initial CLJS
element tree must match the server-rendered markup.

## Conventions

- **CamelCase** — React element/component constructors: `Element`, `Fragment`,
  `Suspense`, `DBProvider`, `ErrorBoundary`, `defnc`-defined components.
- **kebab-case** — everything else: hooks, utilities, handler builders
  (`use-state`, `use-db`, `on-submit`, `clj->js-props`, `forward-ref`).

Rule of thumb: if a symbol returns a React element or is intended to sit in
element-creation position, it is CamelCase. Otherwise it is kebab-case.

### `ErrorBoundary` and `role="alert"`

`ErrorBoundary`'s `:fallback` is either a static React element or a 1-arity
`(fn [error] ...)` returning an element. Use the function form when you need
to display the error itself. Give the root of your fallback `:role "alert"`
so screen readers announce the error when it appears:

```clojure
(ErrorBoundary
  {:fallback (fn [err]
               (Element {:tag "div" :role "alert"}
                 "Something went wrong: " (ex-message err)))}
  (RiskyChild))
```

### `forward-ref` and `use-ref`

`defnc` accepts a `:forward-ref` flag that wraps the component with
`React.forwardRef`. Inside the body, the forwarded ref arrives as a `RefAtom`
on the `:ref` key of props. Use `react-ref` to extract the raw JS ref object
when attaching it to a DOM element:

```clojure
(defnc FancyInput :forward-ref
  [{:keys [ref placeholder]}]
  (Element {:tag "input" :ref (react-ref ref) :placeholder placeholder}))

(defnc Parent []
  (let [input-ref (use-ref nil)]
    (Element {:tag "div"}
      (FancyInput {:ref input-ref :placeholder "type here"})
      (Element {:tag "button"
                :onClick #(.focus @input-ref)}
        "Focus"))))
```

`use-ref` returns a `RefAtom` — `@input-ref` gives back the current DOM node
once the input has mounted. At the call site, pass the `RefAtom` directly;
`forward-ref` accepts either a `RefAtom` or a raw React ref object.

### `:key` on function components

`defnc` components wrap props in a `cljsProps` JS object, which would normally
hide `:key` from React's reconciler. To make keyed lists "just work", `defnc`
hoists `:key` out of the CLJS props map and onto the top-level JS props object,
so calling a component directly is enough:

```clojure
(for [item items]
  (MyItem {:key (:id item) :item item}))
```

Note: `Element` is for DOM tags (strings) and plain JS React components — it
passes props as flat JS objects. For `defnc` components, call them directly
as shown above so they receive their `cljsProps` wrapper.

## Data shapes

The public types in `cljs.react.core` all support `deref` / `reset!` / `swap!`
so consumers can treat them like ordinary atoms.

### `StateAtom` — returned by `use-state`

Wraps React's `[value setter]` tuple. A fresh `StateAtom` is allocated per
render, but two wrappers backed by the same `useState` slot compare equal
under `=` (setter identity is stable across renders), so a `StateAtom` is safe
to place into `use-effect` / `use-memo` / `use-callback` deps.

```clojure
(let [n (use-state 0)]
  @n           ;; current value
  (reset! n 1) ;; replace
  (swap! n inc))
```

### `RefAtom` — returned by `use-ref` and injected by `forward-ref`

Wraps a React ref. `deref` reads the current `.current`; `reset!` / `swap!`
write it. Use `react-ref` to extract the raw JS ref when handing it to a DOM
element or JS component:

```clojure
(let [r (use-ref nil)]
  (Element {:tag "input" :ref (react-ref r)})
  ...
  (.focus @r))
```

### `Cursor` — returned by `use-db`

`(use-db)` returns a cursor over the root db; `(use-db [:user :name])` returns
a cursor scoped to a path. `=`-stable per path, so safe in deps:

```clojure
(let [user (use-db [:user])
      name (use-db [:user :name])]
  @user                                  ;; {:name "Alice", ...}
  @name                                  ;; "Alice"
  (swap! user assoc :name "Bob")         ;; writes through the path
  (reset! name "Carol"))                 ;; equivalent to assoc-in [:user :name]
```

Calling `use-db` outside a `DBProvider` throws `ex-info` with `:type
:cljs.react.db/no-provider`.

### `FormHandle` — returned by `use-form`

Opaque. Pass to `use-field` / `use-form-meta` / `on-submit` / `reset-form!`
etc. The underlying form-state atom is exposed via `form-atom` for testing
and devtools; deref yields:

```clojure
{:values        {...}      ;; current values
 :errors        {...}      ;; field-key → error
 :dirty         #{...}     ;; field-keys the user has changed
 :touched       #{...}     ;; field-keys that have been blurred or submitted
 :validating?   false      ;; true while an async validator is in flight
 :submitting?   false      ;; true while a submit is in flight
 :submitted?    false      ;; true after a submit completes successfully
 :submit-error  nil}       ;; error from the most recent failed submit
```

`use-field` returns a map with `:value`, `:checked`, `:error`, `:dirty`,
`:onChange`, and `:onBlur`. Both `:value` and `:checked` are always present so
destructuring is uniform across field types — pick the one your input needs.

## Troubleshooting

### Do I need `#js` for prop values?

No. `Element` and `defnc` recursively convert CLJS maps to JS objects when
serializing props. Write `:style {:color "red"}`, `:sx {:maxWidth 360}`, even
`:dangerouslySetInnerHTML {:__html "..."}` — all idiomatic CLJS maps. `#js`
is only needed when you're calling `react/createElement` directly (raw JS
interop).

### `defnc` vs `Element` — which one calls a component?

Call `defnc` components directly. `Element` is for DOM tags (strings) and
plain JS React components (e.g. MUI exports):

```clojure
(MyComponent {:key "k" :foo 1})         ;; correct
(Element {:tag MyComponent :foo 1})     ;; wrong — drops :key + cljsProps wrapper
(Element {:tag "div"} ...)              ;; correct — DOM
(Element {:tag MuiCard :sx {...}} ...)  ;; correct — JS component
```

### `ErrorBoundary` `:fallback` won't render my component

`:fallback` is either a React element or a `(fn [err] element)`. Passing a
bare `defnc` doesn't work — it would be invoked with the error as a JS prop.
Wrap it:

```clojure
(ErrorBoundary
  {:fallback (fn [err] (MyFallback {:error err}))}
  ...)
```

### `use-db` throws on startup

`use-db` must be called from inside a `DBProvider`. The throw is `ex-info`
with `:type :cljs.react.db/no-provider`. Mount the provider at the root:

```clojure
(dom/render root (DBProvider {:initial-value {...}} (App)))
```

### Refs and DOM interop

A `RefAtom` is a CLJS wrapper. To hand it to a non-CLJS consumer (a DOM
attribute on a JS component, a JS library's `.observe` API, etc.) extract the
raw React ref with `react-ref`. When passing a ref to another CLJS component
or a DOM element through `Element`, pass the `RefAtom` directly —
`clj->js-props` unwraps it for you.

## Development

Uses `nix-shell` (Node 22, Clojure, Babashka, JDK 17) and `bb` tasks:

```bash
nix-shell --run 'bb dev'    # watch + compile demo → http://localhost:9011
nix-shell --run 'bb test'   # compile and run test suite
nix-shell --run 'bb bench'  # run performance benchmarks
```

Test suite uses `cljs.test` + `@testing-library/react` + `global-jsdom`; the
`:test` build runs under Node.

The `:release-demo` shadow-cljs build target compiles the demo under
`:optimizations :advanced` as a smoke test for externs / dead-code issues:

```bash
nix-shell --run 'npx shadow-cljs release release-demo'
```

## License

MIT — see [LICENSE](LICENSE).
