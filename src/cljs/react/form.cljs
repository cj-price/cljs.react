(ns cljs.react.form
  (:require
   [goog.object :as gobj]
   [cljs.react.hook :as hook]
   [cljs.react.component])
  (:require-macros [cljs.react.core :refer [defnc]]))

(def ^:private new-atom cljs.core/atom)

(deftype FormHandle [form-atom opts-ref handler-cache])

(defn- make-state [values]
  {:values      values
   :errors      {}
   :dirty       #{}
   :touched     #{}
   :submitting? false
   :submitted?  false})

(defn- field-snap [state field-key]
  {:value (get-in state [:values field-key])
   :error (when (contains? (:touched state) field-key)
            (get-in state [:errors field-key]))
   :dirty (contains? (:dirty state) field-key)})

(defn- field-diff? [old-s new-s field-key]
  (or (not= (get-in old-s [:values field-key])
            (get-in new-s [:values field-key]))
      (not= (contains? (:dirty old-s) field-key)
            (contains? (:dirty new-s) field-key))
      (let [t-old (contains? (:touched old-s) field-key)
            t-new (contains? (:touched new-s) field-key)]
        (or (not= t-old t-new)
            (and t-new
                 (not= (get-in old-s [:errors field-key])
                       (get-in new-s [:errors field-key])))))))

(defn- ensure-handlers! [^FormHandle handle field-key checkbox?]
  (let [cache (.-handler-cache handle)
        kname (str (if checkbox? "checkbox" "text") "/" (name field-key))]
    (or (gobj/get cache kname)
        (let [extract-fn (if checkbox?
                           #(.. % -target -checked)
                           #(.. % -target -value))
              form-atom (.-form-atom handle)
              opts-ref  (.-opts-ref handle)
              on-change (fn [^js e]
                          (let [val (extract-fn e)]
                            (swap! form-atom
                                   (fn [s]
                                     (-> s
                                         (assoc-in [:values field-key] val)
                                         (update :dirty conj field-key)
                                         (update :touched conj field-key))))))
              on-blur   (fn [_e]
                          (swap! form-atom update :touched conj field-key)
                          (when (= :blur (:validate-on @opts-ref))
                            (let [validate (:validate @opts-ref)
                                  v (:values @form-atom)]
                              (when validate
                                (-> (js/Promise.resolve (validate v))
                                    (.then (fn [errs]
                                             (swap! form-atom assoc-in [:errors field-key] (get errs field-key)))))))))
              handlers  #js {:onChange on-change :onBlur on-blur}]
          (gobj/set cache kname handlers)
          handlers))))

;;;; Hooks

(defn use-form
  "Create a form handle.

  opts map:
    :values      - initial values map, or a watchable (atom/cursor) for reactive defaults
    :validate    - fn(values) -> errors-map or Promise<errors-map>
    :on-submit   - fn(values) -> nil or Promise
    :validate-on - :blur to also validate on blur (default: submit only)

  When :values is a watchable, un-dirtied fields are kept in sync with it."
  [opts]
  (let [values     (:values opts)
        initial    (if (satisfies? IDeref values) @values values)
        handle-ref (hook/use-ref nil)]
    ;; Initialize once
    (when (nil? @handle-ref)
      (reset! handle-ref
              (FormHandle. (new-atom (make-state initial))
                           (new-atom opts)
                           #js {})))
    ;; Keep opts-ref current every render
    (reset! (.-opts-ref ^FormHandle @handle-ref) opts)
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
  (let [form-atom    (.-form-atom handle)
        snap-ref     (hook/use-ref nil)
        subscribe    (hook/use-callback
                       (fn [callback]
                         (let [key (gensym "use-field")]
                           (add-watch form-atom key
                             (fn [_ _ old new]
                               (when (field-diff? old new field-key)
                                 (callback))))
                           #(remove-watch form-atom key)))
                       [field-key])
        get-snapshot (hook/use-callback
                       (fn []
                         (let [new-snap (field-snap @form-atom field-key)
                               cached   @snap-ref]
                           (if (= new-snap cached)
                             cached
                             (do (reset! snap-ref new-snap) new-snap))))
                       [field-key])
        snap         (hook/use-sync-external-store subscribe get-snapshot)
        handlers     (ensure-handlers! handle field-key checkbox?)]
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
    :type - :checkbox for checkbox fields (reads e.target.checked, returns :checked key)"
  ([^FormHandle handle field-key]
   (use-field* handle field-key nil))
  ([^FormHandle handle field-key opts]
   (use-field* handle field-key {:checkbox? (= :checkbox (:type opts))})))

(defn use-form-meta
  "Subscribe to form meta state (everything except :values).
  Returns {:submitting? :submitted? :errors}.
  Re-renders only when meta state changes."
  [^FormHandle handle]
  (let [form-atom    (.-form-atom handle)
        snap-ref     (hook/use-ref nil)
        subscribe    (hook/use-callback
                       (fn [callback]
                         (let [key (gensym "use-form-meta")]
                           (add-watch form-atom key
                             (fn [_ _ old new]
                               (when (or (not= (:errors old) (:errors new))
                                         (not= (:submitting? old) (:submitting? new))
                                         (not= (:submitted? old) (:submitted? new)))
                                 (callback))))
                           #(remove-watch form-atom key)))
                       [])
        get-snapshot (hook/use-callback
                       (fn []
                         (let [s        @form-atom
                               new-snap {:submitting? (:submitting? s)
                                         :submitted?  (:submitted? s)
                                         :errors      (:errors s)}
                               cached   @snap-ref]
                           (if (= new-snap cached)
                             cached
                             (do (reset! snap-ref new-snap) new-snap))))
                       [])]
    (hook/use-sync-external-store subscribe get-snapshot)))

(defnc Field
  [{:keys [control name render type]}]
  (render (use-field control name (when type {:type type}))))

;;;; Non-reactive utilities — safe to call in event handlers / submit fns

(defn values
  "Current values map."
  [^FormHandle handle]
  (:values @(.-form-atom handle)))

(defn value
  "Current value for a single field."
  [^FormHandle handle field-key]
  (get-in @(.-form-atom handle) [:values field-key]))

(defn errors
  "Current errors map."
  [^FormHandle handle]
  (:errors @(.-form-atom handle)))

(defn error
  "Current error string for a single field, or nil."
  [^FormHandle handle field-key]
  (get-in @(.-form-atom handle) [:errors field-key]))

(defn set-value!
  "Imperatively set a field value."
  [^FormHandle handle field-key val]
  (swap! (.-form-atom handle) assoc-in [:values field-key] val))

(defn set-error!
  "Imperatively set a field error."
  [^FormHandle handle field-key msg]
  (swap! (.-form-atom handle) assoc-in [:errors field-key] msg))

(defn reset-form!
  "Reset to initial state. Optionally provide new values."
  ([^FormHandle handle]
   (let [vals (:values @(.-opts-ref handle))
         init (if (satisfies? IDeref vals) @vals vals)]
     (reset! (.-form-atom handle) (make-state init))))
  ([^FormHandle handle new-values]
   (reset! (.-form-atom handle) (make-state new-values))))

(defn form-atom
  "Return the raw CLJS atom backing the form. Escape hatch for custom subscriptions."
  [^FormHandle handle]
  (.-form-atom handle))

;;;; Submit

(defn- run-submit! [^FormHandle handle submit-fn]
  (let [form-atom (.-form-atom handle)
        _         (swap! form-atom
                         (fn [s]
                           (update s :touched into (keys (:values s)))))
        v         (:values @form-atom)
        validate  (:validate @(.-opts-ref handle))]
    (-> (js/Promise.resolve (when validate (validate v)))
        (.then (fn [errs]
                 (if (and errs (pos? (count errs)))
                   (swap! form-atom assoc :errors errs)
                   (do
                     (swap! form-atom assoc :submitting? true :errors {})
                     (-> (js/Promise.resolve (when submit-fn (submit-fn v)))
                         (.then (fn [_]
                                  (swap! form-atom assoc
                                         :submitting? false
                                         :submitted? true)))
                         (.catch (fn [err]
                                   (swap! form-atom assoc :submitting? false)
                                   (throw err)))))))))))

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
