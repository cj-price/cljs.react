(ns cljs.react.demo.effects
  (:require ["react" :as react]
            [cljs.react.core :refer [use-ref use-effect use-memo use-callback use-state]]
            [cljs.react.sx :refer [use-sx]]
            [cljs.react.demo.ui :refer [CodeAndOutput Btn Row Stack Grid Panel
                                        TextInput SectionTitle Caption Badge
                                        Stat PulseDot]]
            [cljs.react.demo.util :refer [Div Span Section]])
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
                        (pad2 (.getSeconds t)))
          ;; Unconditional — the paused dot below is one branch of an `if`, and
          ;; a hook that only runs in one branch changes the render's hook count.
          idle-dot-cls (use-sx {:display :inline-block
                                :width "0.5rem" :height "0.5rem"
                                :border-radius "50%"
                                :bgcolor :palette.grey.300})]
      (Stack {:gap 2}
        (Stack {:gap 1 :align :center}
          (Div {:className (use-sx {:font-size "3rem" :font-weight 700
                                    :line-height 1 :letter-spacing "0.04em"
                                    :font-variant-numeric "tabular-nums"
                                    :font-family :typography.font-family-mono
                                    :color :palette.primary.main})}
            time-str)
          (Row {:gap 1 :justify :center}
            (if @running?
              (PulseDot {:tone :success})
              (Span {:className idle-dot-cls}))
            (Span {:className (use-sx {:font-size "0.75rem"
                                       :text-transform :uppercase
                                       :letter-spacing "0.06em"
                                       :color :palette.text.secondary})}
              (if @running? "ticking" "paused"))))
        (Grid {:min-width "140px" :gap 1.5}
          (Stat {:label "Ticks" :value @ticks :tone :primary})
          (Btn {:variant (if @running? :secondary :primary)
                :full? true
                :onClick #(swap! running? not)}
            (if @running? "Pause" "Resume")))
        (Caption {:className (use-sx {:text-align :center})}
          "Effect sets up the interval; cleanup tears it down on pause/unmount.")))))

(defnc ValueBox
  [{:keys [label value tone]}]
  (Panel {:className (use-sx [{:display :flex :flex-direction :column :gap 0.5}
                              (when (= :primary tone)
                                {:bgcolor :palette.surface.tint})])}
    (Div {:className (use-sx {:font-size "0.6875rem" :text-transform :uppercase
                              :letter-spacing "0.06em"
                              :color (if (= :primary tone)
                                       :palette.primary.main
                                       :palette.text.secondary)})}
      label)
    (Div {:className (use-sx [{:font-size "0.875rem" :min-height "1.25rem"
                               :overflow-wrap :anywhere
                               :font-family :typography.font-family-mono}
                              (if (empty? value)
                                {:color :palette.text.disabled :font-style :italic}
                                {:color :palette.text.primary})])}
      (if (empty? value) "—" value))))

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

    (Stack {:gap 1.5}
      (TextInput {:value current
                  :placeholder "Type to see the previous value…"
                  :onChange #(reset! value (-> % .-target .-value))})
      (Grid {:min-width "160px" :gap 1.5}
        (ValueBox {:label "Previous" :value prev})
        (ValueBox {:label "Current" :value current :tone :primary}))
      (Row {:justify :space-between :gap 1}
        (Caption {:className (use-sx {:font-style :italic})}
          "Refs persist across renders without triggering them.")
        (Badge {:tone :neutral} "renders: " @render-count)))))

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
    (Stack {:gap 2}
      (Grid {:min-width "140px" :gap 1.5}
        (Stat {:label "Count" :value count :tone :primary})
        (Stat {:label (str "Sum 0–" (dec count)) :value expensive-value
               :tone :primary}))
      (Btn {:full? true :onClick memoized-callback}
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

(defnc HookLabel
  [{:keys [children]}]
  (apply Div {:className (use-sx {:font-size "0.6875rem" :text-transform :uppercase
                                  :letter-spacing "0.06em"
                                  :color :palette.text.secondary})}
    children))

(defnc CustomHooksDemo
  []
  (let [[is-on toggle] (use-toggle false)
        counter (use-counter 5 0 10)]
    (Stack {:gap 2.5}
      (Panel {}
        (Row {:justify :space-between :gap 1.5}
          (Div
            (HookLabel {} "useToggle")
            (Div {:className (use-sx {:font-size "1.125rem" :font-weight 600
                                      :color :palette.text.primary})}
              "Status: "
              (Span {:className (use-sx {:color (if is-on
                                                  :palette.success.main
                                                  :palette.text.disabled)})}
                (if is-on "ON" "OFF"))))
          (Btn {:variant (if is-on :primary :secondary) :onClick toggle}
            "Toggle")))

      (Panel {:className (use-sx {:display :flex :flex-direction :column :gap 1.5})}
        (Div
          (HookLabel {} "useCounter (0–10)")
          (Div {:className (use-sx {:mt 0.5 :font-size "2rem" :font-weight 700
                                    :line-height 1
                                    :font-family :typography.font-family-mono
                                    :color :palette.primary.main})}
            (:count counter)))
        (Row {:gap 1 :wrap? false}
          (Btn {:variant :secondary :full? true
                :onClick (:decrement counter)
                :disabled (= (:count counter) 0)}
            "−")
          (Btn {:variant :ghost :full? true :onClick (:reset counter)} "Reset")
          (Btn {:full? true
                :onClick (:increment counter)
                :disabled (= (:count counter) 10)}
            "+"))))))

(defnc EffectsTab
  []
  (Section
    (SectionTitle {} "⚡ Side Effects & Hooks")

    (CodeAndOutput
     {:title "useEffect — Live Clock with Cleanup"
      :code "(defnc Clock\n  []\n  (let [now (use-state (js/Date.))\n        running? (use-state true)]\n    (use-effect\n      (fn []\n        (if @running?\n          ;; setup — return cleanup\n          (let [id (js/setInterval\n                    #(reset! now (js/Date.))\n                    1000)]\n            (fn [] (js/clearInterval id)))\n          js/undefined))\n      [@running?])\n    (Div\n      (P (str @now))\n      (Button {:onClick #(swap! running? not)}\n        (if @running? \"Pause\" \"Resume\")))))"}
     (EffectDemo))

    (CodeAndOutput
     {:title "useRef — Persist Across Renders"
      :code ";; use-ref: a mutable slot that survives renders.\n\n(defnc PreviousValue\n  []\n  (let [value    (use-state \"\")\n        prev-ref (use-ref \"\")\n        current  @value]\n    ;; After render, stash current as previous\n    (use-effect\n      (fn []\n        (reset! prev-ref current)\n        js/undefined)\n      [current])\n    (Div\n      (Input {:value current\n              :onChange #(reset! value\n                          (-> % .-target .-value))})\n      (P \"Previous: \" @prev-ref)\n      (P \"Current: \"  current))))"}
     (RefDemo))

    (CodeAndOutput
     {:title "useMemo & useCallback"
      :code "(defnc Expensive\n  [{:keys [count on-click]}]\n  (let [total (use-memo\n                (fn [] (reduce + (range count)))\n                [count])\n        cb    (use-callback\n                (fn [] (on-click))\n                [count on-click])]\n    (Div\n      (P \"Sum: \" total)\n      (Button {:onClick cb} \"Increment\"))))"}
     (MemoDemo))

    (CodeAndOutput
     {:title "Custom Hooks"
      :code "(defn use-toggle\n  [initial-value]\n  (let [[value set-value]\n          (react/useState initial-value)\n        toggle (use-callback\n                 (fn [] (set-value not))\n                 [])]\n    [value toggle]))\n\n(defnc CustomHookDemo\n  []\n  (let [[is-on toggle] (use-toggle false)]\n    (Div\n      (P \"Status: \"\n        (if is-on \"ON\" \"OFF\"))\n      (Button {:onClick toggle}\n        \"Toggle\"))))"}
     (CustomHooksDemo))))
