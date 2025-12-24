(ns cljs.react.demo
  (:require ["react" :as react]
            ["react-dom/client" :as react-dom]
            [cljs.react.core :refer [Element]])
  (:require-macros [cljs.react.core :refer [defnc]]))

(defnc HelloWorld
  []
  (Element {:tag "div"}
    "Hello" " " "World!"))

(defnc MoreHello
  [{:keys [children]}]
  (Element {:tag "div"}
    (HelloWorld)
    (HelloWorld)
    (HelloWorld)
    children))

(defnc Counter
  []
  (let [[count set-count] (react/useState 0)]
    (Element {:tag "div" :className "counter"}
      (Element {:tag "h2"} "Counter: " count)
      (Element {:tag "button"
                :onClick #(set-count inc)}
        "Increment"))))

;; Example :as-element component for JS interop
;; Receives raw JS props - use JS interop to access
(defnc JSInteropComponent
  :as-element
  [props]
  (Element {:tag "div" :className "js-interop"}
    (Element {:tag "h3"} (.-title props))
    (Element {:tag "p"} (.-message props))))

(defnc App
  []
  (Element {:tag "div" :className "app"}
    (Element {:tag "h1"} "cljs.react Demo")
    (Element {:tag "hr"})

    (Element {:tag "section"}
      (Element {:tag "h2"} "Basic Components")
      (HelloWorld))

    (Element {:tag "section"}
      (Element {:tag "h2"} "Nested Components with Children")
      (MoreHello {}
        (Element {:tag "p" :className "child-content"}
          "This is a child element passed to MoreHello!")))

    (Element {:tag "section"}
      (Element {:tag "h2"} "Stateful Component (useState)")
      (Counter))

    (Element {:tag "section"}
      (Element {:tag "h2"} "JS Interop Component (:as-element)")
      (Element {:tag "p"} "This component can be used from both CLJS and JS:")
      (JSInteropComponent {:title "From CLJS" :message "Called with CLJS map, automatically converted to JS"})
      ;; Can also be called with react/createElement from JS
      (react/createElement JSInteropComponent #js {:title "From JS" :message "Called with JS object directly"}))))

(defn ^:export init
  "Initialize the React application using React 18+ createRoot API"
  []
  (when-let [root-el (.getElementById js/document "app")]
    (let [root (react-dom/createRoot root-el)]
      ;; App is now (partial create-cljs-element App-memoized)
      ;; Calling (App {}) creates the React element
      (.render root (App {})))))