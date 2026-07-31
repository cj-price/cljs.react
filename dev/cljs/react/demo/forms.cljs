(ns cljs.react.demo.forms
  (:require [cljs.react.core :refer [use-ref use-state use-id
                                     use-form use-field use-form-meta on-submit]]
            [cljs.react.sx :refer [use-sx]]
            [cljs.react.demo.ui :refer [CodeAndOutput Btn Row Stack Grid
                                        TextInput FieldLabel SectionTitle
                                        Caption Pill Badge Divider]]
            [cljs.react.demo.util :refer [Div Span Label Input Form Section H3]]
            [clojure.string :as str])
  (:require-macros [cljs.react.core :refer [defnc]]
                   [cljs.react.sx :refer [defstyle]]))

(defn- validate-signup [{:keys [name email]}]
  (cond-> {}
    (empty? name)                       (assoc :name "Required")
    (not (str/includes? (or email "") "@")) (assoc :email "Invalid email")))

(defn- validate-profile [{:keys [username plan terms]}]
  (cond-> {}
    (empty? username) (assoc :username "Required")
    (nil? plan)       (assoc :plan "Please select a plan")
    (not terms)       (assoc :terms "Must accept terms to continue")))

;; The three shapes every field in this tab is built from. Each used to be a
;; class string repeated verbatim across the file.

(defnc DirtyPill
  [{:keys [dirty]}]
  (when dirty (Pill {:tone :warning} "modified")))

(defnc ErrorLine
  [{:keys [error]}]
  ;; Hooks run before the `when`, not inside it: React counts hooks per render,
  ;; so a class computed only when there is an error changes that count the
  ;; moment validation fires.
  (let [icon-cls (use-sx {:color :palette.error.light})
        text-cls (use-sx {:font-size "0.875rem" :color :palette.error.main})]
    (when error
      (Row {:gap 0.75 :role "alert"}
        (Span {:className icon-cls} "⚠")
        (Span {:className text-cls} error)))))

(defnc FieldHeader
  ;; `htmlFor` when the header labels ONE control; `labelId` when it names a
  ;; group that points back at it with aria-labelledby. A group gets a span,
  ;; because a <label> with no control is not a label.
  [{:keys [label dirty htmlFor labelId children]}]
  (Row {:justify :space-between :gap 1}
    ;; `children` arrives as a seq, so it is spread rather than passed as one
    ;; child — React would otherwise read it as a keyless list and warn.
    (apply Row {:gap 1}
      (if labelId
        (Span {:id labelId
               :className (use-sx {:font-size "0.8125rem" :font-weight 500
                                   :color :palette.text.secondary})}
          label)
        (FieldLabel {:tight? true :htmlFor htmlFor} label))
      children)
    (DirtyPill {:dirty dirty})))

(defnc FormFieldInput
  [{:keys [value error dirty onChange onBlur label type placeholder]}]
  ;; The id is minted HERE and threaded both ways, so the association is the
  ;; component's job rather than every caller's. Without it the label was a
  ;; sibling two levels up the tree and the input's only accessible name was
  ;; its placeholder — or, where there was none, the empty string.
  (let [id     (use-id)
        err-id (str id "-error")]
    (Stack {:gap 1}
      (FieldHeader {:label label :dirty dirty :htmlFor id})
      (TextInput {:id id
                  :type (or type "text")
                  :placeholder placeholder
                  :value (or value "")
                  :error? (boolean error)
                  :aria-invalid (when error "true")
                  :aria-describedby (when error err-id)
                  :onChange onChange
                  :onBlur onBlur})
      (Div {:id err-id} (ErrorLine {:error error})))))

(defnc FormSubmitBtn
  [{:keys [f label]}]
  (let [{:keys [submitting? submitted?]} (use-form-meta f)]
    (Btn {:type "submit"
          :full? true
          :variant (if submitted? :success :primary)
          :disabled (or submitting? submitted?)}
      (cond
        submitted?  "✓ Submitted!"
        submitting? "Submitting…"
        :else       label))))

(defnc FormCheckboxInput
  [{:keys [checked error dirty onChange onBlur label]}]
  ;; WRAPPED in the label rather than sitting beside it. The text was in a
  ;; sibling span, so the checkbox had no accessible name at all — announced as
  ;; an unnamed checkbox — and clicking the words did nothing, which also costs
  ;; anyone with a motor impairment the larger hit target.
  (Label {:className (use-sx {:display :flex :gap 1.5 :align-items :flex-start
                              :cursor :pointer})}
    (Input {:type "checkbox"
            :checked (boolean checked)
            :className (use-sx {:mt 0.25 :width "1rem" :height "1rem"
                                :flex-shrink 0 :cursor :pointer
                                :accent-color :palette.primary.main})
            :onChange onChange
            :onBlur onBlur})
    (Div {:className (use-sx {:flex 1})}
      (Row {:gap 1}
        (Span {:className (use-sx {:font-size "0.875rem" :font-weight 500
                                   :color :palette.text.primary})}
          label)
        (DirtyPill {:dirty dirty}))
      (ErrorLine {:error error}))))

(defstyle radio-option
  {:display :inline-flex :align-items :center :gap 1
   :px 2 :py 1 :border-radius 1 :cursor :pointer
   :font-size "0.875rem" :border "2px solid"
   :transition "border-color 140ms ease, background-color 140ms ease"})

(defnc FormRadioGroup
  [{:keys [value error dirty onChange onBlur label options]}]
  ;; All three classes come from this render, not from the lazy `for` below —
  ;; React realizes that seq after the render returns, where hooks are invalid.
  (let [on-cls  (use-sx [radio-option
                         {:border-color :palette.primary.main
                          :bgcolor :palette.surface.tint
                          :color :palette.text.primary :font-weight 500}])
        off-cls (use-sx [radio-option
                         {:border-color :palette.divider
                          :color :palette.text.secondary
                          :&:hover {:border-color :palette.grey.300}}])
        ;; Visually hidden but still focusable and announced.
        sr-cls  (use-sx {:position :absolute :width "1px" :height "1px"
                         :padding 0 :margin "-1px" :overflow :hidden
                         :border 0 :white-space :nowrap
                         :clip-path "inset(50%)"})
        ;; A bare <label> beside a group of radios labels nothing — a <label>
        ;; needs one control. Naming the GROUP is what makes "Plan" reach a
        ;; screen reader, and each radio keeps its own wrapping label.
        label-id (use-id)]
    (Stack {:gap 1}
      (FieldHeader {:label label :dirty dirty :labelId label-id})
      (Row {:gap 1 :role "radiogroup" :aria-labelledby label-id}
        (for [opt options
              :let [on? (= value (:value opt))]]
          (Label {:key (:value opt)
                  :className (if on? on-cls off-cls)}
            (Input {:type "radio"
                    :name label
                    :value (:value opt)
                    :checked on?
                    :className sr-cls
                    :onChange onChange
                    :onBlur onBlur})
            (:label opt))))
      (ErrorLine {:error error}))))

(defnc ProfileFormDemo []
  (let [f           (use-form
                      {:values   {:username "" :plan nil :newsletter false :terms false}
                       :validate validate-profile
                       :on-submit (fn [values]
                                    (js/Promise.
                                      (fn [resolve _]
                                        (js/setTimeout #(resolve values) 800))))})
        username-fp (use-field f :username)
        plan-fp     (use-field f :plan)
        newsletter-fp (use-field f :newsletter {:checkbox? true})
        terms-fp    (use-field f :terms {:checkbox? true})]
    (Form {:onSubmit (on-submit f)}
      (Stack {:gap 2.5}
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
  (let [f        (use-form
                   {:values   {:name "" :email ""}
                    :validate validate-signup
                    :on-submit (fn [values]
                                 (js/Promise.
                                   (fn [resolve _]
                                     (js/setTimeout #(resolve values) 800))))})
        name-fp  (use-field f :name)
        email-fp (use-field f :email)]
    (Form {:onSubmit (on-submit f)}
      (Stack {:gap 2.5}
        (FormFieldInput (assoc name-fp :label "Name" :placeholder "Your name"))
        (FormFieldInput (assoc email-fp :label "Email" :type "email" :placeholder "you@example.com"))
        (FormSubmitBtn {:f f :label "Sign Up"})))))

;;;; Async Validation Demo

(defnc FormValidatingIndicator [{:keys [f]}]
  (let [{:keys [validating?]} (use-form-meta f)]
    (when validating?
      (Pill {:tone :info} "Checking…"))))

(defn- validate-username-async [{:keys [username]}]
  (js/Promise.
    (fn [resolve _]
      (js/setTimeout
        #(resolve (when (= username "taken")
                    {:username "Username is already taken"}))
        700))))

(defnc AsyncValidationDemo []
  (let [f           (use-form
                      {:values      {:username ""}
                       :validate-on :blur
                       :validate    validate-username-async})
        username-fp (use-field f :username)
        username-id (use-id)]
    (Form {:onSubmit (on-submit f)}
      (Stack {:gap 2.5}
        (Stack {:gap 1}
          (FieldHeader {:label "Username" :htmlFor username-id}
            (FormValidatingIndicator {:f f}))
          (TextInput {:id username-id
                      :placeholder "Try \"taken\""
                      :value (or (:value username-fp) "")
                      :error? (boolean (:error username-fp))
                      :onChange (:onChange username-fp)
                      :onBlur (:onBlur username-fp)})
          (ErrorLine {:error (:error username-fp)}))
        (FormSubmitBtn {:f f :label "Check Availability"})))))

;;;; Reactive Defaults Demo

(defnc ReactiveDefaultsDemo []
  (let [atom-ref     (use-ref nil)
        _            (when (nil? @atom-ref)
                       (reset! atom-ref (atom {:first-name "" :last-name "" :email ""})))
        profile-atom @atom-ref
        active       (use-state "none")
        f            (use-form {:values profile-atom})
        first-fp     (use-field f :first-name)
        last-fp      (use-field f :last-name)
        email-fp     (use-field f :email)]
    (Stack {:gap 2}
      (Row {:gap 1}
        (for [[id label values] [["alice" "Load Alice"
                                  {:first-name "Alice" :last-name "Smith"
                                   :email "alice@example.com"}]
                                 ["bob" "Load Bob"
                                  {:first-name "Bob" :last-name "Jones"
                                   :email "bob@example.com"}]]]
          (Btn {:key id
                :size :sm
                :variant (if (= @active id) :primary :secondary)
                :onClick (fn []
                           (reset! profile-atom values)
                           (reset! active id))}
            label)))
      (Form {}
        (Stack {:gap 2}
          (FormFieldInput (assoc first-fp :label "First Name" :placeholder "First name"))
          (FormFieldInput (assoc last-fp :label "Last Name" :placeholder "Last name"))
          (FormFieldInput (assoc email-fp :label "Email" :type "email" :placeholder "email@example.com")))))))

;;;; Subscription Isolation Demo

(defnc FieldWithCount [{:keys [f field-key label]}]
  (let [fp      (use-field f field-key)
        id      (use-id)
        counter (use-ref 0)
        _       (reset! counter (inc @counter))]
    (Stack {:gap 1}
      (Row {:justify :space-between :gap 1}
        (FieldLabel {:tight? true :htmlFor id} label)
        (Badge {:tone :neutral} "renders: " @counter))
      (TextInput {:id id
                  :value (or (:value fp) "")
                  :onChange (:onChange fp)
                  :onBlur (:onBlur fp)}))))

(defnc SubscriptionIsolationDemo []
  (let [f (use-form {:values {:first-name "" :last-name ""}})]
    (Stack {:gap 2}
      (Caption {} "Type in one field — only that field's render counter increments.")
      (Grid {:min-width "200px" :gap 2}
        (FieldWithCount {:f f :field-key :first-name :label "First Name"})
        (FieldWithCount {:f f :field-key :last-name :label "Last Name"})))))

;;;; Forms Tab

(defnc FormsTab
  []
  (Section
    (SectionTitle {} "📝 Forms")

    (CodeAndOutput
     {:title "Form with per-field subscriptions"
      :code "(defnc SignupForm []\n  (let [f        (use-form\n                   {:values   {:name \"\" :email \"\"}\n                    :validate validate\n                    :on-submit api/create-user!})\n        name-fp  (use-field f :name)\n        email-fp (use-field f :email)]\n    (Form {:onSubmit (on-submit f)}\n      (FieldInput (assoc name-fp :label \"Name\"))\n      (FieldInput (assoc email-fp :label \"Email\"))\n      (Button {:type \"submit\"} \"Submit\"))))"}
     (FormDemo))

    (CodeAndOutput
     {:title "Checkboxes & Radio Buttons"
      :code ";; Radio — use-field reads e.target.value\n(let [plan-fp (use-field f :plan)]\n  (FormRadioGroup\n    (assoc plan-fp :label \"Plan\"\n      :options [{:value \"free\"  :label \"Free\"}\n                {:value \"pro\"   :label \"Pro\"}\n                {:value \"ent\"   :label \"Enterprise\"}])))\n\n;; Checkbox — use-field {:checkbox? true}\n(let [terms-fp (use-field f :terms {:checkbox? true})]\n  (FormCheckboxInput\n    (assoc terms-fp :label \"Accept terms\")))"}
     (ProfileFormDemo))

    (Divider {})
    (H3 {:className (use-sx {:mb 3 :font-size "1.25rem" :font-weight 600
                             :color :palette.text.primary})}
      "Advanced API")

    (CodeAndOutput
     {:title "Async validation & blur mode"
      :code ";; :validate-on :blur; :validate may be async.\n\n(use-form\n  {:values      {:username \"\"}\n   :validate-on :blur\n   :validate    (fn [{:keys [username]}]\n                  (js/Promise.\n                    (fn [resolve _]\n                      (js/setTimeout\n                        #(resolve (when (= username \"taken\")\n                                    {:username \"Already taken\"}))\n                        700))))})\n\n;; Subscribes to validating? independently.\n(defnc ValidatingIndicator [{:keys [f]}]\n  (let [{:keys [validating?]} (use-form-meta f)]\n    (when validating?\n      (Span \"Checking…\"))))"}
     (AsyncValidationDemo))

    (CodeAndOutput
     {:title "Reactive defaults via watchable atom"
      :code ";; Pass a watchable atom as :values.\n\n(let [atom-ref     (use-ref nil)\n      _            (when (nil? @atom-ref)\n                     (reset! atom-ref (atom {:first-name \"\" :email \"\"})))\n      profile-atom @atom-ref\n      f            (use-form {:values profile-atom})]\n  ;; Reset it → only untouched fields update.\n  (Button {:onClick #(reset! profile-atom\n                       {:first-name \"Alice\"\n                        :email \"alice@example.com\"})}\n    \"Load Alice\"))"}
     (ReactiveDefaultsDemo))

    (CodeAndOutput
     {:title "Per-field subscription isolation"
      :code ";; Each use-field subscribes independently.\n\n(defnc FieldWithCount [{:keys [f field-key label]}]\n  (let [fp      (use-field f field-key)\n        counter (use-ref 0)\n        _       (reset! counter (inc @counter))]\n    (Div\n      (Span (str \"renders: \" @counter))\n      (Input {:value    (or (:value fp) \"\")\n              :onChange (:onChange fp)\n              :onBlur   (:onBlur fp)}))))\n\n;; Typing re-renders only that field.\n(FieldWithCount {:f f :field-key :first-name :label \"First Name\"})\n(FieldWithCount {:f f :field-key :last-name  :label \"Last Name\"})"}
     (SubscriptionIsolationDemo))))
