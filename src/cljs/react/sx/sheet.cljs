(ns cljs.react.sx.sheet
  "Style registry and stylesheet injection for `cljs.react.sx`.

  All `document` / CSSOM interaction is contained in this namespace — mirroring
  the containment `cljs.react.lazy` keeps over `shadow.loader`.

  Three `<style>` nodes are appended to `<head>`, in this order:

    [data-cljs-react-sx-baseline]  optional reset, always first
    [data-cljs-react-sx-theme]     `:root` custom properties — REPLACED in place
    [data-cljs-react-sx]           generated class rules — APPEND-ONLY

  The theme node is deliberately not part of the append-only registry. Theme
  vars are mutable state: registering them append-only means toggling a theme
  A -> B -> A is a dedup hit for A while B's later rule still wins at equal
  specificity, leaving the page on B. Replacing one node's text has no such
  ordering hazard.

  Class rules are append-only and content-hashed, which makes registration
  idempotent under StrictMode double-invocation and safe to perform during
  render: a discarded render leaves at most one unused rule, never a duplicate
  and never a wrong class."
  (:require
   [clojure.string :as str]
   [goog.object :as gobj]
   [cljs.react.sx.compile :as sxc]
   [cljs.react.sx.theme :as theme]))

;; defonce so hot reload keeps the registries and the DOM in correspondence.
;; Re-def'ing a registry while the <style> nodes persist re-inserts duplicates;
;; rebuilding the nodes while a registry persists leaves live classes pointing
;; at rules that no longer exist. Both move together or neither.
(defonce ^:private input-cache #js {})
(defonce ^:private composed-cache #js {})
(defonce ^:private css-registry #js {})
(defonce ^:private state #js {})

;; Bumped by reset-sheet!. A StaticStyle and a Keyframes each memoize their
;; resolved name ON THEMSELVES rather than in a registry, so without a
;; generation stamp they would keep handing out a name whose rules were just
;; removed from the document.
(defonce ^:private generation (atom 0))

(def ^:no-doc compile-count
  "Dev-only counter of actual sx compiles (cache misses). Lets a test or the
  demo demonstrate that a theme swap regenerates no CSS."
  (atom 0))

(defn ^:no-doc fnv1a
  "32-bit FNV-1a over `s`, base36-encoded.

  `Math/imul` is required: the FNV prime multiply overflows 2^53, so a plain
  `*` silently loses precision and the hash stops being a function of the
  input."
  [s]
  (let [n (.-length s)]
    (loop [i 0 h (int 2166136261)]
      (if (< i n)
        (recur (inc i)
               (js/Math.imul (bit-xor h (.charCodeAt s i)) 16777619))
        (.toString (unsigned-bit-shift-right h 0) 36)))))

(defn- bucket!
  [obj k]
  (or (gobj/get obj k)
      (let [b #js []] (gobj/set obj k b) b)))

;; ---------------------------------------------------------------------------
;; DOM

(defn- ensure-nodes!
  []
  (when (nil? (gobj/get state "main"))
    (let [head (.-head js/document)
          mk   (fn [attr]
                 (let [el (.createElement js/document "style")]
                   (.setAttribute el attr "")
                   ;; An empty text node forces .sheet to materialize.
                   (.appendChild el (.createTextNode js/document ""))
                   (.appendChild head el)
                   el))]
      (gobj/set state "baseline" (mk "data-cljs-react-sx-baseline"))
      (gobj/set state "theme" (mk "data-cljs-react-sx-theme"))
      (gobj/set state "main" (mk "data-cljs-react-sx")))))

;; The two insertion strategies disagree on multi-rule input — the text-node
;; path accepts it, insertRule rejects the whole thing — so the shipping path
;; is both the more security-relevant one and, with goog.DEBUG true under the
;; :test build, the one a test could never reach. This flag is the seam.
(defonce ^:private text-nodes? (atom ^boolean goog/DEBUG))

(defn ^:no-doc set-insert-mode!
  "Force the insertion strategy. `:text` is the dev path (one text node per
  rule, live-editable in devtools); `:cssom` is the shipping path (insertRule,
  which parses and rejects anything that is not exactly one rule). Defaults to
  `goog/DEBUG`, and under `:advanced` only `:cssom` exists."
  [mode]
  (reset! text-nodes? (= :text mode)))

(defn- insert-css!
  "Insert one CSS rule string.

  Deduplication is the caller's job, and [[intern-class!]] already does it by
  content: a class it hands back for the second time already has its rules in
  the document, so only a freshly interned class reaches here."
  [css]
  (ensure-nodes!)
  (let [el (gobj/get state "main")]
    ;; The `and` keeps the dev branch DCE-able: goog/DEBUG is false under
    ;; :advanced, so the text-node path compiles out entirely.
    (if (and ^boolean goog/DEBUG @text-nodes?)
      ;; Text nodes keep the rules visible and live-editable in devtools, at
      ;; the cost of a re-parse per insertion. DCEs away under :advanced.
      (.appendChild el (.createTextNode js/document css))
      (let [^js sheet (.-sheet el)]
        (try
          (.insertRule sheet css (.-length (.-cssRules sheet)))
          (catch :default e
            ;; Every rule reaching here was assembled from parts this library
            ;; already validated, so a rejection is a library bug, not bad
            ;; input — and a rule that fails to insert is invisible (an
            ;; unstyled element, or worse a non-animating one). Under DEBUG
            ;; that is worth a crash; in release, warning and carrying on
            ;; still beats taking the app down.
            (when ^boolean goog/DEBUG
              (throw (ex-info
                       (str "cljs.react.sx: the stylesheet rejected a rule this"
                            " library generated: " (pr-str css))
                       {:type ::rejected-rule :css css} e)))
            (js/console.warn
              (str "cljs.react.sx: could not insert rule " (pr-str css))
              e)))))))

;; ---------------------------------------------------------------------------
;; Content-addressed class names

(def ^:private class-budget
  "Distinct interned classes past which the dev warning below fires once."
  2000)

(defonce ^:private interned-count (atom 0))

(defn ^:no-doc intern-class!
  "Map CSS `content` to a class name, deduplicating by exact text.

  `on-new` is called with the class name only when this content is interned
  for the first time, and is where its rules get injected. That callback is
  what lets the sheet dedupe insertions without a second registry holding
  another copy of every rule string: a class handed back a second time
  provably already has its rules in the document — because it is recorded only
  after `on-new` returns, so a callback that throws interns nothing and the
  next call retries from scratch.

  The 3-arity accepts an injected hash code — the seam that lets a test force
  a collision without monkeypatching. A genuine collision (different content,
  same hash) takes a probe suffix.

  Buckets are keyed by prefix AND hash. The prefix is a real part of the
  identity — the same text interned under two prefixes must yield two names —
  and sharing a bucket would also let an unrelated neighbour push a probe
  suffix onto content that never collided with anything."
  ([prefix content] (intern-class! prefix content (fnv1a content) nil))
  ([prefix content h] (intern-class! prefix content h nil))
  ([prefix content h on-new]
   (let [b (bucket! css-registry (str prefix h))
         n (alength b)]
     (or (loop [i 0]
           (when (< i n)
             (let [e (aget b i)]
               (if (= content (gobj/get e "css"))
                 (gobj/get e "cls")
                 (recur (inc i))))))
         (let [cls (if (zero? n) (str prefix h) (str prefix h "-" n))]
           ;; `on-new` FIRST, and only then record. Recording first meant a
           ;; failed or half-finished insertion was interned as complete: every
           ;; later call matched the content probe above, returned the class
           ;; without re-running `on-new`, and nothing ever retried — which is
           ;; exactly the invariant this docstring claims, inverted.
           (when on-new (on-new cls))
           (.push b #js {"css" content "cls" cls})
           (when ^boolean goog/DEBUG
             ;; The registry is append-only by design — repeated toggling among
             ;; a FIXED set of inputs adds nothing, since content-hash dedupe
             ;; bounds growth to DISTINCT inputs. Continuously-varying values
             ;; (a class per drag pixel) are the hazard, and they are silent
             ;; without this. Warns once, at the threshold.
             (when (= class-budget (swap! interned-count inc))
               (js/console.warn
                 (str "cljs.react.sx: " class-budget " distinct style classes "
                      "have been interned. The registry never shrinks, so a "
                      "continuously-varying sx value grows it without bound — "
                      "put the varying part in a custom property instead "
                      "(:className (use-sx {:width \"var(--w)\"}) with :style "
                      "#js {\"--w\" w}). Most recent CSS: " (pr-str content)))))
           cls)))))

;; ---------------------------------------------------------------------------
;; Theme custom properties

(defn ^:no-doc write-theme-vars!
  "Replace the `:root` custom properties. Idempotent: unchanged text is a
  no-op, which is what makes a StrictMode double-invocation harmless."
  [vars]
  (ensure-nodes!)
  (let [css (sxc/vars-rule ":root" vars)]
    (when-not (= css (gobj/get state "theme-css"))
      (gobj/set state "theme-css" css)
      (set! (.-textContent ^js (gobj/get state "theme")) css))))

(defn ^:no-doc ensure-default-vars!
  "Write `default-theme`'s vars if no provider has written any yet, so
  `--cx-*` references resolve without a ThemeProvider mounted."
  []
  (when (nil? (gobj/get state "theme-css"))
    (write-theme-vars! (theme/theme->css-vars theme/default-theme-normalized))))

(defn ^:no-doc scoped-theme-class
  "Register a scoped custom-property rule and return its class. Two nested
  providers with `=` themes share one class."
  [vars]
  (let [decls (sxc/vars-decls vars)]
    (intern-class! "cx-theme-" decls (fnv1a decls)
                   (fn [cls] (insert-css! (str "." cls "{" decls "}"))))))

;; ---------------------------------------------------------------------------
;; Baseline

(defn ^:no-doc write-baseline!
  "Write the reset into the baseline node, once per distinct content. The node
  is created before the main sheet, so the reset always precedes generated
  rules regardless of registration order."
  [css]
  (ensure-nodes!)
  (when-not (= css (gobj/get state "baseline-css"))
    (gobj/set state "baseline-css" css)
    (set! (.-textContent ^js (gobj/get state "baseline")) css)))

;; ---------------------------------------------------------------------------
;; Keyframes
;;
;; A `@keyframes` rule does not fit the one-rule-per-generated-class model: its
;; name is global and its body is nested blocks rather than declarations. So it
;; gets the same treatment a class gets instead — content-addressed, interned
;; by body text, append-only.
;;
;; A Keyframes travels through the sx map AS AN OBJECT and is substituted for
;; its name here, on the compile path. Letting the name into the map instead
;; would put a snapshot of registry state inside data the rest of the system
;; treats as pure: a name captured at namespace load survives `reset-sheet!`
;; and hot reload while the rule it names does not, and both failures are
;; silent — the element renders perfectly and simply never animates. Deriving
;; the name on every compile makes both correct by construction.

(defn ^:no-doc keyframes-name!
  "Register an `@keyframes` body and return its global name. Identical bodies
  share one name and one rule.

  Inserted as a single `insertRule` call, which is what the CSSOM path
  requires — and it accepts a whole `@keyframes` rule, so the dev text-node
  path and the shipping path agree on this shape."
  ([body] (keyframes-name! body (fnv1a body)))
  ([body h]
   (intern-class! "cx-kf-" body h
                  (fn [nm] (insert-css! (str "@keyframes " nm "{" body "}"))))))

(defonce ^:private keyframes-defined?
  ;; Gates the substitution walk below, so an app that never defines keyframes
  ;; pays one atom read per compile MISS and nothing on the hot path. Monotone
  ;; and deliberately NOT cleared by reset-sheet!: it gates correctness, and a
  ;; Keyframes defined before a reset is still live after it.
  (atom false))

(deftype ^:no-doc Keyframes [frames body ^:mutable nm ^:mutable gen]
  ;; Derefs to the frames map it was given — the INPUT, exactly as a
  ;; StaticStyle derefs to its sx map. Registration is an effect and gets a
  ;; named function, not a deref.
  IDeref
  (-deref [_] frames)

  ;; The var IS the identity, as for StaticStyle: re-evaluating on hot reload
  ;; is precisely when a fresh identity (and so a fresh name) is wanted.
  IEquiv
  (-equiv [this other] (identical? this other))

  IHash
  (-hash [this] (goog/getUid this)))

(defn keyframes
  "Wrap a frames map as a lazily-registered keyframes identity.

  The body is compiled EAGERLY — it is pure text needing neither theme nor
  breakpoints, so a malformed frames map fails at the `def` rather than at
  whichever render first touches it. Only registration is deferred.

  Unlike a StaticStyle there is no per-breakpoint cache: a keyframes body
  cannot contain a media query, so it has no breakpoint dimension."
  [frames]
  (let [body (sxc/keyframes->body frames)]
    (reset! keyframes-defined? true)
    (Keyframes. frames body nil @generation)))

(defn ^:no-doc ensure-keyframes!
  [kf]
  ;; Checked rather than hinted: `^Keyframes` is erased in CLJS, so without
  ;; this the frames map — the likeliest thing to pass by mistake, since the
  ;; docs spend a paragraph on object-versus-name — reached `.-gen`/`.-body`
  ;; and died on a raw TypeError from library internals, AFTER `set!`ing a
  ;; field on the caller's own value.
  (when-not (instance? Keyframes kf)
    (throw (ex-info
             (str "cljs.react.sx: keyframes-name takes the value returned by "
                  "`keyframes`, not a frames map or a name. Got "
                  (pr-str kf) ".")
             {:type ::not-keyframes :got kf})))
  ;; Re-bound hinted only AFTER the check: hinting the parameter would let the
  ;; field reads compile as direct property access on whatever was passed,
  ;; which is the crash this guard exists to replace.
  (let [^Keyframes kf kf
        g  @generation]
    (when-not (== g (.-gen kf))
      (set! (.-gen kf) g)
      (set! (.-nm kf) nil))
    (or (.-nm kf)
        (let [nm (keyframes-name! (.-body kf))]
          (set! (.-nm kf) nm)
          nm))))

(defn- sx-key-name
  "The name of an sx key, or nil for anything that is not one. Delegates to
  `sxc/key-name` so this namespace cannot drift from the compiler about what a
  key is — it previously excluded symbols, which `key-name` accepts."
  [k]
  (when (or (keyword? k) (string? k) (symbol? k))
    (sxc/key-name k)))

(defn- keyframes-value!
  [prop kf]
  (if (= "animation-name" (some-> (sx-key-name prop) theme/kebab))
    (ensure-keyframes! kf)
    (throw (ex-info
             (str "cljs.react.sx: keyframes are only meaningful under "
                  "`:animation-name`, but one was given for " (pr-str prop)
                  ". Emitting its name for any other property produces a value"
                  " the parser silently discards.")
             {:type ::keyframes-not-animation-name :prop prop}))))

(defn- nested-key?
  "True for a `&` selector key or an `@` at-rule key — the two shapes that open
  a new rule, and so clear the enclosing property rather than becoming one."
  [k]
  (when-let [ks (sx-key-name k)]
    (or (str/starts-with? ks "&") (str/starts-with? ks "@"))))

(defn- resolve-keyframes
  "Substitute Keyframes values for their registered names.

  Recurses as `sxc/sx->rules` does: a `&`/`@` key opens a new rule and clears
  the enclosing property, while any other map value is a responsive map whose
  keys are breakpoints — so `prop` carries the real property down to a
  Keyframes nested inside one."
  [sx prop]
  (reduce-kv
    (fn [m k v]
      (assoc m k
             (cond
               (map? v) (resolve-keyframes
                          v (when-not (nested-key? k) (or prop k)))
               (instance? Keyframes v) (keyframes-value! (or prop k) v)
               :else v)))
    {} sx))

;; ---------------------------------------------------------------------------
;; The style cache

(defn- compile-and-register!
  "Compile `sx` and inject its rules, returning the class.

  Default theme vars are ensured here rather than in [[class-for]]: a warm
  cache hit can only exist because an earlier miss ran this, and `reset-sheet!`
  clears the cache and the nodes together — so the miss path is the last point
  at which they can still be missing, and the probe need not check every call."
  [sx bps]
  (when ^boolean goog/DEBUG (swap! compile-count inc))
  (ensure-default-vars!)
  ;; Rendered once: the declaration bodies are identical in sentinel and final
  ;; form, so only the prelude differs between the hashed text and the text
  ;; that reaches the document.
  (let [sx       (if @keyframes-defined? (resolve-keyframes sx nil) sx)
        rendered (sxc/render-rules (sxc/sx->rules sx bps))
        sentinel (str/join "\n" (sxc/rendered->css rendered "&"))]
    (intern-class! "cx-" sentinel (fnv1a sentinel)
                   (fn [cls]
                     (doseq [css (sxc/rendered->css rendered (str "." cls))]
                       (insert-css! css))))))

(defn class-for
  "Class name for `sx` under `bpk` / `bps`. Compiles and registers on first
  use; later calls with an `=` sx map hit the cache."
  [sx bpk bps]
  ;; Bucket on the sx map's hash alone rather than on [sx bpk]: allocating a
  ;; vector per probe cost more than the collisions it avoided, and nobody
  ;; overrides breakpoints, so bpk is one interned constant that the chain
  ;; scan compares for free.
  (let [b (bucket! input-cache (hash sx))
        n (alength b)]
    (or (loop [i 0]
          (when (< i n)
            (let [e (aget b i)]
              (if (and (= sx (gobj/get e "sx")) (= bpk (gobj/get e "bpk")))
                (gobj/get e "cls")
                (recur (inc i))))))
        (let [cls (compile-and-register! sx bps)]
          (.push b #js {"sx" sx "bpk" bpk "cls" cls})
          cls))))

;; ---------------------------------------------------------------------------
;; defstyle identities

(deftype ^:no-doc StaticStyle [sx cache ^:mutable gen]
  IDeref
  (-deref [_] sx)

  ;; The var IS the identity. Unlike StateAtom/RefAtom/Cursor there is no
  ;; underlying object two wrappers could share — `defstyle` produces exactly
  ;; one instance per evaluation, and re-evaluating on hot reload is precisely
  ;; when a fresh identity (and so a recompile) is wanted.
  IEquiv
  (-equiv [this other] (identical? this other))

  IHash
  (-hash [this] (goog/getUid this)))

(defn static-style
  "Wrap an sx map as a stable style identity. Compiled lazily on first use and
  memoized per breakpoint key, so the hot path is one `=` on a short string
  plus a property lookup — and hook deps compare by `identical?` rather than
  walking the map."
  [sx]
  (StaticStyle. sx #js {} @generation))

(defn ^:no-doc ensure-static!
  [^StaticStyle ss bpk bps]
  (let [cache (.-cache ss)
        g     @generation]
    (when-not (== g (.-gen ss))
      (set! (.-gen ss) g)
      (gobj/clear cache))
    (or (gobj/get cache bpk)
        (let [cls (class-for (.-sx ss) bpk bps)]
          (gobj/set cache bpk cls)
          cls))))

;; ---------------------------------------------------------------------------
;; Composition

(defn- invalid-sx!
  [x]
  (throw (ex-info
           (str "cljs.react.sx: sx must be a map, a defstyle style, or a vector"
                " of them (nils are dropped). Got " (pr-str x) ".")
           {:type ::invalid-sx :got x})))

(defn ^:no-doc class-for-composed
  "Deep-merge `parts` right-wins into one sx map and return its class.

  Concatenating class names instead would be broken: every generated rule is
  anchored on a single generated class, so the cascade would resolve by
  stylesheet insertion order — registration order, not authoring order —
  making `[a b]` and `[b a]` render identically. Merging also combines two
  `&:hover` maps rather than letting one clobber the other.

  Parts are validated: an unmergeable part (a string, a nested vector) would
  otherwise reduce away into the EMPTY class, silently unstyling the element.

  A `StaticStyle` part enters the cache key as ITSELF rather than as its
  compiled class. It already hashes by uid and compares by identity, so it is
  the cheaper key — and resolving one here used to compile it and permanently
  register its standalone rule, which a composition never puts on an element."
  [parts bpk bps]
  (doseq [p parts]
    (when-not (or (nil? p) (map? p) (instance? StaticStyle p))
      (invalid-sx! p)))
  (let [parts (vec (remove nil? parts))
        ckey  [parts bpk]
        b     (bucket! composed-cache (hash ckey))
        n     (alength b)]
    (or (loop [i 0]
          (when (< i n)
            (let [e (aget b i)]
              (if (= ckey (gobj/get e "key"))
                (gobj/get e "cls")
                (recur (inc i))))))
        (let [merged (reduce theme/deep-merge {}
                             (map #(if (instance? StaticStyle %) @% %) parts))
              cls    (class-for merged bpk bps)]
          (.push b #js {"key" ckey "cls" cls})
          cls))))

(defn ^:no-doc class-for-any
  "Class name for any sx shape the public API accepts: nil, a map, a
  `defstyle` style, or a vector of those.

  The single dispatch `use-sx` and `style` share, so the hook and the plain
  function accept and reject exactly the same inputs — the alternative is
  `style` answering a real class for nil and dying on internal destructuring
  noise for a `defstyle` var."
  [sx bpk bps]
  (cond
    (nil? sx)                  nil
    (instance? StaticStyle sx) (ensure-static! sx bpk bps)
    (vector? sx)               (class-for-composed sx bpk bps)
    (map? sx)                  (class-for sx bpk bps)
    :else                      (invalid-sx! sx)))

;; ---------------------------------------------------------------------------

(defn ^:no-doc reset-sheet!
  "Drop every registry and every injected node together. For tests only.

  Deliberately NOT wired to `^:dev/after-load`: components that did not
  re-render still reference their old class names, and clearing the sheet
  would leave them unstyled."
  []
  (doseq [k ["baseline" "theme" "main"]]
    (when-let [^js el (gobj/get state k)]
      (when-let [p (.-parentNode el)] (.removeChild p el))))
  (gobj/clear state)
  (gobj/clear input-cache)
  (gobj/clear composed-cache)
  (gobj/clear css-registry)
  (swap! generation inc)
  (reset! interned-count 0)
  (reset! compile-count 0))
