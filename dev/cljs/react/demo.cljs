(ns cljs.react.demo
  (:require ["react" :as react]
            ["react-dom/client" :as react-dom]
            ["prismjs" :as Prism]
            ["prismjs/components/prism-clojure"]
            [cljs.react.core :refer [Element DBProvider use-cursor]]
            [cljs.react.hook :refer [use-ref use-effect use-memo use-callback use-state]]
            [clojure.string :as str])
  (:require-macros [cljs.react.core :refer [defnc]]))

;; =============================================================================
;; Demo Helper - Code & Output Display
;; =============================================================================

(defnc CodeAndOutput
  [{:keys [code title children]}]
  (Element {:tag "div"}
    (when title
      (Element {:tag "h4"} title))
    (Element {:tag "div" :className "demo-columns"}
      (Element {:tag "div"}
        (Element {:tag "pre" :className "language-clojure"}
          (Element {:tag "code"
                    :className "language-clojure"
                    :dangerouslySetInnerHTML
                    #js {:__html (.highlight Prism code
                                   (.-clojure (.-languages Prism))
                                   "clojure")}})))
      (Element {:tag "div" :className "demo-output"}
        children))))

;; =============================================================================
;; Basic Components
;; =============================================================================

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

;; =============================================================================
;; Stateful Components - useState
;; =============================================================================

(defnc Counter
  []
  (let [count (use-state 0)]
    (Element {:tag "div" :className "space-y-4"}
      (Element {:tag "div" :className "text-center"}
        (Element {:tag "div" :className "text-5xl font-bold text-koi-orange mb-2"} @count)
        (Element {:tag "p" :className "text-sm text-gray-600"} "Current Count"))
      (Element {:tag "div" :className "flex gap-2 justify-center"}
        (Element {:tag "button"
                  :className "px-6 py-2 bg-gray-200 text-gray-700 rounded-lg font-medium hover:bg-gray-300"
                  :onClick #(swap! count dec)}
          "−")
        (Element {:tag "button"
                  :className "px-6 py-2 bg-gray-100 text-gray-600 rounded-lg font-medium hover:bg-gray-200"
                  :onClick #(reset! count 0)}
          "Reset")
        (Element {:tag "button"
                  :className "px-6 py-2 bg-koi-orange text-white rounded-lg font-medium shadow-lg"
                  :onClick #(swap! count inc)}
          "+")))))

(defnc TextInput
  []
  (let [text (use-state "")]
    (Element {:tag "div" :className "space-y-3"}
      (Element {:tag "input"
                :type "text"
                :value @text
                :placeholder "Type something..."
                :className "w-full px-4 py-3 border-2 border-gray-200 rounded-lg focus:border-koi-orange transition-colors"
                :onChange #(reset! text (-> % .-target .-value))})
      (Element {:tag "div" :className "flex justify-between text-sm"}
        (Element {:tag "p" :className "text-gray-600"}
          "You typed: "
          (Element {:tag "span" :className "font-medium text-gray-900"}
            (if (empty? @text) "(nothing)" @text)))
        (Element {:tag "p" :className "text-gray-500"}
          "Characters: "
          (Element {:tag "span" :className "font-semibold text-koi-orange"}
            (count @text)))))))

(defnc TodoList
  []
  (let [todos (use-state [])
        input (use-state "")
        add-todo (fn []
                   (when-not (empty? @input)
                     (swap! todos conj {:id (random-uuid)
                                        :text @input
                                        :done false})
                     (reset! input "")))
        toggle-todo (fn [id]
                      (swap! todos (fn [ts]
                                     (mapv (fn [todo]
                                             (if (= (:id todo) id)
                                               (update todo :done not)
                                               todo))
                                           ts))))
        remove-todo (fn [id]
                      (swap! todos (fn [ts]
                                     (filterv (fn [todo] (not= (:id todo) id)) ts))))]
    (Element {:tag "div" :className "demo-box"}
      (Element {:tag "h3"} "Todo List (" (count @todos) " items)")
      (Element {:tag "div" :className "input-group"}
        (Element {:tag "input"
                  :type "text"
                  :value @input
                  :placeholder "Add a todo..."
                  :onKeyPress #(when (= (.-key %) "Enter") (add-todo))
                  :onChange #(reset! input (-> % .-target .-value))})
        (Element {:tag "button"
                  :onClick add-todo}
          "Add"))
      (Element {:tag "ul" :className "todo-list"}
        (for [todo @todos]
          (Element {:tag "li"
                    :key (str (:id todo))
                    :className (if (:done todo) "done" "")}
            (Element {:tag "input"
                      :type "checkbox"
                      :checked (:done todo)
                      :onChange #(toggle-todo (:id todo))})
            (Element {:tag "span"} (:text todo))
            (Element {:tag "button"
                      :className "delete-btn"
                      :onClick #(remove-todo (:id todo))}
              "×")))))))

;; =============================================================================
;; useEffect Hook
;; =============================================================================

(defnc EffectDemo
  []
  (let [count (use-state 0)
        message (use-state "Component mounted")]
    ;; Effect runs on mount and unmount
    (use-effect
     (fn []
       (reset! message "Component mounted")
       ;; Cleanup function
       (fn [] (js/console.log "Component will unmount")))
     []) ; Empty deps = run once on mount

    ;; Effect runs when count changes
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

;; =============================================================================
;; useReducer Hook
;; =============================================================================

(defnc ReducerCounter
  []
  (let [reducer (fn [state action]
                  (case (:type action)
                    :increment (update state :count inc)
                    :decrement (update state :count dec)
                    :reset (assoc state :count 0)
                    :add (update state :count + (:value action))
                    state))
        [state dispatch] (react/useReducer reducer #js {:count 0})]
    (Element {:tag "div" :className "demo-box"}
      (Element {:tag "h3"} "useReducer Counter: " (.-count state))
      (Element {:tag "div" :className "button-group"}
        (Element {:tag "button"
                  :onClick #(dispatch #js {:type :decrement})}
          "-1")
        (Element {:tag "button"
                  :onClick #(dispatch #js {:type :reset})}
          "Reset")
        (Element {:tag "button"
                  :onClick #(dispatch #js {:type :increment})}
          "+1")
        (Element {:tag "button"
                  :onClick #(dispatch #js {:type :add :value 10})}
          "+10")))))

;; =============================================================================
;; useRef Hook
;; =============================================================================

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

;; =============================================================================
;; useMemo and useCallback Hooks
;; =============================================================================

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

;; =============================================================================
;; Custom Hooks
;; =============================================================================

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

;; =============================================================================
;; Props and Data Flow
;; =============================================================================

(defnc NestedComponent
  [{:keys [level message]}]
  (Element {:tag "div" :className "nested-component"}
    (Element {:tag "h4"} "Level " level)
    (Element {:tag "p"} message)))

(defnc PropsDemo
  []
  (Element {:tag "div" :className "demo-box"}
    (Element {:tag "h3"} "Props & Data Flow")
    (Element {:tag "p"} "Components receive immutable CLJS data structures as props:")
    (NestedComponent {:level 1 :message "First nested component"})
    (NestedComponent {:level 2 :message "Second nested component"})
    (NestedComponent {:level 3 :message "Third nested component"})))

;; =============================================================================
;; Composition and Nesting
;; =============================================================================

(defnc Badge
  [{:keys [text type]}]
  (Element {:tag "span"
            :className (str "badge badge-" (name type))}
    text))

(defnc UserCard
  [{:keys [name email role active]}]
  (Element {:tag "div" :className (str "user-card" (when-not active " inactive"))}
    (Element {:tag "div" :className "user-header"}
      (Element {:tag "h4"} name)
      (Badge {:text (clojure.string/upper-case role) :type role}))
    (Element {:tag "p" :className "user-email"} email)
    (Element {:tag "p" :className "user-status"}
      (if active "✓ Active" "○ Inactive"))))

(defnc CompositionDemo
  []
  (let [users [{:id 1 :name "Alice Johnson" :email "alice@example.com" :role "admin" :active true}
               {:id 2 :name "Bob Smith" :email "bob@example.com" :role "user" :active true}
               {:id 3 :name "Carol White" :email "carol@example.com" :role "moderator" :active false}]]
    (Element {:tag "div" :className "demo-box"}
      (Element {:tag "h3"} "Component Composition")
      (Element {:tag "div" :className "user-grid"}
        (for [user users]
          (Element {:tag "div" :key (:id user)}
            (UserCard user)))))))

;; =============================================================================
;; Performance - Memoization Behavior
;; =============================================================================

(defnc RenderCounter
  [{:keys [name]}]
  (let [render-count (use-ref 0)]
    (swap! render-count inc)
    (Element {:tag "div" :className "render-counter"}
      (Element {:tag "strong"} name)
      (Element {:tag "span"} " - Renders: " @render-count))))

(defnc MemoizationDemo
  []
  (let [[count set-count] (react/useState 0)
        [unrelated-state set-unrelated-state] (react/useState "")]
    (Element {:tag "div" :className "demo-box"}
      (Element {:tag "h3"} "Memoization Behavior")
      (Element {:tag "p"} "Components are memoized with React.memo using CLJS equality")
      (Element {:tag "div" :className "button-group"}
        (Element {:tag "button" :onClick #(set-count inc)}
          "Increment Count")
        (Element {:tag "button" :onClick #(set-unrelated-state (str (random-uuid)))}
          "Update Unrelated State"))
      (Element {:tag "div" :className "memo-test"}
        (RenderCounter {:name "Parent Component"})
        (RenderCounter {:name (str "Child with count=" count)})
        (Element {:tag "p" :className "hint"}
          "Try clicking 'Update Unrelated State' - child shouldn't re-render!")))))

;; =============================================================================
;; Global State with DBProvider and Cursors
;; =============================================================================

(defnc DBCounterDisplay
  []
  (let [count (use-cursor [:counter :value])]
    (Element {:tag "div" :className "text-center"}
      (Element {:tag "div" :className "text-5xl font-bold text-koi-orange mb-2"} @count)
      (Element {:tag "p" :className "text-sm text-gray-600"} "Shared Counter Value"))))

(defnc DBCounterControls
  []
  (let [count (use-cursor [:counter :value])]
    (Element {:tag "div" :className "flex gap-2 justify-center"}
      (Element {:tag "button"
                :className "px-6 py-2 bg-gray-200 text-gray-700 rounded-lg font-medium hover:bg-gray-300"
                :onClick #(swap! count dec)}
        "-")
      (Element {:tag "button"
                :className "px-6 py-2 bg-gray-100 text-gray-600 rounded-lg font-medium hover:bg-gray-200"
                :onClick #(reset! count 0)}
        "Reset")
      (Element {:tag "button"
                :className "px-6 py-2 bg-koi-orange text-white rounded-lg font-medium shadow-lg"
                :onClick #(swap! count inc)}
        "+"))))

(defnc DBUserForm
  []
  (let [name (use-cursor [:user :name])
        email (use-cursor [:user :email])]
    (Element {:tag "div" :className "space-y-3"}
      (Element {:tag "div"}
        (Element {:tag "label" :className "block text-sm font-medium text-gray-700 mb-1"} "Name")
        (Element {:tag "input"
                  :type "text"
                  :value (or @name "")
                  :className "w-full px-4 py-2 border-2 border-gray-200 rounded-lg focus:border-koi-orange transition-colors"
                  :onChange #(reset! name (-> % .-target .-value))}))
      (Element {:tag "div"}
        (Element {:tag "label" :className "block text-sm font-medium text-gray-700 mb-1"} "Email")
        (Element {:tag "input"
                  :type "email"
                  :value (or @email "")
                  :className "w-full px-4 py-2 border-2 border-gray-200 rounded-lg focus:border-koi-orange transition-colors"
                  :onChange #(reset! email (-> % .-target .-value))})))))

(defnc DBUserDisplay
  []
  (let [name (use-cursor [:user :name])
        email (use-cursor [:user :email])]
    (Element {:tag "div" :className "p-4 bg-gray-50 rounded-lg"}
      (Element {:tag "p" :className "text-gray-700"}
        (Element {:tag "strong"} "Name: ") (or @name "(empty)"))
      (Element {:tag "p" :className "text-gray-700"}
        (Element {:tag "strong"} "Email: ") (or @email "(empty)")))))

(defnc DBDemo
  []
  (DBProvider {:initial-value {:counter {:value 0}
                               :user {:name "" :email ""}}}
    (Element {:tag "div" :className "space-y-6"}
      (Element {:tag "div" :className "space-y-4"}
        (Element {:tag "h4" :className "font-medium text-gray-700"} "Shared Counter (two components, one cursor each)")
        (DBCounterDisplay)
        (DBCounterControls))
      (Element {:tag "div" :className "space-y-4"}
        (Element {:tag "h4" :className "font-medium text-gray-700"} "User Form (cursors to nested paths)")
        (Element {:tag "div" :className "grid grid-cols-2 gap-4"}
          (DBUserForm)
          (DBUserDisplay))))))

;; =============================================================================
;; Main App Component
;; =============================================================================

(defnc App
  []
  (let [[selected-section set-selected-section] (react/useState :basics)
        sections [{:id :basics :title "Basic Components" :emoji "🧱"}
                  {:id :state :title "State Management" :emoji "📊"}
                  {:id :effects :title "Side Effects" :emoji "⚡"}
                  {:id :advanced :title "Advanced" :emoji "🚀"}
                  {:id :db :title "Global State" :emoji "🗄️"}]]
    (Element {:tag "div"}
      ;; Header
      (Element {:tag "header"}
        (Element {:tag "div" :className "container"}
          (Element {:tag "h1"}
            (Element {:tag "span" :className "accent"} "cljs.react")
            " Comprehensive Demo")
          (Element {:tag "p" :className "subtitle"}
            "A modern React wrapper for ClojureScript")))

      ;; Navigation
      (Element {:tag "nav"}
        (Element {:tag "div" :className "container"}
          (for [section sections]
            (Element {:tag "button"
                      :key (:id section)
                      :className (if (= selected-section (:id section)) "active" "")
                      :onClick #(set-selected-section (:id section))}
              (:emoji section) " " (:title section)))))

      ;; Main Content
      (Element {:tag "main"}
        ;; Basic Components
        (when (or (= selected-section :all) (= selected-section :basics))
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

        ;; State Management
        (when (or (= selected-section :all) (= selected-section :state))
          (Element {:tag "section"}
            (Element {:tag "h2"} "📊 State Management")

            (CodeAndOutput
             {:title "Counter with useState"
              :code "(defnc Counter\n  []\n  (let [count (use-state 0)]\n    (Element {:tag \"div\"}\n      (Element {:tag \"h3\"} \"Count: \" @count)\n      (Element {:tag \"button\"\n                :onClick #(swap! count dec)}\n        \"Decrement\")\n      (Element {:tag \"button\"\n                :onClick #(swap! count inc)}\n        \"Increment\"))))"}
             (Counter))

            (CodeAndOutput
             {:title "Text Input"
              :code "(defnc TextInput\n  []\n  (let [text (use-state \"\")]\n    (Element {:tag \"div\"}\n      (Element {:tag \"input\"\n                :value @text\n                :onChange #(reset! text\n                            (-> % .-target .-value))})\n      (Element {:tag \"p\"}\n        \"You typed: \" @text))))"}
             (TextInput))

            (CodeAndOutput
             {:title "Todo List"
              :code "(defnc TodoList\n  []\n  (let [todos (use-state [])\n        input (use-state \"\")\n        add-todo (fn []\n                   (when-not (empty? @input)\n                     (swap! todos conj {:id (random-uuid)\n                                         :text @input\n                                         :done false})\n                     (reset! input \"\")))\n        toggle (fn [id]\n                 (swap! todos\n                   (fn [ts]\n                     (mapv\n                       (fn [todo]\n                         (if (= (:id todo) id)\n                           (update todo :done not)\n                           todo))\n                       ts))))]\n    ...))"}
             (TodoList))

            (CodeAndOutput
             {:title "useReducer"
              :code "(defnc ReducerCounter\n  []\n  (let [reducer (fn [state action]\n                  (case (:type action)\n                    :increment (update state :count inc)\n                    :decrement (update state :count dec)\n                    :reset (assoc state :count 0)\n                    state))\n        [state dispatch] (react/useReducer\n                          reducer\n                          #js {:count 0})]\n    ...))"}
             (ReducerCounter))))

        ;; Side Effects
        (when (or (= selected-section :all) (= selected-section :effects))
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

        ;; Advanced
        (when (or (= selected-section :all) (= selected-section :advanced))
          (Element {:tag "section"}
            (Element {:tag "h2"} "🚀 Advanced Features")

            (CodeAndOutput
             {:title "Props & Data Flow"
              :code "(defnc NestedComponent\n  [{:keys [level message]}]\n  (Element {:tag \"div\"}\n    (Element {:tag \"h4\"} \"Level \" level)\n    (Element {:tag \"p\"} message)))\n\n(defnc PropsDemo\n  []\n  (Element {:tag \"div\"}\n    (NestedComponent {:level 1\n                      :message \"First\"})\n    (NestedComponent {:level 2\n                      :message \"Second\"})))"}
             (PropsDemo))

            (CodeAndOutput
             {:title "Component Composition"
              :code "(defnc Badge\n  [{:keys [text type]}]\n  (Element {:tag \"span\"\n            :className (str \"badge badge-\"\n                           (name type))}\n    text))\n\n(defnc UserCard\n  [{:keys [name role]}]\n  (Element {:tag \"div\"}\n    (Element {:tag \"h4\"} name)\n    (Badge {:text role :type role})))"}
             (CompositionDemo))

            (CodeAndOutput
             {:title "Memoization with React.memo"
              :code "(defnc RenderCounter\n  [{:keys [name]}]\n  (let [count (use-ref 0)]\n    (swap! count inc)\n    (Element {:tag \"div\"}\n      (Element {:tag \"strong\"} name)\n      (Element {:tag \"span\"}\n        \" - Renders: \"\n        @count))))\n\n;; defnc automatically wraps\n;; components with React.memo\n;; using CLJS equality"}
             (MemoizationDemo))))

        ;; Global State
        (when (or (= selected-section :all) (= selected-section :db))
          (Element {:tag "section"}
            (Element {:tag "h2"} "🗄️ Global State (DBProvider & Cursors)")

            (CodeAndOutput
             {:title "DBProvider & use-cursor"
              :code "(defnc CounterDisplay []\n  (let [count (use-cursor [:counter])]\n    (Element {:tag \"div\"} @count)))\n\n(defnc CounterButton []\n  (let [count (use-cursor [:counter])]\n    (Element {:tag \"button\"\n              :onClick #(swap! count inc)}\n      \"Increment\")))\n\n(defnc App []\n  (DBProvider {:initial-value {:counter 0}}\n    (CounterDisplay)\n    (CounterButton)))"}
             (DBDemo)))))

      ;; Footer
      (Element {:tag "footer"}
        (Element {:tag "div" :className "container"}
          (Element {:tag "p"}
            "Built with "
            (Element {:tag "strong"} "cljs.react")
            " - A modern React wrapper for ClojureScript")
          (Element {:tag "div" :style {:display "flex" :justifyContent "center" :gap "1rem" :marginTop "1rem" :fontSize "0.875rem"}}
              (Element {:tag "a"
                        :href "https://github.com"
                        :className "hover:text-koi-orange transition-colors"
                        :target "_blank"}
                "GitHub")
              (Element {:tag "span"} "•")
              (Element {:tag "a"
                        :href "https://clojurescript.org"
                        :className "hover:text-koi-orange transition-colors"
                        :target "_blank"}
                "ClojureScript")
              (Element {:tag "span"} "•")
              (Element {:tag "a"
                        :href "https://react.dev"
                        :target "_blank"}
                "React")))))))

(defonce root (atom nil))

(defn ^:dev/after-load reload []
  (when @root
    (.render @root (App {}))))

(defn ^:export init
  "Initialize the React application using React 18+ createRoot API"
  []
  (when-let [root-el (.getElementById js/document "app")]
    (reset! root (react-dom/createRoot root-el))
    (.render @root (App {}))))
