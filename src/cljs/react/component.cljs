(ns cljs.react.component
  (:require ["react" :as react]
            [goog.object :as gobj]
            [cljs.react.hook :as hook]))

(def ^:dynamic *create-element*
  "Dynamic var holding the current element creation function.
  Defaults to react/createElement but can be rebound to use alternative
  renderers like emotion/jsx.

  The function should have the signature:
    (fn [type props & children] ...)"
  react/createElement)

(defn- props->js
  "Always produce a JS object for a CLJS map — empty maps become #js {}.
  Used recursively so nested empty maps round-trip faithfully."
  [props]
  (reduce-kv
    (fn [^js js-obj k v]
      (let [prop-name (if (keyword? k) (name k) (str k))]
        (aset js-obj prop-name
              (if (and (= prop-name "ref") (satisfies? hook/IReactRef v))
                (hook/-react-ref v)
                (cond
                  (map? v) (props->js v)
                  (sequential? v) (to-array v)
                  :else v))))
      js-obj)
    #js {}
    props))

(defn clj->js-props
  "Convert ClojureScript map to JavaScript object for React props.
  Handles nested structures without modifying prop names.

  Returns nil for nil or empty maps — React accepts nil props and skips
  the props-object allocation that would otherwise happen on every call.
  Nested empty maps are preserved as empty JS objects to avoid silently
  changing the shape of values consumers read back."
  [props]
  (when (and props (pos? (count props)))
    (props->js props)))

(defn- make-react-props
  "Build the JS props object for a React createElement call.
  For DOM elements (strings): converts the CLJS map to a JS object.
  For components: wraps in {:cljsProps props}, hoisting :key to the top level
  so React's reconciler can see it."
  [type props]
  (if (string? type)
    (clj->js-props props)
    (let [js-obj #js {:cljsProps props}]
      (when-let [k (:key props)]
        (aset js-obj "key" k))
      js-obj)))

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
  (react/memo (fn [js-props] (component-fn (unwrap-cljs-props js-props)))
              cljs-props-equal?))

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
  (react/memo component-fn))

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
    (when (nil? tag)
      (throw (js/Error. "Element requires a :tag prop")))
    (let [react-props (clj->js-props (dissoc props :tag))
          js-children (to-array children)]
      (apply renderer tag react-props js-children))))

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
  The forwarded ref is wrapped in a RefAtom and injected as :ref in the props map."
  [component-fn]
  (react/forwardRef
    (fn [js-props ref]
      (component-fn
        (assoc (unwrap-cljs-props js-props) :ref (hook/->RefAtom ref))))))

(defn memo-forward-ref
  "Combine forward-ref + React.memo with CLJS equality comparison."
  [component-fn]
  (react/memo (forward-ref component-fn) cljs-props-equal?))