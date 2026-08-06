(ns cljs.react.component-test
  (:require
   [cljs.test :refer [deftest testing is]]
   [cljs.react.component :as component]
   [cljs.react.hook :as hook]
   [goog.object :as gobj]
   ["react" :as react]
   ["global-jsdom/register"]
   ["@testing-library/react" :refer [render renderHook cleanup act fireEvent]]))

;;; clj->js-props

(deftest clj->js-props-nil-test
  (testing "nil → nil"
    (is (nil? (component/clj->js-props nil)))))

(deftest clj->js-props-empty-test
  (testing "empty map → nil (React accepts nil props; saves an allocation)"
    (is (nil? (component/clj->js-props {})))))

(deftest clj->js-props-keyword-keys-test
  (testing "keyword keys become string properties"
    (let [js-obj (component/clj->js-props {:className "foo" :id "bar"})]
      (is (= "foo" (gobj/get js-obj "className")))
      (is (= "bar" (gobj/get js-obj "id"))))))

(deftest clj->js-props-string-keys-test
  (testing "string keys are preserved as-is"
    (let [js-obj (component/clj->js-props {"className" "x" "id" "y"})]
      (is (= "x" (gobj/get js-obj "className")))
      (is (= "y" (gobj/get js-obj "id"))))))

(deftest clj->js-props-nested-map-test
  (testing "nested maps are converted recursively"
    (let [js-obj (component/clj->js-props {:style {:color "red" :margin 10}})
          style (gobj/get js-obj "style")]
      (is (= "red" (gobj/get style "color")))
      (is (= 10 (gobj/get style "margin"))))))

(deftest clj->js-props-sequential-test
  (testing "sequentials become JS arrays"
    (let [js-obj (component/clj->js-props {:items [1 2 3]})
          items (gobj/get js-obj "items")]
      (is (array? items))
      (is (= 3 (alength items)))
      (is (= 1 (aget items 0)))
      (is (= 3 (aget items 2))))))

(deftest clj->js-props-mixed-test
  (testing "handles a mix of scalars, maps, and sequences"
    (let [js-obj (component/clj->js-props
                   {:id "x" :n 42 :flag true
                    :style {:color "red"}
                    :items [:a :b]})]
      (is (= "x" (gobj/get js-obj "id")))
      (is (= 42 (gobj/get js-obj "n")))
      (is (true? (gobj/get js-obj "flag")))
      (is (= "red" (gobj/get (gobj/get js-obj "style") "color")))
      (is (array? (gobj/get js-obj "items"))))))

(deftest clj->js-props-ref-refatom-test
  (testing ":ref with a RefAtom is unwrapped to the raw React ref"
    (let [result (renderHook #(hook/use-ref "init"))
          ref-atom (.. result -result -current)
          js-obj (component/clj->js-props {:ref ref-atom})
          raw-ref (gobj/get js-obj "ref")]
      (is (some? raw-ref))
      (is (= (hook/react-ref ref-atom) raw-ref))
      (cleanup))))

(deftest clj->js-props-ref-passthrough-test
  (testing ":ref with a non-IReactRef value is passed through unchanged"
    (let [callback (fn [_x] :called)
          js-obj (component/clj->js-props {:ref callback})]
      (is (identical? callback (gobj/get js-obj "ref"))))))

(deftest clj->js-props-self-referential-js-object-passthrough-test
  (testing "a self-referential plain JS object passes through by identity — clj->js-props
            does not recurse into pre-built JS objects, so a cycle cannot blow the stack.
            (Persistent CLJS collections are immutable and cannot self-reference, so the
            recursive walk has no reachable cycle; this documents the boundary.)"
    (let [o #js {}]
      (set! (.-self o) o)
      (let [out (component/clj->js-props {:obj o})]
        (is (identical? o (gobj/get out "obj"))
            "the cyclic JS object is handed through untouched, no infinite recursion")))))

(deftest clj->js-props-namespaced-keyword-test
  (testing "namespaced keywords use their fully-qualified name"
    (let [js-obj (component/clj->js-props {:foo/bar "x" :a.b/c "y"})]
      (is (= "x" (gobj/get js-obj "foo/bar")))
      (is (= "y" (gobj/get js-obj "a.b/c"))))))

(deftest clj->js-props-non-keyword-key-test
  (testing "non-keyword, non-string keys are coerced via str (incl. namespaced symbols)"
    (let [js-obj (component/clj->js-props {42 "n" 'sym "s" 'foo/bar "ns"})]
      (is (= "n" (gobj/get js-obj "42")))
      (is (= "s" (gobj/get js-obj "sym")))
      ;; namespaced symbol should preserve its full string form, not just (name s)
      (is (= "ns" (gobj/get js-obj "foo/bar"))))))

(deftest clj->js-props-nil-and-false-values-test
  (testing "nil and false values are preserved (not dropped)"
    (let [js-obj (component/clj->js-props {:a nil :b false :c 0})]
      (is (true? (gobj/containsKey js-obj "a")))
      (is (nil? (gobj/get js-obj "a")))
      (is (false? (gobj/get js-obj "b")))
      (is (= 0 (gobj/get js-obj "c"))))))

(deftest clj->js-props-deep-nesting-test
  (testing "maps nest arbitrarily deep — every level becomes a JS object"
    (let [js-obj (component/clj->js-props
                   {:a {:b {:c {:d {:e "leaf"}}}}})
          a (gobj/get js-obj "a")
          b (gobj/get a "b")
          c (gobj/get b "c")
          d (gobj/get c "d")]
      (is (object? a))
      (is (object? b))
      (is (object? c))
      (is (object? d))
      (is (= "leaf" (gobj/get d "e"))))))

(deftest clj->js-props-js-object-passthrough-test
  (testing "a #js object inside a CLJS map is preserved by reference"
    ;; Lets consumers drop down to #js when they need JS-only data (e.g. for
    ;; libraries with specific JS shape requirements) without forcing a round-
    ;; trip through CLJS.
    (let [inner #js {:already "js"}
          js-obj (component/clj->js-props {:style inner})]
      (is (identical? inner (gobj/get js-obj "style"))))))

(deftest clj->js-props-mixed-js-inside-cljs-test
  (testing "a CLJS map containing a #js sub-object preserves the JS sub-object"
    (let [raw   #js {:nested "raw"}
          js-obj (component/clj->js-props
                   {:wrap {:cljs "inner" :raw raw}})
          wrap   (gobj/get js-obj "wrap")]
      (is (= "inner" (gobj/get wrap "cljs")))
      (is (identical? raw (gobj/get wrap "raw"))))))

(deftest clj->js-props-nested-empty-map-test
  (testing "nested empty maps round-trip as empty JS objects (not nil)"
    (let [js-obj (component/clj->js-props {:style {}})
          style (gobj/get js-obj "style")]
      (is (object? style))
      (is (zero? (alength (gobj/getKeys style)))))))

(deftest clj->js-props-nested-ref-test
  (testing ":ref inside a nested map is also unwrapped"
    (let [result (renderHook #(hook/use-ref "init"))
          ref-atom (.. result -result -current)
          js-obj (component/clj->js-props {:inner {:ref ref-atom}})
          inner (gobj/get js-obj "inner")]
      (is (= (hook/react-ref ref-atom) (gobj/get inner "ref")))
      (cleanup))))

(deftest clj->js-props-list-and-seq-test
  (testing "lists and lazy seqs become JS arrays"
    (let [js-obj (component/clj->js-props {:list '(1 2 3)
                                            :seq  (map inc [9 8 7])})
          list-arr (gobj/get js-obj "list")
          seq-arr  (gobj/get js-obj "seq")]
      (is (array? list-arr))
      (is (= [1 2 3] (vec list-arr)))
      (is (array? seq-arr))
      (is (= [10 9 8] (vec seq-arr))))))

(deftest clj->js-props-vector-deep-test
  (testing "vector conversion is deep — CLJS maps inside arrays become JS objects"
    (let [js-obj (component/clj->js-props {:rows [{:id 1 :name "a"}
                                                  {:id 2 :name "b"}]})
          rows   (gobj/get js-obj "rows")]
      (is (array? rows))
      (is (object? (aget rows 0)))
      (is (= 1 (gobj/get (aget rows 0) "id")))
      (is (= "a" (gobj/get (aget rows 0) "name")))
      (is (= 2 (gobj/get (aget rows 1) "id")))))
  (testing "nested arrays inside arrays also recurse"
    (let [js-obj (component/clj->js-props {:grid [[{:v 1}] [{:v 2}]]})
          grid   (gobj/get js-obj "grid")]
      (is (array? grid))
      (is (array? (aget grid 0)))
      (is (= 1 (gobj/get (aget (aget grid 0) 0) "v")))))
  (testing "primitive elements pass through unchanged"
    (let [js-obj (component/clj->js-props {:items [1 "two" :three]})
          items  (gobj/get js-obj "items")]
      (is (array? items))
      (is (= 1 (aget items 0)))
      (is (= "two" (aget items 1)))))
  (testing "pre-JS objects inside arrays are kept by reference"
    (let [raw    #js {:already "js"}
          js-obj (component/clj->js-props {:rows [raw]})
          rows   (gobj/get js-obj "rows")]
      (is (identical? raw (aget rows 0))))))

(deftest clj->js-props-hashmap-path-test
  (testing "PersistentHashMap (>8 entries) takes the reduce-kv fallback path"
    (let [big (zipmap (map #(keyword (str "k" %)) (range 12)) (range 12))]
      ;; sanity: confirm we actually exercise the non-ArrayMap branch
      (is (not (instance? PersistentArrayMap big)))
      (is (instance? PersistentHashMap big))
      (let [js-obj (component/clj->js-props big)]
        (is (= 11 (gobj/get js-obj "k11")))
        (is (= 0  (gobj/get js-obj "k0")))
        (is (= 12 (alength (gobj/getKeys js-obj))))))))

(deftest clj->js-props-skip-key-arraymap-test
  (testing "2-arity skip-key drops a top-level key (PersistentArrayMap path)"
    (let [m {:tag "div" :id "x" :className "c"}
          _ (is (instance? PersistentArrayMap m))
          js-obj (component/clj->js-props m :tag)]
      (is (nil? (gobj/get js-obj "tag")))
      (is (false? (gobj/containsKey js-obj "tag")))
      (is (= "x" (gobj/get js-obj "id")))
      (is (= "c" (gobj/get js-obj "className"))))))

(deftest clj->js-props-skip-key-hashmap-test
  (testing "2-arity skip-key drops a top-level key (PersistentHashMap path)"
    (let [big (assoc (zipmap (map #(keyword (str "k" %)) (range 12)) (range 12))
                :tag "div")
          _ (is (not (instance? PersistentArrayMap big)))
          js-obj (component/clj->js-props big :tag)]
      (is (false? (gobj/containsKey js-obj "tag")))
      (is (= 11 (gobj/get js-obj "k11"))))))

(deftest clj->js-props-skip-key-only-top-level-test
  (testing "skip-key only applies at the top level, not in nested maps"
    (let [js-obj (component/clj->js-props {:tag "div" :nested {:tag "keep"}} :tag)
          nested (gobj/get js-obj "nested")]
      (is (false? (gobj/containsKey js-obj "tag")))
      (is (= "keep" (gobj/get nested "tag"))))))

(deftest clj->js-props-skip-key-missing-test
  (testing "skip-key for a key not in the map is a no-op"
    (let [js-obj (component/clj->js-props {:id "x" :className "c"} :tag)]
      (is (= "x" (gobj/get js-obj "id")))
      (is (= "c" (gobj/get js-obj "className")))
      (is (= 2 (alength (gobj/getKeys js-obj)))))))

(deftest clj->js-props-skip-key-empty-after-drop-test
  (testing "dropping the only key produces an empty JS object (not nil)"
    ;; has-entries? gates on the input being non-empty, so the empty-map → nil
    ;; short-circuit doesn't fire here — callers get an empty #js {}.
    (let [js-obj (component/clj->js-props {:tag "div"} :tag)]
      (is (object? js-obj))
      (is (zero? (alength (gobj/getKeys js-obj)))))))

;;; make-element-fn (custom renderer)

(deftest make-element-fn-happy-path-test
  (testing "make-element-fn dispatches to the provided renderer"
    (let [calls (atom [])
          mock-renderer (fn [& args] (swap! calls conj args) :fake-element)
          my-element (component/make-element-fn mock-renderer)
          result (my-element {:tag "section" :className "x"} "hi")]
      (is (= :fake-element result))
      (is (= 1 (count @calls)))
      (let [[tag js-props child] (first @calls)]
        (is (= "section" tag))
        (is (= "x" (gobj/get js-props "className")))
        (is (nil? (gobj/get js-props "tag")))
        (is (= "hi" child))))))

(deftest make-element-fn-nil-tag-throws-test
  (testing "make-element-fn with nil :tag throws (matches Element contract)"
    (let [my-element (component/make-element-fn (fn [& _] :never))]
      (is (thrown? js/Error (my-element {:tag nil})))
      (is (thrown? js/Error (my-element {}))))))

;;; make-create-cljs-element-fn

(deftest make-create-cljs-element-fn-test
  (testing "for string types it passes js props (converted)"
    (let [calls (atom [])
          mock-renderer (fn [& args] (swap! calls conj args) :fake-element)
          create (component/make-create-cljs-element-fn mock-renderer)
          _ (create "div" {:id "x"} "a")
          [type js-props child] (first @calls)]
      (is (= "div" type))
      (is (= "x" (gobj/get js-props "id")))
      (is (= "a" child))))

  (testing "for component types it wraps props in cljsProps"
    (let [calls (atom [])
          mock-renderer (fn [& args] (swap! calls conj args) :fake-element)
          create (component/make-create-cljs-element-fn mock-renderer)
          my-comp (fn [_] nil)
          _ (create my-comp {:x 1 :key "k"} "child")
          [type js-props child] (first @calls)]
      (is (identical? my-comp type))
      (is (= {:x 1 :key "k"} (gobj/get js-props "cljsProps")))
      (is (= "k" (gobj/get js-props "key"))
          ":key is hoisted to the top level so React can see it")
      (is (= "child" child)))))

;;; adapt

(defn- raw-labeled [^js js-props]
  (react/createElement "span" #js {:id (.-id js-props)} (.-label js-props)))

(defn- raw-children-panel [^js js-props]
  (react/createElement "div" #js {:id "panel"} (.-children js-props)))

(deftest adapt-props-test
  (testing "adapted component receives converted JS props"
    (let [Labeled (component/adapt raw-labeled)
          result  (render (Labeled {:id "x" :label "hi"}))
          span    (.. result -container -firstChild)]
      (is (= "SPAN" (.-tagName span)))
      (is (= "x" (.-id span)))
      (is (= "hi" (.-textContent span)))
      (cleanup))))

(deftest adapt-no-args-test
  (testing "0-arity renders with nil props"
    (let [Labeled (component/adapt raw-labeled)
          result  (render (Labeled))
          span    (.. result -container -firstChild)]
      (is (= "SPAN" (.-tagName span)))
      (is (= "" (.-textContent span)))
      (cleanup))))

(deftest adapt-children-test
  (testing "children pass through to props.children"
    (let [Panel  (component/adapt raw-children-panel)
          result (render (Panel {}
                           (react/createElement "p" nil "one")
                           (react/createElement "p" nil "two")))
          panel  (.. result -container -firstChild)]
      (is (= 2 (.. panel -children -length)))
      (is (= "one" (.. panel -firstChild -textContent)))
      (is (= "two" (.. panel -lastChild -textContent)))
      (cleanup))))

(deftest adapt-nested-map-test
  (testing "nested prop maps arrive as JS objects (e.g. :style)"
    (let [seen   (atom nil)
          Probe  (component/adapt (fn [^js p] (reset! seen (.-style p)) nil))
          _      (render (Probe {:style {:color "red"}}))]
      (is (object? @seen))
      (is (= "red" (gobj/get @seen "color")))
      (cleanup))))

(deftest adapt-key-test
  (testing ":key in the props map becomes the element's key (extracted by createElement)"
    (let [Labeled (component/adapt raw-labeled)
          el      (Labeled {:key "k1" :label "x"})]
      (is (= "k1" (.-key ^js el))))))

(deftest adapt-custom-renderer-test
  (testing "elements are created through *create-element* at call time"
    (let [calls   (atom [])
          comp-fn (fn [_] nil)
          Adapted (component/adapt comp-fn)]
      (binding [component/*create-element*
                (fn [& args] (swap! calls conj args) :fake-element)]
        (is (= :fake-element (Adapted {:a 1} "child")))
        (let [[type js-props child] (first @calls)]
          (is (identical? comp-fn type))
          (is (= 1 (gobj/get js-props "a")))
          (is (= "child" child)))))))

(deftest adapt-empty-props-test
  (testing "empty props map converts to nil props (no allocation)"
    (let [calls   (atom [])
          Adapted (component/adapt (fn [_] nil))]
      (binding [component/*create-element*
                (fn [& args] (swap! calls conj args) :fake-element)]
        (Adapted {})
        (is (nil? (second (first @calls))))))))

(deftest adapt-invalid-component-throws-test
  (testing "nil, numbers, plain objects, and module namespace objects throw at wrap time"
    (doseq [bad [nil 42 true #js {} #js {:default (fn [_] nil)}]]
      (let [e (try (component/adapt bad) (catch :default e e))]
        (is (= ::component/adapt-invalid-component (:type (ex-data e)))
            (str "expected typed throw for " (pr-str bad)))))))

(deftest adapt-module-object-hint-test
  (testing "a module namespace object's error message hints at the missing $default"
    (let [e (try (component/adapt #js {:default (fn [_] nil)}) (catch :default e e))]
      (is (some? (re-find #"\$default" (ex-message e)))))))

(deftest adapt-cljs-wrapper-throws-test
  (testing "a fn carrying the defnc wrapper marker is rejected"
    (let [f (fn [_] nil)]
      (unchecked-set f "cljsReactWrapper" true)
      (let [e (try (component/adapt f) (catch :default e e))]
        (is (= ::component/adapt-cljs-component (:type (ex-data e))))))))

(deftest adapt-non-map-props-throws-test
  (testing "non-map props throw a typed ex-info instead of an opaque protocol error"
    (let [Labeled (component/adapt raw-labeled)]
      (doseq [bad ["Save" [1 2] 42 #js {:label "x"}]]
        (let [e (try (Labeled bad) (catch :default e e))]
          (is (= ::component/adapt-invalid-props (:type (ex-data e)))
              (str "expected typed throw for props " (pr-str bad))))))))

(deftest adapt-nil-props-test
  (testing "explicit nil props works on the 1-arity and children arities"
    (let [Labeled (component/adapt raw-labeled)
          r1      (render (Labeled nil))]
      (is (= "SPAN" (.-tagName (.. r1 -container -firstChild))))
      (cleanup))
    (let [Panel (component/adapt raw-children-panel)
          r2    (render (Panel nil (react/createElement "p" nil "c")))]
      (is (= 1 (.. r2 -container -firstChild -children -length)))
      (cleanup))))

(deftest adapt-children-arities-test
  (testing "1 through 5 children flow through the fixed and variadic arities"
    (doseq [n [1 2 3 4 5]]
      (let [Panel  (component/adapt raw-children-panel)
            kids   (map #(react/createElement "i" #js {:key %} (str %)) (range n))
            result (render (apply Panel {} kids))
            panel  (.. result -container -firstChild)]
        (is (= n (.. panel -children -length)) (str n " children"))
        (is (= (apply str (range n)) (.-textContent panel)))
        (cleanup)))))

(def ^:private raw-forward-input
  (react/forwardRef
    (fn [^js props ref]
      (react/createElement "input" #js {:ref ref :id (.-id props)}))))

(deftest adapt-refatom-forwardref-test
  (testing "a RefAtom under :ref unwraps and receives the DOM node end-to-end"
    (let [ref-atom (hook/->RefAtom #js {:current nil})
          Input    (component/adapt raw-forward-input)
          _        (render (Input {:ref ref-atom :id "adapted-input"}))]
      (is (some? @ref-atom))
      (is (= "INPUT" (.-tagName @ref-atom)))
      (is (= "adapted-input" (.-id @ref-atom)))
      (cleanup))))

(deftest adapt-memo-exotic-type-test
  (testing "a react/memo exotic type passes the guard and renders"
    (let [Memo   (component/adapt
                   (react/memo (fn [^js p] (react/createElement "b" nil (.-t p)))))
          result (render (Memo {:t "memoized"}))]
      (is (= "memoized" (.-textContent (.. result -container -firstChild))))
      (cleanup))))

(deftest adapt-string-tag-test
  (testing "a string tag works (curried Element behavior)"
    (let [Div    (component/adapt "div")
          result (render (Div {:id "d"} "x"))
          node   (.. result -container -firstChild)]
      (is (= "DIV" (.-tagName node)))
      (is (= "d" (.-id node)))
      (is (= "x" (.-textContent node)))
      (cleanup))))

(deftest adapt-fragment-test
  (testing "a symbol type (Fragment) passes the guard and groups children"
    (let [Frag   (component/adapt react/Fragment)
          result (render (Frag {}
                           (react/createElement "i" #js {:key "a"} "a")
                           (react/createElement "i" #js {:key "b"} "b")))]
      (is (= 2 (.. result -container -children -length)))
      (is (= "ab" (.. result -container -textContent)))
      (cleanup))))

(deftest adapt-element-throws-test
  (testing "a React element (an already-called component) is rejected at wrap time"
    (doseq [el [(react/createElement "div" nil)
                ((component/adapt raw-labeled) {:label "x"})]]
      (let [e (try (component/adapt el) (catch :default e e))]
        (is (= ::component/adapt-invalid-component (:type (ex-data e))))
        (is (some? (re-find #"element" (ex-message e))))))))

(deftest adapt-library-wrapper-throws-test
  (testing "memo-component / forward-ref / memo-forward-ref results are rejected"
    (doseq [wrapped [(component/memo-component (fn [_] nil))
                     (component/forward-ref (fn [_] nil))
                     (component/memo-forward-ref (fn [_] nil))]]
      (let [e (try (component/adapt wrapped) (catch :default e e))]
        (is (= ::component/adapt-cljs-component (:type (ex-data e)))
            "a cljsProps-convention wrapper must not silently receive raw JS props")))))

(deftest adapt-double-adapt-throws-test
  (testing "adapting an adapted component is rejected at wrap time"
    (let [Once (component/adapt raw-labeled)
          e    (try (component/adapt Once) (catch :default e e))]
      (is (= ::component/adapt-cljs-component (:type (ex-data e)))))))

(deftest adapt-memo-component-js-test
  (testing "a memo-component-js result accepts raw JS props and stays adaptable"
    (let [Memo   (component/adapt
                   (component/memo-component-js
                     (fn [^js p] (react/createElement "b" nil (.-t p)))))
          result (render (Memo {:t "js-memoized"}))]
      (is (= "js-memoized" (.-textContent (.. result -container -firstChild))))
      (cleanup))))

(def ^:private raw-clicker
  (fn [^js js-props]
    (react/createElement "button" #js {:onClick (.-onClick js-props)}
      (.-children js-props))))

(deftest adapt-event-handler-test
  (testing "a handler prop receives the DOM event and drives a state update"
    (let [Btn    (component/adapt raw-clicker)
          seen   (atom nil)
          Probe  (fn [_]
                   (let [open (hook/use-state false)]
                     (react/createElement "div" nil
                       (Btn {:onClick (fn [e]
                                        (reset! seen (.-type e))
                                        (reset! open true))}
                         "open")
                       (when @open
                         (react/createElement "div" #js {:id "dialog"} "content")))))
          result (render (react/createElement Probe))
          btn    (.querySelector (.-container result) "button")]
      (is (nil? (.querySelector (.-container result) "#dialog")))
      (act #(.click btn))
      (is (= "click" @seen) "handler received the DOM event")
      (is (some? (.querySelector (.-container result) "#dialog")))
      (cleanup))))

(deftest adapt-controlled-input-test
  (testing "value/onChange round trip through an adapted input"
    (let [Input  (component/adapt "input")
          Probe  (fn [_]
                   (let [v (hook/use-state "a")]
                     (react/createElement "div" nil
                       (Input {:value @v
                               :onChange #(reset! v (.. % -target -value))
                               :aria-label "probe"})
                       (react/createElement "span" #js {:id "echo"} @v))))
          result (render (react/createElement Probe))
          input  (.querySelector (.-container result) "input")]
      (is (= "a" (.-value input)))
      (.change fireEvent input #js {:target #js {:value "ab"}})
      (is (= "ab" (.-value (.querySelector (.-container result) "input"))))
      (is (= "ab" (.-textContent (.querySelector (.-container result) "#echo")))
          "the CLJS state saw the typed value")
      (cleanup))))

;;; memo-component-js

(defn- inner-js [^js js-props]
  (react/createElement "span" nil (.-label js-props)))

(def MemoJS (component/memo-component-js inner-js))

(deftest memo-component-js-test
  (testing "memo-component-js accepts raw JS props"
    (let [result (render (react/createElement MemoJS #js {:label "raw-memo"}))
          span (.. result -container -firstChild)]
      (is (= "SPAN" (.-tagName span)))
      (is (= "raw-memo" (.-textContent span)))
      (cleanup))))
