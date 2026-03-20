(ns cljs.react.demo.basics
  (:require [cljs.react.core :refer [Element]]
            [cljs.react.demo.util :refer [CodeAndOutput]])
  (:require-macros [cljs.react.core :refer [defnc]]))

(defnc HelloWorld
  []
  (Element {:tag "div" :className "text-xl font-medium text-gray-700"}
    "Hello" " " "World!"))

(defnc Greeting
  [{:keys [name emoji]}]
  (Element {:tag "div" :className "text-xl text-gray-700"}
    (Element {:tag "span" :className "text-2xl mr-2"} emoji)
    (Element {:tag "strong" :className "font-semibold text-koi-orange"} "Hello, " name "!")))

(defnc CardWithChildren
  [{:keys [title children]}]
  (Element {:tag "div" :className "card"}
    (Element {:tag "h3" :className "card-title"} title)
    (Element {:tag "div" :className "card-content"} children)))

(defnc BasicsTab
  []
  (Element {:tag "section"}
    (Element {:tag "h2"} "🧱 Basic Components")

    (CodeAndOutput
     {:title "Simple Component"
      :code "(defnc HelloWorld\n  []\n  (Element {:tag \"div\"}\n    \"Hello\" \" \" \"World!\"))"}
     (HelloWorld))

    (CodeAndOutput
     {:title "Props Destructuring"
      :code "(defnc Greeting\n  [{:keys [name emoji]}]\n  (Element {:tag \"div\"}\n    (Element {:tag \"span\"} emoji \" \")\n    (Element {:tag \"strong\"} \"Hello, \" name \"!\")))\n\n(Greeting {:name \"ClojureScript\"\n           :emoji \"👋\"})"}
     (Greeting {:name "ClojureScript" :emoji "👋"}))

    (CodeAndOutput
     {:title "Children Passing"
      :code "(defnc CardWithChildren\n  [{:keys [title children]}]\n  (Element {:tag \"div\" :className \"card\"}\n    (Element {:tag \"h3\"} title)\n    (Element {:tag \"div\"} children)))\n\n(CardWithChildren {:title \"Card Title\"}\n  (Element {:tag \"p\"} \"Content 1\")\n  (Element {:tag \"p\"} \"Content 2\"))"}
     (CardWithChildren {:title "Card Title"}
       (Element {:tag "p"} "This is the card content.")
       (Element {:tag "p"} "Multiple children are supported!")))))
