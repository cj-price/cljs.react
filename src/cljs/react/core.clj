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
             memoized# (~memo-wrapper inner#)
             _#        (set! (.-displayName memoized#) ~display-name)
             build#    ~build-fn
             wrapper#  (fn
                         ([] (build# memoized#))
                         ([props#] (build# memoized# props#))
                         ([props# & children#] (apply build# memoized# props# children#)))]
         (set! (.-displayName wrapper#) ~display-name)
         wrapper#))))
