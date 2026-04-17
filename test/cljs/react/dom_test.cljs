(ns cljs.react.dom-test
  (:require
   [cljs.test :refer [deftest testing is]]
   [cljs.react.dom :as rdom]
   [cljs.react.core :refer [Element]]
   ["react" :as react]
   ["global-jsdom/register"]
   ["@testing-library/react" :refer [render cleanup act]]))

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

(deftest create-root-render-unmount-test
  (testing "create-root + render attaches element; unmount detaches it"
    (let [container (.createElement js/document "div")
          _ (.appendChild (.-body js/document) container)
          root (rdom/create-root container)]
      (act #(rdom/render root (Element {:tag "p" :id "mounted"} "hello")))
      (is (some? (.querySelector container "#mounted"))
          "element should be mounted inside container after render")
      (is (= "hello" (.-textContent container)))
      (act #(rdom/render root (Element {:tag "p" :id "mounted"} "updated")))
      (is (= "updated" (.-textContent container))
          "render on an existing root should update the tree")
      (act #(rdom/unmount root))
      (is (nil? (.querySelector container "#mounted"))
          "unmount should detach the tree")
      (.removeChild (.-body js/document) container))))

(deftest hydrate-root-test
  (testing "hydrate-root attaches to pre-rendered markup"
    (let [container (.createElement js/document "div")
          _ (.appendChild (.-body js/document) container)
          _ (set! (.-innerHTML container) "<p id=\"ssr\">server</p>")
          root (atom nil)]
      (act #(reset! root (rdom/hydrate-root
                           container
                           (Element {:tag "p" :id "ssr"} "server"))))
      (is (some? (.querySelector container "#ssr"))
          "hydrated element should be present after hydrateRoot")
      (is (= "server" (.-textContent container)))
      (act #(rdom/unmount @root))
      (.removeChild (.-body js/document) container))))
