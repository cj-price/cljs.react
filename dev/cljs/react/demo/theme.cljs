(ns cljs.react.demo.theme
  "The demo site's two themes.

  Both maps must declare the SAME keys. `ThemeProvider` replaces the `:root`
  block wholesale from the merged theme, so a token defined only in `light`
  keeps its light value after a swap to `dark` — a stale colour on a dark
  surface, and nothing warns.

  Branches beyond the shipped theme (`:surface`, `:grey`, `:code`) are
  deliberate: `theme->css-vars` flattens any map, so `:palette.surface.sunken`
  is as much a token as `:palette.primary.main`, and every colour in the demo
  can live behind a var rather than in a class.")

(def ^:private shadows-light
  ["none"
   "0 1px 2px rgba(15, 23, 42, 0.06)"
   "0 2px 8px rgba(15, 23, 42, 0.08)"
   "0 8px 24px rgba(15, 23, 42, 0.10)"
   "0 20px 48px rgba(15, 23, 42, 0.14)"])

(def ^:private shadows-dark
  ["none"
   "0 1px 2px rgba(0, 0, 0, 0.5)"
   "0 2px 8px rgba(0, 0, 0, 0.55)"
   "0 8px 24px rgba(0, 0, 0, 0.6)"
   "0 20px 48px rgba(0, 0, 0, 0.7)"])

(def ^:private typography
  {:font-family "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif"
   :font-family-mono "'JetBrains Mono', 'Monaco', 'Menlo', 'Consolas', monospace"
   :line-height 1.6})

(def ^:private code
  "Prism token colours. Shared rather than duplicated, because `:surface.code`
  is dark in both themes, so these are theme-INVARIANT — and eight entries
  written out twice is the cheapest way to break the key-parity rule above.

  `:comment` and `:label` were #64748b, which measured 3.75:1 and 3.93:1 on the
  two code surfaces. Comments are where the samples explain themselves, so the
  lowest-contrast text carried the highest-value content; #8b98ab is 6.10:1 and
  6.40:1."
  {:plain "#e2e8f0" :comment "#8b98ab" :keyword "#c792ea"
   :string "#a5d6a7" :number "#ffcb6b" :function "#82aaff"
   :punctuation "#94a3b8" :label "#8b98ab"})

(def light
  {:palette
   {:mode      :light
    ;; #ea580c measured 3.56:1 against white at 14px/500 — under the 4.5:1 AA
    ;; minimum, on the site's most-used control (every primary button and the
    ;; selected tab). #c2410c is 5.18:1. Everything downstream resolves through
    ;; var(--cx-palette-primary-main), so this one token carries the fix.
    :primary   {:main "#c2410c" :light "#fb923c" :dark "#9a3412"
                :contrast-text "#ffffff"}
    :secondary {:main "#0d9488" :light "#2dd4bf" :dark "#0f766e"
                :contrast-text "#ffffff"}
    :error     {:main "#dc2626" :light "#fca5a5" :dark "#b91c1c"
                :contrast-text "#ffffff"}
    :warning   {:main "#b45309" :light "#fbbf24" :dark "#92400e"
                :contrast-text "#ffffff"}
    :success   {:main "#15803d" :light "#4ade80" :dark "#166534"
                :contrast-text "#ffffff"}
    :info      {:main "#0369a1" :light "#38bdf8" :dark "#075985"
                :contrast-text "#ffffff"}
    :text      {:primary "#0f172a" :secondary "#475569" :disabled "#5f6b7f"}
    :background {:default "#f1f5f9" :paper "#ffffff"}
    :divider   "#e2e8f0"

    ;; Surfaces. `code` is intentionally dark in BOTH themes — a code block
    ;; that inverts with the page makes the Prism palette unreadable in one of
    ;; them, and every editor screenshot people compare against is dark.
    :surface   {:sunken "#e2e8f0" :raised "#ffffff" :code "#0f172a"
                :overlay "rgba(255, 255, 255, 0.82)"
                :code-border "#1e293b"
                :tint "rgba(194, 65, 12, 0.08)"}

    :grey      {:50 "#f8fafc" :100 "#f1f5f9" :200 "#e2e8f0" :300 "#cbd5e1"
                :400 "#94a3b8" :500 "#64748b" :600 "#475569" :700 "#334155"
                :800 "#1e293b" :900 "#0f172a"}

    :code      code

    :focus     {:ring "rgba(194, 65, 12, 0.35)"}
    :dot       "#cbd5e1"}

   :typography typography
   :shape      {:border-radius 8}
   :shadows    shadows-light})

(def dark
  {:palette
   {:mode      :dark
    :primary   {:main "#fb923c" :light "#fdba74" :dark "#ea580c"
                :contrast-text "#1c1917"}
    :secondary {:main "#2dd4bf" :light "#5eead4" :dark "#14b8a6"
                :contrast-text "#042f2e"}
    :error     {:main "#f87171" :light "#fca5a5" :dark "#ef4444"
                :contrast-text "#450a0a"}
    :warning   {:main "#fbbf24" :light "#fcd34d" :dark "#f59e0b"
                :contrast-text "#451a03"}
    :success   {:main "#4ade80" :light "#86efac" :dark "#22c55e"
                :contrast-text "#052e16"}
    :info      {:main "#38bdf8" :light "#7dd3fc" :dark "#0ea5e9"
                :contrast-text "#082f49"}
    :text      {:primary "#e2e8f0" :secondary "#94a3b8" :disabled "#8593a6"}
    :background {:default "#0b1220" :paper "#111a2b"}
    :divider   "#1e293b"

    :surface   {:sunken "#0f172a" :raised "#172136" :code "#0b1220"
                :overlay "rgba(17, 26, 43, 0.82)"
                :code-border "#1e293b"
                :tint "rgba(251, 146, 60, 0.12)"}

    ;; The scale inverts: `grey.50` stays "the one furthest from the text
    ;; colour", so a component written against it reads correctly in both.
    :grey      {:50 "#0f172a" :100 "#172136" :200 "#1e293b" :300 "#334155"
                :400 "#475569" :500 "#64748b" :600 "#94a3b8" :700 "#cbd5e1"
                :800 "#e2e8f0" :900 "#f8fafc"}

    :code      code

    :focus     {:ring "rgba(251, 146, 60, 0.4)"}
    :dot       "#1e293b"}

   :typography typography
   :shape      {:border-radius 8}
   :shadows    shadows-dark})

(defn for-mode
  [dark?]
  (if dark? dark light))
