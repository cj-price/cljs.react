# CLAUDE.md — Project Context

> This file is the authoritative source of project context for Claude. Keep it
> up to date whenever the project structure, APIs, or workflows change.

## Project Overview

ClojureScript library providing idiomatic Clojure bindings for React 19.
Includes hooks, form management, global state (Cursor pattern), and a demo app.

## Directory Layout

```
src/cljs/react/         # Library source
  core.cljs / core.clj  # Public re-exports + Element DSL; defnc macro in core.clj
  hook.cljs             # Hooks with atom integration (StateAtom, RefAtom, ...)
  component.cljs        # clj->js-props, memo-component, forward-ref
  form.cljs             # Form state, per-field subscriptions, validation
  db.cljs               # Global state via Cursor + Context
  dom.cljs              # create-root, render, hydrate-root, unmount, create-portal
  lazy.cljs             # use-lazy-loadable — shadow.lazy code-split modules → React Suspense
  error_boundary.cljs   # ErrorBoundary (class component via Reflect.construct)
  sx.cljs / sx.clj      # PUBLIC styling API (opt-in); defstyle macro in sx.clj
  sx/theme.cljs         # default-theme, deep-merge-theme, theme->css-vars, bp-key
  sx/compile.cljs       # sx->rules + CSS emission — no deps, no DOM
  sx/sheet.cljs         # registry, hashing, <style> nodes — ONLY ns touching the DOM

dev/cljs/react/demo/    # Demo app
  basics.cljs / state.cljs / effects.cljs / advanced.cljs
  db.cljs / forms.cljs / mui.cljs / util.cljs

test/cljs/react/        # Tests (cljs.test + React Testing Library)
  component_test.cljs / core_test.cljs / db_test.cljs / dom_test.cljs
  form_test.cljs / hook_test.cljs / lazy_test.cljs / property_test.cljs

public/index.html       # Demo entry point (port 9011)
shadow-cljs.edn         # Build targets: :demo :test :benchmark :release-demo
bb.edn                  # Babashka task runner
devenv.nix              # Dev environment (Node 22, pnpm 10, Clojure, bb, JDK 25)
devenv.yaml             # nixpkgs input (devenv-nixpkgs/rolling)
devenv.lock             # Pinned inputs — committed
```

## Devenv Rule

The environment is devenv, loaded by direnv via `.envrc` (`use devenv`). Run
every command through `project-exec <project root> <command>` — enforced by
global CLAUDE.md. Devenv tools are not on a raw PATH.

## Common Commands

```bash
project-exec . bb dev             # Start dev server → http://localhost:9011
project-exec . bb test            # Run tests
project-exec . bb lint            # Lint with clj-kondo
project-exec . bb bench           # Run benchmarks
project-exec . bb bench-baseline  # Save baseline
```

## Key APIs

```clojure
;; Element DSL
(Element {:tag "div" :className "foo"} child)

;; Components
(defnc MyComp [{:keys [name]}]
  (Element {:tag "p"} name))

;; JS interop — wrap a raw JS component once, call like a defnc component
(def Button (adapt MuiButton))
(Button {:variant "contained"} "Save")

;; Local state — returns StateAtom (deref / reset! / swap!)
(let [s (use-state 0)]
  @s          ; read
  (reset! s 1)
  (swap! s inc))

;; Global state
(use-db)                ; root Cursor — @ for whole db; reset!/swap! replace root
(use-db [:user :name])  ; Cursor scoped to a path

;; Forms
(use-form {:values {...} :validate f :on-submit f :validate-on :blur})  ; FormHandle

;; Lazy modules — code-split chunk loaded on demand, rendered under Suspense
(def panel (shadow.lazy/loadable my.app.panel/Panel))  ; loadable = split point
(let [Panel (use-lazy-loadable panel)]                           ; stable callable component
  (Element {:tag Suspense :fallback (Element {:tag "div"} "Loading…")}
    (Panel {:label "hi"})))

;; Hooks
use-effect   use-memo   use-callback   use-ref   use-atom   use-lazy-loadable

;; Styling — opt-in, requires cljs.react.sx (NOT re-exported from core)
(use-sx {:p 2 :color :palette.primary.main :&:hover {:box-shadow 2}})  ; → class
(defstyle card {:p 2})   ; stable identity; (use-sx card)
(use-sx [card (when active? {:bgcolor :palette.primary.main})])        ; deep-merged
(ThemeProvider {:theme {...}} children)   ; root: no DOM; nested: scoped class

;; Animations — content-addressed, pass the OBJECT under :animation-name
(def spin (keyframes {:from {:transform "rotate(0deg)"}
                      :to   {:transform "rotate(360deg)"}}))
(use-sx {:animation-name spin :animation-duration "900ms"   ; <time> needs a unit
         :animation-iteration-count :infinite})
```

## Architecture Notes

- `use-state` returns a StateAtom; deref / reset! / swap! work natively
- `use-db` returns a Cursor for reads/writes into global state (root 0-arity or path 1-arity)
- `defnc` compiles to a React function component; use `memo-component` for memoization
- Forms use per-field subscriptions — only affected fields re-render on change
- `*create-element*` dynamic var allows custom renderer injection
- `use-lazy-loadable` (in `lazy.cljs`) bridges a `shadow.lazy/loadable` (or a 0-arg
  `() => Promise` loader) to `React.lazy` + Suspense; needs `:module-loader true`
  + a `:modules` split entry in the build. Only namespace touching `shadow.loader`.
- `cljs.react.sx` is opt-in and zero-dependency. All DOM/CSSOM interaction is
  contained in `sx/sheet.cljs`, mirroring the containment `lazy.cljs` keeps over
  `shadow.loader`. Theme values reach CSS only as `var(--cx-*)`, so the style
  cache keys on breakpoints (which must be baked into media queries) rather than
  on the theme — a theme swap rewrites one `:root` block and regenerates nothing.
  Class rules are append-only and content-hashed; the `:root` theme block is a
  separate node REPLACED via `useInsertionEffect`, because append-only dedupe
  makes an A→B→A toggle stick on B.
- `keyframes` is the one thing that escapes "every rule is anchored on one
  generated class" — a `@keyframes` name is global by CSS design. It is
  content-addressed like a class, and the object (not its name) travels in the
  sx map, resolved on the compile-miss path in `sheet.cljs`. A name captured
  into a map is a snapshot of registry state, and the failure is silent.
  Precisely: after `reset-sheet!` the rule is gone while the captured name
  survives, so the element renders and never animates — the `generation` stamp
  repairs that for an object and cannot for a copy. Across HOT RELOAD the old
  rule survives instead (the registry is append-only and `generation` is only
  bumped by `reset-sheet!`, which is test-only), so a stale name animates the
  OLD frames forever. The object fixes the first case and the common shape of
  the second; it does not fix a `Keyframes` captured into a `defstyle` in a
  namespace shadow did not reload, which keeps the old object and old class.

## Naming Conventions

- **CamelCase** — React element/component constructors (`Element`, `Fragment`,
  `Suspense`, `DBProvider`, user-defined `defnc` components).
- **kebab-case** — regular functions, hooks, and utilities (`use-state`,
  `use-db`, `on-submit`, `clj->js-props`, `forward-ref`).

When exposing new symbols: if the symbol returns a React element or is intended
to be invoked in element-creation position, use CamelCase. Otherwise
kebab-case.

## Testing

- Tests live in `test/cljs/react/`
- Uses `cljs.test` + `@testing-library/react` + `global-jsdom`
- Run with `bb test`; shadow-cljs compiles the `:test` build then runs with Node

## Benchmarks

Always run `project-exec . bb bench` after touching any of:

- `src/cljs/react/component.cljs` (props conversion, element creation, memo)
- `src/cljs/react/db.cljs` (Cursor, DB context)
- `src/cljs/react/hook.cljs` (StateAtom, RefAtom, use-selector cache)
- `src/cljs/react/sx/sheet.cljs` and `src/cljs/react/sx.cljs` (the registry probe
  and the `use-sx` deps compare run per render)
- anything else in the React-render hot path

The saved baseline at `benchmark/baselines.edn` is environmentally fragile —
absolute numbers drift between machines and across system load. To check for
real regression, bench `HEAD` vs `HEAD~1` (or whichever ref predates the
change) on the **same machine in the same run**, and compare medians across
3+ runs. Only treat a delta as a regression if it exceeds the per-bench CV
and is reproducible.
