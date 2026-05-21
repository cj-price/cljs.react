(ns cljs.react.form-test
  (:require
   [cljs.test :refer [deftest testing is async]]
   [cljs.react.form :as form]
   ["react" :as react]
   ["global-jsdom/register"]
   ["@testing-library/react" :refer [renderHook act render cleanup]]))

;;; Test helpers — use the public FormHandle accessors (form-atom, form-opts, form-state)

(defn- form-state   [handle]   (form/form-state handle))
(defn- form-values  [handle]   (:values (form-state handle)))
(defn- form-errors  [handle]   (:errors (form-state handle)))
(defn- form-error   [handle k] (get-in (form-state handle) [:errors k]))
(defn- form-value   [handle k] (get-in (form-state handle) [:values k]))

(defn- form-set-error! [handle k msg]
  (swap! (form/form-atom handle) assoc-in [:errors k] msg))

(defn- form-set-value! [handle k v]
  (swap! (form/form-atom handle) assoc-in [:values k] v))

(defn- make-form-state [values]
  {:values values :errors {} :dirty #{} :touched #{}
   :validating? false :submitting? false :submitted? false :submit-error nil})

(defn- form-reset!
  ([handle]
   (let [vals (:values (form/form-opts handle))
         init (if (satisfies? IDeref vals) @vals vals)]
     (reset! (form/form-atom handle) (make-form-state init))))
  ([handle new-values]
   (reset! (form/form-atom handle) (make-form-state new-values))))

;;; Render helpers

(defn- render-form [opts]
  (renderHook #(form/use-form opts)))

(defn- render-field [opts field-key]
  (let [handle-atom (cljs.core/atom nil)]
    {:handle-atom handle-atom
     :result (renderHook #(let [f (form/use-form opts)]
                            (reset! handle-atom f)
                            (form/use-field f field-key)))}))

(defn- mock-event [value]
  #js {:target #js {:value value}
       :preventDefault (fn [])})

;;; use-form

(deftest use-form-test
  (testing "returns a FormHandle"
    (let [result (render-form {:values {:name "" :email ""}})
          handle (.. result -result -current)]
      (is (some? handle))
      (is (= {:name "" :email ""} (form-values handle)))))

  (testing "initial errors are empty"
    (let [result (render-form {:values {:x 1}})
          handle (.. result -result -current)]
      (is (= {} (form-errors handle)))))

  (testing "handle is stable across re-renders"
    (let [result (render-form {:values {:a 1}})
          first-handle (.. result -result -current)]
      (.rerender result)
      (let [second-handle (.. result -result -current)]
        (is (identical? first-handle second-handle))))))

;;; use-field

(deftest use-field-test
  (testing "returns value for field"
    (let [{:keys [result]} (render-field {:values {:name "Alice"}} :name)
          field (.. result -result -current)]
      (is (= "Alice" (:value field)))))

  (testing "error is nil when not touched"
    (let [{:keys [result handle-atom]} (render-field {:values {:name ""}} :name)
          field (.. result -result -current)]
      (is (nil? (:error field)))))

  (testing "onChange updates value"
    (let [{:keys [result]} (render-field {:values {:name ""}} :name)
          field (.. result -result -current)]
      (act #((:onChange field) (mock-event "Bob")))
      (let [updated (.. result -result -current)]
        (is (= "Bob" (:value updated))))))

  (testing "onBlur marks field touched"
    (let [{:keys [result handle-atom]}
          (render-field {:values {:name ""}
                         :validate (fn [_] {:name "Required"})}
                        :name)
          field (.. result -result -current)]
      ;; Before blur, no error shown
      (is (nil? (:error field)))
      ;; Trigger blur
      (act #((:onBlur field) nil))
      ;; No validate-on :blur set, so error still nil (shown on submit)
      (let [after (.. result -result -current)]
        (is (nil? (:error after))))))

  (testing "error shown after field is touched (via onChange)"
    (let [{:keys [result handle-atom]}
          (render-field {:values {:name ""}} :name)
          field (.. result -result -current)]
      ;; Manually set an error and touch the field
      (act #(do
              (form-set-error! @handle-atom :name "Required")
              ((:onBlur field) nil)))
      (let [after (.. result -result -current)]
        (is (= "Required" (:error after))))))

  (testing "dirty is false initially"
    (let [{:keys [result]} (render-field {:values {:name "Alice"}} :name)
          field (.. result -result -current)]
      (is (false? (:dirty field)))))

  (testing "dirty is true after onChange"
    (let [{:keys [result]} (render-field {:values {:name ""}} :name)
          field (.. result -result -current)]
      (act #((:onChange field) (mock-event "Bob")))
      (let [updated (.. result -result -current)]
        (is (true? (:dirty updated))))))

  (testing "changing field-a does not re-render field-b subscription"
    (let [handle-holder (cljs.core/atom nil)
          b-result (renderHook #(let [f (form/use-form {:values {:a "" :b ""}})]
                                  (reset! handle-holder f)
                                  (form/use-field f :b)))
          b-before (.. b-result -result -current)]
      ;; Directly update :a in the form atom
      (act #(form-set-value! @handle-holder :a "hello"))
      ;; :b subscription should not have changed
      (let [b-after (.. b-result -result -current)]
        (is (= (:value b-before) (:value b-after)))))))

;;; use-field with :type :checkbox

(deftest use-field-checkbox-test
  (testing "use-field with {:type :checkbox} returns :checked key"
    (let [handle-atom (cljs.core/atom nil)
          result (renderHook #(let [f (form/use-form {:values {:terms false}})]
                                (reset! handle-atom f)
                                (form/use-field f :terms {:type :checkbox})))
          field (.. result -result -current)]
      (is (false? (:checked field)))
      (is (nil? (:value field)))
      (is (fn? (:onChange field)))
      (is (fn? (:onBlur field))))))

;;; on-blur validation bug fixes

(defn- flush-microtasks
  "Return a promise that resolves after a setTimeout, letting pending microtasks settle."
  []
  (js/Promise. (fn [resolve] (js/setTimeout resolve 50))))

(deftest blur-validation-scoped-test
  (testing "blurring field A does not wipe field B's error"
    (async done
      (let [handle-atom (cljs.core/atom nil)
            result (renderHook #(let [f (form/use-form
                                          {:values      {:a "" :b ""}
                                           :validate    (fn [{:keys [a]}]
                                                          (when (empty? a) {:a "Required"}))
                                           :validate-on :blur})]
                                  (reset! handle-atom f)
                                  (form/use-field f :a)))]
        (-> (js/Promise.resolve (act #(form-set-error! @handle-atom :b "B is bad")))
            (.then (fn []
                     (is (= "B is bad" (form-error @handle-atom :b)))
                     (let [field (.. result -result -current)]
                       (-> (js/Promise.resolve (act #((:onBlur field) nil)))
                           (.then flush-microtasks)
                           (.then (fn []
                                    (is (= "B is bad" (form-error @handle-atom :b)))
                                    (.unmount result)
                                    (done))))))))))))

(deftest blur-validation-clears-test
  (testing "blurring a passing field clears its error"
    (async done
      (let [handle-atom (cljs.core/atom nil)
            result (renderHook #(let [f (form/use-form
                                          {:values      {:name "valid"}
                                           :validate    (fn [{:keys [name]}]
                                                          (when (empty? name) {:name "Required"}))
                                           :validate-on :blur})]
                                  (reset! handle-atom f)
                                  (form/use-field f :name)))]
        (-> (js/Promise.resolve (act #(form-set-error! @handle-atom :name "was bad")))
            (.then (fn []
                     (is (= "was bad" (form-error @handle-atom :name)))
                     (let [field (.. result -result -current)]
                       (-> (js/Promise.resolve (act #((:onBlur field) nil)))
                           (.then flush-microtasks)
                           (.then (fn []
                                    (is (nil? (form-error @handle-atom :name)))
                                    (.unmount result)
                                    (done))))))))))))

;;; use-form-meta

(deftest use-form-meta-test
  (testing "initial meta state"
    (let [result (renderHook #(let [f (form/use-form {:values {:x 1}})]
                                (form/use-form-meta f)))
          meta   (.. result -result -current)]
      (is (false? (:validating? meta)))
      (is (false? (:submitting? meta)))
      (is (false? (:submitted? meta)))
      (is (= {} (:errors meta))))))

;;; Validation (sync)

(deftest sync-validation-test
  (testing "submit with errors prevents submission"
    (async done
      (let [submitted (cljs.core/atom false)
            result    (render-form
                        {:values    {:name ""}
                         :validate  (fn [{:keys [name]}]
                                      (when (empty? name) {:name "Required"}))
                         :on-submit (fn [_] (reset! submitted true))})
            handle    (.. result -result -current)
            submit!   (form/on-submit handle)
            fake-e    #js {:preventDefault (fn [])}]
        (-> (submit! fake-e)
            (.then (fn []
                     (is (false? @submitted))
                     (is (= "Required" (form-error handle :name)))
                     (done)))))))

  (testing "submit with valid data calls on-submit"
    (async done
      (let [submitted (cljs.core/atom false)
            result    (render-form
                        {:values    {:name "Bob"}
                         :validate  (fn [{:keys [name]}]
                                      (when (empty? name) {:name "Required"}))
                         :on-submit (fn [_] (reset! submitted true))})
            handle    (.. result -result -current)
            submit!   (form/on-submit handle)
            fake-e    #js {:preventDefault (fn [])}]
        (-> (submit! fake-e)
            (.then (fn []
                     (is (true? @submitted))
                     (done))))))))

;;; Async validating? state

(deftest async-validating-test
  (testing "validating? is true while async validator is pending, false after"
    (async done
      (let [deferred    (cljs.core/atom nil)
            handle-atom (cljs.core/atom nil)
            result      (renderHook
                          #(let [f (form/use-form
                                     {:values    {:x 1}
                                      :validate  (fn [_]
                                                   (js/Promise.
                                                     (fn [resolve _]
                                                       (reset! deferred resolve))))
                                      :on-submit (fn [_] nil)})]
                             (reset! handle-atom f)
                             f))
            handle      (.. result -result -current)
            submit!     (form/on-submit handle)
            fake-e      #js {:preventDefault (fn [])}]
        (let [p (submit! fake-e)]
          ;; validation promise is still pending — validating? must be true
          (is (true? (:validating? (form-state handle))))
          ;; resolve the deferred — no errors
          (@deferred nil)
          (-> p
              (.then (fn []
                       (is (false? (:validating? (form-state handle))))
                       (.unmount result)
                       (done)))))))))

;;; Validation (async)

(deftest async-validation-test
  (testing "async validate with errors"
    (async done
      (let [submitted (cljs.core/atom false)
            result    (render-form
                        {:values    {:user "taken"}
                         :validate  (fn [{:keys [user]}]
                                      (js/Promise.resolve
                                        (when (= user "taken") {:user "Already taken"})))
                         :on-submit (fn [_] (reset! submitted true))})
            handle    (.. result -result -current)
            submit!   (form/on-submit handle)
            fake-e    #js {:preventDefault (fn [])}]
        (-> (submit! fake-e)
            (.then (fn []
                     (is (false? @submitted))
                     (is (= "Already taken" (form-error handle :user)))
                     (done))))))))

;;; Submit flow (submitting? state)

(deftest submit-flow-test
  (testing "submitted? is true and submitting? is false after successful submit"
    (async done
      (let [handle-holder (cljs.core/atom nil)
            result  (renderHook
                      #(let [f (form/use-form
                                 {:values    {:x 1}
                                  :on-submit (fn [_]
                                               (js/Promise. (fn [resolve _] (resolve nil))))})]
                         (reset! handle-holder f)
                         f))
            handle  (.. result -result -current)
            submit! (form/on-submit handle)
            fake-e  #js {:preventDefault (fn [])}]
        (-> (submit! fake-e)
            (.then (fn []
                     (is (false? (:submitting? (form-state handle))))
                     (is (true? (:submitted? (form-state handle))))
                     (done))))))))

(deftest submit-error-surfaced-test
  (testing "submit-fn rejection surfaces into :submit-error and clears :submitting?"
    (async done
      (let [err     (js/Error. "boom")
            result  (renderHook
                      #(form/use-form
                         {:values    {:x 1}
                          :on-submit (fn [_]
                                       (js/Promise. (fn [_ reject] (reject err))))}))
            handle  (.. result -result -current)
            submit! (form/on-submit handle)
            fake-e  #js {:preventDefault (fn [])}]
        (-> (submit! fake-e)
            (.then (fn []
                     (let [s (form-state handle)]
                       (is (false? (:submitting? s)))
                       (is (false? (:submitted? s)))
                       (is (identical? err (:submit-error s)))
                       (done)))))))))

(deftest submit-concurrent-guard-test
  (testing "second submit during in-flight first submit is a no-op"
    (async done
      (let [call-count (cljs.core/atom 0)
            result     (renderHook
                         #(form/use-form
                            {:values    {:x 1}
                             :on-submit (fn [_]
                                          (swap! call-count inc)
                                          (js/Promise.
                                            (fn [resolve _]
                                              (js/setTimeout (fn [] (resolve nil)) 30))))}))
            handle     (.. result -result -current)
            submit!    (form/on-submit handle)
            fake-e     #js {:preventDefault (fn [])}
            p1         (submit! fake-e)
            p2         (submit! fake-e)]
        (-> (js/Promise.all #js [p1 p2])
            (.then (fn [_]
                     (is (= 1 @call-count) "submit-fn invoked exactly once")
                     (let [s (form-state handle)]
                       (is (false? (:submitting? s)))
                       (is (true? (:submitted? s))))
                     (done))))))))

(deftest submit-validator-rejection-test
  (testing "validator promise rejection during submit clears submitting?/validating? and sets submit-error"
    (async done
      (let [err    (js/Error. "validator blew up")
            result (renderHook
                     #(form/use-form
                        {:values    {:x 1}
                         :validate  (fn [_]
                                      (js/Promise. (fn [_ reject] (reject err))))
                         :on-submit (fn [_] nil)}))
            handle  (.. result -result -current)
            submit! (form/on-submit handle)
            fake-e  #js {:preventDefault (fn [])}]
        (-> (submit! fake-e)
            (.then (fn []
                     (let [s (form-state handle)]
                       (is (false? (:submitting? s)))
                       (is (false? (:validating? s)))
                       (is (identical? err (:submit-error s)))
                       (done)))))))))

(deftest on-submit-2arity-test
  (testing "on-submit 2-arity override calls the provided submit fn, ignoring opts :on-submit"
    (async done
      (let [opts-calls     (cljs.core/atom 0)
            override-calls (cljs.core/atom 0)
            result (renderHook
                     #(form/use-form
                        {:values    {:x 1}
                         :on-submit (fn [_] (swap! opts-calls inc))}))
            handle (.. result -result -current)
            submit-override (form/on-submit handle
                                            (fn [_] (swap! override-calls inc)))
            fake-e #js {:preventDefault (fn [])}]
        (-> (submit-override fake-e)
            (.then (fn []
                     (is (= 0 @opts-calls))
                     (is (= 1 @override-calls))
                     (done))))))))

(deftest use-form-validator-only-test
  (testing "use-form with only :validate (no :on-submit) completes a clean submit"
    (async done
      (let [result (renderHook
                     #(form/use-form
                        {:values   {:x 1}
                         :validate (fn [_] nil)}))
            handle (.. result -result -current)
            submit! (form/on-submit handle)
            fake-e  #js {:preventDefault (fn [])}]
        (-> (submit! fake-e)
            (.then (fn []
                     (let [s (form-state handle)]
                       (is (false? (:submitting? s)))
                       (is (true?  (:submitted? s)))
                       (is (nil?   (:submit-error s))))
                     (done))))))))

(deftest blur-validator-rejection-test
  (testing "blur validator promise rejection clears validating? (does not leak state)"
    (async done
      (let [handle-atom (cljs.core/atom nil)
            result (renderHook
                     #(let [f (form/use-form
                                {:values      {:name ""}
                                 :validate    (fn [_]
                                                (js/Promise. (fn [_ reject]
                                                               (reject (js/Error. "nope")))))
                                 :validate-on :blur})]
                        (reset! handle-atom f)
                        (form/use-field f :name)))]
        (let [field (.. result -result -current)]
          (-> (js/Promise.resolve
                (act #(try ((:onBlur field) nil) (catch :default _ nil))))
              (.then flush-microtasks)
              (.then (fn []
                       (is (false? (:validating? (form-state @handle-atom))))
                       (.unmount result)
                       (done)))))))))

(deftest submit-clears-prior-error-test
  (testing ":submit-error is cleared at the start of a new submit"
    (async done
      (let [step    (cljs.core/atom 0)
            result  (renderHook
                      #(form/use-form
                         {:values    {:x 1}
                          :on-submit (fn [_]
                                       (swap! step inc)
                                       (if (= 1 @step)
                                         (js/Promise. (fn [_ reject] (reject (js/Error. "bad"))))
                                         (js/Promise. (fn [resolve _] (resolve nil)))))}))
            handle  (.. result -result -current)
            submit! (form/on-submit handle)
            fake-e  #js {:preventDefault (fn [])}]
        (-> (submit! fake-e)
            (.then (fn []
                     (is (some? (:submit-error (form-state handle))))
                     (submit! fake-e)))
            (.then (fn []
                     (is (nil? (:submit-error (form-state handle))))
                     (is (true? (:submitted? (form-state handle))))
                     (done))))))))

;;; reset-form!

(deftest reset-form-test
  (testing "reset-form! clears values and errors"
    (let [result (render-form {:values {:name "Alice"}})
          handle (.. result -result -current)]
      (form-set-value! handle :name "Bob")
      (form-set-error! handle :name "Bad")
      (is (= "Bob" (form-value handle :name)))
      (act #(form-reset! handle))
      (is (= "Alice" (form-value handle :name)))
      (is (= {} (form-errors handle)))))

  (testing "reset-form! with new values"
    (let [result (render-form {:values {:name ""}})
          handle (.. result -result -current)]
      (act #(form-reset! handle {:name "Carol"}))
      (is (= "Carol" (form-value handle :name))))))

;;; Direct mutators: set-values!, set-errors!, clear-errors!, set-field-touched!

(deftest set-values-test
  (testing "set-values! replaces :values without touching errors / flags"
    (let [result (render-form {:values {:a 1 :b 2}})
          handle (.. result -result -current)
          _      (form-set-error! handle :a "boom")]
      (act #(form/set-values! handle {:a 99 :c 3}))
      (is (= {:a 99 :c 3} (form-values handle)))
      (is (= "boom" (form-error handle :a))
          ":errors must be preserved across set-values!"))))

(deftest set-errors-marks-touched-test
  (testing "set-errors! replaces :errors AND marks the listed keys touched"
    (let [result (render-form {:values {:a 1 :b 2}})
          handle (.. result -result -current)]
      (act #(form/set-errors! handle {:a "bad-a" :b "bad-b"}))
      (let [state (form-state handle)]
        (is (= {:a "bad-a" :b "bad-b"} (:errors state)))
        (is (contains? (:touched state) :a))
        (is (contains? (:touched state) :b))))))

(deftest clear-errors-test
  (testing "clear-errors! empties :errors AND clears :submit-error"
    (let [result (render-form {:values {:a 1}})
          handle (.. result -result -current)]
      (swap! (form/form-atom handle)
             assoc :errors {:a "x"} :submit-error (js/Error. "boom"))
      (act #(form/clear-errors! handle))
      (let [state (form-state handle)]
        (is (= {} (:errors state)))
        (is (nil? (:submit-error state)))))))

(deftest use-form-rejects-unknown-validate-on-test
  (testing "use-form throws ex-info when :validate-on is not nil/:submit/:blur"
    (let [thrown (atom nil)]
      (try
        (render-form {:values {:a 1} :validate-on :whenever})
        (catch :default e (reset! thrown e)))
      (is (some? @thrown))
      (is (= :cljs.react.form/invalid-validate-on (:type (ex-data @thrown))))
      (is (= :whenever (:got (ex-data @thrown)))))))

(deftest set-field-touched-test
  (testing "set-field-touched! adds a single key to :touched"
    (let [result (render-form {:values {:a 1}})
          handle (.. result -result -current)]
      (is (false? (contains? (:touched (form-state handle)) :a)))
      (act #(form/set-field-touched! handle :a))
      (is (contains? (:touched (form-state handle)) :a))))

  (testing "set-field-touched! surfaces a previously-hidden error via use-field"
    (let [{:keys [result handle-atom]} (render-field {:values {:name ""}} :name)]
      ;; field starts untouched, so an error set directly on the atom is hidden
      (act #(form-set-error! @handle-atom :name "Required"))
      (is (nil? (:error (.. result -result -current))))
      ;; touching the field surfaces it
      (act #(form/set-field-touched! @handle-atom :name))
      (is (= "Required" (:error (.. result -result -current)))))))

(deftest use-field-handler-identity-test
  (testing "use-field returns identical :onChange/:onBlur across re-renders (cached)"
    (let [{:keys [^js result]} (render-field {:values {:name "Alice"}} :name)
          first-field (.. result -result -current)]
      (.rerender result)
      (let [second-field (.. result -result -current)]
        (is (identical? (:onChange first-field) (:onChange second-field))
            "onChange is cached by [field-key checkbox?] so JSX prop equality holds")
        (is (identical? (:onBlur first-field) (:onBlur second-field))
            "onBlur is cached likewise")))))

;;; Reactive defaults

(deftest reactive-defaults-test
  (testing "un-dirtied fields update when values atom changes"
    (let [values-atom (cljs.core/atom {:name "Alice" :email ""})
          result      (render-form {:values values-atom})
          handle      (.. result -result -current)]
      (is (= "Alice" (form-value handle :name)))
      ;; Update atom - un-dirtied field should update
      (act #(reset! values-atom {:name "Bob" :email "bob@example.com"}))
      (.rerender result)
      (is (= "Bob" (form-value handle :name)))))

  (testing "dirty fields are not overwritten by reactive defaults"
    (let [values-atom (cljs.core/atom {:name "Alice" :email ""})
          handle-holder (cljs.core/atom nil)
          result      (renderHook #(let [f (form/use-form {:values values-atom})]
                                     (reset! handle-holder f)
                                     f))
          handle      (.. result -result -current)]
      ;; Mark :name dirty by changing it
      (act #(swap! (form/form-atom handle)
                   (fn [s]
                     (-> s
                         (assoc-in [:values :name] "Custom")
                         (update :dirty conj :name)))))
      ;; Update atom
      (act #(reset! values-atom {:name "Bob" :email "bob@example.com"}))
      ;; Dirty field should be preserved
      (is (= "Custom" (form-value handle :name)))
      ;; Un-dirtied field should update
      (is (= "bob@example.com" (form-value handle :email))))))
