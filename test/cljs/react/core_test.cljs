(ns cljs.react.core-test
  (:require
   [cljs.test :refer [deftest testing is async]]
   [cljs.react.core :as core :refer [Element ErrorBoundary]]
   [cljs.react.component :as component]
   [cljs.react.hook :as hook]
   ["react" :as react]
   ["global-jsdom/register"]
   ["@testing-library/react" :refer [render renderHook act cleanup]])
  (:require-macros [cljs.react.core :refer [defnc]]))

;;; Element

(deftest element-happy-path-test
  (testing "renders a plain DOM element"
    (let [result (render (Element {:tag "div" :className "foo"} "hello"))
          el (.. result -container -firstChild)]
      (is (= "DIV" (.-tagName el)))
      (is (= "foo" (.-className el)))
      (is (= "hello" (.-textContent el)))
      (cleanup))))

(deftest element-nil-tag-throws-test
  (testing "Element with nil :tag throws"
    (is (thrown? js/Error (Element {:tag nil} "oops")))
    (is (thrown? js/Error (Element {} "oops")))))

(deftest element-varargs-children-test
  (testing "Element passes many children to React"
    (let [result (render (Element {:tag "ul"}
                           (Element {:tag "li" :key "a"} "a")
                           (Element {:tag "li" :key "b"} "b")
                           (Element {:tag "li" :key "c"} "c")))
          ul (.. result -container -firstChild)]
      (is (= 3 (.. ul -children -length)))
      (is (= "a" (.-textContent (aget (.-children ul) 0))))
      (is (= "c" (.-textContent (aget (.-children ul) 2))))
      (cleanup))))

(deftest element-nested-children-test
  (testing "Element handles nested Elements"
    (let [result (render (Element {:tag "div"}
                           (Element {:tag "span"}
                             (Element {:tag "b"} "bold"))))
          b (.. result -container (querySelector "b"))]
      (is (some? b))
      (is (= "bold" (.-textContent b)))
      (cleanup))))

;;; defnc — default branch

(defnc SimpleComp
  []
  (Element {:tag "p" :className "empty"} "empty"))

(defnc PropComp
  [{:keys [name]}]
  (Element {:tag "p"} (str "hi " name)))

(defnc KidsComp
  [{:keys [title children]}]
  (Element {:tag "section"}
    (Element {:tag "h1"} title)
    (Element {:tag "div" :className "kids"} children)))

(deftest defnc-0-arity-test
  (testing "defnc component can be called with no args"
    (let [result (render (SimpleComp))
          p (.. result -container -firstChild)]
      (is (= "P" (.-tagName p)))
      (is (= "empty" (.-textContent p)))
      (cleanup))))

(deftest defnc-1-arity-test
  (testing "defnc component receives CLJS props map"
    (let [result (render (PropComp {:name "Zed"}))
          p (.. result -container -firstChild)]
      (is (= "hi Zed" (.-textContent p)))
      (cleanup))))

(deftest defnc-props-and-children-test
  (testing "defnc component receives children as :children prop"
    (let [result (render (KidsComp {:title "Chapters"}
                           (Element {:tag "span"} "one")
                           (Element {:tag "span"} "two")))
          h1 (.. result -container (querySelector "h1"))
          kids (.. result -container (querySelector ".kids"))]
      (is (= "Chapters" (.-textContent h1)))
      (is (= 2 (.. kids -children -length)))
      (cleanup))))

(def ^:private render-count (atom 0))

(defnc CountedComp
  [{:keys [x]}]
  (swap! render-count inc)
  (Element {:tag "p"} (str x)))

(deftest defnc-memo-cljs-equality-test
  (testing "same CLJS props → memo-component skips the body"
    (reset! render-count 0)
    (let [result (render (CountedComp {:x {:nested [1 2 3]}}))]
      (is (= 1 @render-count))
      (.rerender result (CountedComp {:x {:nested [1 2 3]}}))
      (is (= 1 @render-count) "props = → body not re-invoked")
      (.rerender result (CountedComp {:x {:nested [1 2 4]}}))
      (is (= 2 @render-count) "different props → body runs again")
      (cleanup))))

;;; defnc :as-element

(defnc RawJSComp :as-element
  [^js js-props]
  (react/createElement "span" nil (.-label js-props)))

(deftest defnc-as-element-test
  (testing ":as-element receives raw JS props"
    (let [result (render (RawJSComp {:label "raw"}))
          s (.. result -container -firstChild)]
      (is (= "SPAN" (.-tagName s)))
      (is (= "raw" (.-textContent s)))
      (cleanup))))

;;; defnc :forward-ref

(defnc RefInput :forward-ref
  [{:keys [ref placeholder]}]
  (react/createElement "input"
    #js {:ref (hook/react-ref ref)
         :placeholder placeholder}))

(deftest defnc-forward-ref-test
  (testing ":forward-ref gives component :ref as RefAtom, wire-through to DOM"
    (let [ext-ref (react/createRef)
          result (render
                   (react/createElement (.-type (RefInput))
                     #js {:cljsProps {:placeholder "type here"}
                          :ref ext-ref}))]
      (is (= "INPUT" (.. ext-ref -current -tagName)))
      (is (= "type here" (.. ext-ref -current -placeholder)))
      (cleanup))))

;;; :key hoist — keyed lists must not remount on reorder

(def ^:private mount-log (atom []))

(defnc KeyedItem
  [{:keys [label]}]
  (hook/use-effect
    (fn []
      (swap! mount-log conj [:mount label])
      (fn [] (swap! mount-log conj [:unmount label])))
    [])
  (Element {:tag "li"} label))

(defn- KeyedList [items]
  (apply Element {:tag "ul"}
    (for [i items]
      (KeyedItem {:key (str i) :label (str i)}))))

(deftest keyed-list-reorder-no-remount-test
  (testing "reordering a list with stable keys does not remount items"
    (reset! mount-log [])
    (let [result (render (KeyedList [1 2 3]))]
      (is (= 3 (count (filter #(= :mount (first %)) @mount-log))))
      (.rerender result (KeyedList [3 2 1]))
      ;; Only the initial 3 mounts; no unmount/remount cycles for a reorder.
      (is (= 3 (count (filter #(= :mount (first %)) @mount-log)))
          "stable keys → React reuses the fibers, no remount")
      (is (zero? (count (filter #(= :unmount (first %)) @mount-log)))
          "no unmounts for a reorder")
      (cleanup))))

;;; memo-component render-count

(def ^:private memo-count (atom 0))

(defn- inner [{:keys [x y]}]
  (swap! memo-count inc)
  (Element {:tag "p"} (str x "/" y)))

(def MemoBasic (component/memo-component inner))

(deftest memo-component-render-count-test
  (testing "memo-component short-circuits identical CLJS props"
    (reset! memo-count 0)
    (let [result (render (component/create-cljs-element MemoBasic {:x 1 :y 2}))]
      (is (= 1 @memo-count))
      (.rerender result (component/create-cljs-element MemoBasic {:x 1 :y 2}))
      (is (= 1 @memo-count) "identical CLJS map content → body not re-invoked")
      (.rerender result (component/create-cljs-element MemoBasic {:x 1 :y 3}))
      (is (= 2 @memo-count) "different CLJS content → body runs")
      (cleanup))))

;;; displayName

(defnc ^:private DisplayNameProbe [_]
  (Element {:tag "span"} "x"))

(deftest defnc-display-name-test
  (testing "defnc sets displayName for React DevTools legibility"
    (is (= "DisplayNameProbe" (.-displayName DisplayNameProbe)))))

(deftest memo-component-propagates-display-name-test
  (testing "memo-component preserves the inner fn's displayName"
    (let [inner-fn (fn [_] (Element {:tag "i"}))
          _        (set! (.-displayName inner-fn) "MyInner")
          wrapped  (component/memo-component inner-fn)]
      (is (= "MyInner" (.-displayName wrapped))))))

(deftest forward-ref-propagates-display-name-test
  (testing "forward-ref preserves the inner fn's displayName"
    (let [inner-fn (fn [_] (Element {:tag "i"}))
          _        (set! (.-displayName inner-fn) "FwdInner")
          wrapped  (component/forward-ref inner-fn)]
      (is (= "FwdInner" (.-displayName wrapped))))))

(deftest memo-forward-ref-propagates-display-name-test
  (testing "memo-forward-ref preserves the inner fn's displayName"
    (let [inner-fn (fn [_] (Element {:tag "i"}))
          _        (set! (.-displayName inner-fn) "MemoFwdInner")
          wrapped  (component/memo-forward-ref inner-fn)]
      (is (= "MemoFwdInner" (.-displayName wrapped))))))

(deftest memo-component-js-propagates-display-name-test
  (testing "memo-component-js preserves the inner fn's displayName"
    (let [inner-fn (fn [_] (Element {:tag "i"}))
          _        (set! (.-displayName inner-fn) "MemoJsInner")
          wrapped  (component/memo-component-js inner-fn)]
      (is (= "MemoJsInner" (.-displayName wrapped))))))

;;; ErrorBoundary

(defn- throwing-child [{:keys [msg]}]
  (throw (js/Error. (or msg "boom"))))

(def ^:private Throwing (component/memo-component throwing-child))

(deftest error-boundary-renders-fallback-test
  (testing "ErrorBoundary renders fallback (fn) when a child throws"
    ;; Silence React's expected error logging during this test.
    (let [orig js/console.error]
      (set! js/console.error (fn [& _]))
      (try
        (let [result (render
                       (ErrorBoundary
                         {:fallback (fn [err]
                                      (Element {:tag "p" :className "err"}
                                        (.-message err)))}
                         (component/create-cljs-element Throwing {:msg "kaboom"})))
              el (.. result -container -firstChild)]
          (is (= "P" (.-tagName el)))
          (is (= "err" (.-className el)))
          (is (= "kaboom" (.-textContent el)))
          (cleanup))
        (finally (set! js/console.error orig))))))

(deftest error-boundary-static-fallback-test
  (testing "ErrorBoundary renders a non-fn :fallback as-is"
    (let [orig js/console.error]
      (set! js/console.error (fn [& _]))
      (try
        (let [result (render
                       (ErrorBoundary
                         {:fallback (Element {:tag "span"} "static fallback")}
                         (component/create-cljs-element Throwing {})))]
          (is (= "static fallback" (.-textContent (.-container result))))
          (cleanup))
        (finally (set! js/console.error orig))))))

(deftest error-boundary-on-error-test
  (testing "ErrorBoundary calls :on-error callback when a child throws"
    (let [captured (atom nil)
          orig js/console.error]
      (set! js/console.error (fn [& _]))
      (try
        (render
          (ErrorBoundary
            {:fallback (fn [_] (Element {:tag "span"} "x"))
             :on-error (fn [err _info] (reset! captured err))}
            (component/create-cljs-element Throwing {:msg "telemetry"})))
        (is (some? @captured))
        (is (= "telemetry" (.-message @captured)))
        (cleanup)
        (finally (set! js/console.error orig))))))

;;; StrictMode double-invocation safety

(defn- strict [element]
  (react/createElement react/StrictMode nil element))

(deftest use-atom-strict-mode-test
  (testing "use-atom under StrictMode leaves exactly one watch after mount"
    (let [a (cljs.core/atom 0)
          Probe (fn [_]
                  (hook/use-atom a)
                  (Element {:tag "i"}))
          memoized (component/memo-component Probe)
          result (render (strict (component/create-cljs-element memoized {})))]
      ;; StrictMode intentionally double-invokes render / effect setup+cleanup,
      ;; but after mount settles there should be exactly one live subscription.
      (is (= 1 (count (.-watches a))))
      (.unmount result)
      (is (zero? (count (.-watches a)))))))

(deftest db-provider-strict-mode-test
  (testing "DBProvider under StrictMode keeps the db identity stable"
    (let [seen (atom [])
          Probe (fn [_]
                  (let [db (core/use-db-atom)]
                    (swap! seen conj db))
                  (Element {:tag "i"}))
          memoized (component/memo-component Probe)
          result (render (strict
                           (core/DBProvider {:initial-value {:x 1}}
                             (component/create-cljs-element memoized {}))))]
      (is (pos? (count @seen)))
      (is (apply identical? @seen)
          "all renders observe the same db atom")
      (.unmount result))))
