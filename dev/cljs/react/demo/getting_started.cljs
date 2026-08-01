(ns cljs.react.demo.getting-started
  (:require [cljs.react.sx :refer [use-sx]]
            [cljs.react.demo.ui :refer [Caption CodeBlock DemoTitle
                                        Mono Muted SectionTitle Stack]]
            [cljs.react.demo.util :refer [Div Section]])
  (:require-macros [cljs.react.core :refer [defnc]]))

(def ^:private deps-edn
  "{:deps
 {io.github.cj-price/cljs.react
  {:git/url \"https://github.com/cj-price/cljs.react.git\"
   :git/sha \"<latest commit sha>\"}}
 :paths [\"src\"]}")

(def ^:private npm-install
  "npm install react react-dom
npm install --save-dev shadow-cljs")

(def ^:private shadow-config
  "{:deps true
 :builds
 {:app {:target :browser
        :output-dir \"public/js\"
        :asset-path \"/js\"
        :modules {:main {:init-fn app.main/init}}
        :devtools {:http-root \"public\"
                   :http-port 8080}}}}")

(def ^:private index-html
  "<!doctype html>
<html>
  <head>
    <meta charset=\"utf-8\">
    <title>My App</title>
  </head>
  <body>
    <div id=\"app\"></div>
    <script src=\"/js/main.js\"></script>
  </body>
</html>")

(def ^:private main-cljs
  "(ns app.main
  (:require [cljs.react.core :refer [defnc Element use-state]]
            [cljs.react.dom :as dom]))

(defnc App []
  (let [n (use-state 0)]
    (Element {:tag \"div\"}
      (Element {:tag \"p\"} \"Count: \" @n)
      (Element {:tag \"button\" :onClick #(swap! n inc)} \"+1\"))))

(defonce root (dom/create-root (js/document.getElementById \"app\")))

(defn ^:dev/after-load reload []
  (dom/render root (App {})))

(defn ^:export init []
  (reload))")

(defnc GettingStartedTab
  []
  (Section
    (SectionTitle {} "🏁 Getting Started")
    (Div {:className (use-sx {:max-width "860px"})}
      (Muted {:className (use-sx {:mb 4})}
        "From an empty directory to a hot-reloading React app in five steps. "
        "cljs.react is a git dependency; React itself comes from npm.")

      (Div {:className (use-sx {:mb 4})}
        (DemoTitle {} "1. Add the dependencies")
        (Stack {:gap 1.5}
          (CodeBlock {:code deps-edn :label "deps.edn"})
          (CodeBlock {:code npm-install :label "shell" :language :bash})
          (Caption {}
            "Pin " (Mono {} ":git/sha")
            " to the latest commit on trunk. React and react-dom must be 19.")))

      (Div {:className (use-sx {:mb 4})}
        (DemoTitle {} "2. Configure shadow-cljs")
        (Stack {:gap 1.5}
          (CodeBlock {:code shadow-config :label "shadow-cljs.edn"})
          (Caption {}
            (Mono {} ":deps true") " reads dependencies from deps.edn. "
            (Mono {} ":init-fn")
            " names the function shadow-cljs calls once after the first load.")))

      (Div {:className (use-sx {:mb 4})}
        (DemoTitle {} "3. Add the host page")
        (Stack {:gap 1.5}
          (CodeBlock {:code index-html :label "public/index.html"
                      :language :html})
          (Caption {}
            "The dev server serves " (Mono {} ":http-root") ", and "
            (Mono {} ":output-dir")
            " writes the compiled bundle inside it, so the page and the script"
            " come from one place.")))

      (Div {:className (use-sx {:mb 4})}
        (DemoTitle {} "4. Write the entry namespace")
        (Stack {:gap 1.5}
          (CodeBlock {:code main-cljs :label "src/app/main.cljs"})
          (Caption {}
            (Mono {} "defonce")
            " keeps the React root alive across recompiles — a second "
            (Mono {} "create-root")
            " on the same element would replace the tree and drop its state.")
          (Caption {}
            (Mono {} "^:dev/after-load")
            " runs after every recompile and re-renders into the existing"
            " root. React reconciles, so component state — the counter above —"
            " survives the reload.")))

      (Div {}
        (DemoTitle {} "5. Run it")
        (Stack {:gap 1.5}
          (CodeBlock {:code "npx shadow-cljs watch app" :label "shell"
                      :language :bash})
          (Caption {}
            "Open http://localhost:8080, then edit " (Mono {} "App")
            " and save — the change renders in place and the counter keeps"
            " its value."))))))
