(ns cljs.react.db
  "Global state via a single Clojure atom held in React context. `DBProvider`
  installs the atom; `use-db`/`use-db-atom`/`use-cursor` subscribe to all or
  part of it with per-path fan-out.

  Re-exported from `cljs.react.core`; prefer that namespace in consumer code."
  (:require
   ["react" :as react]
   [cljs.react.hook :as hook]))

(declare ^:no-doc Cursor)

(defn- swap-cursor! [^Cursor cursor update-fn]
  (let [new-val (volatile! nil)
        path (.-path cursor)]
    (swap! (.-atom cursor)
           (fn [s]
             (let [nv (update-fn (get-in s path))]
               (vreset! new-val nv)
               (assoc-in s path nv))))
    @new-val))

(deftype ^:no-doc Cursor [atom path]
  IDeref
  (-deref [_] (get-in @atom path))

  IReset
  (-reset! [_ v]
    (swap! atom assoc-in path v)
    v)

  ISwap
  (-swap! [cursor f]         (swap-cursor! cursor f))
  (-swap! [cursor f a]       (swap-cursor! cursor #(f % a)))
  (-swap! [cursor f a b]     (swap-cursor! cursor #(f % a b)))
  (-swap! [cursor f a b xs]  (swap-cursor! cursor #(apply f % a b xs)))

  IWatchable
  (-notify-watches [_ _ _]
    (throw (ex-info
             "Cursor does not support -notify-watches directly; use swap!/reset! which propagate through the underlying atom's watches."
             {})))
  (-add-watch [cursor k f]
    (add-watch (.-atom cursor) [::cursor k cursor]
      (fn [_ _ old-state new-state]
        (let [old-val (get-in old-state (.-path cursor))
              new-val (get-in new-state (.-path cursor))]
          (when (not= old-val new-val)
            (f [::cursor k cursor] cursor old-val new-val))))))
  (-remove-watch [cursor k]
    (remove-watch (.-atom cursor) [::cursor k cursor])))

;; defonce so hot-reload preserves context identity — otherwise existing
;; <Provider> instances and their consumers would orphan on every reload.
(defonce ^:private db-context (react/createContext nil))

(defn- db-provider-inner
  [^js props]
  (let [initial-value (.-initialValue props)
        children (.-children props)
        db-ref (hook/use-ref nil)
        _ (when (nil? @db-ref)
            (reset! db-ref (atom initial-value)))
        db @db-ref]
    (react/createElement
      (.-Provider db-context)
      #js {:value db}
      children)))

(defn DBProvider
  "Provide a database context for child components.
  Usage: (DBProvider {:initial-value {...}} child1 child2 ...)"
  [{:keys [initial-value]} & children]
  (apply react/createElement
         db-provider-inner
         #js {:initialValue initial-value}
         children))

(defn use-db-atom
  "Returns the raw db atom from context. Throws if called outside a `DBProvider`."
  []
  (or (hook/use-context db-context)
      (throw (ex-info
               "use-db / use-db-atom / use-cursor called outside a DBProvider — wrap your tree in (DBProvider {:initial-value ...} ...)"
               {}))))

(defn use-db
  "Subscribe to the entire db. Returns the current value."
  []
  (hook/use-atom (use-db-atom)))

(defn use-cursor
  "Subscribe to a path in the db. Returns a cursor that can be deref'd and updated.
  Only re-renders when the value at path changes."
  [path]
  (let [atom (use-db-atom)]
    ;; use-selector is called for its subscription side-effect: it wires the
    ;; component up to re-render when the value at `path` changes. We discard
    ;; the returned snapshot — callers read through the Cursor instead, which
    ;; gives them swap!/reset! and stays the same identity across renders.
    (hook/use-selector atom
                       (fn [o n] (not= (get-in o path) (get-in n path)))
                       (fn [s] (get-in s path))
                       [path])
    (hook/use-memo (fn [] (Cursor. atom path)) [path])))
