(ns cljs.react.core)

(defmacro defnc
  "Define a React function component with memoization.

  The component:
  - Receives ClojureScript props map
  - Returns React element (created with Element)
  - Is wrapped with React.memo using CLJS = for comparison

  Usage:
    (defnc MyComponent
      [{:keys [name age]}]
      (Element {:tag \"div\"}
        (Element {:tag \"h1\"} \"Hello, \" name)
        (Element {:tag \"p\"} \"Age: \" age)))

  With :as-element option for JS interop:
    (defnc MyComponent
      :as-element
      [{:keys [name age]}]
      (Element {:tag \"div\"}
        (Element {:tag \"h1\"} \"Hello, \" name)))

  Component can be called with or without props:
    (MyComponent {:name \"Alice\"})  ; with props
    (MyComponent)                    ; defaults to {}

  Args:
    name: Component name
    options: Optional :as-element flag
    args: Argument vector (receives CLJS map)
    body: Component body (should return React element)"
  [name & args]
  (let [[options args] (if (keyword? (first args))
                         [(first args) (rest args)]
                         [nil args])
        [arg-vec & body] args
        as-element? (= options :as-element)
        forward-ref? (= options :forward-ref)
        inner-name (symbol (str name "-inner"))
        props-sym (if (empty? arg-vec) '_ (first arg-vec))]
    (cond
      as-element?
      ;; :as-element version - accepts raw JS props
      `(do
         ;; Define the inner component function
         (defn ~inner-name [~props-sym]
           ~@body)
         ;; Define as regular React element (uses react/createElement directly)
         (def ~name
           (let [memoized# (cljs.react.component/memo-component-js ~inner-name)]
             (fn
               ([] (react/createElement memoized# (cljs.react.component/clj->js-props {})))
               ([props#] (react/createElement memoized# (cljs.react.component/clj->js-props props#)))
               ([props# & children#] (apply react/createElement memoized# (cljs.react.component/clj->js-props props#) children#))))))
      forward-ref?
      ;; :forward-ref version - wraps with React.forwardRef + memo
      `(do
         (defn ~inner-name [~props-sym]
           ~@body)
         (def ~name
           (let [memoized# (cljs.react.component/memo-forward-ref ~inner-name)]
             (fn
               ([] (cljs.react.component/create-cljs-element memoized# {}))
               ([props#] (cljs.react.component/create-cljs-element memoized# props#))
               ([props# & children#] (apply cljs.react.component/create-cljs-element memoized# props# children#))))))

      :else
      ;; Regular version - uses cljsProps wrapper
      `(do
         ;; Define the inner component function
         (defn ~inner-name [~props-sym]
           ~@body)
         ;; Define the outer component as a function that creates React elements
         ;; Supports both 0-arity and varargs
         (def ~name
           (let [memoized# (cljs.react.component/memo-component ~inner-name)]
             (fn
               ([] (cljs.react.component/create-cljs-element memoized# {}))
               ([props#] (cljs.react.component/create-cljs-element memoized# props#))
               ([props# & children#] (apply cljs.react.component/create-cljs-element memoized# props# children#)))))))))
