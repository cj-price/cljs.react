(ns cljs.react.demo.effects
  (:require ["react" :as react]
            [cljs.react.core :refer [Element]]
            [cljs.react.hook :refer [use-ref use-effect use-memo use-callback use-state]]
            [cljs.react.demo.util :refer [CodeAndOutput]])
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

    (Element {:tag "div" :className "demo-box"}
      (Element {:tag "h3"} "useEffect Demo")
      (Element {:tag "p"} "Count: " @count)
      (Element {:tag "p" :className "effect-message"} "Message: " @message)
      (Element {:tag "button"
                :onClick #(swap! count inc)}
        "Increment"))))

(defnc RefDemo
  []
  (let [input-ref (use-ref)
        [value set-value] (react/useState "")
        focus-input (fn []
                      (.focus @input-ref))]
    (Element {:tag "div" :className "demo-box"}
      (Element {:tag "h3"} "useRef Demo")
      (Element {:tag "input"
                :ref input-ref
                :type "text"
                :value value
                :placeholder "Click focus button..."
                :onChange #(set-value (-> % .-target .-value))})
      (Element {:tag "button"
                :onClick focus-input}
        "Focus Input")
      (Element {:tag "p"} "Value: " value))))

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
    (Element {:tag "div" :className "demo-box"}
      (Element {:tag "h3"} "useMemo & useCallback")
      (Element {:tag "p"} "Count: " count)
      (Element {:tag "p"} "Sum of 0 to " (dec count) ": " expensive-value)
      (Element {:tag "button"
                :onClick memoized-callback}
        "Increment (check console)"))))

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
  [initial min max]
  (let [[count set-count] (react/useState initial)
        increment (use-callback
                   (fn [] (set-count #(min max (inc %))))
                   [max])
        decrement (use-callback
                   (fn [] (set-count #(cljs.core/max min (dec %))))
                   [min])
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
    (Element {:tag "div" :className "demo-box"}
      (Element {:tag "h3"} "Custom Hooks")
      (Element {:tag "div"}
        (Element {:tag "h4"} "useToggle Hook")
        (Element {:tag "p"} "Status: " (if is-on "ON" "OFF"))
        (Element {:tag "button" :onClick toggle}
          "Toggle"))
      (Element {:tag "div"}
        (Element {:tag "h4"} "useCounter Hook (0-10)")
        (Element {:tag "p"} "Count: " (:count counter))
        (Element {:tag "div" :className "button-group"}
          (Element {:tag "button"
                    :onClick (:decrement counter)
                    :disabled (= (:count counter) 0)}
            "Dec")
          (Element {:tag "button" :onClick (:reset counter)}
            "Reset")
          (Element {:tag "button"
                    :onClick (:increment counter)
                    :disabled (= (:count counter) 10)}
            "Inc"))))))

(defnc EffectsTab
  []
  (Element {:tag "section"}
    (Element {:tag "h2"} "⚡ Side Effects & Hooks")

    (CodeAndOutput
     {:title "useEffect Hook"
      :code "(defnc EffectDemo\n  []\n  (let [count (use-state 0)\n        message (use-state \"Mounted\")]\n    ;; Effect runs on mount\n    (use-effect\n      (fn []\n        (reset! message \"Component mounted\")\n        ;; Cleanup function\n        (fn [] (js/console.log \"Unmount\")))\n      [])\n    ;; Effect runs when count changes\n    (use-effect\n      (fn []\n        (reset! message (str \"Count: \" @count))\n        js/undefined)\n      [@count])\n    ...))"}
     (EffectDemo))

    (CodeAndOutput
     {:title "useRef Hook"
      :code "(defnc RefDemo\n  []\n  (let [input-ref (use-ref)\n        focus-input (fn []\n                      (.focus @input-ref))]\n    (Element {:tag \"div\"}\n      (Element {:tag \"input\"\n                :ref input-ref\n                :type \"text\"})\n      (Element {:tag \"button\"\n                :onClick focus-input}\n        \"Focus Input\"))))"}
     (RefDemo))

    (CodeAndOutput
     {:title "Custom Hooks"
      :code "(defn use-toggle\n  [initial-value]\n  (let [[value set-value]\n          (react/useState initial-value)\n        toggle (use-callback\n                 (fn [] (set-value not))\n                 [])]\n    [value toggle]))\n\n(defnc CustomHookDemo\n  []\n  (let [[is-on toggle] (use-toggle false)]\n    (Element {:tag \"div\"}\n      (Element {:tag \"p\"} \"Status: \"\n        (if is-on \"ON\" \"OFF\"))\n      (Element {:tag \"button\"\n                :onClick toggle}\n        \"Toggle\"))))"}
     (CustomHooksDemo))))
