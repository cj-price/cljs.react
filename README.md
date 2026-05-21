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
| `make-element-fn` | Build an Element-like function bound to a custom renderer |
| `make-create-cljs-element-fn` | Same, for the `create-cljs-element` shape |

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
| `set-field-touched!` | Mark a single field touched so its error renders |

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

`ErrorBoundary`'s `:fallback` is arbitrary markup. Give the root of your
fallback `:role "alert"` so screen readers announce the error when it
appears:

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
      (FancyInput {:ref (react-ref input-ref) :placeholder "type here"})
      (Element {:tag "button"
                :onClick #(.focus @input-ref)}
        "Focus"))))
```

`use-ref` returns a `RefAtom` — `@input-ref` gives back the current DOM node
once the input has mounted.

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
