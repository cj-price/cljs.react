(ns cljs.react.component
  "Component-authoring primitives: CLJS-props interop (`create-cljs-element`,
  `clj->js-props`), memoization wrappers (`memo-component`,
  `memo-component-js`, `memo-forward-ref`), `forward-ref`, and the
  `*create-element*` dynamic var for custom renderers.

  The `defnc` macro in `cljs.react.core` composes these for typical components;
  use this ns directly when defnc isn't a fit (custom renderer, manual
  memoization, etc.)."
  (:require ["react" :as react]
            [goog.object :as gobj]
            [cljs.react.hook :as hook]))

(def ^:dynamic *create-element*
  "Dynamic var holding the current element creation function. Read by `Element`
  and `create-cljs-element`; `make-element-fn` / `make-create-cljs-element-fn`
  bind their renderer at construction time and ignore this var.

  Defaults to react/createElement; rebind to use an alternative renderer
  (for example emotion/jsx).

  The function should have the signature:
    (fn [type props & children] ...)"
  react/createElement)

(declare props->js)

(defn- convert-value
  "Convert one prop value. Maps recurse through props->js; vectors and other
  sequentials become JS arrays via to-array (shallow — array elements are not
  walked, since React-array contents are typically already JS-friendly).
  Fast-paths PersistentArrayMap / PersistentVector ahead of the generic
  map?/sequential? branches to skip protocol dispatch on the common case."
  [v]
  (cond
    (instance? PersistentArrayMap v) (props->js v)
    (instance? PersistentVector  v)  (to-array v)
    (map?         v)                  (props->js v)
    (sequential?  v)                  (to-array v)
    :else                              v))

(defn- convert-prop
  "Convert one (key, value) pair to its JS prop value. A :ref whose value
  satisfies IReactRef is unwrapped; otherwise the value is converted via
  convert-value. Shared by pam->js and props->js."
  [k v]
  (if (and (keyword-identical? k :ref)
           (satisfies? hook/IReactRef v))
    (hook/-react-ref v)
    (convert-value v)))

(defn- pam->js
  "Tight conversion path for PersistentArrayMap — reads the flat .arr field
  directly, skipping reduce-kv closure + IFn dispatch per pair. The
  `skip-key` argument lets callers (Element) request a key be omitted from
  the output without first allocating a dissoc'd map; pass nil to keep all
  keys."
  ^js [^js am skip-key]
  (let [arr  (.-arr am)
        n2   (* 2 (.-cnt am))
        out  #js {}]
    (loop [i 0]
      (when (< i n2)
        (let [k (aget arr i)]
          (when-not (keyword-identical? k skip-key)
            (let [v  (aget arr (inc i))
                  pn (if (keyword? k) (.-fqn k) (str k))]
              (aset out pn (convert-prop k v)))))
        (recur (+ i 2))))
    out))

(defn- props->js
  "Always produce a JS object for a CLJS map — empty maps become #js {}.
  Used recursively so nested empty maps round-trip faithfully."
  (^js [props] (props->js props nil))
  (^js [props skip-key]
   (if (instance? PersistentArrayMap props)
     (pam->js props skip-key)
     (reduce-kv
       (fn [^js out k v]
         (if (keyword-identical? k skip-key)
           out
           (let [pn (if (keyword? k) (.-fqn k) (str k))]
             (aset out pn (convert-prop k v))
             out)))
       #js {}
       props))))

(defn- has-entries?
  "Cheap non-empty check that skips the ICounted protocol for the two
  persistent-map types that hold ~all real-world prop maps."
  [props]
  (cond
    (nil? props)                          false
    (instance? PersistentArrayMap props) (pos? (.-cnt props))
    (instance? PersistentHashMap  props)  (pos? (.-cnt props))
    :else                                 (pos? (count props))))

(defn clj->js-props
  "Convert a ClojureScript map to a JavaScript object for React props.

  - Nested maps are walked recursively (so `{:style {:color \"red\"}}` becomes
    `#js {:style #js {:color \"red\"}}`).
  - Sequentials become JS arrays via `to-array` — conversion is **shallow**:
    elements inside the array (e.g. CLJS maps inside `:rows`) are kept by
    reference, not recursively converted. Pass already-JS-friendly values when
    handing arrays to a JS-only consumer.
  - `:ref` whose value is a RefAtom is unwrapped to the raw React ref.
  - nil / empty input → nil (React accepts nil props; this saves an
    allocation per call). Nested empty maps round-trip as `#js {}` to avoid
    quietly changing the shape consumers read back.

  The 2-arity form drops `skip-key` from the output without first allocating
  a dissoc'd map — used by `Element` to omit `:tag`."
  (^js [props] (clj->js-props props nil))
  (^js [props skip-key]
   (when (has-entries? props)
     (props->js props skip-key))))

(defn- make-react-props
  "Build the JS props object for a React createElement call.
  For DOM elements (strings): converts the CLJS map to a JS object.
  For components: wraps in {:cljsProps props}, hoisting :key to the top level
  so React's reconciler can see it."
  [type props]
  (if (string? type)
    (clj->js-props props)
    (if-let [k (:key props)]
      #js {:cljsProps props :key k}
      #js {:cljsProps props})))

(defn create-cljs-element
  "Create a React element that works with ClojureScript data structures.

  For component types: props are wrapped in JS object under the :cljsProps key.
  For DOM elements (strings): props are converted to JavaScript.

  Uses *create-element* dynamic var which defaults to react/createElement
  but can be rebound to use alternative renderers like emotion/jsx.

  Args:
    type: Component function or string tag (e.g., 'div')
    props: ClojureScript map (can be nil)
    children: Varargs of children

  Returns:
    React element"
  ([type props]
   (*create-element* type (make-react-props type props)))
  ([type props c1]
   (*create-element* type (make-react-props type props) c1))
  ([type props c1 c2]
   (*create-element* type (make-react-props type props) c1 c2))
  ([type props c1 c2 c3]
   (*create-element* type (make-react-props type props) c1 c2 c3))
  ([type props c1 c2 c3 & more]
   (apply *create-element* type (make-react-props type props) c1 c2 c3 more)))

(defn- unwrap-cljs-props
  "Read the CLJS props map out of the JS wrapper object, merging :children in
  when React has attached them. Shared by memo-component, forward-ref, and
  memo-forward-ref."
  [js-props]
  (let [cljs-props (gobj/get js-props "cljsProps")
        children (gobj/get js-props "children")]
    (if (undefined? children)
      cljs-props
      (assoc cljs-props :children children))))

(defn- cljs-props-equal?
  "React.memo comparator: CLJS = on cljsProps, JS === on children.
  Used by both memo-component and memo-forward-ref."
  [prev-js-props next-js-props]
  (and (= (gobj/get prev-js-props "cljsProps")
          (gobj/get next-js-props "cljsProps"))
       (identical? (gobj/get prev-js-props "children")
                   (gobj/get next-js-props "children"))))

(defn- propagate-display-name!
  "Copy displayName from `src` onto `dst` when present. React DevTools reads
  this for the node label; without it wrapped components render as Anonymous."
  [src dst]
  (when-let [n (.-displayName src)]
    (set! (.-displayName dst) n))
  dst)

(defn memo-component
  "Wrap component with React.memo using ClojureScript equality.

  The wrapper extracts CLJS props and compares them using CLJS =.
  Also compares React-managed children to detect changes.
  This enables efficient memoization with persistent data structures.

  Args:
    component-fn: Function taking cljs props map, returning React element

  Returns:
    Memoized React component"
  [component-fn]
  (propagate-display-name!
    component-fn
    (react/memo (fn [js-props] (component-fn (unwrap-cljs-props js-props)))
                cljs-props-equal?)))

(defn memo-component-js
  "Wrap component with React.memo for components that accept raw JS props.

  No conversion - component receives raw JS props and must use JS interop.
  Uses React's default shallow comparison for props.

  This is used for :as-element components that need to work like regular
  React components with full JS compatibility.

  Args:
    component-fn: Function taking JS props, returning React element

  Returns:
    Memoized React component that accepts JS props"
  [component-fn]
  ;; Just use React.memo with default comparison (shallow equality)
  (propagate-display-name! component-fn (react/memo component-fn)))

(defn element-props
  "Validate the :tag key and convert the rest of the props map to a JS object.
  Throws ex-info with :type ::missing-tag if :tag is nil — programmatic callers
  can catch on type. Shared by `Element` (core) and `make-element-fn`."
  [tag props]
  (when (nil? tag)
    (throw (ex-info "Element requires a :tag prop"
                    {:type ::missing-tag :props props})))
  (clj->js-props props :tag))

;; Custom renderer support

(defn make-element-fn
  "Create an Element-like function bound to a specific renderer.

  Usage:
    (def EmotionElement (make-element-fn emotion/jsx))
    (EmotionElement {:tag \"div\"} \"Styled with Emotion\")

  Returns a function with the same signature as Element but using
  the provided renderer instead of react/createElement."
  [renderer]
  (fn [{:keys [tag] :as props} & children]
    (apply renderer tag (element-props tag props) (to-array children))))

(defn make-create-cljs-element-fn
  "Create a create-cljs-element-like function bound to a specific renderer.

  Usage:
    (def emotion-create-element (make-create-cljs-element-fn emotion/jsx))
    (emotion-create-element \"div\" {:className \"styled\"} child1 child2)

  Returns a function with the same signature as create-cljs-element but using
  the provided renderer instead of react/createElement."
  [renderer]
  (fn [type props & children]
    (apply renderer type (make-react-props type props) (to-array children))))

(defn forward-ref
  "Wrap a CLJS component fn with React.forwardRef.

  The forwarded ref is wrapped in a RefAtom and injected as :ref in the props
  map. Callers may supply the ref via either path:

  - Direct CLJS call: (MyComp {:ref some-ref ...}) — :ref lives inside the CLJS
    props map. RefAtom values are unwrapped to their raw React ref before being
    handed to the component body.
  - Raw createElement: (react/createElement MyComp #js {:ref some-ref ...}) —
    React.forwardRef receives the ref via its second argument.

  React's top-level ref takes precedence; the cljsProps :ref is the fallback
  path that makes the direct-call API work."
  [component-fn]
  (propagate-display-name!
    component-fn
    (react/forwardRef
      (fn [js-props react-ref]
        (let [props (unwrap-cljs-props js-props)
              raw   (or react-ref
                        (let [r (:ref props)]
                          (if (satisfies? hook/IReactRef r)
                            (hook/-react-ref r)
                            r)))]
          (component-fn (assoc props :ref (hook/->RefAtom raw))))))))

(defn memo-forward-ref
  "Combine forward-ref + React.memo with CLJS equality comparison."
  [component-fn]
  (propagate-display-name!
    component-fn
    (react/memo (forward-ref component-fn) cljs-props-equal?)))