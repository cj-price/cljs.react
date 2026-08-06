(ns cljs.react.demo.mui
  (:require ["@mui/material/Button$default"          :as MuiButton]
            ["@mui/material/TextField$default"        :as MuiTextField]
            ["@mui/material/Card$default"             :as MuiCard]
            ["@mui/material/CardContent$default"      :as MuiCardContent]
            ["@mui/material/CardActions$default"      :as MuiCardActions]
            ["@mui/material/Dialog$default"           :as MuiDialog]
            ["@mui/material/DialogTitle$default"      :as MuiDialogTitle]
            ["@mui/material/DialogContent$default"    :as MuiDialogContent]
            ["@mui/material/DialogContentText$default" :as MuiDialogContentText]
            ["@mui/material/DialogActions$default"    :as MuiDialogActions]
            ["@mui/material/Stack$default"            :as MuiStack]
            ["@mui/material/Typography$default"       :as MuiTypography]
            ["@mui/material/Chip$default"             :as MuiChip]
            [cljs.react.core :refer [adapt use-state]]
            [cljs.react.demo.ui :refer [CodeAndOutput SectionTitle Muted]]
            [cljs.react.demo.util :refer [Section]])
  (:require-macros [cljs.react.core :refer [defnc]]))

;; This tab deliberately stays on MUI's OWN styling — its `:variant`, `:color`
;; and `:sx` props. It is the interop tab; the point is that a JS component
;; library styles itself and needs nothing from us.
;;
;; Note the two `sx`es are unrelated. MUI's is a prop that MUI reads, which is
;; why `Element` reserves `:sx` as a passthrough and never interprets it.
;; `cljs.react.sx` is always explicit: `(use-sx …)` returns a class you put on
;; `:className`. Nothing here is styled by it.

(def Button            (adapt MuiButton))
(def TextField         (adapt MuiTextField))
(def Card              (adapt MuiCard))
(def CardContent       (adapt MuiCardContent))
(def CardActions       (adapt MuiCardActions))
(def Dialog            (adapt MuiDialog))
(def DialogTitle       (adapt MuiDialogTitle))
(def DialogContent     (adapt MuiDialogContent))
(def DialogContentText (adapt MuiDialogContentText))
(def DialogActions     (adapt MuiDialogActions))
(def Stack             (adapt MuiStack))
(def Typography        (adapt MuiTypography))
(def Chip              (adapt MuiChip))

;; ── 1. Buttons ───────────────────────────────────────────────────────────────

(defnc ButtonsDemo []
  (Stack {:direction "row" :spacing 2 :flexWrap "wrap" :useFlexGap true}
    (Button {:variant "contained" :color "primary"}   "Contained")
    (Button {:variant "outlined"  :color "primary"}   "Outlined")
    (Button {:variant "text"      :color "primary"}   "Text")
    (Button {:variant "contained" :color "secondary"} "Secondary")
    (Button {:variant "contained" :disabled true}     "Disabled")))

(def buttons-code
  "(def Button (adapt MuiButton))

(Button {:variant \"contained\" :color \"primary\"}
  \"Contained\")

(Button {:variant \"outlined\" :color \"primary\"}
  \"Outlined\")

(Button {:variant \"text\"}
  \"Text\")

(Button {:variant \"contained\" :disabled true}
  \"Disabled\")")

;; ── 2. Controlled TextField ──────────────────────────────────────────────────

(defnc TextFieldDemo []
  (let [value (use-state "")]
    (Stack {:spacing 2}
      (TextField {:label "Your Name"
                  :variant "outlined"
                  :value @value
                  :onChange #(reset! value (-> % .-target .-value))
                  :fullWidth true})
      (Typography {:variant "body1"}
        "Hello, " (if (empty? @value) "stranger" @value) "!"))))

(def textfield-code
  "(let [value (use-state \"\")]
  (TextField {:label \"Your Name\"
              :variant \"outlined\"
              :value @value
              :onChange #(reset! value
                           (-> % .-target .-value))
              :fullWidth true})
  (Typography {:variant \"body1\"}
    \"Hello, \" (if (empty? @value) \"stranger\" @value) \"!\"))")

;; ── 3. Card Nesting ──────────────────────────────────────────────────────────

(defnc CardDemo []
  (Card {:sx {:maxWidth 360}}
    (CardContent {}
      (Typography {:variant "h6" :component "div" :sx {:mb 1}}
        "ClojureScript + MUI")
      (Typography {:variant "body2" :color "text.secondary"}
        "Adapted MUI components composed like defnc components. CardContent "
        "and CardActions are nested exactly like plain HTML elements."))
    (CardActions {}
      (Button {:size "small" :variant "contained"} "Learn More")
      (Button {:size "small"} "Share"))))

(def card-code
  "(Card {:sx {:maxWidth 360}}
  (CardContent {}
    (Typography {:variant \"h6\" :component \"div\"}
      \"ClojureScript + MUI\")
    (Typography {:variant \"body2\" :color \"text.secondary\"}
      \"Card content...\"))
  (CardActions {}
    (Button {:size \"small\" :variant \"contained\"}
      \"Learn More\")
    (Button {:size \"small\"} \"Share\")))")

;; ── 4. Dialog ────────────────────────────────────────────────────────────────

(defnc DialogDemo []
  (let [open? (use-state false)]
    (Stack {:spacing 2 :alignItems "flex-start"}
      (Button {:variant "outlined"
               :onClick #(reset! open? true)}
        "Open Dialog")
      (Dialog {:open @open?
               :onClose #(reset! open? false)}
        (DialogTitle {} "Hello from MUI Dialog")
        (DialogContent {}
          (DialogContentText {}
            "This dialog is driven by a use-state atom. "
            "Clicking the backdrop or Close resets it to false."))
        (DialogActions {}
          (Button {:onClick #(reset! open? false)} "Close"))))))

(def dialog-code
  "(let [open? (use-state false)]
  (Button {:variant \"outlined\"
           :onClick #(reset! open? true)}
    \"Open Dialog\")
  (Dialog {:open @open?
           :onClose #(reset! open? false)}
    (DialogTitle {} \"Hello from MUI Dialog\")
    (DialogContent {}
      (DialogContentText {}
        \"Driven by a use-state atom.\"))
    (DialogActions {}
      (Button {:onClick #(reset! open? false)}
        \"Close\"))))")

;; ── 5. Stack Layout ──────────────────────────────────────────────────────────

(defnc StackDemo []
  (Stack {:spacing 2}
    (Stack {:direction "row" :spacing 1 :alignItems "center" :flexWrap "wrap" :useFlexGap true}
      (Typography {:variant "subtitle2" :sx {:minWidth 60}} "Chips:")
      (Chip {:label "Alpha"   :color "primary"})
      (Chip {:label "Beta"    :color "secondary"})
      (Chip {:label "Gamma"   :variant "outlined"})
      (Chip {:label "Delta"   :color "success"}))
    (Stack {:direction "column" :spacing 1}
      (Button {:variant "contained" :fullWidth true} "Full Width Contained")
      (Button {:variant "outlined"  :fullWidth true} "Full Width Outlined"))))

(def stack-code
  ";; Row of chips
(Stack {:direction \"row\" :spacing 1}
  (Chip {:label \"Alpha\" :color \"primary\"})
  (Chip {:label \"Beta\"  :color \"secondary\"})
  (Chip {:label \"Gamma\" :variant \"outlined\"}))

;; Column of full-width buttons
(Stack {:direction \"column\" :spacing 1}
  (Button {:variant \"contained\" :fullWidth true}
    \"Full Width Contained\")
  (Button {:variant \"outlined\" :fullWidth true}
    \"Full Width Outlined\"))")

;; ── Tab root ─────────────────────────────────────────────────────────────────

(defnc MUITab []
  (Section
    (SectionTitle {} "🎨 MUI Components")
    (Muted {:style {:marginBottom "1.5rem"}}
      "Material UI v6 components, each wrapped once with `adapt` and then "
      "called like a defnc component. Any JS React component also works "
      "directly as a :tag value on Element. These are styled by MUI's own "
      "`sx` prop, not by cljs.react.sx.")

    (CodeAndOutput {:title "Basic Buttons"    :code buttons-code}    (ButtonsDemo))
    (CodeAndOutput {:title "Controlled Input" :code textfield-code}  (TextFieldDemo))
    (CodeAndOutput {:title "Card Nesting"     :code card-code}       (CardDemo))
    (CodeAndOutput {:title "Dialog"           :code dialog-code}     (DialogDemo))
    (CodeAndOutput {:title "Stack Layout"     :code stack-code}      (StackDemo))))
