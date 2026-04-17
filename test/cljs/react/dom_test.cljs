(ns cljs.react.dom-test
  (:require
   [cljs.test :refer [deftest testing is]]
   [cljs.react.dom :as rdom]
   [cljs.react.core :refer [Element]]
   ["react" :as react]
   ["global-jsdom/register"]
   ["@testing-library/react" :refer [render cleanup]]))

(deftest create-portal-test
  (testing "create-portal renders children into the target DOM node"
    (let [portal-target (.createElement js/document "div")
          _ (.setAttribute portal-target "id" "portal-target")
          _ (.appendChild (.-body js/document) portal-target)
          child (Element {:tag "p" :id "portal-child"} "inside portal")
          portal (rdom/create-portal child portal-target)
          result (render (Element {:tag "div"} "normal content" portal))]
      ;; The portal content is NOT in the render container
      (let [container (.-container result)]
        (is (nil? (.querySelector container "#portal-child"))
            "portal content must not be in the React container"))
      ;; But it IS in the portal-target
      (is (= "inside portal" (.-textContent portal-target)))
      (is (some? (.querySelector portal-target "#portal-child")))
      (cleanup)
      (.removeChild (.-body js/document) portal-target))))
