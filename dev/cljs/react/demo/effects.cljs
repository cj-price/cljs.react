(ns cljs.react.demo.effects
  (:require ["react" :as react]
            [cljs.react.hook :refer [use-ref use-effect use-memo use-callback use-state]]
            [cljs.react.demo.util :refer [CodeAndOutput Div P H2 H3 H4
                                          Button Input Section]])
  (:require-macros [cljs.react.core :refer [defnc]]))

(defnc EffectDemo
  []
  (let [count (use-state 0)
        message (use-state "Component mounted")]
    (use-effect
     (fn []
       (reset! message "Component mounted")
       (fn [] (js/console.log "Component will unmount")))
     [])

    (use-effect
     (fn []
       (reset! message (str "Count changed to " @count))
       js/undefined)
     [@count])

    (Div {:className "demo-box"}
      (H3 "useEffect Demo")
      (P "Count: " @count)
      (P {:className "effect-message"} "Message: " @message)
      (Button {:onClick #(swap! count inc)} "Increment"))))

(defnc RefDemo
  []
  (let [input-ref (use-ref)
        [value set-value] (react/useState "")
        focus-input (fn []
                      (.focus @input-ref))]
    (Div {:className "demo-box"}
      (H3 "useRef Demo")
      (Input {:ref input-ref
              :type "text"
              :value value
              :placeholder "Click focus button..."
              :onChange #(set-value (-> % .-target .-value))})
      (Button {:onClick focus-input} "Focus Input")
      (P "Value: " value))))

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
    (Div {:className "demo-box"}
      (H3 "useMemo & useCallback")
      (P "Count: " count)
      (P "Sum of 0 to " (dec count) ": " expensive-value)
      (Button {:onClick memoized-callback} "Increment (check console)"))))

(defnc MemoDemo
  []
  (let [[count set-count] (react/useState 1)]
    (ExpensiveComponent {:count count
                         :on-click #(set-count inc)})))

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
    (Div {:className "demo-box"}
      (H3 "Custom Hooks")
      (Div
        (H4 "useToggle Hook")
        (P "Status: " (if is-on "ON" "OFF"))
        (Button {:onClick toggle} "Toggle"))
      (Div
        (H4 "useCounter Hook (0-10)")
        (P "Count: " (:count counter))
        (Div {:className "button-group"}
          (Button {:onClick (:decrement counter)
                   :disabled (= (:count counter) 0)}
            "Dec")
          (Button {:onClick (:reset counter)} "Reset")
          (Button {:onClick (:increment counter)
                   :disabled (= (:count counter) 10)}
            "Inc"))))))

(defnc EffectsTab
  []
  (Section
    (H2 "⚡ Side Effects & Hooks")

    (CodeAndOutput
     {:title "useEffect Hook"
      :code "(defnc EffectDemo\n  []\n  (let [count (use-state 0)\n        message (use-state \"Mounted\")]\n    ;; Effect runs on mount\n    (use-effect\n      (fn []\n        (reset! message \"Component mounted\")\n        ;; Cleanup function\n        (fn [] (js/console.log \"Unmount\")))\n      [])\n    ;; Effect runs when count changes\n    (use-effect\n      (fn []\n        (reset! message (str \"Count: \" @count))\n        js/undefined)\n      [@count])\n    ...))"}
     (EffectDemo))

    (CodeAndOutput
     {:title "useRef Hook"
      :code "(defnc RefDemo\n  []\n  (let [input-ref (use-ref)\n        focus-input (fn []\n                      (.focus @input-ref))]\n    (Div\n      (Input {:ref input-ref\n              :type \"text\"})\n      (Button {:onClick focus-input}\n        \"Focus Input\"))))"}
     (RefDemo))

    (CodeAndOutput
     {:title "Custom Hooks"
      :code "(defn use-toggle\n  [initial-value]\n  (let [[value set-value]\n          (react/useState initial-value)\n        toggle (use-callback\n                 (fn [] (set-value not))\n                 [])]\n    [value toggle]))\n\n(defnc CustomHookDemo\n  []\n  (let [[is-on toggle] (use-toggle false)]\n    (Div\n      (P \"Status: \"\n        (if is-on \"ON\" \"OFF\"))\n      (Button {:onClick toggle}\n        \"Toggle\"))))"}
     (CustomHooksDemo))))
