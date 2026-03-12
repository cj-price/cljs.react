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

(defn clj->js-props
  "Convert ClojureScript map to JavaScript object for React props.
  Handles nested structures without modifying prop names.

  Optimized version using reduce-kv and direct property access with aset."
  [props]
  (if (nil? props)
    nil
    (if (zero? (count props))
      #js {}
      (reduce-kv
        (fn [^js js-obj k v]
          ;; Use aset for direct property assignment (fastest)
          (aset js-obj
                ;; Convert key - inline keyword check for speed
                (if (keyword? k)
                  (name k)
                  (str k))
                ;; Convert value - inline checks for speed
                (cond
                  (map? v) (clj->js-props v)
                  (sequential? v) (to-array v)
                  :else v))
          js-obj)
        #js {}
        props))))

(defn create-cljs-element
  "Create a React element that works with ClojureScript data structures.

  For component types: props are wrapped in JS object with :cljs-props key.
  For DOM elements (strings): props are converted to JavaScript.

  Uses *create-element* dynamic var which defaults to react/createElement
  but can be rebound to use alternative renderers like emotion/jsx.

  Args:
    type: Component function or string tag (e.g., 'div')
    props: ClojureScript map (can be nil)
    children: Varargs of children

  Returns:
    React element"
  [type props & children]
  (let [;; For DOM elements, convert to JS
        ;; For components, wrap CLJS props in JS object
        react-props (if (string? type)
                      (clj->js-props props)
                      #js {:cljsProps props})
        js-children (to-array children)]
    (apply *create-element* type react-props js-children)))

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
  (let [;; Wrapper that extracts CLJS props and children
        wrapper-fn
        (fn [js-props]
          (let [cljs-props (gobj/get js-props "cljsProps")
                children (gobj/get js-props "children")
                ;; Merge children into props if present
                props-with-children (if (undefined? children)
                                      cljs-props
                                      (assoc cljs-props :children children))]
            (component-fn props-with-children)))

        ;; Comparison using ClojureScript = for props, JS === for children
        are-equal?
        (fn [prev-js-props next-js-props]
          (let [prev-props (gobj/get prev-js-props "cljsProps")
                next-props (gobj/get next-js-props "cljsProps")
                prev-children (gobj/get prev-js-props "children")
                next-children (gobj/get next-js-props "children")]
            ;; Props must be equal AND children must be identical
            (and (= prev-props next-props)
                 (identical? prev-children next-children))))]

    (react/memo wrapper-fn are-equal?)))

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
    (let [element-type (or tag "div")
          react-props (clj->js-props (dissoc props :tag))
          js-children (to-array children)]
      (apply renderer element-type react-props js-children))))

(defn make-create-cljs-element-fn
  "Create a create-cljs-element-like function bound to a specific renderer.

  Usage:
    (def emotion-create-element (make-create-cljs-element-fn emotion/jsx))
    (emotion-create-element \"div\" {:className \"styled\"} child1 child2)

  Returns a function with the same signature as create-cljs-element but using
  the provided renderer instead of react/createElement."
  [renderer]
  (fn [type props & children]
    (let [react-props (if (string? type)
                        (clj->js-props props)
                        #js {:cljsProps props})
          js-children (to-array children)]
      (apply renderer type react-props js-children))))

(defn forward-ref
  "Wrap a CLJS component fn with React.forwardRef.
  The forwarded ref is wrapped in a RefAtom and injected as :ref in the props map."
  [component-fn]
  (react/forwardRef
    (fn [js-props ref]
      (let [cljs-props (gobj/get js-props "cljsProps")
            children (gobj/get js-props "children")
            props (cond-> (assoc cljs-props :ref (hook/->RefAtom ref))
                    (not (undefined? children))
                    (assoc :children children))]
        (component-fn props)))))

(defn memo-forward-ref
  "Combine forward-ref + React.memo with CLJS equality comparison."
  [component-fn]
  (react/memo (forward-ref component-fn)
    (fn [prev next]
      (and (= (gobj/get prev "cljsProps") (gobj/get next "cljsProps"))
           (identical? (gobj/get prev "children") (gobj/get next "children"))))))