(ns cljs.react.db
  "Global state via a single Clojure atom held in React context. `DBProvider`
  installs the atom; `use-db`/`use-db-atom` subscribe to all or part of it
  with per-path fan-out.

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
             (let [nv (update-fn (if (seq path) (get-in s path) s))]
               (vreset! new-val nv)
               (if (seq path) (assoc-in s path nv) nv))))
    @new-val))

(deftype ^:no-doc Cursor [atom path]
  IDeref
  (-deref [_] (if (seq path) (get-in @atom path) @atom))

  IReset
  (-reset! [_ v]
    (if (seq path)
      (swap! atom assoc-in path v)
      (reset! atom v))
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
        (let [path    (.-path cursor)
              old-val (if (seq path) (get-in old-state path) old-state)
              new-val (if (seq path) (get-in new-state path) new-state)]
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
               "use-db / use-db-atom called outside a DBProvider — wrap your tree in (DBProvider {:initial-value ...} ...)"
               {:type ::no-provider}))))

(defn use-db
  "Subscribe to the db. Returns a Cursor (deref / reset! / swap!) that re-renders
  only when the value at `path` changes.

  0-arity: root cursor — `@cursor` is the whole db; `reset!`/`swap!` replace
  the root value. 1-arity: cursor scoped to a vector path; an empty vector is
  equivalent to the 0-arity root cursor.

  Path must be a vector. Non-vector paths are rejected with ex-info
  `:type ::invalid-cursor-path`."
  ([] (use-db []))
  ([path]
   (when-not (vector? path)
     (throw (ex-info (str "use-db: path must be a vector (got "
                          (pr-str path) ")")
                     {:type ::invalid-cursor-path :got path})))
   (let [atom (use-db-atom)]
     ;; use-selector is called for its subscription side-effect: it wires the
     ;; component up to re-render when the value at `path` changes. We discard
     ;; the returned snapshot — callers read through the Cursor instead, which
     ;; gives them swap!/reset! and stays the same identity across renders.
     (hook/use-selector atom
                        (fn [o n]
                          (if (seq path)
                            (not= (get-in o path) (get-in n path))
                            (not= o n)))
                        (fn [s] (if (seq path) (get-in s path) s))
                        [path])
     (hook/use-memo (fn [] (Cursor. atom path)) [path]))))
