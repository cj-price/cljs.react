(ns cljs.react.reagent-interop-test
  (:require
   [cljs.test :refer [deftest testing is]]
   [reagent.core :as r]
   [cljs.react.core :refer [Element]]
   ["global-jsdom/register"]
   ["@testing-library/react" :refer [render cleanup act]])
  (:require-macros [cljs.react.core :refer [defnc]]))

(defn- reagent-counter
  [props]
  (let [n (r/atom (:start props))]
    (fn [_]
      [:button {:on-click #(swap! n inc)} (str "n: " @n)])))

(deftest reagent-to-cljs-react-test
  (testing "a reactified Reagent component renders and re-renders in an Element tree"
    (let [Counter (r/reactify-component reagent-counter)
          result  (render (Element {:tag Counter :start 3}))
          btn     (.querySelector (.-container result) "button")]
      (is (= "n: 3" (.-textContent btn)))
      (act (fn []
             (.click btn)
             (r/flush)))
      (is (= "n: 4" (.-textContent (.querySelector (.-container result) "button")))
          "the r/atom swap re-rendered through Reagent inside the cljs.react tree")
      (cleanup))))

(defnc Chip
  [{:keys [label]}]
  (Element {:tag "span" :id "chip"} label))

(deftest cljs-react-in-reagent-hiccup-test
  (testing "a defnc call is a valid hiccup child inside a reactified parent"
    (let [parent  (fn [_] [:div (Chip {:label "hi"})])
          result  (render (Element {:tag (r/reactify-component parent)}))
          chip    (.querySelector (.-container result) "#chip")]
      (is (some? chip))
      (is (= "hi" (.-textContent chip)))
      (cleanup))))
