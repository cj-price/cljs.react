(ns cljs.react.lazy-test
  (:require
   [cljs.test :refer [deftest testing is async]]
   [cljs.react.lazy :as lazy]
   [cljs.react.core :refer [Element Suspense ErrorBoundary]]
   [goog.async.Deferred]
   ["global-jsdom/register"]
   ["@testing-library/react" :refer [render renderHook waitFor]])
  (:require-macros [cljs.react.core :refer [defnc]]))

;; A stand-in for a code-split component. The tests drive use-lazy-loadable through the
;; loader-fn path (() => Promise<component>) so they don't depend on the browser
;; module manager or the loadable macro's compiler-env module lookup, neither of
;; which exists under :node-test.

(defnc Panel
  [{:keys [label]}]
  (Element {:tag "div"} (str "loaded:" label)))

(defnc Panel-with-kids
  [{:keys [children]}]
  (Element {:tag "div"} "kids:" children))

(defnc Harness
  [{:keys [src label]}]
  (let [LazyPanel (lazy/use-lazy-loadable src)]
    (Element {:tag Suspense :fallback (Element {:tag "div"} "loading")}
      (LazyPanel {:label label}))))

(defnc Harness-kids
  [{:keys [src]}]
  (let [LazyPanel (lazy/use-lazy-loadable src)]
    (Element {:tag Suspense :fallback (Element {:tag "div"} "loading")}
      (LazyPanel {} "child-text"))))

(defnc Harness-noprops
  [{:keys [src]}]
  (let [LazyPanel (lazy/use-lazy-loadable src)]
    (Element {:tag Suspense :fallback (Element {:tag "div"} "loading")}
      (LazyPanel))))

;; ErrorBoundary must wrap a *child* that calls use-lazy-loadable, so a render-phase
;; throw from inside use-lazy-loadable is catchable rather than escaping the tree.
(defnc Caught
  [{:keys [src]}]
  (ErrorBoundary
    {:fallback (fn [err] (Element {:tag "div"} (str "boundary:" (ex-message err))))}
    (Harness {:src src :label "x"})))

(deftest lazy-loads-and-passes-props-test
  (testing "Suspense shows the fallback, then the lazy component renders with its props"
    (async done
      (let [src         (fn [] (js/Promise.resolve Panel))
            ^js result  (render (Harness {:src src :label "hi"}))
            container   (.-container result)]
        (is (re-find #"loading" (.-textContent container))
            "fallback is visible while the loader promise is pending")
        ;; waitFor retries only while its callback throws, so throw until the
        ;; chunk resolves; then assert the resolved content + prop pass-through.
        (-> (waitFor #(when-not (re-find #"loaded:hi" (.-textContent container))
                        (throw (js/Error. "lazy component not resolved yet"))))
            (.then (fn []
                     (is (re-find #"loaded:hi" (.-textContent container))
                         "resolved component renders with the prop passed at the call site")
                     (.unmount result)
                     (done)))
            (.catch (fn [e] (is false (str e)) (.unmount result) (done))))))))

(deftest stable-wrapper-test
  (testing "use-lazy-loadable returns the same callable wrapper across renders for a stable src"
    (let [src        (fn [] (js/Promise.resolve Panel))
          ^js result (renderHook #(lazy/use-lazy-loadable src))
          first-wrap (.. result -result -current)]
      (.rerender result)
      (is (identical? first-wrap (.. result -result -current)))
      (.unmount result))))

(deftest children-flow-through-test
  (testing "children passed at the call site reach the lazily-loaded component"
    (async done
      (let [src        (fn [] (js/Promise.resolve Panel-with-kids))
            ^js result (render (Harness-kids {:src src}))
            container  (.-container result)]
        (-> (waitFor #(when-not (re-find #"kids:child-text" (.-textContent container))
                        (throw (js/Error. "not resolved yet"))))
            (.then (fn []
                     (is (re-find #"kids:child-text" (.-textContent container)))
                     (.unmount result)
                     (done)))
            (.catch (fn [e] (is false (str e)) (.unmount result) (done))))))))

(deftest loader-rejection-surfaces-test
  (testing "a rejected loader promise surfaces to a wrapping ErrorBoundary"
    (async done
      (let [src        (fn [] (js/Promise.reject (js/Error. "chunk fetch failed")))
            ^js result (render (Caught {:src src}))
            container  (.-container result)]
        (-> (waitFor #(when-not (re-find #"boundary:" (.-textContent container))
                        (throw (js/Error. "boundary not triggered yet"))))
            (.then (fn []
                     (is (re-find #"boundary:chunk fetch failed" (.-textContent container))
                         "the boundary fallback shows the loader's error")
                     (.unmount result)
                     (done)))
            (.catch (fn [e] (is false (str e)) (.unmount result) (done))))))))

(deftest warns-on-unstable-src-test
  (testing "a dev build warns when src identity changes between renders (the inline-loadable footgun)"
    (let [warnings (atom [])
          orig     js/console.warn]
      (set! js/console.warn (fn [& args] (swap! warnings conj (apply str args))))
      (try
        (let [^js result (renderHook (fn [p] (lazy/use-lazy-loadable p))
                                     #js {:initialProps (fn [] (js/Promise.resolve Panel))})]
          (.rerender result (fn [] (js/Promise.resolve Panel)))
          (is (some #(re-find #"changed identity" %) @warnings)
              "changing src identity emits the stability warning")
          (.unmount result))
        (finally (set! js/console.warn orig))))))

(deftest stable-src-does-not-warn-test
  (testing "a stable src across renders emits no warning"
    (let [warnings (atom [])
          orig     js/console.warn
          src      (fn [] (js/Promise.resolve Panel))]
      (set! js/console.warn (fn [& args] (swap! warnings conj (apply str args))))
      (try
        (let [^js result (renderHook #(lazy/use-lazy-loadable src))]
          (.rerender result)
          (.rerender result)
          (is (empty? @warnings) "no warning when src identity is stable")
          (.unmount result))
        (finally (set! js/console.warn orig))))))

(deftest no-props-call-test
  (testing "the 0-arity wrapper call renders the lazy component"
    (async done
      (let [src        (fn [] (js/Promise.resolve Panel))
            ^js result (render (Harness-noprops {:src src}))
            container  (.-container result)]
        (-> (waitFor #(when-not (re-find #"loaded:" (.-textContent container))
                        (throw (js/Error. "not resolved yet"))))
            (.then (fn []
                     (is (re-find #"loaded:" (.-textContent container)))
                     (.unmount result)
                     (done)))
            (.catch (fn [e] (is false (str e)) (.unmount result) (done))))))))

(deftest deferred-thenable-test
  (testing "React.lazy accepts a goog.async.Deferred — the thenable shadow.lazy/load returns"
    (async done
      (let [d          (goog.async.Deferred.)
            ^js result (render (Harness {:src (fn [] d) :label "def"}))
            container  (.-container result)]
        (is (re-find #"loading" (.-textContent container))
            "fallback shows while the Deferred is pending")
        (.callback d Panel)
        (-> (waitFor #(when-not (re-find #"loaded:def" (.-textContent container))
                        (throw (js/Error. "deferred not resolved yet"))))
            (.then (fn []
                     (is (re-find #"loaded:def" (.-textContent container))
                         "resolving the Deferred renders the component")
                     (.unmount result)
                     (done)))
            (.catch (fn [e] (is false (str e)) (.unmount result) (done))))))))

(deftest bad-input-surfaces-test
  (testing "a src that is neither a Loadable nor a fn throws, catchable by an ErrorBoundary"
    (let [^js result (render (Caught {:src 42}))
          container  (.-container result)]
      (is (re-find #"boundary:" (.-textContent container))
          "the guard's ex-info is caught by the ErrorBoundary, not left to wedge render")
      (is (re-find #"Loadable or a 0-arg loader fn" (.-textContent container)))
      (.unmount result))))
