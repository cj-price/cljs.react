(ns cljs.react.demo.effects
  (:require ["react" :as react]
            [cljs.react.core :refer [use-ref use-effect use-memo use-callback use-state]]
            [cljs.react.demo.util :refer [CodeAndOutput Div P Span H2
                                          Button Input Section]])
  (:require-macros [cljs.react.core :refer [defnc]]))

(defn- pad2 [n]
  (.padStart (str n) 2 "0"))

(defnc EffectDemo
  []
  (let [now (use-state (js/Date.))
        running? (use-state true)
        ticks (use-state 0)]
    (use-effect
     (fn []
       (if @running?
         (let [id (js/setInterval
                   (fn []
                     (reset! now (js/Date.))
                     (swap! ticks inc))
                   1000)]
           (fn [] (js/clearInterval id)))
         js/undefined))
     [@running?])

    (let [t @now
          time-str (str (pad2 (.getHours t)) ":"
                        (pad2 (.getMinutes t)) ":"
                        (pad2 (.getSeconds t)))]
      (Div {:className "w-full space-y-4"}
        (Div {:className "text-center"}
          (Div {:className "text-5xl font-bold font-mono text-koi-orange tracking-wider tabular-nums"}
            time-str)
          (Div {:className "flex items-center justify-center gap-2 mt-2"}
            (Span {:className (str "inline-block w-2 h-2 rounded-full "
                                   (if @running? "bg-emerald-500 animate-pulse" "bg-gray-300"))})
            (Span {:className "text-xs uppercase tracking-wide text-gray-500"}
              (if @running? "ticking" "paused"))))
        (Div {:className "grid grid-cols-2 gap-3"}
          (Div {:className "p-3 bg-gray-50 border border-gray-200 rounded-lg text-center"}
            (Div {:className "text-2xl font-bold text-koi-orange"} @ticks)
            (Div {:className "text-xs text-gray-500 mt-1 uppercase tracking-wide"} "Ticks"))
          (Button {:className (str "rounded-lg font-medium shadow transition-colors "
                                   (if @running?
                                     "bg-gray-200 text-gray-700 hover:bg-gray-300"
                                     "bg-koi-orange text-white hover:bg-orange-600"))
                   :onClick #(swap! running? not)}
            (if @running? "Pause" "Resume")))
        (P {:className "text-xs text-gray-500 italic text-center"}
          "Effect sets up the interval; cleanup tears it down on pause/unmount.")))))

(defnc RefDemo
  []
  (let [value (use-state "")
        prev-ref (use-ref "")
        render-count (use-ref 0)
        current @value
        prev @prev-ref]
    (swap! render-count inc)
    (use-effect
     (fn []
       (reset! prev-ref current)
       js/undefined)
     [current])

    (Div {:className "w-full space-y-3"}
      (Input {:type "text"
              :value current
              :placeholder "Type to see the previous value…"
              :className "w-full px-4 py-2.5 border-2 border-gray-200 rounded-lg focus:border-koi-orange focus:ring-2 focus:ring-koi-orange/20 outline-none transition-all"
              :onChange #(reset! value (-> % .-target .-value))})
      (Div {:className "grid grid-cols-2 gap-3"}
        (Div {:className "p-3 bg-gray-50 border border-gray-200 rounded-lg"}
          (Div {:className "text-xs uppercase tracking-wide text-gray-500 mb-1"}
            "Previous")
          (Div {:className (str "text-sm font-mono break-all min-h-[1.25rem] "
                                (if (empty? prev) "text-gray-400 italic" "text-gray-800"))}
            (if (empty? prev) "—" prev)))
        (Div {:className "p-3 bg-orange-50 border border-orange-200 rounded-lg"}
          (Div {:className "text-xs uppercase tracking-wide text-koi-orange mb-1"}
            "Current")
          (Div {:className (str "text-sm font-mono break-all min-h-[1.25rem] "
                                (if (empty? current) "text-gray-400 italic" "text-gray-900 font-medium"))}
            (if (empty? current) "—" current))))
      (Div {:className "flex items-center justify-between text-xs text-gray-500"}
        (Span {:className "italic"}
          "Refs persist across renders without triggering them.")
        (Span {:className "font-mono px-2 py-0.5 bg-gray-100 rounded-full"}
          "renders: " @render-count)))))

(defnc ExpensiveComponent
  [{:keys [count on-click]}]
  (let [expensive-value (use-memo
                         (fn []
                           (js/console.log "Computing expensive value...")
                           (reduce + (range count)))
                         [count])
        memoized-callback (use-callback
                           (fn []
                             (js/console.log "Button clicked with count:" count)
                             (on-click))
                           [count on-click])]
    (Div {:className "w-full space-y-4"}
      (Div {:className "grid grid-cols-2 gap-3"}
        (Div {:className "p-3 bg-gray-50 border border-gray-200 rounded-lg text-center"}
          (Div {:className "text-2xl font-bold text-koi-orange"} count)
          (Div {:className "text-xs text-gray-500 mt-1 uppercase tracking-wide"} "Count"))
        (Div {:className "p-3 bg-gray-50 border border-gray-200 rounded-lg text-center"}
          (Div {:className "text-2xl font-bold text-koi-orange"} expensive-value)
          (Div {:className "text-xs text-gray-500 mt-1 uppercase tracking-wide"}
            "Sum 0–" (dec count))))
      (Button {:className "w-full px-5 py-2 bg-koi-orange text-white rounded-lg font-medium shadow hover:bg-orange-600 transition-colors"
               :onClick memoized-callback}
        "Increment (check console)"))))

(defnc MemoDemo
  []
  (let [count (use-state 1)]
    (ExpensiveComponent {:count @count
                         :on-click #(swap! count inc)})))

(defn use-toggle
  "Custom hook for toggle state"
  [initial-value]
  (let [[value set-value] (react/useState initial-value)
        toggle (use-callback
                (fn [] (set-value not))
                [])]
    [value toggle]))

(defn use-counter
  "Custom hook for counter with min/max bounds"
  [initial lo hi]
  (let [[count set-count] (react/useState initial)
        increment (use-callback
                   (fn [] (set-count #(min hi (inc %))))
                   [hi])
        decrement (use-callback
                   (fn [] (set-count #(max lo (dec %))))
                   [lo])
        reset (use-callback
               (fn [] (set-count initial))
               [initial])]
    {:count count
     :increment increment
     :decrement decrement
     :reset reset}))

(defnc CustomHooksDemo
  []
  (let [[is-on toggle] (use-toggle false)
        counter (use-counter 5 0 10)]
    (Div {:className "w-full space-y-5"}
      (Div {:className "p-4 bg-gray-50 border border-gray-200 rounded-lg space-y-3"}
        (Div {:className "flex items-center justify-between"}
          (Div
            (Div {:className "text-xs uppercase tracking-wide text-gray-500"} "useToggle")
            (Div {:className "text-lg font-semibold text-gray-900"}
              "Status: "
              (Span {:className (if is-on "text-emerald-600" "text-gray-400")}
                (if is-on "ON" "OFF"))))
          (Button {:className (str "px-4 py-2 rounded-lg font-medium transition-colors "
                                (if is-on
                                  "bg-koi-orange text-white shadow hover:bg-orange-600"
                                  "bg-gray-200 text-gray-700 hover:bg-gray-300"))
                   :onClick toggle}
            "Toggle")))
      (Div {:className "p-4 bg-gray-50 border border-gray-200 rounded-lg space-y-3"}
        (Div
          (Div {:className "text-xs uppercase tracking-wide text-gray-500"} "useCounter (0–10)")
          (Div {:className "text-3xl font-bold text-koi-orange mt-1"} (:count counter)))
        (Div {:className "flex gap-2"}
          (Button {:className "flex-1 px-3 py-2 bg-gray-200 text-gray-700 rounded-lg font-medium hover:bg-gray-300 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
                   :onClick (:decrement counter)
                   :disabled (= (:count counter) 0)}
            "−")
          (Button {:className "flex-1 px-3 py-2 bg-gray-100 text-gray-600 rounded-lg font-medium hover:bg-gray-200 transition-colors"
                   :onClick (:reset counter)}
            "Reset")
          (Button {:className "flex-1 px-3 py-2 bg-koi-orange text-white rounded-lg font-medium shadow hover:bg-orange-600 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
                   :onClick (:increment counter)
                   :disabled (= (:count counter) 10)}
            "+"))))))

(defnc EffectsTab
  []
  (Section
    (H2 "⚡ Side Effects & Hooks")

    (CodeAndOutput
     {:title "useEffect — Live Clock with Cleanup"
      :code "(defnc Clock\n  []\n  (let [now (use-state (js/Date.))\n        running? (use-state true)]\n    (use-effect\n      (fn []\n        (if @running?\n          ;; setup — return cleanup\n          (let [id (js/setInterval\n                    #(reset! now (js/Date.))\n                    1000)]\n            (fn [] (js/clearInterval id)))\n          js/undefined))\n      [@running?])\n    (Div\n      (P (str @now))\n      (Button {:onClick #(swap! running? not)}\n        (if @running? \"Pause\" \"Resume\")))))"}
     (EffectDemo))

    (CodeAndOutput
     {:title "useRef — Persist Across Renders"
      :code ";; use-ref is any mutable slot that persists across renders without causing them.\n\n(defnc PreviousValue\n  []\n  (let [value    (use-state \"\")\n        prev-ref (use-ref \"\")\n        current  @value]\n    ;; After each render, stash current as previous\n    (use-effect\n      (fn []\n        (reset! prev-ref current)\n        js/undefined)\n      [current])\n    (Div\n      (Input {:value current\n              :onChange #(reset! value\n                          (-> % .-target .-value))})\n      (P \"Previous: \" @prev-ref)\n      (P \"Current: \"  current))))"}
     (RefDemo))

    (CodeAndOutput
     {:title "Custom Hooks"
      :code "(defn use-toggle\n  [initial-value]\n  (let [[value set-value]\n          (react/useState initial-value)\n        toggle (use-callback\n                 (fn [] (set-value not))\n                 [])]\n    [value toggle]))\n\n(defnc CustomHookDemo\n  []\n  (let [[is-on toggle] (use-toggle false)]\n    (Div\n      (P \"Status: \"\n        (if is-on \"ON\" \"OFF\"))\n      (Button {:onClick toggle}\n        \"Toggle\"))))"}
     (CustomHooksDemo))))
