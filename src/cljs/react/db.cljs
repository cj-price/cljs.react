(ns cljs.react.db
  "Global state via a single Clojure atom held in React context. `DBProvider`
  installs the atom; `use-db`/`use-db-atom`/`use-cursor` subscribe to all or
  part of it with per-path fan-out.

  Re-exported from `cljs.react.core`; prefer that namespace in consumer code."
  (:require
   ["react" :as react]
   [cljs.react.hook :as hook]))

(deftype ^:no-doc Cursor [atom path]
  IDeref
  (-deref [_] (get-in @atom path))

  IReset
  (-reset! [_ v]
    (swap! atom assoc-in path v)
    v)

  ISwap
  (-swap! [cursor f]
    (let [new-val (volatile! nil)]
      (swap! (.-atom cursor)
             (fn [s]
               (let [nv (f (get-in s path))]
                 (vreset! new-val nv)
                 (assoc-in s path nv))))
      @new-val))
  (-swap! [cursor f a]
    (let [new-val (volatile! nil)]
      (swap! (.-atom cursor)
             (fn [s]
               (let [nv (f (get-in s path) a)]
                 (vreset! new-val nv)
                 (assoc-in s path nv))))
      @new-val))
  (-swap! [cursor f a b]
    (let [new-val (volatile! nil)]
      (swap! (.-atom cursor)
             (fn [s]
               (let [nv (f (get-in s path) a b)]
                 (vreset! new-val nv)
                 (assoc-in s path nv))))
      @new-val))
  (-swap! [cursor f a b xs]
    (let [new-val (volatile! nil)]
      (swap! (.-atom cursor)
             (fn [s]
               (let [nv (apply f (get-in s path) a b xs)]
                 (vreset! new-val nv)
                 (assoc-in s path nv))))
      @new-val))

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

(def ^:private db-context (react/createContext nil))

(defn- db-provider-inner
  [^js props]
  (let [initial-value (.-initialValue props)
        children (.-children props)
        db-ref (hook/use-ref nil)
        _ (when (nil? @db-ref)
            (reset! db-ref (atom initial-value)))
        db @db-ref
        ;; Memoize the JS value object so context consumers don't re-run on
        ;; every render of the parent tree — db itself is stable.
        js-value (hook/use-memo (fn [] #js {:value db}) [db])]
    (react/createElement
      (.-Provider db-context)
      js-value
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
  "Returns the raw db atom from context."
  []
  (hook/use-context db-context))

(defn use-db
  "Subscribe to the entire db. Returns the current value."
  []
  (hook/use-atom (use-db-atom)))

(defn use-cursor
  "Subscribe to a path in the db. Returns a cursor that can be deref'd and updated.
  Only re-renders when the value at path changes."
  [path]
  (let [atom (use-db-atom)]
    (hook/use-selector atom
                       (fn [o n] (not= (get-in o path) (get-in n path)))
                       (fn [s] (get-in s path))
                       [path])
    (hook/use-memo (fn [] (Cursor. atom path)) [path])))
