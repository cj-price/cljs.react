(ns cljs.react.form
  "Form state with per-field subscriptions: `use-form` owns the atom, each
  `use-field` subscribes only to its own slice, and submit flow handles sync
  + async validators, concurrent submits, and rejection safely.

  Re-exported from `cljs.react.core`; prefer that namespace in consumer code."
  (:require
   [cljs.react.hook :as hook]))

(deftype ^:no-doc FormHandle [form-atom opts-ref handler-cache])

(defn- make-state [values]
  {:values       values
   :errors       {}
   :dirty        #{}
   :touched      #{}
   :validating?  false
   :submitting?  false
   :submitted?   false
   :submit-error nil})

(defn form-atom
  "Return the mutable form-state atom for direct inspection/mutation (testing, devtools)."
  [^FormHandle h]
  (.-form-atom h))

(defn form-opts
  "Return the current :use-form opts map (always fresh)."
  [^FormHandle h]
  @(.-opts-ref h))

(defn form-state
  "Return the current form state snapshot (equivalent to @(form-atom h))."
  [^FormHandle h]
  @(.-form-atom h))

(defn- current-initial-values [^FormHandle h]
  (let [values (:values @(.-opts-ref h))]
    (if (satisfies? IDeref values) @values values)))

(defn reset-form!
  "Reset the form to its initial values, clearing errors, dirty/touched flags,
  and submit state. With a 2-arity call, reset to the supplied values instead."
  ([^FormHandle h]
   (reset! (.-form-atom h) (make-state (current-initial-values h))))
  ([^FormHandle h values]
   (reset! (.-form-atom h) (make-state values))))

(defn set-values!
  "Replace the form's `:values` map. Does not touch errors or flags."
  [^FormHandle h values]
  (swap! (.-form-atom h) assoc :values values))

(defn set-errors!
  "Replace the form's `:errors` map. Marks every keyed field as touched so the
  errors are visible to `use-field` consumers."
  [^FormHandle h errors]
  (swap! (.-form-atom h)
         (fn [s]
           (-> s
               (assoc :errors errors)
               (update :touched into (keys errors))))))

(defn clear-errors!
  "Clear all field errors and any `:submit-error`."
  [^FormHandle h]
  (swap! (.-form-atom h) assoc :errors {} :submit-error nil))

(defn set-field-touched!
  "Mark a field as touched so its error becomes visible to `use-field`."
  [^FormHandle h field-key]
  (swap! (.-form-atom h) update :touched conj field-key))

(defn- field-snap [state field-key]
  {:value (get-in state [:values field-key])
   :error (when (contains? (:touched state) field-key)
            (get-in state [:errors field-key]))
   :dirty (contains? (:dirty state) field-key)})

(defn- touched-error-changed? [old-s new-s field-key]
  (let [t-old (contains? (:touched old-s) field-key)
        t-new (contains? (:touched new-s) field-key)]
    (or (not= t-old t-new)
        (and t-new
             (not= (get-in old-s [:errors field-key])
                   (get-in new-s [:errors field-key]))))))

(defn- field-diff? [old-s new-s field-key]
  (or (not= (get-in old-s [:values field-key])
            (get-in new-s [:values field-key]))
      (not= (contains? (:dirty old-s) field-key)
            (contains? (:dirty new-s) field-key))
      (touched-error-changed? old-s new-s field-key)))

(defn- build-handlers [^FormHandle handle field-key checkbox?]
  (let [extract-fn (if checkbox?
                     #(.. % -target -checked)
                     #(.. % -target -value))
        form-atom (.-form-atom handle)
        opts-ref  (.-opts-ref handle)
        on-change (fn [^js e]
                    (let [val (extract-fn e)]
                      (swap! form-atom
                             (fn [s]
                               (assoc s
                                      :values  (assoc (:values s) field-key val)
                                      :dirty   (conj (:dirty s) field-key)
                                      :touched (conj (:touched s) field-key))))))
        on-blur   (fn [_e]
                    (swap! form-atom update :touched conj field-key)
                    (when (= :blur (:validate-on @opts-ref))
                      (let [validate (:validate @opts-ref)
                            v        (:values @form-atom)]
                        (when validate
                          (let [result (validate v)]
                            (when (instance? js/Promise result)
                              (swap! form-atom assoc :validating? true))
                            (-> (js/Promise.resolve result)
                                (.then (fn [errs]
                                         (swap! form-atom
                                                (fn [s]
                                                  (-> s
                                                      (assoc :validating? false)
                                                      (assoc-in [:errors field-key]
                                                                (get errs field-key)))))))
                                (.catch (fn [_err]
                                          ;; Blur validation is fire-and-forget;
                                          ;; a rejected validator just clears the
                                          ;; in-flight flag without leaking an
                                          ;; unhandled rejection to the runtime.
                                          (swap! form-atom assoc :validating? false)))))))))]
    #js {:onChange on-change :onBlur on-blur}))

(defn- ensure-handlers!
  "Value-keyed cache of {field-key, checkbox?} → #js {:onChange :onBlur}.
  The cache lives in a CLJS atom on the FormHandle rather than a mutable JS
  object, so it reads like every other piece of form state."
  [^FormHandle handle field-key checkbox?]
  (let [cache-atom (.-handler-cache handle)
        cache-key  [field-key (boolean checkbox?)]]
    (or (get @cache-atom cache-key)
        (let [handlers (build-handlers handle field-key checkbox?)]
          (swap! cache-atom assoc cache-key handlers)
          handlers))))

;;;; Hooks

(defn use-form
  "Create a form handle.

  opts map:
    :values      - initial values map, or a watchable (atom/cursor) for reactive defaults
    :validate    - fn(values) -> errors-map or Promise<errors-map>
    :on-submit   - fn(values) -> nil or Promise
    :validate-on - :blur to also validate on blur (default: submit only)

  When :values is a plain map, it is captured once on first render — later
  changes to the same map key (e.g. props re-rendering with a new :values)
  do NOT reset the form. Pass an atom/cursor if you need reactive defaults;
  un-dirtied fields will then follow changes to the watchable.

  When :on-submit rejects, the error is stored at :submit-error (observable
  via use-form-meta) and :submitting? returns to false. The error is cleared
  at the start of the next submit."
  [opts]
  (let [values     (:values opts)
        initial    (if (satisfies? IDeref values) @values values)
        handle-ref (hook/use-ref nil)]
    ;; Initialize once
    (when (nil? @handle-ref)
      (reset! handle-ref
              (FormHandle. (atom (make-state initial))
                           (atom opts)
                           (atom {}))))
    ;; Keep opts-ref current every render — skip the reset! when opts is stable
    ;; so unchanged-render paths avoid an atom write + watch fan-out.
    (let [opts-ref (.-opts-ref ^FormHandle @handle-ref)]
      (when-not (identical? @opts-ref opts)
        (reset! opts-ref opts)))
    ;; Reactive values: watch if :values is watchable
    (hook/use-effect
      (fn []
        (if (satisfies? IWatchable values)
          (let [form-atom (.-form-atom ^FormHandle @handle-ref)
                key       (gensym "form-values")]
            (add-watch values key
              (fn [_ _ _ new-vals]
                (swap! form-atom
                       (fn [s]
                         (let [dirty (:dirty s)]
                           (update s :values
                                   (fn [current]
                                     (reduce-kv
                                       (fn [acc k v]
                                         (if (contains? dirty k) acc (assoc acc k v)))
                                       current
                                       new-vals))))))))
            #(remove-watch values key))
          js/undefined))
      [values])
    @handle-ref))

(defn- use-field*
  "Internal: shared logic for text and checkbox fields."
  [^FormHandle handle field-key {:keys [checkbox?]}]
  (let [snap (hook/use-selector (.-form-atom handle)
                           (fn [o n] (field-diff? o n field-key))
                           (fn [s] (field-snap s field-key))
                           [field-key])
        ^js handlers (ensure-handlers! handle field-key checkbox?)]
    (if checkbox?
      {:checked  (boolean (:value snap))
       :error    (:error snap)
       :dirty    (:dirty snap)
       :onChange (.-onChange handlers)
       :onBlur   (.-onBlur handlers)}
      {:value    (:value snap)
       :error    (:error snap)
       :dirty    (:dirty snap)
       :onChange (.-onChange handlers)
       :onBlur   (.-onBlur handlers)})))

(defn use-field
  "Subscribe to a single field. Returns {:value :error :onChange :onBlur}.
  Only re-renders when this specific field's value or error changes.

  opts map (optional):
    :type - :checkbox for checkbox fields (reads e.target.checked, returns :checked key)

  Radio groups: there is no dedicated :type :radio — use the field as a string
  and set each input's :checked to (= field-value option) and :value to option,
  e.g. (Element {:tag \"input\" :type \"radio\" :name \"color\" :value \"red\"
                :checked (= (:value field) \"red\") :onChange (:onChange field)})."
  ([^FormHandle handle field-key]
   (use-field* handle field-key nil))
  ([^FormHandle handle field-key opts]
   (use-field* handle field-key {:checkbox? (= :checkbox (:type opts))})))

(defn- meta-diff? [old new]
  (or (not= (:errors old) (:errors new))
      (not= (:validating? old) (:validating? new))
      (not= (:submitting? old) (:submitting? new))
      (not= (:submitted? old) (:submitted? new))
      (not= (:submit-error old) (:submit-error new))))

(defn- meta-select [s]
  {:validating?  (:validating? s)
   :submitting?  (:submitting? s)
   :submitted?   (:submitted? s)
   :errors       (:errors s)
   :submit-error (:submit-error s)})

(defn use-form-meta
  "Subscribe to form meta state (everything except :values).
  Returns {:validating? :submitting? :submitted? :errors :submit-error}.
  Re-renders only when meta state changes."
  [^FormHandle handle]
  (hook/use-selector (.-form-atom handle) meta-diff? meta-select []))

;;;; Submit

(defn- run-submit! [^FormHandle handle submit-fn]
  ;; Atomically claim the in-flight slot by flipping :submitting? to true.
  ;; If :submitting? was already true we bail out without touching state —
  ;; this protects against double-click races where the second call arrives
  ;; before the first has written any state to the atom.
  (let [form-atom     (.-form-atom handle)
        [old-s new-s] (swap-vals! form-atom
                        (fn [s]
                          (if (:submitting? s)
                            s
                            (-> s
                                (update :touched into (keys (:values s)))
                                (assoc :submit-error nil
                                       :submitting?  true
                                       :errors       {})))))]
    (if (identical? old-s new-s)
      (js/Promise.resolve nil)
      (let [v        (:values new-s)
            validate (:validate @(.-opts-ref handle))
            vresult  (when validate (validate v))
            _        (when (instance? js/Promise vresult)
                       (swap! form-atom assoc :validating? true))]
        (-> (js/Promise.resolve vresult)
            (.then (fn [errs]
                     (swap! form-atom assoc :validating? false)
                     (if (and errs (pos? (count errs)))
                       (swap! form-atom assoc
                              :errors      errs
                              :submitting? false)
                       (-> (js/Promise.resolve (when submit-fn (submit-fn v)))
                           (.then (fn [_]
                                    (swap! form-atom assoc
                                           :submitting? false
                                           :submitted?  true)))
                           (.catch (fn [err]
                                     (swap! form-atom assoc
                                            :submitting?  false
                                            :submit-error err)))))))
            (.catch (fn [err]
                      (swap! form-atom assoc
                             :validating?  false
                             :submitting?  false
                             :submit-error err))))))))

(defn on-submit
  "Returns an onSubmit event handler.

  1-arity: uses :on-submit from opts (read at event time, always fresh).
  2-arity: override with a specific submit fn."
  ([^FormHandle handle]
   (fn [^js e]
     (.preventDefault e)
     (run-submit! handle (:on-submit @(.-opts-ref handle)))))
  ([^FormHandle handle submit-fn]
   (fn [^js e]
     (.preventDefault e)
     (run-submit! handle submit-fn))))
