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

(declare props->js convert-value)

(defn- walk-seq
  "Convert a sequential to a JS array, recursively converting CLJS map /
  sequential elements via convert-value. Strings, numbers, booleans, nil,
  keywords — and anything non-`object?` — short-circuit before the
  protocol-based checks, keeping vectors-of-primitives at `to-array` cost.
  Persistent collections take the fast `instance?` branch; the slower
  `map?` / `sequential?` fallback only fires for non-Persistent CLJS
  collections (Cons, LazySeq, Range, etc.)."
  ^js [v]
  (let [arr (to-array v)
        n   (alength arr)]
    (loop [i 0]
      (when (< i n)
        (let [e (aget arr i)]
          (when (or (instance? PersistentArrayMap e)
                    (instance? PersistentVector  e)
                    (instance? PersistentHashMap e)
                    (and (object? e)
                         (or (map? e) (sequential? e))))
            (aset arr i (convert-value e))))
        (recur (inc i))))
    arr))

(defn- convert-value
  "Convert one prop value. Maps recurse through props->js; sequentials become
  JS arrays with each element converted via convert-value (so arrays-of-maps
  arrive as arrays-of-JS-objects, matching consumer expectations).
  Fast-paths PersistentArrayMap / PersistentVector / PersistentHashMap ahead of
  the generic map?/sequential? branches to skip protocol dispatch on the common
  case (mirrors the fast-path set in walk-seq)."
  [v]
  (cond
    (instance? PersistentArrayMap v) (props->js v)
    (instance? PersistentVector  v)  (walk-seq v)
    (instance? PersistentHashMap v)  (props->js v)
    (map?         v)                 (props->js v)
    (sequential?  v)                 (walk-seq v)
    :else                            v))

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
  - Sequentials become JS arrays with each element walked recursively (so
    `{:rows [{:id 1} {:id 2}]}` becomes `#js {:rows #js [#js {:id 1} #js {:id 2}]}`).
    Pre-JS values (`#js {}`, dates, etc.) pass through unchanged.
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

(defn- normalize-children
  "React stores children as undefined for 0, the bare child for 1, and a JS
  array for 2+. Project that onto a CLJS seq (or nil) so component bodies can
  iterate or pass through uniformly."
  [children]
  (cond
    (nil? children)   nil
    (array? children) (seq children)
    :else             (cons children nil)))

(defn unwrap-cljs-props
  "Read the CLJS props map out of the JS wrapper object, merging :children in
  as a CLJS seq (nil when there are none). Shared by memo-component,
  forward-ref, memo-forward-ref, and the lazy-loading shim."
  [js-props]
  (let [cljs-props (gobj/get js-props "cljsProps")
        children   (normalize-children (gobj/get js-props "children"))]
    (if (nil? children)
      cljs-props
      (assoc cljs-props :children children))))

(declare element=)

(defn- js-value=
  "Structural equality on values found inside React props/children. Walks JS
  arrays and plain JS objects recursively; routes React-element values through
  [[element=]]; falls back to CLJS `=` for primitives, CLJS values, and
  functions. Used by the deep comparator."
  [a b]
  (cond
    (identical? a b) true
    ;; React elements — route through element= so cljsProps gets compared as a
    ;; CLJS map, not via JS-object walk.
    (and (react/isValidElement a) (react/isValidElement b))
    (element= a b)
    ;; JS arrays (e.g. multi-child arrays, or CLJS vectors that have already
    ;; been converted by clj->js-props).
    (and (array? a) (array? b))
    (let [na (alength a)]
      (and (== na (alength b))
           (loop [i 0]
             (cond
               (>= i na)                          true
               (js-value= (aget a i) (aget b i))  (recur (inc i))
               :else                              false))))
    ;; Plain JS objects (e.g. raw JS props on DOM elements, nested :style maps).
    (and (object? a) (object? b))
    (let [ka (js-keys a)
          kb (js-keys b)
          n  (alength ka)]
      (and (== n (alength kb))
           (loop [i 0]
             (if (>= i n)
               true
               (let [k (aget ka i)]
                 (if (js-value= (gobj/get a k) (gobj/get b k))
                   (recur (inc i))
                   false))))))
    :else (= a b)))

(defn- element=
  "Structural equality on two React elements. Two elements are `=` when they
  share `type` (identity) and `key`, and their props are structurally equal:
  - For library-created elements (those carrying a `cljsProps` slot), the
    CLJS map is compared with `=` and `children` is recursed via [[js-value=]].
  - For DOM elements (raw JS props), the props object is walked via
    [[js-value=]] (same-key set, same value at each key)."
  [^js a ^js b]
  (or (identical? a b)
      (and (object? a) (object? b)
           (identical? (.-type a) (.-type b))
           (let [ka (.-key a) kb (.-key b)]
             (or (identical? ka kb) (= ka kb)))
           (let [ap (.-props a) bp (.-props b)]
             (or (identical? ap bp)
                 (and (some? ap) (some? bp)
                      (let [acp (gobj/get ap "cljsProps")]
                        (if (some? acp)
                          (and (= acp (gobj/get bp "cljsProps"))
                               (js-value= (gobj/get ap "children")
                                          (gobj/get bp "children")))
                          (js-value= ap bp)))))))))

(defn- cljs-props-equal?
  "Default memo comparator: structural `=` on `cljsProps`, and structural
  walk on `children` via [[js-value=]] so freshly-allocated trees (from `for`,
  inline literals, etc.) compare equal when their data matches. Cost is
  bounded by the size of the children tree. For hot paths where the
  comparator cost outweighs the component body, opt into the shallow
  variant via `memo-component`'s `:shallow? true` kwarg."
  [prev-js-props next-js-props]
  (and (= (gobj/get prev-js-props "cljsProps")
          (gobj/get next-js-props "cljsProps"))
       (js-value= (gobj/get prev-js-props "children")
                  (gobj/get next-js-props "children"))))

(defn- cljs-props-shallow-equal?
  "Shallow memo comparator: `=` on `cljsProps`, identity (`===`) on children.
  Selected via `memo-component`'s `:shallow? true`. Cheaper per-call but
  defeats memo whenever a parent constructs fresh child elements each render
  (e.g. via `for`). Reach for it only when the deep comparator measurably
  outweighs the component body work."
  [prev-js-props next-js-props]
  (and (= (gobj/get prev-js-props "cljsProps")
          (gobj/get next-js-props "cljsProps"))
       (identical? (gobj/get prev-js-props "children")
                   (gobj/get next-js-props "children"))))

(defn- cljs-props-comparator
  "Select the React.memo comparator for the cljsProps path: the shallow
  comparator (identity on children) when `shallow?`, else the default deep
  structural walk. Shared by `memo-component` and `memo-forward-ref`."
  [shallow?]
  (if shallow? cljs-props-shallow-equal? cljs-props-equal?))

(defn- propagate-display-name!
  "Copy displayName from `src` onto `dst` when present. React DevTools reads
  this for the node label; without it wrapped components render as Anonymous."
  [src dst]
  (when-let [n (.-displayName src)]
    (set! (.-displayName dst) n))
  dst)

(defn memo-component
  "Wrap component with React.memo using ClojureScript equality.

  Default comparator semantics:
  - `:cljsProps` (the CLJS map passed in props position) is compared with `=`,
    so persistent data structures memoize correctly.
  - `children` are compared structurally — React elements recurse on `type`,
    `key`, `cljsProps` / raw JS props, and nested children. Freshly-allocated
    children trees (e.g. from `for`) memoize correctly when their data matches.
    Cost is bounded by the children tree size.

  Args:
    component-fn   - Function taking cljs props map, returning a React element.
    :shallow? true - Opt out of the structural children walk: children compare
                     by identity (`===`) only. Cheaper per-call but defeats
                     memo whenever a parent constructs fresh child elements.
                     Use only when the deep comparator measurably outweighs
                     the component body — measure with `bb bench` first.

  Returns:
    Memoized React component."
  [component-fn & {:keys [shallow?]}]
  (propagate-display-name!
    component-fn
    (react/memo (fn [js-props] (component-fn (unwrap-cljs-props js-props)))
                (cljs-props-comparator shallow?))))

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
  "Combine forward-ref + React.memo with CLJS equality comparison.

  Accepts the same `:shallow? true` opt-out as [[memo-component]]."
  [component-fn & {:keys [shallow?]}]
  (propagate-display-name!
    component-fn
    (react/memo (forward-ref component-fn)
                (cljs-props-comparator shallow?))))
