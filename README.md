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
   :git/tag "v0.2.0"
   :git/sha "<sha of the tagged commit>"}}}
```

Pin a [release tag](https://github.com/cj-price/cljs.react/releases) and its
commit sha; `clj -X:deps find-versions :lib io.github.cj-price/cljs.react`
lists them. Tracking a plain `:git/sha` from trunk also works.

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

### Styling (cljs.react.sx)

**Not re-exported** — require `cljs.react.sx` directly. It is opt-in: nothing in
`cljs.react.core` pulls it in, so you pay nothing if you don't use it. It adds
no dependencies, Clojure or npm.

| Symbol | Purpose |
| --- | --- |
| `use-sx` | Compile an sx map (or `defstyle` var, or vector of them) to a class name |
| `defstyle` | Define a stable style identity — macro, `(:require-macros [cljs.react.sx :refer [defstyle]])` |
| `keyframes` | Define an animation; the returned value goes under `:animation-name` |
| `keyframes-name` | Its generated global name, for the `animation` shorthand or interop |
| `use-theme` | The merged theme map, for values rather than vars |
| `use-theme-class` | Scope class from the nearest nested `ThemeProvider`, or nil |
| `theme-var` | `"var(--cx-…)"` string for a theme path, for hand-written CSS |
| `style` | Compile to a class outside React |
| `ThemeProvider` | Install a theme — `:root` vars at the root, a scoped class when nested |
| `BaselineProvider` | Minimal global reset (see the warning under Conventions) |

```clojure
(ns app.card
  (:require [cljs.react.core :refer [Element]]
            [cljs.react.sx :refer [use-sx ThemeProvider]])
  (:require-macros [cljs.react.core :refer [defnc]]
                   [cljs.react.sx :refer [defstyle]]))

(defstyle card {:p 2 :border-radius :shape.border-radius
                :bgcolor :palette.background.paper
                :&:hover {:box-shadow 2}})

(defnc Card [{:keys [selected? children]}]
  (Element {:tag "div"
            :className (use-sx [card (when selected?
                                       {:bgcolor :palette.primary.main})])}
    children))
```

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

### The sx dialect

`use-sx` takes a map of CSS declarations and returns a class name. There is
deliberately **no `:sx` prop on `Element`** — `:sx` already means "pass this
through to MUI" (see Troubleshooting), so styling is an explicit hook call.

Value types partition with no heuristics:

| You write | You get |
| --- | --- |
| dotted keyword `:palette.primary.main` | theme token → `var(--cx-palette-primary-main)` |
| dotless keyword `:flex`, `:absolute` | literal CSS identifier, kebab-cased |
| string `"1px solid red"` | literal value, verbatim (except `:content`, which is quoted unless it is already a complete CSS string, a CSS-wide keyword or a function call) |
| number | theme scale or `px`, decided by the property |
| map under a `&`/`@` key | nested selector / at-rule |
| map under a property key | responsive breakpoints |

Numbers mean different things per property class: `:p 2` multiplies the theme
spacing unit, `:border-radius 1` the shape scale, `:box-shadow 2` indexes the
elevation list, unitless properties (`:font-weight`, `:opacity`, `:z-index`,
`:line-height`) stay raw, and everything else gets `px`. `0` is bare `0` except
in two places: `:box-shadow`, where it is elevation 0 (`none`) because a bare
`box-shadow: 0` is invalid CSS; and the `<time>` longhands
(`animation-duration`, `animation-delay`, `transition-duration`,
`transition-delay`), where **any** bare number including `0` throws, because
CSS has no unitless time — write `"0s"` or `"200ms"`. Strings bypass the scale
entirely (`:p "1.5rem"`).

Values are a **trust boundary**. A value that could escape its own declaration
— `;`, `{`, `}`, `<`, a CSS comment, an unbalanced quote, parenthesis or
bracket, or a trailing backslash — is rejected with
`ex-info`, not escaped: a `;` payload still parses as one valid rule, so
`insertRule` would accept it in production. Delimiters are checked by a
left-to-right depth scan rather than by counting, because counting cannot see
order — `")("` has one of each and still closes a construct it never opened.
The same goes for selector keys (no top-level `,`, and every `(`/`[` closed),
at-rule keys (`@media`, `@supports`, `@container`,
`@layer` only) and property names. Theme values are checked the same way, more
strictly — the `:root` block is written as text with nothing downstream to
re-parse it.

A value must be nil, a keyword, a number or a string. `{:width [1 2]}` and
`{:display true}` throw rather than emitting `width:[1 2]` — which a parser
discards silently.

Keys accept camelCase, kebab-case or strings — `:backgroundColor`,
`:background-color` and `"background-color"` are the same property. Nested
selectors need an explicit `&`; **string keys are the primary spelling**
(`"&:hover"`, `"& .child"`) since keywords with interior colons lean on
reader behaviour that is tolerated rather than specified. A bare selector key
like `:.child` is rejected with `ex-info` `::invalid-nested-key` rather than
implicitly prefixed — that rejection is what keeps responsive maps unambiguous.

Responsive values use the object form only: `{:width {:xs "100%" :md 300}}`.
`:xs` is the base rule; the rest emit mobile-first `min-width` blocks.
Breakpoints are the one part of the theme baked into rule text, because media
query parameters cannot reference custom properties — which is why breakpoints,
and only breakpoints, form part of the style cache key.

Vectors compose: `(use-sx [card (when active? active) {:mt 2}])` drops nils and
**deep-merges right-wins into one class**. It does not concatenate class names —
every generated rule is anchored on a single generated class, so two sx classes
on one element resolve by stylesheet insertion order (registration order, not
authoring order) and `[a b]` would render identically to `[b a]`.

Function-valued sx (`(fn [theme] …)`) is deliberately unsupported: it would make
generated CSS depend on arbitrary theme values and collapse the cache to
per-theme. Theme tokens are the supported escape hatch.

The style registry is **append-only** — a class is never removed, because
components that did not re-render still reference it. Content-hash dedupe bounds
growth to *distinct* inputs, so toggling among a fixed set of styles adds
nothing; a *continuously varying* value is the hazard. Put the varying part in a
custom property instead of in the sx map:

```clojure
(Element {:tag "div"
          :className (use-sx {:width "var(--w)"})
          :style {"--w" (str w "px")}})   ;; one class, not one per pixel
```

The library dev-warns once past a few thousand interned classes.

### Theming, and why a theme swap regenerates no style CSS

Theme values reach CSS only as custom properties, never inlined. Swapping the
root theme rewrites the `:root { --cx-…: … }` block: not one style rule is
regenerated and every component keeps the class it already had.

The one thing a swap *can* add is a **nested** provider's scope rule, because
those variables are baked into a class rather than referenced — see
[`ThemeProvider`](#themeprovider) below. Those are var blocks, not restyles;
the style rules and the compile count still do not move.
Components that read the theme *do* re-render — `use-sx` calls `use-theme`, so
every styled component is a context subscriber by construction and context
propagation bypasses memo bailouts — but that render is a memo hit returning the
same class and mutating no DOM.

`ThemeProvider` at the root renders **no DOM node**. Nested, it registers a
scoped `.cx-theme-…` rule and wraps children in a `display: contents` element.
That rule redefines custom properties **only**: the wrapper generates no box and
cannot paint a background (its inline `display: contents` also beats the
documented `:className`), so a dark dialog sets its own surface on an element
*inside* the scope —
`(use-sx {:bgcolor :palette.background.default :color :palette.text.primary})`.
Note `display: contents` breaks flex/grid item relationships and
percentage-height chains, and can strip a semantic tag's structure from the
accessibility tree; `:as :none` is the escape hatch, and then placing
`(use-theme-class)` yourself is mandatory — without it the scoped theme has no
effect at all.

`:palette.mode` only drives `color-scheme` (see `BaselineProvider`). It carries
no colours, and the default theme has no dark variant, so `{:palette {:mode
:dark}}` on its own leaves dark text on a now-dark UA canvas; ship
`:palette.background` and `:palette.text` alongside it. The library dev-warns
when it sees that combination.

Mount **exactly one root provider**. Two would share the single `:root` node and
overwrite each other; the library dev-warns when it happens, and `:scoped? true`
forces any extra provider onto the scoped path. A nested provider does register
one scoped rule per distinct theme it inherits, since its variables are baked
into a class rather than referenced — that rule carries the *entire* merged
theme (~2 KB) and, like every generated rule, is permanent.

**With no provider mounted, `use-theme` returns `default-theme` — it does not
throw.** This is deliberately unlike `use-db`'s `::no-provider`: there is a
sensible default theme, and opting in to sx should not secretly mean
restructuring your root.

Theme maps use kebab-case keys. A camelCase override is normalized onto the same
slot, so `{:shape {:borderRadius 8}}` and `{:shape {:border-radius 8}}` are
interchangeable and cannot produce two colliding custom properties.

### `BaselineProvider` is global CSS

`BaselineProvider` writes an element-level reset (`a`, `img`, `button`,
`input`…) into a `<style>` node that always precedes generated class rules. Its
selectors are global and the stylesheet is append-only with no unmount cleanup,
so **mounting it anywhere styles the whole document for the rest of the
session**. Mount it once at your app root, or not at all — there is no scoped
baseline. `:body?` (theme-driven `body` styling) and `:enable-color-scheme?` are
both opt-in.

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

### `StaticStyle` — returned by `defstyle`

`(defstyle card {...})` defines one `StaticStyle` per evaluation. Unlike
`StateAtom` / `RefAtom` / `Cursor`, which key equality on a shared underlying
object, the var itself *is* the identity: two `defstyle`s with identical maps
are not `=`.

```clojure
(defstyle card {:p 2})
@card                                    ;; {:p 2} — derefs to its sx map
(use-sx card)                            ;; "cx-1a2b3c"
```

That identity is the point. `use-sx`'s deps compare by `identical?` on the var
instead of walking the map every render, and repeated mounts skip the cache
probe entirely. The sx map is compiled once, lazily, on first use and memoized
per breakpoint key.

`defstyle` expands to `def`, not `defonce`, deliberately: editing a style and
saving re-evaluates it, yielding a fresh identity so components recompile and
re-render against the new class. `defonce` would make style edits invisible on
hot reload.

### `Keyframes` — returned by `keyframes`

```clojure
(def spin (sx/keyframes {:from {:transform "rotate(0deg)"}
                         :to   {:transform "rotate(360deg)"}}))

@spin                                    ;; the frames map — a deref is a read
(use-sx {:animation-name spin            ;; the OBJECT, not its name
         :animation-duration "900ms"
         :animation-timing-function :linear
         :animation-iteration-count :infinite})
```

Offsets are `:from`, `:to`, a number 0–100, or a percentage string; a vector key
shares one block between offsets (`{[0 100] {:opacity 1}}`). Each frame is an
ordinary sx map — shorthands, the spacing scale and theme tokens all work — but
declarations only. For a responsive animation, define two and switch
`:animation-name` in a breakpoint map.

Like a class, a `Keyframes` is content-addressed: identical frames share one
`cx-kf-…` rule, and the name is derived on every compile rather than captured.
That is what keeps it correct across hot reload. `keyframes-name` exists for the
`animation` shorthand and interop, but the string it returns is a snapshot —
store it and it outlives the rule it names, leaving an element that renders
perfectly and never animates. Prefer the `animation-*` longhands anyway: sx
composition deep-merges per property, so the shorthand resets every sub-property
a later part meant to keep.

Two footguns the compiler now refuses rather than emitting: `<time>` properties
(`animation-duration`, `transition-delay`, …) reject bare numbers, since CSS has
no unitless time even for zero — write `"200ms"`; and empty frames are rejected
at definition, because `@keyframes x{}` is valid CSS that no downstream check
would catch.

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

Note `:sx` there is **MUI's** prop, passed straight through to a MUI component;
it is unrelated to this library's styling layer. cljs.react's own styling is the
`use-sx` hook, which returns a class name you put on `:className`.

### My sx styles lose to (or beat) my own CSS

The sx stylesheet is appended to `<head>`, and every generated rule is anchored
on a single generated class (a nested selector splices around it, so
`{"&:hover" {…}}` is (0,2,0), but the anchor is still one class). So sx wins
over most author CSS by virtue of coming later — usually what you want, but it
is documented behaviour rather than an accident, and it cuts both ways: a
stylesheet injected at runtime *after* sx (a CDN script, say) wins over sx at
equal specificity. Two sx classes on one element resolve by stylesheet insertion
order — registration order, not authoring order — so don't put a utility class
and a `use-sx` class on the same element and expect a predictable winner.

If author CSS must win, raise its specificity; sx deliberately ships no
specificity knob.

One exception to "every rule is anchored on a generated class": `keyframes`
registers a `@keyframes cx-kf-…` rule, whose name is global by CSS design. Its
*reach* is unchanged — a keyframes rule paints nothing on its own, and a hashed
name is exactly as global as the hashed class names already are — but a class is
no longer self-contained. `.cx-abc{animation-name:cx-kf-def}` references CSS
outside its own rules, so reproducing an element's styling now takes the class
*and* its keyframes.

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

Uses [devenv](https://devenv.sh) (Node 22, pnpm 10, Clojure, Babashka, JDK 25)
and `bb` tasks. With direnv installed, `direnv allow` loads the environment on
`cd`; otherwise prefix each command with `devenv shell --`:

```bash
bb dev    # watch + compile demo → http://localhost:9011
bb test   # compile and run test suite
bb bench  # run performance benchmarks
```

Test suite uses `cljs.test` + `@testing-library/react` + `global-jsdom`; the
`:test` build runs under Node.

`defstyle` lives in `src/cljs/react/sx.clj`, a macro namespace (`sx/sheet.cljs`
is an ordinary runtime namespace). Editing a macro does not re-expand
already-compiled call sites — touch the calling file or restart the watch. No
code implication; it just shouldn't get debugged twice.

The `:release-demo` shadow-cljs build target compiles the demo under
`:optimizations :advanced` as a smoke test for externs / dead-code issues:

```bash
pnpm exec shadow-cljs release release-demo
```

## License

MIT — see [LICENSE](LICENSE).
