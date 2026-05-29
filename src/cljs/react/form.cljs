(ns cljs.react.form
  "Form state with per-field subscriptions: `use-form` owns the atom, each
  `use-field` subscribes only to its own slice, and submit flow handles sync
  + async validators, concurrent submits, and rejection safely.

  Re-exported from `cljs.react.core`; prefer that namespace in consumer code."
  (:require
   [cljs.react.hook :as hook]))

(deftype ^:no-doc FormHandle [form-atom opts-ref])

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
  "Return the raw form-state atom for direct inspection/mutation.

  Dereferencing yields a snapshot map (this shape is part of the public
  contract):
    :values        — current values map
    :errors        — map of field-key → error
    :dirty         — set of field-keys the user has changed
    :touched       — set of field-keys that have been blurred or submitted
    :validating?   — true while an async validator is in flight
    :submitting?   — true while a submit is in flight
    :submitted?    — true after a submit completes successfully
    :submit-error  — error from the most recent failed submit, or nil

  Advanced — prefer `use-form-meta` / `use-field` for reactive reads."
  [^FormHandle h]
  (.-form-atom h))

(defn form-opts
  "Return the current `use-form` opts map. Reads through to the handle's
  internal ref so callers always see the latest opts, even mid-render."
  [^FormHandle h]
  @(.-opts-ref h))

(defn- unwrap-values
  "Resolve a :values opt to a plain map. Accepts a map or an IDeref (atom/cursor)."
  [values]
  (if (satisfies? IDeref values) @values values))

(defn- current-initial-values [^FormHandle h]
  (unwrap-values (:values @(.-opts-ref h))))

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
  "Replace the form's `:errors` map. Field-keys with **non-nil** error values
  are also added to `:touched` so the errors are surfaced by `use-field`
  without the user having to blur each field first. Nil-valued keys
  (e.g. `{:email nil}` to clear that error specifically) are not touched —
  touched-ness tracks user interaction, not error presence."
  [^FormHandle h errors]
  (swap! (.-form-atom h)
         (fn [s]
           (-> s
               (assoc :errors errors)
               (update :touched into
                       (keep (fn [[k v]] (when (some? v) k)) errors))))))

(defn clear-errors!
  "Clear all field errors and any `:submit-error`."
  [^FormHandle h]
  (swap! (.-form-atom h) assoc :errors {} :submit-error nil))

(defn touch-field!
  "Mark a field as touched so its error becomes visible to `use-field`."
  [^FormHandle h field-key]
  (swap! (.-form-atom h) update :touched conj field-key))

(defn- call-validator
  "Run validate(values), funneling any sync throw into a rejected promise so
  both blur and submit paths can rely on a single .catch. Returns nil when
  validate is nil — no try frame opened on the no-validator path."
  [validate v]
  (when validate
    (try (validate v)
         (catch :default e (js/Promise.reject e)))))

(defn- field-snap [state field-key]
  {:value (get (:values state) field-key)
   :error (when (contains? (:touched state) field-key)
            (get (:errors state) field-key))
   :dirty (contains? (:dirty state) field-key)})

(defn- touched-error-changed? [old-s new-s field-key]
  (let [old-touched (:touched old-s)
        new-touched (:touched new-s)
        same-touched? (identical? old-touched new-touched)
        t-old (contains? old-touched field-key)
        t-new (contains? new-touched field-key)]
    (or (and (not same-touched?) (not= t-old t-new))
        (and t-new
             (let [old-errors (:errors old-s)
                   new-errors (:errors new-s)]
               (and (not (identical? old-errors new-errors))
                    (not= (get old-errors field-key)
                          (get new-errors field-key))))))))

(defn- field-diff? [old-s new-s field-key]
  ;; Fast path: same state identity → nothing changed for this field either.
  ;; Watchers can fire with identical states under some test/devtool flows.
  (and (not (identical? old-s new-s))
       (let [old-values (:values old-s)
             new-values (:values new-s)
             old-dirty  (:dirty old-s)
             new-dirty  (:dirty new-s)]
         ;; Per-submap identity short-circuits the cheap-but-not-free `get` /
         ;; `contains?` work — a swap! that only touches :submitting? leaves
         ;; :values / :dirty / :errors / :touched referentially identical.
         (or (and (not (identical? old-values new-values))
                  (not= (get old-values field-key)
                        (get new-values field-key)))
             (and (not (identical? old-dirty new-dirty))
                  (not= (contains? old-dirty field-key)
                        (contains? new-dirty field-key)))
             (touched-error-changed? old-s new-s field-key)))))

(defn- build-handlers [^FormHandle handle field-key checkbox?]
  (let [extract-fn (if checkbox?
                     #(.. % -target -checked)
                     #(.. % -target -value))
        form-atom (.-form-atom handle)
        opts-ref  (.-opts-ref handle)
        ;; onChange marks the field :dirty but NOT :touched — touched tracks
        ;; user interaction in the "has been blurred or submitted" sense (see
        ;; the form-atom contract), which is what gates error visibility. A
        ;; field the user is still typing into should not flash its error.
        on-change (fn [^js e]
                    (let [val (extract-fn e)]
                      (swap! form-atom
                             (fn [s]
                               (assoc s
                                      :values (assoc (:values s) field-key val)
                                      :dirty  (conj (:dirty s) field-key))))))
        on-blur   (fn [_e]
                    (swap! form-atom update :touched conj field-key)
                    (when (= :blur (:validate-on @opts-ref))
                      (when-let [validate (:validate @opts-ref)]
                        (let [result (call-validator validate (:values @form-atom))]
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
                              ;; Rejected validator (sync or async) just clears
                              ;; :validating? — blur is not the place to surface
                              ;; validator bugs; submit will.
                              (.catch (fn [_err]
                                        (swap! form-atom assoc :validating? false))))))))]
    #js {:onChange on-change :onBlur on-blur}))

;;;; Hooks

(defn use-form
  "Create a form handle owning the form-state atom. Pass the handle to
  `use-field` / `use-form-meta` to subscribe to slices, and to `on-submit`
  to build the DOM submit handler.

  opts map:
    :values      - initial values map, or a watchable (atom/cursor) for reactive defaults
    :validate    - fn(values) -> errors-map or Promise<errors-map>
    :on-submit   - fn(values) -> nil or Promise
    :validate-on - nil | :submit | :blur. :submit (the default) validates only
                   when the form is submitted; :blur additionally re-validates
                   when a field blurs. On :blur, the validator runs over all
                   values but only the blurred field's :errors entry is written
                   back — other fields keep their existing errors untouched. A
                   cross-field error (e.g. password-confirmation keyed on a
                   different field) therefore surfaces when that field blurs or
                   on submit, not from blurring an unrelated field.

  When :values is a plain map, it is captured once on first render — later
  changes to the same map key (e.g. props re-rendering with a new :values)
  do NOT reset the form. Pass an atom/cursor if you need reactive defaults;
  un-dirtied fields will then follow changes to the watchable.

  Validator and submit failures (both sync throws and async rejections) are
  funneled into :submit-error and reset :submitting? / :validating? to false.
  :submit-error is cleared at the start of each new submit.

  Throws ex-info :type :cljs.react.form/invalid-validate-on if :validate-on
  is anything other than nil, :submit, or :blur."
  [opts]
  (let [validate-on (:validate-on opts)]
    ;; Validate opts BEFORE any hook call — throwing after a hook would corrupt
    ;; React's hook order for the next render. Bad config crashes the component
    ;; before it registers any state, which is the safe behaviour.
    (when-not (or (nil? validate-on) (= :blur validate-on) (= :submit validate-on))
      (throw (ex-info (str "use-form: :validate-on must be nil, :submit, or :blur (got "
                           (pr-str validate-on) ")")
                      {:type ::invalid-validate-on :got validate-on}))))
  (let [values     (:values opts)
        handle-ref (hook/use-ref nil)]
    ;; Initialize once. `initial` is computed inside the guard so the
    ;; `@values` deref of a watchable doesn't run on every render.
    (when (nil? @handle-ref)
      (let [initial (unwrap-values values)]
        (reset! handle-ref
                (FormHandle. (atom (make-state initial))
                             (atom opts)))))
    ;; Keep opts-ref current every render — skip the reset! when opts is stable
    ;; so unchanged-render paths avoid an atom write + watch fan-out.
    (let [opts-ref (.-opts-ref ^FormHandle @handle-ref)]
      (when-not (identical? @opts-ref opts)
        (reset! opts-ref opts)))
    ;; Reactive values: watch if :values is watchable.
    ;; Deps collapse to `nil` when :values is a plain map so re-renders with a
    ;; freshly-allocated map literal don't churn cljs-deps structural compare
    ;; or fire the effect cleanup/setup pair.
    (let [watchable-values (when (satisfies? IWatchable values) values)]
      (hook/use-effect
        (fn []
          (if watchable-values
            (let [form-atom (.-form-atom ^FormHandle @handle-ref)
                  ;; Fresh JS object per effect setup — identity-keyed, no
                  ;; gensym global-counter touch / symbol allocation. Same
                  ;; pattern as use-selector's subscribe key.
                  key       #js {}]
              (add-watch watchable-values key
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
              #(remove-watch watchable-values key))
            js/undefined))
        [watchable-values]))
    @handle-ref))

(defn- use-field*
  "Internal: shared logic for text and checkbox fields. Always returns both
  :value and :checked so callers can destructure uniformly regardless of
  field type."
  [^FormHandle handle field-key {:keys [checkbox?]}]
  (let [snap (hook/use-selector (.-form-atom handle)
                           (fn [o n] (field-diff? o n field-key))
                           (fn [s] (field-snap s field-key))
                           [field-key])
        cb?  (boolean checkbox?)
        ;; Memoize per consumer — handlers stay identical across renders of
        ;; the same component, and are garbage-collected when the component
        ;; unmounts. Avoids the per-FormHandle cache that would otherwise
        ;; leak entries for every dynamic field-key (e.g. [:items 0 :name]).
        ^js handlers (hook/use-memo
                       (fn [] (build-handlers handle field-key cb?))
                       [handle field-key cb?])
        value (:value snap)]
    {:value    value
     ;; `true?` (not `boolean`) so :checked is only true for an explicit `true`
     ;; value. Empty strings — truthy in CLJS, unlike host JS — would otherwise
     ;; read as checked. Same goes for `"true"`, `1`, and other non-canonical
     ;; truthy values: they don't get coerced to a checked state.
     :checked  (true? value)
     :error    (:error snap)
     :dirty    (:dirty snap)
     :onChange (.-onChange handlers)
     :onBlur   (.-onBlur handlers)}))

(defn use-field
  "Subscribe to a single field. Returns a map with:
    :value    — current value (use for text/select inputs)
    :checked  — true only when :value is the literal boolean `true`
                (use for checkbox inputs)
    :error    — error string, or nil while the field is untouched
    :dirty    — boolean: has the user changed this field since reset?
    :onChange — DOM change handler (extracts e.target.value / .checked)
    :onBlur   — DOM blur handler (marks the field touched)

  Both :value and :checked are always present so callers can destructure
  uniformly; only one is meaningful per field type. Re-renders only when this
  specific field's value, error, dirty, or touched state changes.

  opts map (optional):
    :checkbox? - true for checkbox fields. Switches :onChange to read
                 e.target.checked instead of e.target.value.

  Radio groups: no dedicated mode — use the field as a string and set each
  input's :checked to (= field-value option) and :value to option, e.g.
  (Element {:tag \"input\" :type \"radio\" :name \"color\" :value \"red\"
            :checked (= (:value field) \"red\") :onChange (:onChange field)})."
  ([^FormHandle handle field-key]
   (use-field* handle field-key nil))
  ([^FormHandle handle field-key opts]
   (use-field* handle field-key {:checkbox? (boolean (:checkbox? opts))})))

;; Single source of truth for the meta-state shape — adding a key here
;; updates both the diff predicate and the projected snapshot.
(def ^:private meta-keys [:validating? :submitting? :submitted? :errors :submit-error])

(defn- meta-diff? [old new]
  (boolean (some #(not= (% old) (% new)) meta-keys)))

(defn- meta-select [s] (select-keys s meta-keys))

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
            vresult  (call-validator validate v)
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
  2-arity: override with a specific submit fn.

  Both arities call `.preventDefault` on the event, run validation, and then
  invoke the submit fn with the current :values. While a submit is in flight
  (:submitting? true), further submits are ignored — clicking the submit
  button twice will not run on-submit twice."
  ([^FormHandle handle]
   (fn [^js e]
     (.preventDefault e)
     (run-submit! handle (:on-submit @(.-opts-ref handle)))))
  ([^FormHandle handle submit-fn]
   (fn [^js e]
     (.preventDefault e)
     (run-submit! handle submit-fn))))
