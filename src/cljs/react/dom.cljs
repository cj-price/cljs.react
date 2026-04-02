(ns cljs.react.dom
  (:require ["react-dom" :as react-dom]))

(defn create-portal
  "Render children into a different DOM node."
  [child container]
  (react-dom/createPortal child container))
