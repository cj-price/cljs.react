(ns cljs.benchmark.ssr
  "SSR hot-path coverage: react-dom/server renderToString of a small cljs.react
  element tree vs the equivalent raw React tree. renderToString is synchronous
  in Node, so this fits the pure micro-bench harness (no jsdom / commit loop)."
  (:require [cljs.react.core :refer [Element]]
            [cljs.benchmark.utils :as utils]
            ["react" :as react]
            ["react-dom/server" :as rdom-server]))

(defn- cljs-tree []
  (Element {:tag "div" :className "card"}
    (Element {:tag "h1"} "Title")
    (Element {:tag "ul"}
      (Element {:tag "li"} "a")
      (Element {:tag "li"} "b")
      (Element {:tag "li"} "c"))))

(defn- raw-tree []
  (react/createElement "div" #js {:className "card"}
    (react/createElement "h1" nil "Title")
    (react/createElement "ul" nil
      (react/createElement "li" nil "a")
      (react/createElement "li" nil "b")
      (react/createElement "li" nil "c"))))

(defn bench-ssr-render-to-string []
  ;; Pre-build the element trees so the measurement isolates renderToString,
  ;; not element construction (which the element-creation benches already cover).
  (let [cljs-el (cljs-tree)
        raw-el  (raw-tree)]
    (merge {:id :ssr/render-to-string
            :category :ssr
            :name "renderToString (small tree)"}
           (utils/bench-compare
             #(rdom-server/renderToString cljs-el)
             #(rdom-server/renderToString raw-el)
             {:iterations 2000 :samples 8}))))

(defn run-all []
  [(bench-ssr-render-to-string)])
