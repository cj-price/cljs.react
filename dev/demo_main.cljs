(ns demo-main
  "Dev/release entry point for the demo. Kept OUT of the `cljs.*` tree on
  purpose: shadow-cljs skips lifecycle-hook scanning for any resource whose
  name starts with `cljs/` (its ClojureScript-stdlib heuristic), so a
  `^:dev/after-load` hook placed in `cljs.react.demo` is never registered and
  hot reload never re-renders. This namespace resolves to `demo_main.cljs`,
  which is scanned, so the hook here fires."
  (:require [cljs.react.demo :as demo]
            [cljs.react.dom :as dom]))

(defonce root (atom nil))

(defn ^:dev/after-load reload []
  (when @root
    (dom/render @root (demo/App {}))))

(defn ^:export init
  []
  (set! (.. js/document -documentElement -style -overflowY) "scroll")
  (when-let [root-el (.getElementById js/document "app")]
    (reset! root (dom/create-root root-el))
    (dom/render @root (demo/App {}))))
