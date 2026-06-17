(ns cljs.react.form-test
  (:require
   [cljs.test :refer [deftest testing is async]]
   [cljs.react.form :as form]
   ["global-jsdom/register"]
   ["react" :as react]
   ["@testing-library/react" :refer [renderHook act]]))

;;; Test helpers — use the public FormHandle accessors (form-atom, form-opts)

(defn- form-state   [handle]   @(form/form-atom handle))
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
    (let [{:keys [result]} (render-field {:values {:name ""}} :name)
          field (.. result -result -current)]
      (is (nil? (:error field)))))

  (testing "onChange updates value"
    (let [{:keys [result]} (render-field {:values {:name ""}} :name)
          field (.. result -result -current)]
      (act #((:onChange field) (mock-event "Bob")))
      (let [updated (.. result -result -current)]
        (is (= "Bob" (:value updated))))))

  (testing "onBlur marks field touched in form state"
    (let [{:keys [result handle-atom]}
          (render-field {:values {:name ""}} :name)
          field (.. result -result -current)]
      (is (not (contains? (:touched (form-state @handle-atom)) :name)))
      (act #((:onBlur field) nil))
      (is (contains? (:touched (form-state @handle-atom)) :name))))

  (testing "error renders only after field is touched via onBlur"
    (let [{:keys [result handle-atom]}
          (render-field {:values {:name ""}} :name)
          field-pre (.. result -result -current)]
      ;; Set an error before touching — should not surface to :error
      (act #(form-set-error! @handle-atom :name "Required"))
      (is (nil? (:error (.. result -result -current))))
      ;; Now touch via onBlur — error should surface
      (act #((:onBlur field-pre) nil))
      (is (= "Required" (:error (.. result -result -current))))))

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

  (testing "onChange marks the field dirty but NOT touched (touched = blurred/submitted)"
    (let [{:keys [result handle-atom]} (render-field {:values {:name ""}} :name)
          field (.. result -result -current)]
      (act #((:onChange field) (mock-event "Bob")))
      (let [state (form-state @handle-atom)]
        (is (contains? (:dirty state) :name) "onChange marks dirty")
        (is (not (contains? (:touched state) :name))
            "onChange must not mark touched — a field being typed into hasn't been blurred"))))

  (testing "changing field-a does not re-render field-b subscription"
    (let [handle-holder (cljs.core/atom nil)
          renders       (cljs.core/atom 0)
          b-result (renderHook #(let [f (form/use-form {:values {:a "" :b ""}})]
                                  (reset! handle-holder f)
                                  (swap! renders inc)
                                  (form/use-field f :b)))
          baseline @renders
          b-before (.. b-result -result -current)]
      ;; Directly update :a in the form atom; :b subscription should not bump
      (act #(form-set-value! @handle-holder :a "hello"))
      (is (= baseline @renders) "field-b hook was re-invoked despite unrelated :a change")
      (let [b-after (.. b-result -result -current)]
        (is (= (:value b-before) (:value b-after))))
      ;; Sanity: updating :b *does* bump the hook
      (act #(form-set-value! @handle-holder :b "x"))
      (is (= (inc baseline) @renders)
          "field-b hook should re-invoke when :b changes"))))

;;; use-field with :checkbox?

(deftest use-field-checkbox-test
  (testing "use-field with {:checkbox? true} returns :checked key and toggles via onChange"
    (let [handle-atom (cljs.core/atom nil)
          result (renderHook #(let [f (form/use-form {:values {:terms false}})]
                                (reset! handle-atom f)
                                (form/use-field f :terms {:checkbox? true})))
          field (.. result -result -current)]
      (is (false? (:checked field)))
      ;; :value is also present (coerced source for :checked) — both keys are
      ;; always there so destructuring is uniform across field types.
      (is (contains? field :value))
      (is (fn? (:onChange field)))
      (is (fn? (:onBlur field)))
      ;; Flip via the checkbox onChange path — value should reflect e.target.checked
      (act #((:onChange field) #js {:target #js {:checked true}}))
      (is (true? (:checked (.. result -result -current))))
      (is (true? (form-value @handle-atom :terms)))
      ;; And flip back
      (act #((:onChange (.. result -result -current))
              #js {:target #js {:checked false}}))
      (is (false? (:checked (.. result -result -current))))
      (is (false? (form-value @handle-atom :terms))))))

(deftest use-field-uniform-shape-test
  (testing "use-field always returns both :value and :checked regardless of opts"
    ;; Both keys present in every shape — destructure once, branch on field
    ;; semantics, not on key presence.
    (testing "text field — :checked is false (non-boolean values never check)"
      (let [{:keys [result]} (render-field {:values {:name "Alice"}} :name)
            field (.. result -result -current)]
        (is (contains? field :value))
        (is (contains? field :checked))
        (is (= "Alice" (:value field)))
        (is (false? (:checked field)) "non-boolean values are not :checked")))
    (testing "empty-string text field — :checked is false"
      (let [{:keys [result]} (render-field {:values {:name ""}} :name)
            field (.. result -result -current)]
        (is (= "" (:value field)))
        (is (false? (:checked field)) "empty string is not :checked")))
    (testing "checkbox field — :value is the underlying boolean, :checked mirrors it"
      (let [result (renderHook #(let [f (form/use-form {:values {:terms true}})]
                                  (form/use-field f :terms {:checkbox? true})))
            field (.. result -result -current)]
        (is (contains? field :value))
        (is (contains? field :checked))
        (is (true? (:value field)))
        (is (true? (:checked field)))))
    ;; Pin `(true? value)` semantics: only an explicit boolean `true` checks.
    ;; A regression to `(boolean value)` would pass the empty-string case
    ;; above but would flip these on, so they guard the invariant.
    (testing "non-canonical truthy values are not :checked"
      (doseq [v ["true" 1 0 "false" [] {}]]
        (let [{:keys [result]} (render-field {:values {:terms v}} :terms)
              field (.. result -result -current)]
          (is (false? (:checked field))
              (str "value " (pr-str v) " must not be :checked")))))))

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

;;; use-form watch lifecycle under StrictMode

(deftest use-form-watchable-strict-mode-test
  (testing "watchable :values leaves exactly one watch after StrictMode mount, zero after unmount"
    (let [source  (cljs.core/atom {:name "init"})
          wrapper (fn [^js props]
                    (react/createElement react/StrictMode nil (.-children props)))
          result  (renderHook #(form/use-form {:values source})
                              #js {:wrapper wrapper})]
      ;; StrictMode double-invokes effect setup/cleanup; after mount settles
      ;; there must be exactly one live watch on the source.
      (is (= 1 (count (.-watches source)))
          "one live watch after StrictMode settles")
      (.unmount result)
      (is (zero? (count (.-watches source)))
          "watch removed on unmount"))))

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

(deftest sync-validation-errors-test
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
                     (done))))))))

(deftest sync-validation-valid-test
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
            fake-e      #js {:preventDefault (fn [])}
            p           (submit! fake-e)]
        ;; validation promise is still pending — validating? must be true
        (is (true? (:validating? (form-state handle))))
        ;; resolve the deferred — no errors
        (@deferred nil)
        (-> p
            (.then (fn []
                     (is (false? (:validating? (form-state handle))))
                     (.unmount result)
                     (done))))))))

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

(deftest submit-fn-sync-throw-test
  (testing "submit-fn that throws synchronously surfaces into :submit-error
            and clears :submitting? — the throw must not leak past run-submit!"
    (async done
      (let [err    (js/Error. "submit blew up sync")
            result (renderHook
                     #(form/use-form
                        {:values    {:x 1}
                         :on-submit (fn [_] (throw err))}))
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

(deftest submit-validator-sync-throw-test
  (testing "validator that throws synchronously is converted to a rejected
            submit promise — form-atom does not get stuck with :submitting? true"
    (async done
      (let [err    (js/Error. "sync validator blew up")
            result (renderHook
                     #(form/use-form
                        {:values    {:x 1}
                         :validate  (fn [_] (throw err))
                         :on-submit (fn [_] nil)}))
            handle  (.. result -result -current)
            submit! (form/on-submit handle)
            fake-e  #js {:preventDefault (fn [])}
            ;; submit! must not throw synchronously — it must return a promise
            ;; that resolves once the form-atom is back to a sane state.
            submit-promise (submit! fake-e)]
        (is (instance? js/Promise submit-promise))
        (-> submit-promise
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
                        (form/use-field f :name)))
            field  (.. result -result -current)]
        (-> (js/Promise.resolve
              (act #(try ((:onBlur field) nil) (catch :default _ nil))))
            (.then flush-microtasks)
            (.then (fn []
                     (is (false? (:validating? (form-state @handle-atom))))
                     (.unmount result)
                     (done))))))))

(deftest blur-validator-sync-throw-test
  (testing "blur validator that throws synchronously does not leak the throw
            into React's event handler; :validating? remains false"
    (async done
      (let [handle-atom (cljs.core/atom nil)
            result (renderHook
                     #(let [f (form/use-form
                                {:values      {:name ""}
                                 :validate    (fn [_]
                                                (throw (js/Error. "blur boom")))
                                 :validate-on :blur})]
                        (reset! handle-atom f)
                        (form/use-field f :name)))
            field  (.. result -result -current)
            outcome (cljs.core/atom nil)]
        (-> (js/Promise.resolve
              (act #(reset! outcome
                            (try ((:onBlur field) nil) ::ok
                                 (catch :default _ ::threw)))))
            (.then flush-microtasks)
            (.then (fn []
                     (is (= ::ok @outcome)
                         "sync throw from validator must not leak into onBlur")
                     (is (false? (:validating? (form-state @handle-atom))))
                     (.unmount result)
                     (done))))))))

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

(deftest submit-clears-prior-error-on-validation-failure-test
  (testing ":submit-error from a previous submit is cleared even when the
            next submit fails validation (errs path, not on-submit path)"
    (async done
      (let [step (cljs.core/atom 0)
            result (renderHook
                     #(form/use-form
                        {:values    {:x 1}
                         :validate  (fn [_]
                                      (when (> @step 1) {:x "bad"}))
                         :on-submit (fn [_]
                                      (swap! step inc)
                                      (js/Promise. (fn [_ reject]
                                                     (reject (js/Error. "boom")))))}))
            handle (.. result -result -current)
            submit! (form/on-submit handle)
            fake-e #js {:preventDefault (fn [])}]
        (-> (submit! fake-e)
            (.then (fn []
                     ;; First submit: on-submit rejects → :submit-error set
                     (is (some? (:submit-error (form-state handle))))
                     (swap! step inc) ;; arm validate to return errors
                     (submit! fake-e)))
            (.then (fn []
                     ;; Second submit: validate returns errors → :submit-error
                     ;; must be cleared at submit start, even though on-submit
                     ;; never runs this time.
                     (is (nil? (:submit-error (form-state handle))))
                     (is (= {:x "bad"} (:errors (form-state handle))))
                     (is (false? (:submitting? (form-state handle))))
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

;;; Direct mutators: set-values!, set-errors!, clear-errors!, touch-field!

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

(deftest set-errors-preserves-submit-error-test
  (testing "set-errors! must not clear :submit-error (clear-errors! is the way)"
    (let [result (render-form {:values {:a 1}})
          handle (.. result -result -current)
          err    (js/Error. "kaboom")]
      (swap! (form/form-atom handle) assoc :submit-error err)
      (act #(form/set-errors! handle {:a "bad"}))
      (let [state (form-state handle)]
        (is (= {:a "bad"} (:errors state)))
        (is (identical? err (:submit-error state))
            ":submit-error survives set-errors!")))))

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

(deftest touch-field-test
  (testing "touch-field! adds a single key to :touched"
    (let [result (render-form {:values {:a 1}})
          handle (.. result -result -current)]
      (is (false? (contains? (:touched (form-state handle)) :a)))
      (act #(form/touch-field! handle :a))
      (is (contains? (:touched (form-state handle)) :a))))

  (testing "touch-field! surfaces a previously-hidden error via use-field"
    (let [{:keys [result handle-atom]} (render-field {:values {:name ""}} :name)]
      ;; field starts untouched, so an error set directly on the atom is hidden
      (act #(form-set-error! @handle-atom :name "Required"))
      (is (nil? (:error (.. result -result -current))))
      ;; touching the field surfaces it
      (act #(form/touch-field! @handle-atom :name))
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
      (is (= "bob@example.com" (form-value handle :email)))))

  (testing "watch on values atom is removed on unmount"
    (let [values-atom (cljs.core/atom {:x 1})
          before      (count (.-watches values-atom))
          result      (render-form {:values values-atom})]
      (is (= (inc before) (count (.-watches values-atom)))
          "use-form registered a watch on the values atom")
      (.unmount result)
      (is (= before (count (.-watches values-atom)))
          "watch was removed when the hook unmounted"))))

;;; Cross-field (interdependent) validation
;;; Counterpart to blur-validation-scoped-test: submit runs the validator over
;;; the whole values map, so a validator whose verdict depends on more than one
;;; field surfaces its error — unlike :blur, which only writes the blurred
;;; field's slice (see form.cljs :validate-on contract).

(deftest submit-cross-field-validation-mismatch-test
  (testing "submit blocks and writes a dependent-field error when fields disagree"
    (async done
      (let [submitted (cljs.core/atom false)
            result    (render-form
                        {:values    {:password "secret" :confirm "typo"}
                         :validate  (fn [{:keys [password confirm]}]
                                      (when (not= password confirm)
                                        {:confirm "Passwords do not match"}))
                         :on-submit (fn [_] (reset! submitted true))})
            handle    (.. result -result -current)
            submit!   (form/on-submit handle)
            fake-e    #js {:preventDefault (fn [])}]
        (-> (submit! fake-e)
            (.then (fn []
                     (is (false? @submitted) "cross-field mismatch blocks submit")
                     (is (= "Passwords do not match" (form-error handle :confirm))
                         "the dependent field's error is written on submit")
                     (.unmount result)
                     (done))))))))

(deftest submit-cross-field-validation-match-test
  (testing "submit proceeds once the interdependent fields agree"
    (async done
      (let [submitted (cljs.core/atom false)
            result    (render-form
                        {:values    {:password "secret" :confirm "secret"}
                         :validate  (fn [{:keys [password confirm]}]
                                      (when (not= password confirm)
                                        {:confirm "Passwords do not match"}))
                         :on-submit (fn [_] (reset! submitted true))})
            handle    (.. result -result -current)
            submit!   (form/on-submit handle)
            fake-e    #js {:preventDefault (fn [])}]
        (-> (submit! fake-e)
            (.then (fn []
                     (is (true? @submitted) "matching fields pass validation")
                     (is (nil? (form-error handle :confirm)))
                     (.unmount result)
                     (done))))))))

(deftest blur-cross-field-scoped-test
  (testing "on :blur the validator runs over all values but only the blurred field's
            error slice is written, so a cross-field error keyed on another field
            surfaces only when THAT field blurs"
    (async done
      (let [handle-atom (cljs.core/atom nil)
            result (renderHook
                     #(let [f (form/use-form
                                {:values      {:password "secret" :confirm "typo"}
                                 :validate    (fn [{:keys [password confirm]}]
                                                (when (not= password confirm)
                                                  {:confirm "Passwords do not match"}))
                                 :validate-on :blur})]
                        (reset! handle-atom f)
                        {:pw (form/use-field f :password)
                         :cf (form/use-field f :confirm)}))]
        ;; Blur :password — validator returns a :confirm error, but only
        ;; :password's slice is written, so :confirm's error stays hidden.
        (-> (js/Promise.resolve (act #((:onBlur (:pw (.. result -result -current))) nil)))
            (.then flush-microtasks)
            (.then (fn []
                     (is (nil? (form-error @handle-atom :confirm))
                         "cross-field error not written when an unrelated field blurs")
                     (is (nil? (:error (:cf (.. result -result -current))))
                         "and use-field :confirm shows no error (untouched)")
                     ;; Blur :confirm — now its slice is written and it's touched.
                     (-> (js/Promise.resolve (act #((:onBlur (:cf (.. result -result -current))) nil)))
                         (.then flush-microtasks)
                         (.then (fn []
                                  (is (= "Passwords do not match" (form-error @handle-atom :confirm))
                                      "blurring the keyed field surfaces its cross-field error")
                                  (is (= "Passwords do not match"
                                         (:error (:cf (.. result -result -current)))))
                                  (.unmount result)
                                  (done)))))))))))

;;; reset-form! while a submit is in flight
;;; A reset mid-submit must win: the in-flight submit's late completion must NOT
;;; resurrect :submitting?/:submitted? on the freshly-reset state. Guarded by the
;;; submit-id token in run-submit!.

(deftest reset-during-in-flight-submit-test
  (testing "reset-form! during an in-flight submit wins — the late completion does not clobber it"
    (async done
      (let [result  (render-form
                      {:values    {:name "Alice"}
                       :on-submit (fn [_]
                                    (js/Promise.
                                      (fn [resolve _]
                                        (js/setTimeout (fn [] (resolve nil)) 30))))})
            handle  (.. result -result -current)
            submit! (form/on-submit handle)
            fake-e  #js {:preventDefault (fn [])}
            p       (submit! fake-e)]
        (is (true? (:submitting? (form-state handle)))
            "submit is in flight before the promise settles")
        ;; Reset while the submit promise is still pending.
        (act #(form/reset-form! handle {:name "Carol"}))
        (-> p
            (.then (fn []
                     (let [s (form-state handle)]
                       (is (false? (:submitting? s))
                           "form is not stuck :submitting? after reset + settle")
                       (is (false? (:submitted? s))
                           "the reset wins: a late success does NOT mark the reset form :submitted?")
                       (is (= "Carol" (form-value handle :name))
                           "reset values are applied"))
                     ;; The form is still usable: a fresh submit after the reset
                     ;; completes normally (new submit-id, not blocked).
                     (let [p2 (submit! fake-e)]
                       (is (true? (:submitting? (form-state handle)))
                           "a new submit after reset starts cleanly")
                       (-> p2
                           (.then (fn []
                                    (is (true? (:submitted? (form-state handle)))
                                        "the post-reset submit completes and marks :submitted?")
                                    (.unmount result)
                                    (done))))))))))))
