(ns cljs.react.db-test
  (:require
   [cljs.test :refer [deftest testing is]]
   [cljs.react.db :as db]
   [cljs.react.hook :as hook]
   ["global-jsdom/register"]
   ["react" :as react]
   ["@testing-library/react" :refer [renderHook act]]))

(defn db-wrapper
  "Creates a wrapper component that provides DBProvider context"
  [initial-value]
  (fn [props]
    (db/DBProvider {:initial-value initial-value}
                   (.-children props))))

(deftest use-db-test
  (testing "use-db returns initial value"
    (let [result (renderHook #(db/use-db)
                             #js {:wrapper (db-wrapper {:count 0})})
          value (.. result -result -current)]
      (is (= {:count 0} value))))

  (testing "use-db returns nested data"
    (let [initial {:user {:name "Alice" :age 30}
                   :settings {:theme "dark"}}
          result (renderHook #(db/use-db)
                             #js {:wrapper (db-wrapper initial)})
          value (.. result -result -current)]
      (is (= initial value)))))

(deftest use-cursor-test
  (testing "use-cursor returns value at path"
    (let [result (renderHook #(db/use-cursor [:user :name])
                             #js {:wrapper (db-wrapper {:user {:name "Alice"}})})
          cursor (.. result -result -current)]
      (is (= "Alice" @cursor))))

  (testing "use-cursor returns nil for missing path"
    (let [result (renderHook #(db/use-cursor [:missing :path])
                             #js {:wrapper (db-wrapper {:user {:name "Alice"}})})
          cursor (.. result -result -current)]
      (is (nil? @cursor))))

  (testing "reset! updates value at path"
    (let [result (renderHook #(db/use-cursor [:count])
                             #js {:wrapper (db-wrapper {:count 0})})
          cursor (.. result -result -current)]
      (is (= 0 @cursor))
      (act #(reset! cursor 5))
      (let [new-cursor (.. result -result -current)]
        (is (= 5 @new-cursor)))))

  (testing "swap! with single fn"
    (let [result (renderHook #(db/use-cursor [:count])
                             #js {:wrapper (db-wrapper {:count 0})})
          cursor (.. result -result -current)]
      (act #(swap! cursor inc))
      (let [new-cursor (.. result -result -current)]
        (is (= 1 @new-cursor)))))

  (testing "swap! with fn and args"
    (let [result (renderHook #(db/use-cursor [:count])
                             #js {:wrapper (db-wrapper {:count 0})})
          cursor (.. result -result -current)]
      (act #(swap! cursor + 10))
      (let [new-cursor (.. result -result -current)]
        (is (= 10 @new-cursor)))))

  (testing "swap! with multiple args"
    (let [result (renderHook #(db/use-cursor [:data])
                             #js {:wrapper (db-wrapper {:data {:a 1}})})
          cursor (.. result -result -current)]
      (act #(swap! cursor assoc :b 2 :c 3))
      (let [new-cursor (.. result -result -current)]
        (is (= {:a 1 :b 2 :c 3} @new-cursor)))))

  (testing "nested cursor path"
    (let [result (renderHook #(db/use-cursor [:user :profile :email])
                             #js {:wrapper (db-wrapper {:user {:profile {:email "test@example.com"}}})})
          cursor (.. result -result -current)]
      (is (= "test@example.com" @cursor))
      (act #(reset! cursor "new@example.com"))
      (let [new-cursor (.. result -result -current)]
        (is (= "new@example.com" @new-cursor))))))

(deftest cursor-isolation-test
  (testing "updating one cursor doesn't affect unrelated paths"
    (let [wrapper (db-wrapper {:a 1 :b 2})
          result-a (renderHook #(db/use-cursor [:a]) #js {:wrapper wrapper})
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
