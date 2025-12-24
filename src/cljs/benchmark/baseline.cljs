(ns cljs.benchmark.baseline
  (:require ["react" :as react]))

;; ============================================================================
;; Raw React Element Creation Baselines
;; ============================================================================

(defn element-simple
  "Baseline: Simple div element using raw React.createElement"
  []
  (react/createElement "div" nil))

(defn element-with-props
  "Baseline: Element with props using raw React.createElement"
  []
  (react/createElement "div" #js {:className "foo"}))

(defn element-with-children
  "Baseline: Element with children using raw React.createElement"
  []
  (react/createElement "div" nil "a" "b" "c"))

(defn element-nested
  "Baseline: Nested elements using raw React.createElement"
  []
  (react/createElement "div" nil
    (react/createElement "div" nil
      (react/createElement "div" nil
        (react/createElement "div" nil
          (react/createElement "div" nil))))))

;; ============================================================================
;; Props Conversion Baselines
;; ============================================================================

(defn manual-props-empty
  "Baseline: Empty JS object (no conversion)"
  []
  #js {})

(defn manual-props-shallow
  "Baseline: Manually create shallow props object"
  []
  #js {:className "foo"
       :id "bar"
       :data-test "baz"
       :aria-label "qux"
       :tabIndex 0})

(defn manual-props-deep
  "Baseline: Manually create nested props object"
  []
  #js {:style #js {:margin #js {:top 10 :bottom 20}}
       :data #js {:user #js {:name "Alice" :age 30}}})

(defn manual-props-with-array
  "Baseline: Props with array"
  []
  #js {:items #js ["a" "b" "c"]
       :numbers #js [1 2 3]})

(defn manual-props-large
  "Baseline: Large props object with 50 keys"
  []
  #js {:key0 "value0" :key1 "value1" :key2 "value2" :key3 "value3" :key4 "value4"
       :key5 "value5" :key6 "value6" :key7 "value7" :key8 "value8" :key9 "value9"
       :key10 "value10" :key11 "value11" :key12 "value12" :key13 "value13" :key14 "value14"
       :key15 "value15" :key16 "value16" :key17 "value17" :key18 "value18" :key19 "value19"
       :key20 "value20" :key21 "value21" :key22 "value22" :key23 "value23" :key24 "value24"
       :key25 "value25" :key26 "value26" :key27 "value27" :key28 "value28" :key29 "value29"
       :key30 "value30" :key31 "value31" :key32 "value32" :key33 "value33" :key34 "value34"
       :key35 "value35" :key36 "value36" :key37 "value37" :key38 "value38" :key39 "value39"
       :key40 "value40" :key41 "value41" :key42 "value42" :key43 "value43" :key44 "value44"
       :key45 "value45" :key46 "value46" :key47 "value47" :key48 "value48" :key49 "value49"})

;; ============================================================================
;; Component Memoization Baselines
;; ============================================================================

(defn simple-component-unmemoized
  "Baseline: Simple component without memoization"
  [props]
  (react/createElement "div" nil (.-name props)))

(def simple-component-memoized
  "Baseline: Component wrapped with React.memo (default shallow comparison)"
  (react/memo simple-component-unmemoized))

(defn equality-check-shallow
  "Baseline: React's default shallow equality check for memo"
  [prev-props next-props]
  ;; This mimics React's default shallowEqual
  (let [prev-keys (js/Object.keys prev-props)
        next-keys (js/Object.keys next-props)]
    (and (= (.-length prev-keys) (.-length next-keys))
         (every? (fn [key]
                  (identical? (aget prev-props key)
                             (aget next-props key)))
                prev-keys))))
