(ns cljs.react.demo.util
  (:require ["prismjs" :as Prism]
            ["prismjs/components/prism-clojure"]
            [cljs.react.core :refer [Element]])
  (:require-macros [cljs.react.core :refer [defnc]]))

(defnc CodeAndOutput
  [{:keys [code title children]}]
  (Element {:tag "div" :className "code-and-output"}
    (when title
      (Element {:tag "h4"} title))
    (Element {:tag "div" :className "demo-columns"}
      (Element {:tag "div"}
        (Element {:tag "pre" :className "language-clojure"}
          (Element {:tag "code"
                    :className "language-clojure"
                    :dangerouslySetInnerHTML
                    #js {:__html (.highlight Prism code
                                   (.-clojure (.-languages Prism))
                                   "clojure")}})))
      (Element {:tag "div" :className "demo-output"}
        children))))
