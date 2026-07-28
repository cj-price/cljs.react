(ns cljs.react.sx)

(defmacro defstyle
  "Define a stable style identity.

  Usage:
    (defstyle card {:p 2 :border-radius :shape.border-radius
                    :&:hover {:box-shadow 2}})

    (defnc Card [{:keys [children]}]
      (Element {:tag \"div\" :className (use-sx card)} children))

  The sx map is compiled once, on first use, and memoized per breakpoint key.
  Two payoffs over an inline literal:

    - `use-sx` deps compare by `identical?` on the var rather than walking the
      map structurally on every render.
    - repeated mounts (a thousand list rows) skip the cache probe entirely.

  Expands to `def`, not `defonce`, deliberately: editing the style and saving
  re-evaluates it, producing a fresh identity so components recompile and
  re-render against the new class. `defonce` would make style edits invisible
  on hot reload."
  ([name sx]
   `(def ~name (cljs.react.sx.sheet/static-style ~sx)))
  ([name doc sx]
   `(def ~name ~doc (cljs.react.sx.sheet/static-style ~sx))))
