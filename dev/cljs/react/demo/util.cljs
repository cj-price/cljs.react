(ns cljs.react.demo.util
  (:require ["prismjs" :as Prism]
            ["prismjs/components/prism-clojure"]
            [cljs.react.core :refer [Element]])
  (:require-macros [cljs.react.core :refer [defnc]]))

(defn- el [tag args]
  (if (map? (first args))
    (apply Element (assoc (first args) :tag tag) (rest args))
    (apply Element {:tag tag} args)))

(defn Div     [& args] (el "div" args))
(defn Span    [& args] (el "span" args))
(defn P       [& args] (el "p" args))
(defn H1      [& args] (el "h1" args))
(defn H2      [& args] (el "h2" args))
(defn H3      [& args] (el "h3" args))
(defn H4      [& args] (el "h4" args))
(defn Button  [& args] (el "button" args))
(defn Input   [& args] (el "input" args))
(defn Label   [& args] (el "label" args))
(defn Form    [& args] (el "form" args))
(defn Section [& args] (el "section" args))
(defn Header  [& args] (el "header" args))
(defn Nav     [& args] (el "nav" args))
(defn Main    [& args] (el "main" args))
(defn Footer  [& args] (el "footer" args))
(defn A       [& args] (el "a" args))
(defn Strong  [& args] (el "strong" args))
(defn Pre     [& args] (el "pre" args))
(defn Code    [& args] (el "code" args))
(defn Ul      [& args] (el "ul" args))
(defn Li      [& args] (el "li" args))

(defnc CodeAndOutput
  [{:keys [code title children]}]
  (Div {:className "code-and-output"}
    (when title (H4 title))
    (Div {:className "demo-columns"}
      (Div
        (Pre {:className "language-clojure"}
          (Code {:className "language-clojure"
                 :dangerouslySetInnerHTML
                 #js {:__html (.highlight Prism code
                                (.-clojure (.-languages Prism))
                                "clojure")}})))
      (Div {:className "demo-output"} children))))
