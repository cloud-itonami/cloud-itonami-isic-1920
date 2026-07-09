(ns refining.governor
  "Refinery Safety Governor -- the independent compliance layer that
  earns the RefiningAdvisor the right to commit. The LLM has no notion
  of jurisdictional refinery process-safety / major-accident-hazard law,
  whether a unit's own measured operating temperature actually lies
  inside its declared safe window, whether the unit's own operating
  pressure actually lies inside its declared safe window, whether the
  realized product yield actually meets its required fraction, whether
  the flare (the overpressure-relief path) is actually operational,
  whether an open contamination flag has actually been resolved, or when
  an act stops being a draft and becomes a real-world unit process or
  product yield, so this MUST be a separate system able to *reject* a
  proposal and fall back to HOLD.

  Unlike `freightops`/4920's own governor (built on TOP of a real,
  pre-existing bespoke capability library `kotoba-lang/logistics`),
  this petroleum-refining vertical has NO pre-existing refinery
  capability library to delegate to -- so the three physical range
  checks (unit-temperature window, unit-pressure window, yield-rate vs
  required) are pure functions defined in `refining.registry` and called
  directly here, the SAME 'reuse a capability library's own validated
  function' discipline `retailops.governor`'s ean13 check establishes,
  here applied to this vertical's OWN pure registry functions rather
  than a separate library.

  `:itonami.blueprint/governor` is `:refinery-safety-governor`, grep-
  verified UNIQUE fleet-wide -- no naming-collision precedent
  question, a fresh independent build following the SAME governed-
  actor architecture (langgraph StateGraph + independent Governor +
  Phase 0->3 rollout) established by `cloud-itonami-isic-6511`.

  Seven checks, in priority order, ALL HARD violations: a human
  approver CANNOT override them. The confidence/actuation gate is
  SOFT: it asks a human to look (low confidence / actuation), and the
  human may approve -- but see `refining.phase`: for `:stake :unit/
  process`/`:product/yield` (a real process or yield) NO phase ever
  allows auto-commit either. Two independent layers agree that
  actuation is always a human call.

    1. Spec-basis                  -- did the jurisdiction proposal cite
                                       an OFFICIAL source
                                       (`refining.facts`), or invent one?
    2. Evidence incomplete         -- for `:unit/process`/`:product/
                                       yield`, has the batch actually
                                       been assayed with a full process-
                                       safety / mechanical-integrity
                                       evidence checklist on file?
    3. Unit temperature out of
       range                       -- for `:unit/process`, INDEPENDENTLY
                                       verify the unit's own measured
                                       operating temperature stays inside
                                       its declared safe window [min,max]
                                       via `refining.registry/unit-temp-
                                       out-of-range?` (the aerospace
                                       two-sided-tolerance discipline),
                                       evaluated UNCONDITIONALLY.
    4. Unit pressure out of range  -- for `:unit/process`, INDEPENDENTLY
                                       verify the unit's own measured
                                       operating pressure stays inside
                                       its declared safe window [min,max]
                                       via `refining.registry/unit-
                                       pressure-out-of-range?` (the
                                       aerospace two-sided-tolerance
                                       discipline), evaluated
                                       UNCONDITIONALLY.
    5. Yield rate insufficient     -- for `:product/yield`, INDEPENDENTLY
                                       verify the realized yield fraction
                                       meets the required fraction via
                                       `refining.registry/yield-rate-
                                       insufficient?` (the fabrication
                                       measured-ratio-vs-rated-minimum
                                       discipline).
    6. Contamination flag
       unresolved                  -- reported by THIS proposal itself,
                                       or already on file for the batch
                                       (`:contamination-flag-raised? true`
                                       AND `:contamination-flag-resolved?
                                       false`) -- a HARD, un-overridable
                                       hold. Evaluated UNCONDITIONALLY at
                                       `:unit/process` (a contaminated
                                       charge never runs).
    7. Flare system inoperational  -- for `:unit/process`, INDEPENDENTLY
                                       verify the flare (the overpressure-
                                       relief path) is operational: the
                                       Texas City BP isomerization unit
                                       explosion (2005) root cause was a
                                       raffinate splitter tower liquid-
                                       level and pressure relief venting
                                       to a blowdown stack with a lit
                                       flare -- the relief path MUST be
                                       available before a unit is charged.
                                       Evaluated UNCONDITIONALLY.
    8. Confidence floor / actuation
       gate                        -- LLM confidence below threshold,
                                       OR the op is `:unit/process`/
                                       `:product/yield` (REAL acts)
                                       -> escalate.

  Two more guards, double-process/double-yield prevention, are
  enforced but NOT listed as numbered HARD checks above because they
  need no upstream comparison at all -- `already-processed-violations`/
  `already-yielded-violations` refuse to process/yield the SAME batch
  twice, off dedicated `:processed?`/`:yield-finalized?` facts (never a
  `:status` value) -- the SAME 'check a dedicated boolean, not status'
  discipline every prior governor's guards establish, informed by
  `cloud-itonami-isic-6492`'s status-lifecycle bug (ADR-2607071320)."
  (:require [refining.facts :as facts]
            [refining.registry :as registry]
            [refining.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Charging and running a real refinery unit (a distillation/reformer
  unit processing a batch of crude) and finalizing/yielding real
  refined product (custody transfer of on-spec product) are the two
  real-world actuation events this actor performs -- a two-member set,
  matching every sibling's own dual-actuation shape."
  #{:unit/process :product/yield})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A `:assay/verify` (or `:unit/process`/`:product/yield`) proposal
  with no spec-basis citation is a HARD violation -- never invent a
  jurisdiction's refinery process-safety requirements."
  [{:keys [op]} proposal]
  (when (contains? #{:assay/verify :unit/process :product/yield} op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-spec-basis
          :detail "公式spec-basisの引用が無い提案は法域要件として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:unit/process`/`:product/yield`, the jurisdiction's required
  process-safety / mechanical-integrity evidence (crude-assay record,
  unit-integrity inspection, pressure-relief device test record) must
  actually be satisfied -- do not trust the advisor's self-reported
  confidence alone."
  [{:keys [op subject]} st]
  (when (contains? #{:unit/process :product/yield} op)
    (let [b (store/refinery-batch st subject)
          assay (store/assay-of st subject)]
      (when-not (and assay
                     (facts/required-evidence-satisfied?
                      (:jurisdiction b) (:checklist assay)))
        [{:rule :evidence-incomplete
          :detail "法域の必要書類(crude-assay/unit-integrity/pressure-relief device test record等)が充足していない状態での提案"}]))))

(defn- unit-temp-out-of-range-violations
  "For `:unit/process`, INDEPENDENTLY verify the unit's own measured
  operating temperature stays inside its declared safe window [min,max]
  via `refining.registry/unit-temp-out-of-range?` (the aerospace
  two-sided-tolerance discipline). Evaluated UNCONDITIONALLY (every
  process needs a unit temperature inside its safe window)."
  [{:keys [op subject]} st]
  (when (= op :unit/process)
    (let [b (store/refinery-batch st subject)]
      (when (registry/unit-temp-out-of-range?
             (:unit-temp-celsius-actual b)
             (:unit-temp-min b)
             (:unit-temp-max b))
        [{:rule :unit-temp-out-of-range
          :detail (str subject " のユニット温度(" (:unit-temp-celsius-actual b)
                      " ℃)が安全窓[" (:unit-temp-min b) ", "
                      (:unit-temp-max b) "] ℃ の外 -- 処理提案は進められない")}]))))

(defn- unit-pressure-out-of-range-violations
  "For `:unit/process`, INDEPENDENTLY verify the unit's own measured
  operating pressure stays inside its declared safe window [min,max]
  via `refining.registry/unit-pressure-out-of-range?` (the aerospace
  two-sided-tolerance discipline). Evaluated UNCONDITIONALLY."
  [{:keys [op subject]} st]
  (when (= op :unit/process)
    (let [b (store/refinery-batch st subject)]
      (when (registry/unit-pressure-out-of-range?
             (:unit-pressure-mpa-actual b)
             (:unit-pressure-min b)
             (:unit-pressure-max b))
        [{:rule :unit-pressure-out-of-range
          :detail (str subject " のユニット圧力(" (:unit-pressure-mpa-actual b)
                      " MPa)が安全窓[" (:unit-pressure-min b) ", "
                      (:unit-pressure-max b) "] MPa の外 -- 処理提案は進められない")}]))))

(defn- yield-rate-insufficient-violations
  "For `:product/yield`, INDEPENDENTLY verify the realized yield
  fraction meets the required fraction via `refining.registry/yield-
  rate-insufficient?` (the fabrication measured-ratio-vs-rated-minimum
  discipline). Evaluated UNCONDITIONALLY (a yield cannot be finalized
  below its required fraction)."
  [{:keys [op subject]} st]
  (when (= op :product/yield)
    (let [b (store/refinery-batch st subject)
          yields (:target-yields b)]
      (when (registry/yield-rate-insufficient?
             (:actual yields) (:required yields))
        [{:rule :yield-rate-insufficient
          :detail (str subject " の実収率(" (:actual yields)
                      ")が要求収率(" (:required yields)
                      ")を下回る -- 製品歩留提案は進められない")}]))))

(defn- contamination-flag-unresolved-violations
  "An unresolved contamination flag -- reported by THIS proposal
  itself, or already on file for the batch -- is a HARD, un-
  overridable hold. Evaluated UNCONDITIONALLY at `:unit/process` so a
  batch with an open contamination concern never runs."
  [{:keys [op subject]} st]
  (when (= op :unit/process)
    (let [b (store/refinery-batch st subject)]
      (when (and (true? (:contamination-flag-raised? b)) (not (true? (:contamination-flag-resolved? b))))
        [{:rule :contamination-flag-unresolved
          :detail (str subject " は未解決の汚染フラグがある -- 処理提案は進められない")}]))))

(defn- flare-system-inoperational-violations
  "For `:unit/process`, INDEPENDENTLY verify the flare (the over-
  pressure-relief path) is operational. The Texas City BP isomerization
  unit explosion (2005) root was a pressure-relief path venting to a
  blowdown stack: the relief path MUST be available before a unit is
  charged. Evaluated UNCONDITIONALLY."
  [{:keys [op subject]} st]
  (when (= op :unit/process)
    (let [b (store/refinery-batch st subject)]
      (when (not (true? (:flare-operational? b)))
        [{:rule :flare-system-inoperational
          :detail (str subject " のフレア(過圧 relief 経路)が非稼働 -- 過圧逃がし経路が利用不可の状態での処理提案は進められない")}]))))

(defn- already-processed-violations
  "For `:unit/process`, refuses to process the SAME batch twice, off a
  dedicated `:processed?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :unit/process)
    (when (store/refinery-batch-already-processed? st subject)
      [{:rule :already-processed
        :detail (str subject " は既に処理済み")}])))

(defn- already-yielded-violations
  "For `:product/yield`, refuses to yield the SAME batch's product
  twice, off a dedicated `:yield-finalized?` fact (never a `:status`
  value)."
  [{:keys [op subject]} st]
  (when (= op :product/yield)
    (when (store/refinery-batch-already-yielded? st subject)
      [{:rule :already-yielded
        :detail (str subject " は既に製品歩留確定済み")}])))

(defn check
  "Censors a RefiningAdvisor proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (unit-temp-out-of-range-violations request st)
                           (unit-pressure-out-of-range-violations request st)
                           (yield-rate-insufficient-violations request st)
                           (contamination-flag-unresolved-violations request st)
                           (flare-system-inoperational-violations request st)
                           (already-processed-violations request st)
                           (already-yielded-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
