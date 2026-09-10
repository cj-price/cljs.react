(ns cljs.react.error-boundary
  "React error boundary — a class component that catches render-phase and
  lifecycle errors thrown by descendants and renders a fallback instead.

  Error boundaries still require class components in React 19, so this ns
  constructs one via Reflect.construct + prototype wiring. Consumers use the
  CamelCase [[ErrorBoundary]] element constructor; the class itself is an
  implementation detail."
  (:require ["react" :as react]))

(declare ^:no-doc ^js EBClass)

(defn- ^:no-doc eb-ctor [props]
  (let [self (js/Reflect.construct react/Component #js [props] EBClass)]
    (set! (.-state self) #js {:error nil :hasError false})
    self))

(def ^:no-doc ^js EBClass eb-ctor)

(let [proto (js/Object.create (.-prototype ^js react/Component))]
  (set! (.-prototype EBClass) proto)
  (set! (.-constructor proto) EBClass)
  (set! (.-componentDidCatch proto)
        (fn [err info]
          (this-as this
            (when-let [cb (.-onError ^js (.-props this))]
              ;; Guard the user callback: a throwing telemetry hook should not
              ;; itself crash the boundary's fallback render.
              (try (cb err info)
                   (catch :default cb-err
                     (js/console.error "ErrorBoundary :on-error threw" cb-err)))))))
  (set! (.-render proto)
        (fn []
          (this-as this
            (let [state ^js (.-state this)
                  err   (.-error state)
                  props ^js (.-props this)]
              (if (.-hasError state)
                ;; Guard the fallback render itself: if the user's fallback fn
                ;; throws (e.g. a zero-arg fn called with one arg, or a broken
                ;; element produced from the error), the boundary cannot catch
                ;; its own render error and the whole tree would unmount.
                ;; Catch here, log both errors, and render a minimal sentinel
                ;; so the UI shows *something* and the dev console has signal.
                (try
                  ((.-renderFallback props) err)
                  (catch :default fb-err
                    (js/console.error
                      "ErrorBoundary :fallback threw while rendering. Original error:"
                      err
                      "\nFallback error:" fb-err)
                    (react/createElement "pre"
                      #js {:style #js {:color "red" :whiteSpace "pre-wrap"}}
                      (str "ErrorBoundary :fallback threw: "
                           (or (.-message fb-err) (str fb-err))
                           "\n\nOriginal error: "
                           (or (some-> err .-message) (str err))))))
                (.-children props)))))))

(set! (.-getDerivedStateFromError EBClass)
      (fn [err] #js {:error err :hasError true}))

(defn ErrorBoundary
  "Render children inside a React error boundary.

  Props map:
    :fallback  — element value, or a 1-arity fn (fn [error] -> element) called
                 with the thrown error. **Required, must be non-nil** — a missing
                 or nil `:fallback` throws ex-info `:type ::missing-fallback` /
                 `::nil-fallback` at element-creation time so a typo'd key
                 (e.g. `:fall-back`) is loud rather than silently swallowing the
                 error. If the fallback fn itself throws (e.g. wrong arity, or
                 the rendered element is malformed), the boundary catches that
                 too and renders a `<pre>` sentinel with both errors logged to
                 the console.

                 NOTE: a `defnc` component is itself a function, so passing one
                 directly will invoke it with the error as its props (almost
                 never what you want). Wrap it:
                 `:fallback (fn [err] (MyFallback {:error err}))`.
    :on-error  — optional (fn [error info] ...) invoked in componentDidCatch,
                 useful for logging/telemetry.

  Usage:
    (ErrorBoundary
      {:fallback (fn [err]
                   (Element {:tag \"div\" :role \"alert\"}
                     (ex-message err)))}
      (RiskyChild))

  Tip: give the fallback markup `:role \"alert\"` so screen readers announce
  the error when it appears."
  [{:keys [fallback on-error] :as props} & children]
  (when-not (contains? props :fallback)
    (throw (ex-info
             "ErrorBoundary requires a :fallback prop (an element or 1-arity fn)"
             {:type ::missing-fallback})))
  (when (nil? fallback)
    (throw (ex-info
             "ErrorBoundary :fallback is nil — pass an element or a 1-arity fn (fn [error] element)"
             {:type ::nil-fallback})))
  (let [render-fallback (if (fn? fallback)
                          fallback
                          (fn [_err] fallback))]
    (apply react/createElement
           EBClass
           #js {:renderFallback render-fallback
                :onError        on-error}
           children)))
