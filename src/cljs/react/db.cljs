(ns cljs.react.db
  (:require
   ["react" :as react]
   [cljs.react.hook :as hook]))

(deftype Cursor [atom path]
  IDeref
  (-deref [_] (get-in @atom path))

  IReset
  (-reset! [_ v]
    (swap! atom assoc-in path v)
    v)

  ISwap
  (-swap! [_ f]
    (get-in (swap! atom update-in path f) path))
  (-swap! [_ f a]
    (get-in (swap! atom update-in path f a) path))
  (-swap! [_ f a b]
    (get-in (swap! atom update-in path f a b) path))
  (-swap! [_ f a b xs]
    (get-in (swap! atom #(apply update-in % path f a b xs)) path)))

(def ^:private db-context (react/createContext nil))

(defn- db-provider-inner
  [^js props]
  (let [initial-value (.-initialValue props)
        children (.-children props)
        db-ref (hook/use-ref nil)]
    (when (nil? @db-ref)
      (reset! db-ref (atom initial-value)))
    (react/createElement
      (.-Provider db-context)
      #js {:value @db-ref}
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
  (hook/use-state (use-db-atom)))

(defn use-cursor
  "Subscribe to a path in the db. Returns a cursor that can be deref'd and updated.
  Only re-renders when the value at path changes."
  [path]
  (let [atom (use-db-atom)
        subscribe (hook/use-callback
                    (fn [callback]
                      (let [key (gensym "cursor")]
                        (add-watch atom key
                          (fn [_ _ old new]
                            (when (not= (get-in old path) (get-in new path))
                              (callback))))
                        #(remove-watch atom key)))
                    [path])
        get-snapshot (hook/use-callback
                       (fn [] (get-in @atom path))
                       [path])]
    (hook/use-sync-external-store subscribe get-snapshot)
    (Cursor. atom path)))
