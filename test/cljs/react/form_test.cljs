(ns cljs.react.form-test
  (:require
   [cljs.test :refer [deftest testing is async]]
   [cljs.react.form :as form]
   ["react" :as react]
   ["global-jsdom/register"]
   ["@testing-library/react" :refer [renderHook act render cleanup]]))

;;; Helpers

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
      (is (= {:name "" :email ""} (form/values handle)))))

  (testing "initial errors are empty"
    (let [result (render-form {:values {:x 1}})
          handle (.. result -result -current)]
      (is (= {} (form/errors handle)))))

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
              (form/set-error! @handle-atom :name "Required")
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
      (act #(swap! (form/form-atom @handle-holder)
                   assoc-in [:values :a] "hello"))
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
        (-> (js/Promise.resolve (act #(form/set-error! @handle-atom :b "B is bad")))
            (.then (fn []
                     (is (= "B is bad" (form/error @handle-atom :b)))
                     (let [field (.. result -result -current)]
                       (-> (js/Promise.resolve (act #((:onBlur field) nil)))
                           (.then flush-microtasks)
                           (.then (fn []
                                    (is (= "B is bad" (form/error @handle-atom :b)))
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
        (-> (js/Promise.resolve (act #(form/set-error! @handle-atom :name "was bad")))
            (.then (fn []
                     (is (= "was bad" (form/error @handle-atom :name)))
                     (let [field (.. result -result -current)]
                       (-> (js/Promise.resolve (act #((:onBlur field) nil)))
                           (.then flush-microtasks)
                           (.then (fn []
                                    (is (nil? (form/error @handle-atom :name)))
                                    (.unmount result)
                                    (done))))))))))))

;;; use-form-meta

(deftest use-form-meta-test
  (testing "initial meta state"
    (let [result (renderHook #(let [f (form/use-form {:values {:x 1}})]
                                (form/use-form-meta f)))
          meta   (.. result -result -current)]
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
                     (is (= "Required" (form/error handle :name)))
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
                     (is (= "Already taken" (form/error handle :user)))
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
                     (is (false? (:submitting? @(form/form-atom handle))))
                     (is (true? (:submitted? @(form/form-atom handle))))
                     (done))))))))

;;; reset-form!

(deftest reset-form-test
  (testing "reset-form! clears values and errors"
    (let [result (render-form {:values {:name "Alice"}})
          handle (.. result -result -current)]
      (form/set-value! handle :name "Bob")
      (form/set-error! handle :name "Bad")
      (is (= "Bob" (form/value handle :name)))
      (act #(form/reset-form! handle))
      (is (= "Alice" (form/value handle :name)))
      (is (= {} (form/errors handle)))))

  (testing "reset-form! with new values"
    (let [result (render-form {:values {:name ""}})
          handle (.. result -result -current)]
      (act #(form/reset-form! handle {:name "Carol"}))
      (is (= "Carol" (form/value handle :name))))))

;;; Reactive defaults

(deftest reactive-defaults-test
  (testing "un-dirtied fields update when values atom changes"
    (let [values-atom (cljs.core/atom {:name "Alice" :email ""})
          result      (render-form {:values values-atom})
          handle      (.. result -result -current)]
      (is (= "Alice" (form/value handle :name)))
      ;; Update atom - un-dirtied field should update
      (act #(reset! values-atom {:name "Bob" :email "bob@example.com"}))
      (.rerender result)
      (is (= "Bob" (form/value handle :name)))))

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
      (is (= "Custom" (form/value handle :name)))
      ;; Un-dirtied field should update
      (is (= "bob@example.com" (form/value handle :email))))))

;;; Field component

(deftest field-component-test
  (testing "Field calls :render with field props including :dirty"
    (let [captured (cljs.core/atom nil)]
      (render
        (react/createElement
          (fn []
            (let [f (form/use-form {:values {:name "Alice"}})]
              (form/Field {:control f
                           :name    :name
                           :render  (fn [fp]
                                      (reset! captured fp)
                                      nil)})))))
      (is (= "Alice" (:value @captured)))
      (is (false? (:dirty @captured)))
      (is (fn? (:onChange @captured)))
      (is (fn? (:onBlur @captured)))))

  (testing "Field with :type :checkbox returns :checked key"
    (let [captured (cljs.core/atom nil)]
      (render
        (react/createElement
          (fn []
            (let [f (form/use-form {:values {:terms false}})]
              (form/Field {:control f
                           :name    :terms
                           :type    :checkbox
                           :render  (fn [fp]
                                      (reset! captured fp)
                                      nil)})))))
      (is (false? (:checked @captured)))
      (is (nil? (:value @captured)))
      (is (fn? (:onChange @captured))))))
