# cljs.react

**[Live demo →](https://cj-price.github.io/cljs.react/)**

Idiomatic ClojureScript bindings for React 19. Element DSL, `defnc` function
components, hooks that return CLJS atoms, a `Cursor`-based global store, and a
per-field form module — all built so persistent data structures flow through
React without constant `clj->js` / `js->clj` churn.

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
| `use-lazy-loadable` | Lazy-load a shadow-cljs code-split module as a Suspense-ready component |

`start-transition` (non-hook) is also re-exported from `cljs.react.core` —
standalone `React.startTransition` for marking updates non-urgent outside a
component, where the pending flag of `use-transition` isn't needed.

### Components

| Symbol | Purpose |
| --- | --- |
| `defnc` (macro) | Define a memoized function component |
| `forward-ref` | Wrap a CLJS component fn with `React.forwardRef` |

### Global state (db / Cursor)

| Symbol | Purpose |
| --- | --- |
| `DBProvider` | Install the db at the root of the tree. `:value` may be an atom (used as-is) or a plain value (atom-ified); defaults to `(atom {})` |
| `use-db` | Subscribe to the db; returns a Cursor (deref / reset! / swap!). 0-arity is the root cursor; 1-arity takes a vector path. |

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
| `mount!` | `create-root` + `render` in one call; returns the root |
| `flush-sync` | Flush updates made inside a thunk synchronously (`ReactDOM.flushSync`) |
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
on the `:ref` key of props. Pass the `RefAtom` directly through `Element` —
`clj->js-props` unwraps it for you:

```clojure
(defnc FancyInput :forward-ref
  [{:keys [ref placeholder]}]
  (Element {:tag "input" :ref ref :placeholder placeholder}))

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

### Lazy loading code-split modules (`use-lazy-loadable`)

`use-lazy-loadable` bridges a shadow-cljs code-split module to React Suspense. Define the
split point with `shadow.lazy/loadable` (referencing a component **without**
`:require`-ing its namespace — that reference is what tells shadow to emit a
separate chunk), then hand the loadable to `use-lazy-loadable`. It returns a stable,
callable component you render inside a `Suspense` boundary:

```clojure
(ns my.app.view
  (:require [shadow.lazy :as lazy]
            [cljs.react.core :refer [Element Suspense use-lazy-loadable]])
  (:require-macros [cljs.react.core :refer [defnc]]))

;; Module-level def so the loadable's identity stays stable across renders.
(def panel (lazy/loadable my.app.panel/Panel))

(defnc View []
  (let [Panel (use-lazy-loadable panel)]
    (Element {:tag Suspense :fallback (Element {:tag "div"} "Loading…")}
      (Panel {:label "hi"}))))
```

The fallback shows while the chunk is fetched; once resolved, the component
renders and receives its props/children exactly like an eager `defnc` component.
This requires `:module-loader true` plus a `:modules` entry for the split
namespace in your shadow-cljs build (see `shadow-cljs.edn` and
`dev/cljs/react/demo/lazy_panel.cljs` in this repo for a worked example).

`use-lazy-loadable` also accepts a plain `() => Promise<component>` loader fn, so it works
without the shadow module machinery (and is unit-testable on its own).

Notes:

- **Keep `src` stable** — hold the loadable/loader in a module-level `def`. Passing
  a fresh value each render (e.g. an inline `(lazy/loadable …)`) rebuilds the lazy
  component and re-suspends every render; a dev build warns when it detects this.
- **Retry** — `React.lazy` caches the settled result (success *or* failure). To
  recover from a failed chunk load, remount the component that calls `use-lazy-loadable`
  (bump a `:key`); the cached error can't be cleared in place. A rejected load
  surfaces to the nearest `ErrorBoundary`.
- **SSR** — lazy content is client-only. Under `hydrate-root` the Suspense fallback
  renders on the server and the chunk loads on the client.

### `:as-element` components (JS-side interop)

`defnc` accepts an `:as-element` flag for components that are mounted by a
JS-side React tree (a JS library that calls your component with a plain JS
props object). Instead of the `cljsProps` wrapper, the component is memoized
with React's default shallow (`Object.is`) comparison on raw JS props:

```clojure
(defnc Row :as-element
  [props]
  (Element {:tag "li"} (.-label props)))   ; props is a raw JS object here
```

Two caveats versus the default path:

- **Children arrive raw.** They come through in React's tri-shape
  (`undefined` / single child / JS array), without the seq normalization the
  default and `:forward-ref` paths apply.
- **Memo is shallow, not structural.** This path uses `Object.is`, not the deep
  CLJS `=` used by default. Passing a freshly-built CLJS map each render defeats
  the memo (every render is a new JS object) — pass plain JS props or stable
  references when memoization matters here.

Reach for `:as-element` only at a CLJS↔JS boundary; for normal CLJS-to-CLJS
composition, the default `defnc` is what you want.

### Custom renderers (advanced)

`Element` and `defnc` build elements through the dynamic var
`cljs.react.component/*create-element*` (default `react/createElement`). To
target an alternative renderer (e.g. an emotion `jsx`), build bound element
functions with `make-element-fn` / `make-create-cljs-element-fn`. These live in
`cljs.react.component`, which is semi-stable implementation detail — pin a
version if you depend on them.

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
write it. Pass the `RefAtom` directly to `Element`; `clj->js-props` unwraps
it. Use `react-ref` only when handing the raw JS ref to a non-CLJS consumer:

```clojure
(let [r (use-ref nil)]
  (Element {:tag "input" :ref r})
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

## Re-render model

A quick mental model of what causes a component to re-render:

- **`defnc` components are memoized by default.** A child re-renders when its
  parent renders *and* its new props are not `=` (deep CLJS structural equality)
  to the previous props — so passing an `=`-equal map skips the re-render. The
  `:as-element` path instead uses React's shallow `Object.is` (see above).
- **`use-state` follows React.** `reset!` / `swap!` schedule a re-render; React
  bails out via `Object.is`, so replacing state with an identical *reference*
  is a no-op, but a freshly-built equal map is a new reference and re-renders.
- **Atom / cursor subscriptions bail on `=`.** `use-atom`, `use-selector`, and
  `use-db` re-render only when the watched value changes by structural `=`. A
  `swap!` that produces an `=`-equal value does not re-render consumers. A
  `Cursor` fires only when the value *at its path* changes — sibling writes are
  ignored.
- **Forms subscribe per field.** `use-field` re-renders only when that one
  field's value / error / dirty / touched slice changes; `use-form-meta` only
  on meta changes. Typing in one field does not re-render the others.

The wrapper types (`StateAtom`, `RefAtom`, `Cursor`) are all `=`-stable across
renders for the same underlying slot/path, so they are safe to place directly in
`use-effect` / `use-memo` / `use-callback` deps.

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
