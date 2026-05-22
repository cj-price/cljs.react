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
  error_boundary.cljs   # ErrorBoundary (class component via Reflect.construct)

dev/cljs/react/demo/    # Demo app
  basics.cljs / state.cljs / effects.cljs / advanced.cljs
  db.cljs / forms.cljs / mui.cljs / util.cljs

test/cljs/react/        # Tests (cljs.test + React Testing Library)
  component_test.cljs / core_test.cljs / db_test.cljs / dom_test.cljs
  form_test.cljs / hook_test.cljs / property_test.cljs

public/index.html       # Demo entry point (port 9011)
shadow-cljs.edn         # Build targets: :demo :test :benchmark :release-demo
bb.edn                  # Babashka task runner
shell.nix               # Nix dev environment (Node 22, Clojure, bb, JDK 17)
```

## Nix Shell Rule

Always run commands inside `nix-shell --run '...'` — enforced by global CLAUDE.md.

## Common Commands

```bash
nix-shell --run 'bb dev'             # Start dev server → http://localhost:9011
nix-shell --run 'bb test'            # Run tests
nix-shell --run 'bb lint'            # Lint with clj-kondo
nix-shell --run 'bb bench'           # Run benchmarks
nix-shell --run 'bb bench-baseline'  # Save baseline
```

## Key APIs

```clojure
;; Element DSL
(Element {:tag "div" :className "foo"} child)

;; Components
(defnc MyComp [{:keys [name]}]
  (Element {:tag "p"} name))

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

;; Hooks
use-effect   use-memo   use-callback   use-ref   use-atom
```

## Architecture Notes

- `use-state` returns a StateAtom; deref / reset! / swap! work natively
- `use-db` returns a Cursor for reads/writes into global state (root 0-arity or path 1-arity)
- `defnc` compiles to a React function component; use `memo-component` for memoization
- Forms use per-field subscriptions — only affected fields re-render on change
- `*create-element*` dynamic var allows custom renderer injection

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
