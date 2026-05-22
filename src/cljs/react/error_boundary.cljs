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
    (set! (.-state self) #js {:error nil})
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
            (let [err   (.. this -state -error)
                  props ^js (.-props this)]
              (if err
                ((.-renderFallback props) err)
                (.-children props)))))))

(set! (.-getDerivedStateFromError EBClass)
      (fn [err] #js {:error err}))

(defn ErrorBoundary
  "Render children inside a React error boundary.

  Props map:
    :fallback  — element value, or a 1-arity fn (fn [error] -> element) called
                 with the thrown error. Required. NOTE: a `defnc` component is
                 itself a function, so passing one directly will invoke it with
                 the error as its props (almost never what you want). Wrap it:
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
  [{:keys [fallback on-error]} & children]
  (let [render-fallback (if (fn? fallback)
                          fallback
                          (fn [_err] fallback))]
    (apply react/createElement
           EBClass
           #js {:renderFallback render-fallback
                :onError        on-error}
           children)))
