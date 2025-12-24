(ns cljs.react.component
  (:require ["react" :as react]
            [goog.object :as gobj]))

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
    (apply react/createElement type react-props js-children)))

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
  (let [;; Wrapper that extracts CLJS props
        wrapper-fn
        (fn [js-props]
          (let [cljs-props (gobj/get js-props "cljsProps")]
            (component-fn cljs-props)))

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