(ns cljs.react.hook-test
  (:require
   [cljs.test :refer [deftest testing is async use-fixtures]]
   [cljs.react.hook :as hook]
   ["global-jsdom/register"]
   ["react" :as react]
   ["@testing-library/react" :refer [renderHook act]]))

(deftest state-atom-test
  (testing "StateAtom returns derefable value"
    (let [result (renderHook #(hook/->StateAtom (react/useState 0)))
          state (.. result -result -current)]
      (is (= 0 @state))))

  (testing "reset! updates state"
    (let [result (renderHook #(hook/->StateAtom (react/useState 0)))
          state (.. result -result -current)]
      (act #(reset! state 5))
      (let [new-state (.. result -result -current)]
        (is (= 5 @new-state)))))

  (testing "swap! with single fn"
    (let [result (renderHook #(hook/->StateAtom (react/useState 0)))
          state (.. result -result -current)]
      (act #(swap! state inc))
      (let [new-state (.. result -result -current)]
        (is (= 1 @new-state)))))

  (testing "swap! with fn and args"
    (let [result (renderHook #(hook/->StateAtom (react/useState {:count 0})))
          state (.. result -result -current)]
      (act #(swap! state assoc :count 5))
      (let [new-state (.. result -result -current)]
        (is (= {:count 5} @new-state)))))

  (testing "swap! with multiple args"
    (let [result (renderHook #(hook/->StateAtom (react/useState {:a 1 :b 2})))
          state (.. result -result -current)]
      (act #(swap! state assoc :c 3 :d 4))
      (let [new-state (.. result -result -current)]
        (is (= {:a 1 :b 2 :c 3 :d 4} @new-state))))))

(deftest cljs-deps-test
  (testing "same deps keep counter stable"
    (let [deps (atom [{:a 1}])
          result (renderHook #(hook/cljs-deps @deps))]
      (is (= 0 (aget (.. result -result -current) 0)))
      (.rerender result)
      (is (= 0 (aget (.. result -result -current) 0)))))

  (testing "different deps increment counter"
    (let [deps (atom [{:a 1}])
          result (renderHook #(hook/cljs-deps @deps))]
      (is (= 0 (aget (.. result -result -current) 0)))
      (reset! deps [{:a 2}])
      (.rerender result)
      (is (= 1 (aget (.. result -result -current) 0)))))

  (testing "distinguishes values with hash collisions (0, false, nil)"
    (let [deps (atom [0])
          result (renderHook #(hook/cljs-deps @deps))]
      (is (= 0 (aget (.. result -result -current) 0)))
      (reset! deps [false])
      (.rerender result)
      (is (= 1 (aget (.. result -result -current) 0)))
      (reset! deps [nil])
      (.rerender result)
      (is (= 2 (aget (.. result -result -current) 0))))))

(deftest use-effect-test
  (testing "effect runs on mount"
    (let [ran (atom false)
          _ (renderHook #(hook/use-effect (fn [] (reset! ran true) js/undefined)))]
      (is (= true @ran))))

  (testing "effect with deps runs when deps change"
    (let [run-count (atom 0)
          deps (atom [1])
          result (renderHook
                  #(hook/use-effect (fn [] (swap! run-count inc) js/undefined) @deps)
                  #js {:initialProps nil})]
      (is (= 1 @run-count))
      ;; Re-render with same deps - effect should not run again
      (.rerender result)
      (is (= 1 @run-count))
      ;; Change deps and re-render
      (reset! deps [2])
      (.rerender result)
      (is (= 2 @run-count))))

  (testing "effect with cljs map deps"
    (let [run-count (atom 0)
          deps (atom {:key "value"})
          result (renderHook
                  #(hook/use-effect (fn [] (swap! run-count inc) js/undefined) [@deps])
                  #js {:initialProps nil})]
      (is (= 1 @run-count))
      ;; Same value, different reference - should not re-run
      (reset! deps {:key "value"})
      (.rerender result)
      (is (= 1 @run-count))
      ;; Different value - should re-run
      (reset! deps {:key "other"})
      (.rerender result)
      (is (= 2 @run-count)))))

(deftest use-layout-effect-test
  (testing "effect runs on mount"
    (let [ran (atom false)
          _ (renderHook #(hook/use-layout-effect (fn [] (reset! ran true) js/undefined)))]
      (is (= true @ran))))

  (testing "effect with deps runs when deps change"
    (let [run-count (atom 0)
          deps (atom [1])
          result (renderHook
                  #(hook/use-layout-effect (fn [] (swap! run-count inc) js/undefined) @deps)
                  #js {:initialProps nil})]
      (is (= 1 @run-count))
      (.rerender result)
      (is (= 1 @run-count))
      (reset! deps [2])
      (.rerender result)
      (is (= 2 @run-count)))))

(deftest use-callback-test
  (testing "returns a function"
    (let [result (renderHook #(hook/use-callback (fn [] :test) []))
          callback (.. result -result -current)]
      (is (fn? callback))
      (is (= :test (callback)))))

  (testing "same deps return same function reference"
    (let [deps (atom [1])
          result (renderHook #(hook/use-callback (fn [] :test) @deps))
          first-callback (.. result -result -current)]
      (.rerender result)
      (let [second-callback (.. result -result -current)]
        (is (identical? first-callback second-callback)))))

  (testing "different deps return new function reference"
    (let [deps (atom [1])
          result (renderHook #(hook/use-callback (fn [] :test) @deps))
          first-callback (.. result -result -current)]
      (reset! deps [2])
      (.rerender result)
      (let [second-callback (.. result -result -current)]
        (is (not (identical? first-callback second-callback)))))))

(deftest use-memo-test
  (testing "memoizes value"
    (let [compute-count (atom 0)
          result (renderHook #(hook/use-memo
                               (fn []
                                 (swap! compute-count inc)
                                 (* 2 21))
                               []))
          value (.. result -result -current)]
      (is (= 42 value))
      (is (= 1 @compute-count))
      ;; Re-render, should not recompute
      (.rerender result)
      (is (= 1 @compute-count))))

  (testing "recomputes when deps change"
    (let [compute-count (atom 0)
          deps (atom [1])
          result (renderHook #(hook/use-memo
                               (fn []
                                 (swap! compute-count inc)
                                 :computed)
                               @deps))]
      (is (= 1 @compute-count))
      ;; Same deps, no recompute
      (.rerender result)
      (is (= 1 @compute-count))
      ;; Different deps, recompute
      (reset! deps [2])
      (.rerender result)
      (is (= 2 @compute-count))))

  (testing "works with cljs data structures as deps"
    (let [compute-count (atom 0)
          deps (atom {:id 1})
          result (renderHook #(hook/use-memo
                               (fn []
                                 (swap! compute-count inc)
                                 :computed)
                               [@deps]))]
      (is (= 1 @compute-count))
      ;; Same value, different reference
      (reset! deps {:id 1})
      (.rerender result)
      (is (= 1 @compute-count))
      ;; Different value
      (reset! deps {:id 2})
      (.rerender result)
      (is (= 2 @compute-count)))))

(deftest use-ref-test
  (testing "use-ref returns RefAtom with nil default"
    (let [result (renderHook #(hook/use-ref))
          ref (.. result -result -current)]
      (is (nil? @ref))))

  (testing "use-ref returns RefAtom with initial value"
    (let [result (renderHook #(hook/use-ref 42))
          ref (.. result -result -current)]
      (is (= 42 @ref))))

  (testing "ref value persists across renders"
    (let [result (renderHook #(hook/use-ref {:data "test"}))
          ref (.. result -result -current)]
      (is (= {:data "test"} @ref))
      (.rerender result)
      (let [ref2 (.. result -result -current)]
        (is (identical? @ref @ref2)))))

  (testing "reset! updates ref value"
    (let [result (renderHook #(hook/use-ref 0))
          ref (.. result -result -current)]
      (reset! ref 100)
      (is (= 100 @ref))))

  (testing "swap! with single fn"
    (let [result (renderHook #(hook/use-ref 0))
          ref (.. result -result -current)]
      (swap! ref inc)
      (is (= 1 @ref))))

  (testing "swap! with fn and arg"
    (let [result (renderHook #(hook/use-ref 0))
          ref (.. result -result -current)]
      (swap! ref + 10)
      (is (= 10 @ref))))

  (testing "swap! with fn and two args"
    (let [result (renderHook #(hook/use-ref {:a 1}))
          ref (.. result -result -current)]
      (swap! ref assoc :b 2 :c 3)
      (is (= {:a 1 :b 2 :c 3} @ref))))

  (testing "swap! with fn and variadic args"
    (let [result (renderHook #(hook/use-ref {:a 1}))
          ref (.. result -result -current)]
      (swap! ref merge {:b 2} {:c 3} {:d 4})
      (is (= {:a 1 :b 2 :c 3 :d 4} @ref)))))

(def TestContext (react/createContext "default"))

(deftest use-context-test
  (testing "use-context returns default value"
    (let [result (renderHook #(hook/use-context TestContext))
          value (.. result -result -current)]
      (is (= "default" value))))

  (testing "use-context returns provided value"
    (let [wrapper (fn [props]
                    (react/createElement
                     (.-Provider TestContext)
                     #js {:value "provided"}
                     (.-children props)))
          result (renderHook #(hook/use-context TestContext)
                             #js {:wrapper wrapper})
          value (.. result -result -current)]
      (is (= "provided" value)))))