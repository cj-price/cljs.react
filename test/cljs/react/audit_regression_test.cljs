(ns cljs.react.audit-regression-test
  "Regression cases from the parallel library audit. Tests assert intended
  behavior, not the current bugs. Cursor refactor coverage is intentionally
  green: behavior-preserving simplifications must preserve those contracts."
  (:require
   [cljs.test :refer [deftest is testing async use-fixtures]]
   [cljs.react.component :as component]
   [cljs.react.core :refer [Element ErrorBoundary]]
   [cljs.react.db :as db]
   [cljs.react.form :as form]
   [cljs.react.hook :as hook]
   [cljs.react.sx :as sx]
   [cljs.react.sx.sheet :as sheet]
   ["global-jsdom/register"]
   ["react" :as react]
   ["@testing-library/react" :refer [render renderHook act cleanup]]))

(use-fixtures :each {:after (fn [] (cleanup))})

(deftest selector-dependency-change-invalidates-snapshot-test
  (let [source (atom {:a 1 :b 2})
        selected (atom :a)
        r (renderHook #(let [k @selected]
                         (hook/use-selector source not= k [k])))]
    (is (= 1 (.. r -result -current)))
    (reset! selected :b)
    (.rerender r)
    (is (= 2 (.. r -result -current))
        "changing the selector must recompute even when source state is identical")))

(deftest field-key-change-invalidates-snapshot-test
  (let [selected (atom :a)
        r (renderHook #(let [h (form/use-form {:values {:a "A" :b "B"}})]
                         (form/use-field h @selected)))]
    (is (= "A" (:value (.. r -result -current))))
    (reset! selected :b)
    (.rerender r)
    (is (= "B" (:value (.. r -result -current)))
        "use-field must immediately show the newly selected field")))

(deftest form-handle-switch-moves-field-and-meta-subscriptions-test
  (let [forms (renderHook #(vector (form/use-form {:values {:name "A"}})
                                   (form/use-form {:values {:name "B"}})))
        [a b] (.. forms -result -current)
        selected (atom a)
        r (renderHook #(vector (form/use-field @selected :name)
                               (form/use-form-meta @selected)))]
    (form/set-errors! b {:name "B error"})
    (reset! selected b)
    (.rerender r)
    (let [[field meta] (.. r -result -current)]
      (is (= "B" (:value field)) "reads move to B with the handle")
      (is (= {:name "B error"} (:errors meta)) "meta subscription also moves")
      (act #((:onChange field) #js {:target #js {:value "B edited"}})))
    (is (= "B edited" (:value (first (.. r -result -current))))
        "the field observes writes performed by its own new handler")
    (is (= "A" (get-in @(form/form-atom a) [:values :name])))
    (act #(form/set-values! a {:name "A changed"}))
    (is (= "B edited" (:value (first (.. r -result -current))))
        "the old form no longer controls the displayed value")
    (act #(form/set-errors! b {:name "new B error"}))
    (is (= {:name "new B error"} (:errors (second (.. r -result -current)))))))

(deftest memo-child-prop-key-change-with-nil-values-test
  (let [child (fn [props]
                (react/createElement "span" nil
                  (if (.call (.-hasOwnProperty js/Object.prototype) props "foo")
                    "foo" "bar")))
        parent (component/memo-component (fn [{:keys [children]}] children))
        tree (fn [props]
               (component/create-cljs-element parent {}
                 (react/createElement child props)))
        r (render (tree #js {:foo nil}))]
    (is (= "foo" (.. r -container -textContent)))
    (.rerender r (tree #js {:bar nil}))
    (is (= "bar" (.. r -container -textContent))
        "same key count and nil values do not imply the same prop keys")))

(deftest memo-child-forwarded-ref-change-test
  (let [child (component/forward-ref
                (fn [{:keys [ref]}] (Element {:tag "input" :ref ref})))
        parent (component/memo-component (fn [{:keys [children]}] children))
        ref-a (react/createRef)
        ref-b (react/createRef)
        tree (fn [ref]
               (component/create-cljs-element parent {}
                 (react/createElement child #js {:cljsProps {} :ref ref})))
        r (render (tree ref-a))
        node (.-current ref-a)]
    (is (some? node))
    (.rerender r (tree ref-b))
    (is (nil? (.-current ref-a)) "React must detach the old child ref")
    (is (identical? node (.-current ref-b))
        "a memoized ancestor must not swallow a nested ref change")))

(deftest error-boundary-falsy-thrown-values-test
  ;; 0 and "" are truthy in CLJS, so they are not regression cases.
  (doseq [value [nil false]]
    (testing (str "thrown " (pr-str value))
      (let [original-error (.-error js/console)
            escaped (atom [])
            child (fn [] (throw value))]
        (set! (.-error js/console) (fn [& _]))
        (try
          (try
            (render (ErrorBoundary
                      {:fallback (Element {:tag "span" :data-testid "fallback"} "caught")}
                      (react/createElement child)))
            (catch :default e (swap! escaped conj e)))
          (is (empty? @escaped) "the error must not escape the boundary")
          (is (some? (.querySelector js/document "[data-testid='fallback']"))
              "the fallback must render even when the error value is nil/false")
          (finally
            (cleanup)
            (set! (.-error js/console) original-error)))))))

(deftest baseline-alone-installs-default-theme-variables-test
  (sheet/reset-sheet!)
  (try
    (render (sx/BaselineProvider {:body? true}
              (Element {:tag "div"} "unstyled child")))
    (let [node (.querySelector js/document "[data-cljs-react-sx-theme]")
          css (if node (.-textContent node) "")]
      (is (re-find #"--cx-palette-mode:" css))
      (is (re-find #"--cx-palette-background-default:" css))
      (is (re-find #"--cx-spacing:" css)
          "baseline must not depend on a use-sx consumer to initialize variables"))
    (finally
      (cleanup)
      (sheet/reset-sheet!))))

(defn- deferred []
  (let [resolve-ref (atom nil)
        promise (js/Promise. (fn [resolve _reject] (reset! resolve-ref resolve)))]
    {:promise promise :resolve (fn [value] (@resolve-ref value))}))

(defn- blur-fixture []
  (let [pending (atom [])
        r (renderHook
            #(let [h (form/use-form
                       {:values {:name "initial"}
                        :validate-on :blur
                        :validate (fn [_]
                                    (let [d (deferred)]
                                      (swap! pending conj d)
                                      (:promise d)))})]
               {:handle h :field (form/use-field h :name)}))]
    {:result r :pending pending
     :handle (:handle (.. r -result -current))
     :blur (fn [] (act (fn []
                            ((:onBlur (:field (.. r -result -current))) nil)
                            js/undefined)))}))

(defn- settle! [pending index errors]
  ;; Await React's async act, including the validation promise chain; no timers.
  (js/Promise.resolve
    (act (fn []
           ((:resolve (nth @pending index)) errors)
           (:promise (nth @pending index))))))

(deftest overlapping-blurs-keep-validating-until-all-settle-test
  (async done
    (let [{:keys [pending handle blur]} (blur-fixture)]
      (blur)
      (blur)
      (is (:validating? @(form/form-atom handle)))
      (-> (settle! pending 0 {})
          (.then (fn []
                   (is (:validating? @(form/form-atom handle))
                       "one pending validator still exists after the first settles")
                   (settle! pending 1 {})))
          (.then (fn []
                   (is (false? (:validating? @(form/form-atom handle))))))
          (.catch (fn [e] (is false (str "unexpected rejection: " e))))
          (.finally done)))))

(deftest older-blur-result-cannot-overwrite-newer-errors-test
  (async done
    (let [{:keys [pending handle blur]} (blur-fixture)]
      (blur)
      (act #(form/set-values! handle {:name "new value"}))
      (blur)
      (-> (settle! pending 1 {:name "new error"})
          (.then (fn []
                   (is (= "new error" (get-in @(form/form-atom handle) [:errors :name])))
                   (settle! pending 0 {:name "stale error"})))
          (.then (fn []
                   (is (= "new error" (get-in @(form/form-atom handle) [:errors :name]))
                       "out-of-order completion must not restore an older error")))
          (.catch (fn [e] (is false (str "unexpected rejection: " e))))
          (.finally done)))))

(deftest reset-invalidates-pending-blur-test
  (async done
    (let [{:keys [pending handle blur]} (blur-fixture)]
      (blur)
      (act #(form/reset-form! handle))
      (let [fresh @(form/form-atom handle)]
        (-> (settle! pending 0 {:name "pre-reset error"})
            (.then (fn []
                     (is (= fresh @(form/form-atom handle))
                         "a pending blur must not mutate the reset form")))
            (.catch (fn [e] (is false (str "unexpected rejection: " e))))
            (.finally done))))))

(deftest submit-and-blur-share-pending-validation-accounting-test
  (async done
    (let [{:keys [pending handle blur]} (blur-fixture)
          submission (atom nil)]
      (act (fn []
             (reset! submission ((form/on-submit handle)
                                 #js {:preventDefault (fn [])}))
             js/undefined))
      (blur)
      (-> (settle! pending 0 {})
          (.then (fn [] @submission))
          (.then (fn []
                   (is (false? (:submitting? @(form/form-atom handle))))
                   (is (:validating? @(form/form-atom handle))
                       "finishing submit must not clear a pending blur's flag")
                   (settle! pending 1 {})))
          (.then (fn []
                   (is (false? (:validating? @(form/form-atom handle))))))
          (.catch (fn [e] (is false (str "unexpected rejection: " e))))
          (.finally done)))))

(deftest cursor-watch-callback-and-return-contract-test
  (let [source (atom {:x 1})
        cursor (db/->Cursor source [:x])
        calls (atom [])]
    (try
      (is (identical? cursor (add-watch cursor :caller-key
                              (fn [k ref old new]
                                (swap! calls conj [k ref old new]))))
          "add-watch returns the watched cursor, not the backing atom")
      (swap! source assoc :x 2)
      (is (= 1 (count @calls)))
      (let [[k ref old new] (first @calls)]
        (is (= :caller-key k) "callback receives the caller's key")
        (is (identical? cursor ref))
        (is (= [1 2] [old new])))
      (is (identical? cursor (remove-watch cursor :caller-key))
          "remove-watch returns the cursor")
      (swap! source assoc :x 3)
      (is (= 1 (count @calls)) "removed watch stays removed")
      (finally (remove-watch cursor :caller-key)))))

(deftest cursor-subscription-refactor-preserves-isolation-test
  ;; Characterization coverage for the proposed use-db simplification. There
  ;; is no correctness claim that could honestly make this refactor test red.
  (let [source (atom {:x {:n 1} :other 0})
        wrapper (fn [props] (db/DBProvider {:value source} (.-children props)))
        renders-a (atom 0)
        renders-b (atom 0)
        mount (fn [renders]
                (renderHook #(do (swap! renders inc) (db/use-db [:x]))
                            #js {:wrapper wrapper}))
        a (mount renders-a)
        b (mount renders-b)]
    (is (= @(.. a -result -current) @(.. b -result -current)))
    (act #(swap! source assoc :other 1))
    (act #(swap! source assoc :x {:n 1}))
    (is (= [1 1] [@renders-a @renders-b])
        "unrelated and structurally equal updates do not rerender either consumer")
    (act #(swap! source assoc :x {:n 2}))
    (is (= [2 2] [@renders-a @renders-b]))
    (is (= {:n 2} @(.. a -result -current)))
    (is (= {:n 2} @(.. b -result -current)))
    (.unmount a)
    (act #(swap! source assoc :x {:n 3}))
    (is (= [2 3] [@renders-a @renders-b])
        "unmounting one same-path consumer must not unsubscribe the other")
    (is (= {:n 3} @(.. b -result -current)))))
