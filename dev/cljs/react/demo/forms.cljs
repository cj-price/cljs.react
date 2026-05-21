(ns cljs.react.demo.forms
  (:require [cljs.react.core :refer [use-ref use-state]]
            [cljs.react.form :as form]
            [cljs.react.demo.util :refer [CodeAndOutput Div P H2 H3 Span
                                          Label Input Form Button Section]]
            [clojure.string :as str])
  (:require-macros [cljs.react.core :refer [defnc]]))

(defn- validate-signup [{:keys [name email]}]
  (cond-> {}
    (empty? name)                       (assoc :name "Required")
    (not (str/includes? (or email "") "@")) (assoc :email "Invalid email")))

(defn- validate-profile [{:keys [username plan terms]}]
  (cond-> {}
    (empty? username) (assoc :username "Required")
    (nil? plan)       (assoc :plan "Please select a plan")
    (not terms)       (assoc :terms "Must accept terms to continue")))

(defnc FormFieldInput [{:keys [value error dirty onChange onBlur label type placeholder]}]
  (Div {:className "space-y-1.5"}
    (Div {:className "flex items-center justify-between"}
      (Label {:className "text-sm font-semibold text-gray-700"} label)
      (when dirty
        (Span {:className "text-xs font-medium text-amber-600 bg-amber-50 border border-amber-200 px-2 py-0.5 rounded-full"}
          "modified")))
    (Input {:type (or type "text")
            :placeholder placeholder
            :value (or value "")
            :className (str "w-full px-4 py-2.5 rounded-lg border-2 outline-none transition-all "
                            (if error
                              "border-red-300 bg-red-50 ring-1 ring-red-300 focus:ring-2 focus:ring-red-300"
                              "border-gray-200 bg-white focus:border-koi-orange focus:ring-2 focus:ring-koi-orange/20"))
            :onChange onChange
            :onBlur onBlur})
    (when error
      (Div {:className "flex items-center gap-1.5 text-sm text-red-600"}
        (Span {:className "text-red-400"} "⚠")
        (Span error)))))

(defnc FormSubmitBtn [{:keys [f label]}]
  (let [{:keys [submitting? submitted?]} (form/use-form-meta f)]
    (Button {:type "submit"
             :disabled (or submitting? submitted?)
             :className "w-full px-6 py-2.5 bg-koi-orange text-white rounded-lg font-semibold shadow-md hover:bg-orange-600 active:bg-orange-700 disabled:opacity-60 disabled:cursor-not-allowed transition-all"}
      (cond
        submitted?  "✓ Submitted!"
        submitting? "Submitting…"
        :else       label))))

(defnc FormCheckboxInput
  [{:keys [checked error dirty onChange onBlur label]}]
  (Div {:className "flex items-start gap-3 py-1"}
    (Input {:type "checkbox"
            :checked (boolean checked)
            :className "mt-0.5 h-4 w-4 cursor-pointer accent-orange-500"
            :onChange onChange
            :onBlur onBlur})
    (Div {:className "flex-1"}
      (Div {:className "flex items-center gap-2"}
        (Span {:className "text-sm font-semibold text-gray-700"} label)
        (when dirty
          (Span {:className "text-xs font-medium text-amber-600 bg-amber-50 border border-amber-200 px-2 py-0.5 rounded-full"}
            "modified")))
      (when error
        (Div {:className "flex items-center gap-1 text-sm text-red-600 mt-0.5"}
          (Span {:className "text-red-400"} "⚠")
          error)))))

(defnc FormRadioGroup
  [{:keys [value error dirty onChange onBlur label options]}]
  (Div {:className "space-y-2"}
    (Div {:className "flex items-center justify-between"}
      (Label {:className "text-sm font-semibold text-gray-700"} label)
      (when dirty
        (Span {:className "text-xs font-medium text-amber-600 bg-amber-50 border border-amber-200 px-2 py-0.5 rounded-full"}
          "modified")))
    (Div {:className "flex flex-wrap gap-2"}
      (for [opt options]
        (Label {:key (:value opt)
                :className (str "flex items-center gap-2 px-4 py-2 rounded-lg border-2 cursor-pointer text-sm transition-all "
                                (if (= value (:value opt))
                                  "border-koi-orange bg-orange-50 text-gray-900 font-medium"
                                  "border-gray-200 text-gray-600 hover:border-gray-300"))}
          (Input {:type "radio"
                  :name label
                  :value (:value opt)
                  :checked (= value (:value opt))
                  :className "sr-only"
                  :onChange onChange
                  :onBlur onBlur})
          (:label opt))))
    (when error
      (Div {:className "flex items-center gap-1.5 text-sm text-red-600"}
        (Span {:className "text-red-400"} "⚠")
        (Span error)))))

(defnc ProfileFormDemo []
  (let [f           (form/use-form
                      {:values   {:username "" :plan nil :newsletter false :terms false}
                       :validate validate-profile
                       :on-submit (fn [values]
                                    (js/Promise.
                                      (fn [resolve _]
                                        (js/setTimeout #(resolve values) 800))))})
        username-fp (form/use-field f :username)
        plan-fp     (form/use-field f :plan)
        newsletter-fp (form/use-field f :newsletter {:checkbox? true})
        terms-fp    (form/use-field f :terms {:checkbox? true})]
    (Div {:className "w-full"}
      (Form {:className "space-y-5"
             :onSubmit (form/on-submit f)}
        (FormFieldInput (assoc username-fp :label "Username" :placeholder "your-username"))
        (FormRadioGroup (assoc plan-fp
                          :label "Plan"
                          :options [{:value "free"       :label "Free"}
                                    {:value "pro"        :label "Pro"}
                                    {:value "enterprise" :label "Enterprise"}]))
        (FormCheckboxInput (assoc newsletter-fp :label "Subscribe to newsletter"))
        (FormCheckboxInput (assoc terms-fp :label "I accept the terms and conditions"))
        (FormSubmitBtn {:f f :label "Create Account"})))))

(defnc FormDemo []
  (let [f        (form/use-form
                   {:values   {:name "" :email ""}
                    :validate validate-signup
                    :on-submit (fn [values]
                                 (js/Promise.
                                   (fn [resolve _]
                                     (js/setTimeout #(resolve values) 800))))})
        name-fp  (form/use-field f :name)
        email-fp (form/use-field f :email)]
    (Div {:className "w-full"}
      (Form {:className "space-y-5"
             :onSubmit (form/on-submit f)}
        (FormFieldInput (assoc name-fp :label "Name" :placeholder "Your name"))
        (FormFieldInput (assoc email-fp :label "Email" :type "email" :placeholder "you@example.com"))
        (FormSubmitBtn {:f f :label "Sign Up"})))))

;;;; Async Validation Demo

(defnc FormValidatingIndicator [{:keys [f]}]
  (let [{:keys [validating?]} (form/use-form-meta f)]
    (when validating?
      (Span {:className "text-xs text-gray-500 italic animate-pulse"}
        "Checking…"))))

(defn- validate-username-async [{:keys [username]}]
  (js/Promise.
    (fn [resolve _]
      (js/setTimeout
        #(resolve (when (= username "taken")
                    {:username "Username is already taken"}))
        700))))

(defnc AsyncValidationDemo []
  (let [f           (form/use-form
                      {:values      {:username ""}
                       :validate-on :blur
                       :validate    validate-username-async})
        username-fp (form/use-field f :username)]
    (Div {:className "w-full"}
      (Form {:className "space-y-5"
             :onSubmit (form/on-submit f)}
        (Div {:className "space-y-1.5"}
          (Div {:className "flex items-center gap-2"}
            (Label {:className "text-sm font-semibold text-gray-700"}
              "Username")
            (FormValidatingIndicator {:f f}))
          (Input {:type "text"
                  :placeholder "Try \"taken\""
                  :value (or (:value username-fp) "")
                  :className (str "w-full px-4 py-2.5 rounded-lg border-2 outline-none transition-all "
                                  (if (:error username-fp)
                                    "border-red-300 bg-red-50 ring-1 ring-red-300 focus:ring-2 focus:ring-red-300"
                                    "border-gray-200 bg-white focus:border-koi-orange focus:ring-2 focus:ring-koi-orange/20"))
                  :onChange (:onChange username-fp)
                  :onBlur (:onBlur username-fp)})
          (when (:error username-fp)
            (Div {:className "flex items-center gap-1.5 text-sm text-red-600"}
              (Span {:className "text-red-400"} "⚠")
              (Span (:error username-fp)))))
        (FormSubmitBtn {:f f :label "Check Availability"})))))

;;;; Reactive Defaults Demo

(defnc ReactiveDefaultsDemo []
  (let [atom-ref     (use-ref nil)
        _            (when (nil? @atom-ref)
                       (reset! atom-ref (atom {:first-name "" :last-name "" :email ""})))
        profile-atom @atom-ref
        active       (use-state "none")
        f            (form/use-form {:values profile-atom})
        first-fp     (form/use-field f :first-name)
        last-fp      (form/use-field f :last-name)
        email-fp     (form/use-field f :email)]
    (Div {:className "w-full space-y-4"}
      (Div {:className "flex gap-2"}
        (Button {:type "button"
                 :className (str "px-4 py-2 rounded-lg text-sm font-semibold border-2 transition-all "
                                 (if (= @active "alice")
                                   "border-koi-orange bg-orange-50 text-gray-900"
                                   "border-gray-200 text-gray-600 hover:border-gray-300"))
                 :onClick (fn []
                            (reset! profile-atom {:first-name "Alice" :last-name "Smith" :email "alice@example.com"})
                            (reset! active "alice"))}
          "Load Alice")
        (Button {:type "button"
                 :className (str "px-4 py-2 rounded-lg text-sm font-semibold border-2 transition-all "
                                 (if (= @active "bob")
                                   "border-koi-orange bg-orange-50 text-gray-900"
                                   "border-gray-200 text-gray-600 hover:border-gray-300"))
                 :onClick (fn []
                            (reset! profile-atom {:first-name "Bob" :last-name "Jones" :email "bob@example.com"})
                            (reset! active "bob"))}
          "Load Bob"))
      (Form {:className "space-y-4"}
        (FormFieldInput (assoc first-fp :label "First Name" :placeholder "First name"))
        (FormFieldInput (assoc last-fp :label "Last Name" :placeholder "Last name"))
        (FormFieldInput (assoc email-fp :label "Email" :type "email" :placeholder "email@example.com"))))))

;;;; Subscription Isolation Demo

(defnc FieldWithCount [{:keys [f field-key label]}]
  (let [fp      (form/use-field f field-key)
        counter (use-ref 0)
        _       (reset! counter (inc @counter))]
    (Div {:className "space-y-1"}
      (Div {:className "flex items-center justify-between"}
        (Label {:className "text-sm font-semibold text-gray-700"} label)
        (Span {:className "text-xs font-mono bg-gray-100 text-gray-500 px-2 py-0.5 rounded"}
          (str "renders: " @counter)))
      (Input {:type "text"
              :value (or (:value fp) "")
              :className "w-full px-4 py-2.5 rounded-lg border-2 border-gray-200 bg-white outline-none focus:border-koi-orange focus:ring-2 focus:ring-koi-orange/20 transition-all"
              :onChange (:onChange fp)
              :onBlur (:onBlur fp)}))))

(defnc SubscriptionIsolationDemo []
  (let [f (form/use-form {:values {:first-name "" :last-name ""}})]
    (Div {:className "w-full space-y-4"}
      (P {:className "text-sm text-gray-500"}
        "Type in one field — only that field's render counter increments.")
      (FieldWithCount {:f f :field-key :first-name :label "First Name"})
      (FieldWithCount {:f f :field-key :last-name :label "Last Name"}))))

;;;; Forms Tab

(defnc FormsTab
  []
  (Section
    (H2 "📝 Forms")

    (CodeAndOutput
     {:title "Form with per-field subscriptions"
      :code "(defnc SignupForm []\n  (let [f        (form/use-form\n                   {:values   {:name \"\" :email \"\"}\n                    :validate validate\n                    :on-submit api/create-user!})\n        name-fp  (form/use-field f :name)\n        email-fp (form/use-field f :email)]\n    (Form {:onSubmit (form/on-submit f)}\n      (FieldInput (assoc name-fp :label \"Name\"))\n      (FieldInput (assoc email-fp :label \"Email\"))\n      (Button {:type \"submit\"} \"Submit\"))))"}
     (FormDemo))

    (CodeAndOutput
     {:title "Checkboxes & Radio Buttons"
      :code ";; Radio — use-field, reads e.target.value\n(let [plan-fp (form/use-field f :plan)]\n  (FormRadioGroup\n    (assoc plan-fp :label \"Plan\"\n      :options [{:value \"free\"  :label \"Free\"}\n                {:value \"pro\"   :label \"Pro\"}\n                {:value \"ent\"   :label \"Enterprise\"}])))\n\n;; Checkbox — use-field with {:checkbox? true}\n(let [terms-fp (form/use-field f :terms {:checkbox? true})]\n  (FormCheckboxInput\n    (assoc terms-fp :label \"Accept terms\")))"}
     (ProfileFormDemo))

    (H3 "Advanced API")

    (CodeAndOutput
     {:title "Async validation & blur mode"
      :code ";; validate-on :blur triggers validation when a field loses focus.\n;; Return a Promise from :validate to signal async work.\n;; validating? in use-form-meta flips true until the Promise resolves.\n\n(form/use-form\n  {:values      {:username \"\"}\n   :validate-on :blur\n   :validate    (fn [{:keys [username]}]\n                  (js/Promise.\n                    (fn [resolve _]\n                      (js/setTimeout\n                        #(resolve (when (= username \"taken\")\n                                    {:username \"Already taken\"}))\n                        700))))})\n\n;; Sub-component subscribes independently to validating? state\n(defnc ValidatingIndicator [{:keys [f]}]\n  (let [{:keys [validating?]} (form/use-form-meta f)]\n    (when validating?\n      (Span \"Checking…\"))))"}
     (AsyncValidationDemo))

    (CodeAndOutput
     {:title "Reactive defaults via watchable atom"
      :code ";; Pass a watchable atom as :values.\n;; Un-dirtied fields stay in sync with the atom.\n;; Fields the user has typed in are frozen — not overwritten on load.\n\n(let [atom-ref     (use-ref nil)\n      _            (when (nil? @atom-ref)\n                     (reset! atom-ref (atom {:first-name \"\" :email \"\"})))\n      profile-atom @atom-ref\n      f            (form/use-form {:values profile-atom})]\n  ;; Reset the atom → only untouched fields update\n  (Button {:onClick #(reset! profile-atom\n                       {:first-name \"Alice\"\n                        :email \"alice@example.com\"})}\n    \"Load Alice\"))"}
     (ReactiveDefaultsDemo))

    (CodeAndOutput
     {:title "Per-field subscription isolation"
      :code ";; use-field creates an independent per-field subscription via\n;; use-sync-external-store. Typing in one field ONLY re-renders\n;; the component subscribed to that field.\n\n(defnc FieldWithCount [{:keys [f field-key label]}]\n  (let [fp      (form/use-field f field-key)\n        counter (use-ref 0)\n        _       (reset! counter (inc @counter))]\n    (Div\n      (Span (str \"renders: \" @counter))\n      (Input {:value    (or (:value fp) \"\")\n              :onChange (:onChange fp)\n              :onBlur   (:onBlur fp)}))))\n\n;; Typing in :first-name only re-renders that component\n(FieldWithCount {:f f :field-key :first-name :label \"First Name\"})\n(FieldWithCount {:f f :field-key :last-name  :label \"Last Name\"})"}
     (SubscriptionIsolationDemo))))
