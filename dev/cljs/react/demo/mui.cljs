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
            [cljs.react.core :refer [Element use-state]]
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

;; ── 1. Buttons ───────────────────────────────────────────────────────────────

(defnc ButtonsDemo []
  (Element {:tag MuiStack :direction "row" :spacing 2 :flexWrap "wrap" :useFlexGap true}
    (Element {:tag MuiButton :variant "contained" :color "primary"}   "Contained")
    (Element {:tag MuiButton :variant "outlined"  :color "primary"}   "Outlined")
    (Element {:tag MuiButton :variant "text"      :color "primary"}   "Text")
    (Element {:tag MuiButton :variant "contained" :color "secondary"} "Secondary")
    (Element {:tag MuiButton :variant "contained" :disabled true}     "Disabled")))

(def buttons-code
  "(Element {:tag MuiButton :variant \"contained\" :color \"primary\"}
  \"Contained\")

(Element {:tag MuiButton :variant \"outlined\" :color \"primary\"}
  \"Outlined\")

(Element {:tag MuiButton :variant \"text\"}
  \"Text\")

(Element {:tag MuiButton :variant \"contained\" :disabled true}
  \"Disabled\")")

;; ── 2. Controlled TextField ──────────────────────────────────────────────────

(defnc TextFieldDemo []
  (let [value (use-state "")]
    (Element {:tag MuiStack :spacing 2}
      (Element {:tag MuiTextField
                :label "Your Name"
                :variant "outlined"
                :value @value
                :onChange #(reset! value (-> % .-target .-value))
                :fullWidth true})
      (Element {:tag MuiTypography :variant "body1"}
        "Hello, " (if (empty? @value) "stranger" @value) "!"))))

(def textfield-code
  "(let [value (use-state \"\")]
  (Element {:tag MuiTextField
            :label \"Your Name\"
            :variant \"outlined\"
            :value @value
            :onChange #(reset! value
                         (-> % .-target .-value))
            :fullWidth true})
  (Element {:tag MuiTypography :variant \"body1\"}
    \"Hello, \" (if (empty? @value) \"stranger\" @value) \"!\"))")

;; ── 3. Card Nesting ──────────────────────────────────────────────────────────

(defnc CardDemo []
  (Element {:tag MuiCard :sx {:maxWidth 360}}
    (Element {:tag MuiCardContent}
      (Element {:tag MuiTypography :variant "h6" :component "div"
                :sx {:mb 1}}
        "ClojureScript + MUI")
      (Element {:tag MuiTypography :variant "body2" :color "text.secondary"}
        "MUI components composed with the Element DSL. CardContent and "
        "CardActions are nested exactly like plain HTML elements."))
    (Element {:tag MuiCardActions}
      (Element {:tag MuiButton :size "small" :variant "contained"} "Learn More")
      (Element {:tag MuiButton :size "small"} "Share"))))

(def card-code
  "(Element {:tag MuiCard :sx {:maxWidth 360}}
  (Element {:tag MuiCardContent}
    (Element {:tag MuiTypography :variant \"h6\" :component \"div\"}
      \"ClojureScript + MUI\")
    (Element {:tag MuiTypography :variant \"body2\" :color \"text.secondary\"}
      \"Card content...\"))
  (Element {:tag MuiCardActions}
    (Element {:tag MuiButton :size \"small\" :variant \"contained\"}
      \"Learn More\")
    (Element {:tag MuiButton :size \"small\"} \"Share\")))")

;; ── 4. Dialog ────────────────────────────────────────────────────────────────

(defnc DialogDemo []
  (let [open? (use-state false)]
    (Element {:tag MuiStack :spacing 2 :alignItems "flex-start"}
      (Element {:tag MuiButton
                :variant "outlined"
                :onClick #(reset! open? true)}
        "Open Dialog")
      (Element {:tag MuiDialog
                :open @open?
                :onClose #(reset! open? false)}
        (Element {:tag MuiDialogTitle} "Hello from MUI Dialog")
        (Element {:tag MuiDialogContent}
          (Element {:tag MuiDialogContentText}
            "This dialog is driven by a use-state atom. "
            "Clicking the backdrop or Close resets it to false."))
        (Element {:tag MuiDialogActions}
          (Element {:tag MuiButton :onClick #(reset! open? false)} "Close"))))))

(def dialog-code
  "(let [open? (use-state false)]
  (Element {:tag MuiButton
            :variant \"outlined\"
            :onClick #(reset! open? true)}
    \"Open Dialog\")
  (Element {:tag MuiDialog
            :open @open?
            :onClose #(reset! open? false)}
    (Element {:tag MuiDialogTitle}
      \"Hello from MUI Dialog\")
    (Element {:tag MuiDialogContent}
      (Element {:tag MuiDialogContentText}
        \"Driven by a use-state atom.\"))
    (Element {:tag MuiDialogActions}
      (Element {:tag MuiButton
                :onClick #(reset! open? false)}
        \"Close\"))))")

;; ── 5. Stack Layout ──────────────────────────────────────────────────────────

(defnc StackDemo []
  (Element {:tag MuiStack :spacing 2}
    (Element {:tag MuiStack :direction "row" :spacing 1 :alignItems "center" :flexWrap "wrap" :useFlexGap true}
      (Element {:tag MuiTypography :variant "subtitle2" :sx {:minWidth 60}} "Chips:")
      (Element {:tag MuiChip :label "Alpha"   :color "primary"})
      (Element {:tag MuiChip :label "Beta"    :color "secondary"})
      (Element {:tag MuiChip :label "Gamma"   :variant "outlined"})
      (Element {:tag MuiChip :label "Delta"   :color "success"}))
    (Element {:tag MuiStack :direction "column" :spacing 1}
      (Element {:tag MuiButton :variant "contained" :fullWidth true} "Full Width Contained")
      (Element {:tag MuiButton :variant "outlined"  :fullWidth true} "Full Width Outlined"))))

(def stack-code
  ";; Row of chips
(Element {:tag MuiStack :direction \"row\" :spacing 1}
  (Element {:tag MuiChip :label \"Alpha\" :color \"primary\"})
  (Element {:tag MuiChip :label \"Beta\"  :color \"secondary\"})
  (Element {:tag MuiChip :label \"Gamma\" :variant \"outlined\"}))

;; Column of full-width buttons
(Element {:tag MuiStack :direction \"column\" :spacing 1}
  (Element {:tag MuiButton :variant \"contained\" :fullWidth true}
    \"Full Width Contained\")
  (Element {:tag MuiButton :variant \"outlined\" :fullWidth true}
    \"Full Width Outlined\"))")

;; ── Tab root ─────────────────────────────────────────────────────────────────

(defnc MUITab []
  (Section
    (SectionTitle {} "🎨 MUI Components")
    (Muted {:style {:marginBottom "1.5rem"}}
      "Material UI v6 components used via the Element DSL — any JS React "
      "component can be used as a :tag value. These are styled by MUI's own "
      "`sx` prop, not by cljs.react.sx.")

    (CodeAndOutput {:title "Basic Buttons"    :code buttons-code}    (ButtonsDemo))
    (CodeAndOutput {:title "Controlled Input" :code textfield-code}  (TextFieldDemo))
    (CodeAndOutput {:title "Card Nesting"     :code card-code}       (CardDemo))
    (CodeAndOutput {:title "Dialog"           :code dialog-code}     (DialogDemo))
    (CodeAndOutput {:title "Stack Layout"     :code stack-code}      (StackDemo))))
