(ns cljs.react.hook-test
  (:require
   [cljs.test :refer [deftest testing is]]
   [cljs.react.hook :as hook]
   [cljs.react.component :as component]
   ["global-jsdom/register"]
   ["react" :as react]
   ["@testing-library/react" :refer [renderHook act render]]))

(deftest state-atom-test
  (testing "StateAtom returns derefable value"
    (let [result (renderHook #(hook/use-state 0))
          state (.. result -result -current)]
      (is (= 0 @state))))

  (testing "reset! updates state; re-pulled wrapper sees the new value"
    (let [result (renderHook #(hook/use-state 0))
          state (.. result -result -current)]
      (act #(reset! state 5))
      (let [new-state (.. result -result -current)]
        (is (= 5 @new-state)))))

  (testing "swap! with single fn"
    (let [result (renderHook #(hook/use-state 0))
          state (.. result -result -current)]
      (act #(swap! state inc))
      (let [new-state (.. result -result -current)]
        (is (= 1 @new-state)))))

  (testing "swap! with fn and args"
    (let [result (renderHook #(hook/use-state {:count 0}))
          state (.. result -result -current)]
      (act #(swap! state assoc :count 5))
      (let [new-state (.. result -result -current)]
        (is (= {:count 5} @new-state)))))

  (testing "swap! with multiple args"
    (let [result (renderHook #(hook/use-state {:a 1 :b 2}))
          state (.. result -result -current)]
      (act #(swap! state assoc :c 3 :d 4))
      (let [new-state (.. result -result -current)]
        (is (= {:a 1 :b 2 :c 3 :d 4} @new-state)))))

  (testing "reset! returns the new value (matches clojure.core/reset! contract)"
    (let [result (renderHook #(hook/use-state 0))
          state  (.. result -result -current)
          ret    (atom nil)]
      (act #(reset! ret (reset! state 42)))
      (is (= 42 @ret))))

  (testing "StateAtom is = across renders (via IEquiv on setter identity)"
    (let [result (renderHook #(hook/use-state 0))
          first-state (.. result -result -current)]
      (.rerender result)
      (let [second-state (.. result -result -current)]
        ;; Wrapper is fresh each render (snapshot semantics), but they compare
        ;; equal under = because they share the same useState setter. This is
        ;; what makes a StateAtom safe to place into cljs-deps.
        (is (not (identical? first-state second-state)))
        (is (= first-state second-state))
        (is (= (hash first-state) (hash second-state))))))

  (testing "StateAtoms from different useState slots are not ="
    (let [result (renderHook #(let [a (hook/use-state 0)
                                    b (hook/use-state 0)]
                                #js [a b]))
          arr (.. result -result -current)
          a (aget arr 0)
          b (aget arr 1)]
      (is (not= a b)))))

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
  (testing "use-ref 0-arity wraps a RefAtom around a fresh React ref"
    (let [result (renderHook #(hook/use-ref))
          ref (.. result -result -current)]
      (is (instance? hook/RefAtom ref))
      (is (some? (hook/react-ref ref)))
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

  ;; Mutation tests: each verifies the NEW value survives a subsequent render
  ;; via the same ref handle. Without the rerender + re-pull, these would
  ;; pass even if use-ref returned a fresh ref every render.
  (testing "reset! updates ref value and persists across renders"
    (let [result (renderHook #(hook/use-ref 0))
          ref (.. result -result -current)]
      (reset! ref 100)
      (is (= 100 @ref))
      (.rerender result)
      (let [ref2 (.. result -result -current)]
        ;; RefAtom wrapper is fresh per render (snapshot semantics), but it
        ;; wraps the same underlying React ref so it compares = via IEquiv
        ;; and the mutation is visible through the new wrapper.
        (is (not (identical? ref ref2)))
        (is (= ref ref2))
        (is (identical? (hook/react-ref ref) (hook/react-ref ref2)))
        (is (= 100 @ref2)))))

  (testing "swap! with single fn persists across renders"
    (let [result (renderHook #(hook/use-ref 0))
          ref (.. result -result -current)]
      (swap! ref inc)
      (.rerender result)
      (is (= 1 @(.. result -result -current)))))

  (testing "swap! with fn and arg persists across renders"
    (let [result (renderHook #(hook/use-ref 0))
          ref (.. result -result -current)]
      (swap! ref + 10)
      (.rerender result)
      (is (= 10 @(.. result -result -current)))))

  (testing "swap! with fn and two args persists across renders"
    (let [result (renderHook #(hook/use-ref {:a 1}))
          ref (.. result -result -current)]
      (swap! ref assoc :b 2 :c 3)
      (.rerender result)
      (is (= {:a 1 :b 2 :c 3} @(.. result -result -current)))))

  (testing "swap! with fn and variadic args persists across renders"
    (let [result (renderHook #(hook/use-ref {:a 1}))
          ref (.. result -result -current)]
      (swap! ref merge {:b 2} {:c 3} {:d 4})
      (.rerender result)
      (is (= {:a 1 :b 2 :c 3 :d 4} @(.. result -result -current)))))

  (testing "RefAtom is = across renders (via IEquiv on react-ref identity)"
    (let [result (renderHook #(hook/use-ref 0))
          first-ref (.. result -result -current)]
      (.rerender result)
      (let [second-ref (.. result -result -current)]
        (is (not (identical? first-ref second-ref)))
        (is (= first-ref second-ref))
        (is (= (hash first-ref) (hash second-ref))))))

  (testing "RefAtoms from different useRef slots are not ="
    (let [result (renderHook #(let [a (hook/use-ref 0)
                                    b (hook/use-ref 0)]
                                #js [a b]))
          arr (.. result -result -current)
          a (aget arr 0)
          b (aget arr 1)]
      (is (not= a b)))))

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

(deftest react-ref-test
  (testing "react-ref extracts raw React ref from RefAtom"
    (let [result (renderHook #(hook/use-ref 42))
          ref-atom (.. result -result -current)
          raw-ref (hook/react-ref ref-atom)]
      (is (= 42 (.-current raw-ref)))))

  (testing "react-ref shares state with RefAtom"
    (let [result (renderHook #(hook/use-ref "initial"))
          ref-atom (.. result -result -current)
          raw-ref (hook/react-ref ref-atom)]
      (reset! ref-atom "updated")
      (is (= "updated" (.-current raw-ref)))
      (set! (.-current raw-ref) "from-js")
      (is (= "from-js" @ref-atom)))))

(deftest use-imperative-handle-test
  (testing "use-imperative-handle exposes custom handle"
    (let [parent-ref (react/createRef)
          child-component
          (component/forward-ref
            (fn [{:keys [ref]}]
              (hook/use-imperative-handle ref
                (fn [] #js {:focus (fn [] "focused")
                            :getValue (fn [] 42)}))
              (react/createElement "div" nil "child")))
          parent-component
          (fn []
            (react/createElement child-component
              #js {:cljsProps {} :ref parent-ref}))]
      (render (react/createElement parent-component))
      (is (= "focused" (.focus (.-current parent-ref))))
      (is (= 42 (.getValue (.-current parent-ref)))))))

(deftest use-atom-test
  (testing "returns current atom value on mount"
    (let [a      (cljs.core/atom {:count 0})
          result (renderHook #(hook/use-atom a))]
      (is (= {:count 0} (.. result -result -current)))))

  (testing "re-renders when atom changes to a different value"
    (let [a      (cljs.core/atom 1)
          result (renderHook #(hook/use-atom a))]
      (is (= 1 (.. result -result -current)))
      (act #(reset! a 2))
      (is (= 2 (.. result -result -current)))))

  (testing "swap! that produces a =-equal value does not create a new render"
    (let [a            (cljs.core/atom {:k "v"})
          render-count (cljs.core/atom 0)
          _            (renderHook #(do (swap! render-count inc)
                                        (hook/use-atom a)))]
      (is (= 1 @render-count))
      ;; swap to a structurally equal but non-identical map
      (act #(reset! a {:k "v"}))
      ;; snapshot cache should prevent a re-render
      (is (= 1 @render-count))
      ;; a real change does re-render
      (act #(reset! a {:k "w"}))
      (is (= 2 @render-count))))

  (testing "cleans up the watch on unmount"
    (let [a      (cljs.core/atom 0)
          result (renderHook #(hook/use-atom a))]
      (is (pos? (count (.-watches a))))
      (.unmount result)
      (is (zero? (count (.-watches a)))))))

(deftest forward-ref-component-test
  (testing "forward-ref component receives ref in props"
    (let [ext-ref (react/createRef)
          my-input
          (component/forward-ref
            (fn [{:keys [ref placeholder]}]
              (react/createElement "input"
                #js {:ref (hook/react-ref ref)
                     :placeholder placeholder})))
          _      (render
                   (react/createElement my-input
                     #js {:cljsProps {:placeholder "type here"}
                          :ref ext-ref}))]
      (is (some? (.-current ext-ref)))
      (is (= "INPUT" (.. ext-ref -current -tagName))))))

(deftest use-selector-custom-diff-and-select-test
  (testing "diff? suppresses unrelated source changes; select projects a slice"
    (let [a            (cljs.core/atom {:tracked 1 :other 0})
          render-count (cljs.core/atom 0)
          r            (renderHook
                         #(do (swap! render-count inc)
                              (hook/use-selector
                                a
                                (fn [o n] (not= (:tracked o) (:tracked n)))
                                :tracked
                                [])))]
      (is (= 1 (.. r -result -current)))
      (is (= 1 @render-count))
      ;; Change unrelated key — no re-render.
      (act #(swap! a assoc :other 99))
      (is (= 1 @render-count))
      ;; Change tracked key — re-render.
      (act #(swap! a assoc :tracked 2))
      (is (= 2 @render-count))
      (is (= 2 (.. r -result -current)))
      (.unmount r)))

  (testing "structurally-equal select reuses the previous reference"
    (let [a (cljs.core/atom {:k {:nested 1}})
          r (renderHook
              #(hook/use-selector
                 a (fn [o n] (not= (:k o) (:k n))) :k []))
          snap1 (.. r -result -current)]
      ;; Replace :k with a new map that is = but not identical.
      (act #(swap! a assoc :k {:nested 1}))
      (let [snap2 (.. r -result -current)]
        (is (identical? snap1 snap2)
            "structurally-equal selects must reuse the cached reference"))
      (.unmount r))))

(deftest use-id-test
  (testing "returns a non-empty string id, stable across re-renders"
    (let [r (renderHook #(hook/use-id))
          id1 (.. r -result -current)]
      (is (string? id1))
      (is (pos? (count id1)))
      (.rerender r)
      (is (= id1 (.. r -result -current)) ":id is stable across re-renders")))
  (testing "two hook calls in the same component yield distinct ids"
    (let [r (renderHook #(let [a (hook/use-id)
                               b (hook/use-id)]
                           [a b]))
          [a b] (.. r -result -current)]
      (is (not= a b)))))

(deftest use-transition-test
  (testing "use-transition returns [pending? start-transition]"
    (let [r (renderHook #(hook/use-transition))
          [pending start] (.. r -result -current)]
      (is (false? pending))
      (is (fn? start)))))

(deftest use-deferred-value-test
  (testing "use-deferred-value returns its argument (no concurrent priority in test)"
    (let [r (renderHook #(hook/use-deferred-value "abc"))]
      (is (= "abc" (.. r -result -current))))))