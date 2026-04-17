(ns cljs.react.core-test
  (:require
   [cljs.test :refer [deftest testing is]]
   [cljs.react.core :as core :refer [Element]]
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
