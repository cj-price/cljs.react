(ns cljs.react.core)

(defmacro defnc
  "Define a memoized React function component.

  The component receives a ClojureScript props map, returns a React element,
  and is wrapped with React.memo using CLJS `=` for prop comparison.

  Usage:
    (defnc MyComponent
      [{:keys [name age]}]
      (Element {:tag \"div\"}
        (Element {:tag \"h1\"} \"Hello, \" name)
        (Element {:tag \"p\"} \"Age: \" age)))

  Call with or without props:
    (MyComponent {:name \"Alice\"})  ; with props
    (MyComponent)                    ; defaults to {}

  Options (positional keyword before the arg vector):

    :as-element   Memoized via React.memo with raw JS props instead of the
                  cljsProps wrapper. Use when the component is called from a
                  JS-side React tree that expects a plain JS props object.
                  Note: children arrive as React's raw tri-shape
                  (undefined / single / JS array) on this path — the seq
                  normalization applied to default and :forward-ref does not
                  run here.
                  Memoization caveat: this path uses React's default shallow
                  (Object.is) prop comparison, NOT the deep CLJS `=` used by the
                  default/:forward-ref paths. Passing a freshly-built CLJS map
                  each render defeats the memo (every render produces a new JS
                  object); pass plain JS props, or stable references, when memo
                  benefit matters here.

    :forward-ref  Wrap with React.forwardRef. The forwarded ref arrives in the
                  CLJS props map under :ref as a RefAtom — deref for the raw
                  React ref via `react-ref`. Callers can supply :ref directly
                  in the CLJS props map or via React's second-arg ref slot."
  [name & args]
  (let [[options args] (if (keyword? (first args))
                         [(first args) (rest args)]
                         [nil args])
        _ (when-not (contains? #{nil :as-element :forward-ref} options)
            (throw (ex-info (str "defnc: unknown option " options
                                 " — expected :as-element, :forward-ref, or no option")
                            {:component name :option options})))
        [arg-vec & body] args
        display-name (str name)
        props-sym (if (empty? arg-vec) '_ (first arg-vec))
        ;; Pick the memo wrapper + element constructor per option.
        ;; :as-element uses raw JS props (react/createElement + clj->js-props),
        ;; default and :forward-ref both wrap props into cljsProps via create-cljs-element.
        memo-wrapper (case options
                       :as-element   'cljs.react.component/memo-component-js
                       :forward-ref  'cljs.react.component/memo-forward-ref
                       'cljs.react.component/memo-component)
        build-fn (if (= options :as-element)
                   '(fn
                      ([memoized#] (react/createElement memoized# nil))
                      ([memoized# props#]
                       (react/createElement memoized# (cljs.react.component/clj->js-props props#)))
                      ([memoized# props# & children#]
                       (apply react/createElement memoized#
                              (cljs.react.component/clj->js-props props#)
                              children#)))
                   '(fn
                      ([memoized#] (cljs.react.component/create-cljs-element memoized# {}))
                      ([memoized# props#] (cljs.react.component/create-cljs-element memoized# props#))
                      ([memoized# props# & children#]
                       (apply cljs.react.component/create-cljs-element memoized# props# children#))))]
    `(def ~name
       (let [inner#    (fn [~props-sym] ~@body)
             _#        (set! (.-displayName inner#) ~display-name)
             ;; memo-wrapper threads displayName from inner# onto memoized#
             ;; via propagate-display-name!, so we don't need to re-set it here.
             memoized# (~memo-wrapper inner#)
             build#    ~build-fn
             wrapper#  (fn
                         ([] (build# memoized#))
                         ([props#] (build# memoized# props#))
                         ([props# & children#] (apply build# memoized# props# children#)))]
         (set! (.-displayName wrapper#) ~display-name)
         wrapper#))))
