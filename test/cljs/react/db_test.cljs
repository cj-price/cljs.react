(ns cljs.react.db-test
  (:require
   [cljs.test :refer [deftest testing is]]
   [cljs.react.db :as db]
   ["global-jsdom/register"]
   ["@testing-library/react" :refer [renderHook act]]))

(defn db-wrapper
  "Creates a wrapper component that provides DBProvider context"
  [initial-value]
  (fn [props]
    (db/DBProvider {:initial-value initial-value}
                   (.-children props))))

(deftest use-db-root-test
  (testing "use-db 0-arity returns a root cursor whose @ is the whole db"
    (let [result (renderHook #(db/use-db)
                             #js {:wrapper (db-wrapper {:count 0})})
          cursor (.. result -result -current)]
      (is (= {:count 0} @cursor))))

  (testing "use-db 0-arity returns nested data"
    (let [initial {:user {:name "Alice" :age 30}
                   :settings {:theme "dark"}}
          result (renderHook #(db/use-db)
                             #js {:wrapper (db-wrapper initial)})
          cursor (.. result -result -current)]
      (is (= initial @cursor))))

  (testing "root cursor reset! replaces the whole db"
    (let [result (renderHook #(db/use-db)
                             #js {:wrapper (db-wrapper {:a 1})})
          cursor (.. result -result -current)]
      (act #(reset! cursor {:b 2}))
      (let [new-cursor (.. result -result -current)]
        (is (= {:b 2} @new-cursor)))))

  (testing "root cursor swap! updates the whole db"
    (let [result (renderHook #(db/use-db)
                             #js {:wrapper (db-wrapper {:n 1})})
          cursor (.. result -result -current)]
      (act #(swap! cursor update :n inc))
      (let [new-cursor (.. result -result -current)]
        (is (= {:n 2} @new-cursor))))))

(deftest use-db-path-test
  (testing "use-db returns cursor scoped to path"
    (let [result (renderHook #(db/use-db [:user :name])
                             #js {:wrapper (db-wrapper {:user {:name "Alice"}})})
          cursor (.. result -result -current)]
      (is (= "Alice" @cursor))))

  (testing "use-db returns nil for missing path"
    (let [result (renderHook #(db/use-db [:missing :path])
                             #js {:wrapper (db-wrapper {:user {:name "Alice"}})})
          cursor (.. result -result -current)]
      (is (nil? @cursor))))

  (testing "reset! updates value at path"
    (let [result (renderHook #(db/use-db [:count])
                             #js {:wrapper (db-wrapper {:count 0})})
          cursor (.. result -result -current)]
      (is (= 0 @cursor))
      (act #(reset! cursor 5))
      (let [new-cursor (.. result -result -current)]
        (is (= 5 @new-cursor)))))

  (testing "swap! with single fn"
    (let [result (renderHook #(db/use-db [:count])
                             #js {:wrapper (db-wrapper {:count 0})})
          cursor (.. result -result -current)]
      (act #(swap! cursor inc))
      (let [new-cursor (.. result -result -current)]
        (is (= 1 @new-cursor)))))

  (testing "swap! with fn and args"
    (let [result (renderHook #(db/use-db [:count])
                             #js {:wrapper (db-wrapper {:count 0})})
          cursor (.. result -result -current)]
      (act #(swap! cursor + 10))
      (let [new-cursor (.. result -result -current)]
        (is (= 10 @new-cursor)))))

  (testing "swap! with multiple args"
    (let [result (renderHook #(db/use-db [:data])
                             #js {:wrapper (db-wrapper {:data {:a 1}})})
          cursor (.. result -result -current)]
      (act #(swap! cursor assoc :b 2 :c 3))
      (let [new-cursor (.. result -result -current)]
        (is (= {:a 1 :b 2 :c 3} @new-cursor)))))

  (testing "nested cursor path"
    (let [result (renderHook #(db/use-db [:user :profile :email])
                             #js {:wrapper (db-wrapper {:user {:profile {:email "test@example.com"}}})})
          cursor (.. result -result -current)]
      (is (= "test@example.com" @cursor))
      (act #(reset! cursor "new@example.com"))
      (let [new-cursor (.. result -result -current)]
        (is (= "new@example.com" @new-cursor))))))

(deftest use-db-rejects-non-vector-path-test
  (testing "use-db throws when path is not a vector"
    (is (thrown-with-msg? js/Error #"path must be a vector"
          (renderHook #(db/use-db :not-a-vector)
                      #js {:wrapper (db-wrapper {})})))))

(deftest cursor-isolation-test
  (testing "updating one cursor doesn't affect unrelated paths"
    (let [wrapper (db-wrapper {:a 1 :b 2})
          result-a (renderHook #(db/use-db [:a]) #js {:wrapper wrapper})
          cursor-a (.. result-a -result -current)]
      (is (= 1 @cursor-a))
      (act #(reset! cursor-a 100))
      (let [new-cursor-a (.. result-a -result -current)]
        (is (= 100 @new-cursor-a))))))

(deftest use-db-atom-test
  (testing "use-db-atom returns the atom"
    (let [result (renderHook #(db/use-db-atom)
                             #js {:wrapper (db-wrapper {:test true})})
          atom (.. result -result -current)]
      (is (satisfies? IDeref atom))
      (is (= {:test true} @atom)))))

(deftest cursor-notify-watches-throws-test
  (testing "-notify-watches on a Cursor throws — callers should use swap!/reset!"
    (let [a (atom {:x 1})
          c (db/->Cursor a [:x])]
      (is (thrown-with-msg? js/Error #"does not support -notify-watches"
            (-notify-watches c nil nil))))))

(deftest use-db-atom-outside-provider-throws-test
  (testing "use-db-atom throws when called without a DBProvider in the tree"
    (is (thrown-with-msg? js/Error #"outside a DBProvider"
          (renderHook #(db/use-db-atom))))))

(deftest cursor-add-watch-fires-on-path-change-test
  (testing "Cursor add-watch fires when the value at path changes"
    (let [a (atom {:x 1})
          c (db/->Cursor a [:x])
          fired (atom [])]
      (add-watch c :k (fn [_ _ old new] (swap! fired conj [old new])))
      (reset! c 2)
      (is (= [[1 2]] @fired))
      (remove-watch c :k))))

(deftest cursor-add-watch-suppresses-unrelated-change-test
  (testing "Cursor add-watch does NOT fire when only an unrelated sibling changes"
    (let [a (atom {:x 1 :y 1})
          c (db/->Cursor a [:x])
          fired (atom 0)]
      (add-watch c :k (fn [& _] (swap! fired inc)))
      (swap! a assoc :y 99)
      (is (zero? @fired))
      (remove-watch c :k))))

(deftest cursor-remove-watch-unsubscribes-test
  (testing "After remove-watch, subsequent path changes do not fire the watch"
    (let [a (atom {:x 1})
          c (db/->Cursor a [:x])
          fired (atom 0)]
      (add-watch c :k (fn [& _] (swap! fired inc)))
      (reset! c 2)
      (is (= 1 @fired))
      (remove-watch c :k)
      (reset! c 3)
      (is (= 1 @fired) "watch should not have fired after remove-watch"))))
