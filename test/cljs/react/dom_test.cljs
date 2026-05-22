(ns cljs.react.dom-test
  (:require
   [cljs.test :refer [deftest testing is]]
   [cljs.react.dom :as rdom]
   [cljs.react.core :refer [Element]]
   [cljs.react.form :as form]
   [cljs.react.hook :as hook]
   ["global-jsdom/register"]
   ["react-dom/server" :as rdom-server]
   ["@testing-library/react" :refer [render cleanup act]])
  (:require-macros [cljs.react.core :refer [defnc]]))

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

;;; SSR end-to-end: renderToString → hydrateRoot, verifying that
;;; useSyncExternalStore consumers (use-atom / use-db / use-selector) hydrate
;;; cleanly. Without get-server-snapshot wired through, React throws during
;;; hydration of any component that calls use-atom.

(defnc ^:private SsrCounter [{:keys [source label]}]
  (let [n (hook/use-atom source)]
    (Element {:tag "p" :id "ssr-counter"}
      (str label ": " n))))

(defn- capture-console-errors!
  "Patches js/console.error to capture calls in an atom. Returns a 0-arity
  fn that restores the original. React surfaces hydration mismatches via
  console.error, so capturing here gives us a real assertion target."
  [sink-atom]
  (let [orig (.-error js/console)]
    (set! (.-error js/console)
          (fn [& args] (swap! sink-atom conj (vec args))))
    #(set! (.-error js/console) orig)))

(deftest ssr-render-to-string-then-hydrate-test
  (testing "renderToString output hydrates without throwing or warning"
    (let [source (atom 7)
          element (SsrCounter {:source source :label "count"})
          ;; "Server" render: produce the HTML string. With Node + React 19,
          ;; renderToString synchronously emits the markup for our static tree.
          html    (rdom-server/renderToString element)
          ;; Sanity-check the SSR output before hydrating.
          _       (is (re-find #"count: 7" html)
                      "renderToString must include use-atom's snapshot")
          _       (is (re-find #"id=\"ssr-counter\"" html)
                      "renderToString must include the rendered DOM id")
          container (.createElement js/document "div")
          _         (.appendChild (.-body js/document) container)
          _         (set! (.-innerHTML container) html)
          errors    (atom [])
          restore!  (capture-console-errors! errors)
          root      (atom nil)]
      (try
        (act #(reset! root (rdom/hydrate-root container element)))
        ;; React reports hydration problems via console.error. None should
        ;; fire for matching server/client snapshots.
        (is (empty? @errors)
            (str "hydration emitted console.error(s): " (pr-str @errors)))
        ;; The hydrated tree should still reflect the source atom's value.
        (is (some? (.querySelector container "#ssr-counter")))
        (is (= "count: 7" (.-textContent container)))
        ;; Post-hydration reactivity: a client-side swap! must flow through.
        (act #(reset! source 42))
        (is (= "count: 42" (.-textContent container))
            "post-hydration updates must still re-render via use-atom")
        (finally
          (restore!)
          (act #(rdom/unmount @root))
          (.removeChild (.-body js/document) container))))))

(deftest ssr-render-to-string-then-hydrate-mismatch-test
  (testing "hydration with a mismatched client snapshot still completes (no throw)"
    ;; Documents the contract: get-server-snapshot is wired so React doesn't
    ;; throw the 'no getServerSnapshot' invariant; if server and client
    ;; snapshots diverge, React reconciles (and warns via console.error).
    ;; This test exists to lock in that the *invariant violation* fix from
    ;; the use-selector change does not regress.
    (let [source  (atom 1)
          element (SsrCounter {:source source :label "n"})
          html    (rdom-server/renderToString element)
          ;; Mutate the source *between* server render and hydration to
          ;; force a mismatch. React will warn, but must NOT throw, and
          ;; the client-side value must win after reconciliation.
          _       (reset! source 2)
          container (.createElement js/document "div")
          _         (.appendChild (.-body js/document) container)
          _         (set! (.-innerHTML container) html)
          errors    (atom [])
          restore!  (capture-console-errors! errors)
          root      (atom nil)]
      (try
        ;; The key assertion: hydration completes without an exception, even
        ;; with a mismatched snapshot. Without get-server-snapshot, React
        ;; would throw the 'Missing getServerSnapshot' invariant before any
        ;; mismatch reconciliation could run.
        (act #(reset! root (rdom/hydrate-root container element)))
        (is (some? @root) "hydrate-root must return a root despite mismatch")
        (is (= "n: 2" (.-textContent container))
            "client snapshot wins after reconciliation")
        (finally
          (restore!)
          (act #(rdom/unmount @root))
          (.removeChild (.-body js/document) container))))))

;;; SSR for use-form with a watchable :values — verifies that the
;;; add-watch effect (which won't run on the server) doesn't trip the
;;; server renderer, and the initial @-snapshot of the watchable surfaces
;;; in the rendered HTML.

(defnc ^:private SsrForm [{:keys [defaults]}]
  (let [handle (form/use-form {:values defaults})
        name   (form/use-field handle :name)]
    (Element {:tag "form"}
      (Element {:tag "input"
                :id "ssr-name"
                :defaultValue (:value name)}))))

(deftest ssr-use-form-watchable-values-test
  (testing "use-form with a watchable :values renders and hydrates cleanly"
    (let [defaults (atom {:name "Alice"})
          element  (SsrForm {:defaults defaults})
          html     (rdom-server/renderToString element)
          _        (is (re-find #"value=\"Alice\"" html)
                       "server render must snapshot the watchable's current value")
          container (.createElement js/document "div")
          _         (.appendChild (.-body js/document) container)
          _         (set! (.-innerHTML container) html)
          errors    (atom [])
          restore!  (capture-console-errors! errors)
          root      (atom nil)]
      (try
        ;; The watchable's add-watch lives behind a use-effect — those don't
        ;; run on the server. Hydration on the client wires the watch up;
        ;; this must complete without throwing.
        (act #(reset! root (rdom/hydrate-root container element)))
        (is (some? @root) "hydrate-root must succeed for use-form components")
        ;; After hydration, mutating the watchable should propagate into
        ;; un-dirtied fields — the watch-effect attached on the client.
        (act #(reset! defaults {:name "Bob"}))
        (let [input (.querySelector container "#ssr-name")]
          (is (= "Bob" (.-value input))
              "watchable :values change must flow through to the field post-hydration"))
        (finally
          (restore!)
          (act #(rdom/unmount @root))
          (.removeChild (.-body js/document) container))))))
