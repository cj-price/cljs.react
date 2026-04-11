# CLAUDE.md — Project Context

> This file is the authoritative source of project context for Claude. Keep it
> up to date whenever the project structure, APIs, or workflows change.

## Project Overview

ClojureScript library providing idiomatic Clojure bindings for React 19.
Includes hooks, form management, global state (Cursor pattern), and a demo app.

## Directory Layout

```
src/cljs/react/         # Library source
  core.cljs             # Element DSL, hooks, context, portals
  hook.cljs             # useState/useEffect/etc. with atom integration
  component.cljs        # defnc macro, memoization
  form.cljs             # Form state, per-field subscriptions, validation
  db.cljs               # Global state via Cursor + Context

dev/cljs/react/demo/    # Demo app (6 tabs)
  basics.cljs / state.cljs / effects.cljs
  advanced.cljs / db.cljs / forms.cljs / util.cljs

test/cljs/react/        # Tests (cljs.test + React Testing Library)
  form_test.cljs / hook_test.cljs / db_test.cljs

public/index.html       # Demo entry point (port 9011)
shadow-cljs.edn         # Build targets: :library :demo :test :benchmark
bb.edn                  # Babashka task runner
shell.nix               # Nix dev environment (Node 22, Clojure, bb, JDK 17)
```

## Nix Shell Rule

Always run commands inside `nix-shell --run '...'` — enforced by global CLAUDE.md.

## Common Commands

```bash
nix-shell --run 'bb dev'             # Start dev server → http://localhost:9011
nix-shell --run 'bb test'            # Run tests
nix-shell --run 'bb build'            # Build library → dist/cljs-react.js
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
(use-db)                    ; full db atom
(use-cursor [:user :name])  ; Cursor for nested path

;; Forms
(use-form {:fields {...} :on-submit f})  ; returns FormHandle

;; Hooks
use-effect   use-memo   use-callback   use-ref   use-atom
```

## Architecture Notes

- `use-state` returns a StateAtom; deref / reset! / swap! work natively
- `use-cursor` returns a Cursor for scoped reads/writes into global state
- `defnc` compiles to a React function component; use `memo-component` for memoization
- Forms use per-field subscriptions — only affected fields re-render on change
- `*create-element*` dynamic var allows custom renderer injection

## Testing

- Tests live in `test/cljs/react/`
- Uses `cljs.test` + `@testing-library/react` + `global-jsdom`
- Run with `bb test`; shadow-cljs compiles the `:test` build then runs with Node
