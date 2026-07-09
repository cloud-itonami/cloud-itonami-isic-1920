(ns refining.registry
  "Pure-function refinery-process + product-yield record construction --
  an append-only refinery-batch book-of-record draft -- AND the pure
  refinery-safety range-check functions the Refinery Safety Governor
  calls to re-verify a batch's own physical ground truth before any unit
  processing or product yield.

  Unlike `freightops`/4920's own registry (which delegates tracking-
  number validation to a real, pre-existing bespoke capability library
  `kotoba-lang/logistics`), this petroleum-refining vertical has NO
  pre-existing capability library to wrap -- there is no 'kotoba-lang/
  refining' to call. So this namespace is self-contained: the range
  checks (unit-temperature two-sided window, unit-pressure two-sided
  window, yield-rate vs required fraction) are pure functions defined
  HERE, not delegated. The actor layer adds the governed proposal/
  approval loop on top; the governor calls these same pure functions to
  INDEPENDENTLY re-verify the batch's own recorded values before any
  real-world unit processing or product yield, rather than trusting the
  advisor's self-reported confidence.

  Like every sibling actor's registry, there is no single international
  reference-number standard for a refinery-process or product-yield
  record -- every operator/jurisdiction assigns its own reference
  format. This namespace does NOT invent one beyond a jurisdiction-
  scoped sequence number; it validates the record's required fields,
  the same honest, non-fabricating discipline `refining.facts` uses.

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real DCS / refinery information system. It builds the
  RECORD an operator would keep, not the act of processing a real
  refinery batch or yielding real product itself (that is `refining.
  operation`'s `:unit/process`/`:product/yield`, always human-gated --
  see README `Actuation`)."
  (:require [clojure.string :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is
  the operator's act, not this actor's. See README `Actuation`."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

;; ----------------------------- refinery-safety range checks (pure) -----------------------------
;;
;; The Refinery Safety Governor calls these to INDEPENDENTLY re-verify the
;; batch's own recorded physical values before authorizing a unit process
;; or product yield. Each returns true when the value is provably OUTSIDE
;; the safe envelope -- the conservative refinery-safety choice, matching
;; the two-sided-tolerance discipline of the aerospace siblings and the
;; ratio/threshold discipline of the fabrication siblings: a value that
;; cannot be certified inside the safe envelope is treated as a violation,
;; not as 'unknown therefore ok'. Missing data -> violation (cannot verify
;; safe to process / yield).

(defn unit-temp-out-of-range?
  "Two-sided unit-temperature window (the aerospace two-sided-tolerance
  pattern, applied to a refinery distillation/reformer unit): the unit's
  measured operating temperature must stay within its declared safe
  window [min, max] in degrees Celsius. Actual below min fails to reach
  conversion / leaves light ends under-fractionated; above max risks
  thermal runaway, coking, and overpressure of the column -- the
  precursor to a refinery fire or explosion. Missing any bound ->
  unsafe (cannot verify the safe window before charging the unit)."
  [actual min max]
  (cond
    (or (nil? actual) (nil? min) (nil? max)) true
    (or (< actual min) (> actual max))       true
    :else                                    false))

(defn unit-pressure-out-of-range?
  "Two-sided unit-pressure window (the aerospace two-sided-tolerance
  pattern, applied to a refinery unit operating pressure): the unit's
  measured operating pressure must stay within its declared safe window
  [min, max] in megapascals. Actual below min indicates loss of
  containment direction / upset feed; above max risks vessel rupture and
  overpressure relief through the flare -- exceed the rated envelope and
  the pressure-relief path is the only thing between the unit and a
  catastrophic loss of containment. Missing any bound -> unsafe."
  [actual min max]
  (cond
    (or (nil? actual) (nil? min) (nil? max)) true
    (or (< actual min) (> actual max))       true
    :else                                    false))

(defn yield-rate-insufficient?
  "Realized yield fraction vs required yield fraction -- the fabrication
  'measured ratio falls short of rated minimum' discipline (the inverse
  of the measured-exceeds-limit pattern: a yield too LOW is the failure).
  When the realized product yield fraction falls below the required yield
  fraction, the unit is not converting feed to specification and the run
  cannot be finalized as on-spec product. Missing either value ->
  unsafe (cannot verify the run met its yield basis)."
  [actual-yield required-yield]
  (cond
    (or (nil? actual-yield) (nil? required-yield)) true
    (< actual-yield required-yield)                true
    :else                                          false))

;; ----------------------------- record construction -----------------------------

(defn register-process-record
  "Validate + construct the REFINERY-PROCESS registration DRAFT -- the
  operator's own legal act of charging and running a real refinery unit
  (a distillation/reformer/cracker unit processing a batch of crude).
  Pure function -- does not touch any real DCS or refinery information
  system; it builds the RECORD an operator would keep. `refining.
  governor` independently re-verifies the batch's own unit-temperature
  window, unit-pressure window, contamination-flag and flare ground
  truth, and blocks a double-process of the same batch, before this is
  ever allowed to commit."
  [refinery-batch-id jurisdiction sequence]
  (when-not (and refinery-batch-id (not= refinery-batch-id ""))
    (throw (ex-info "process: refinery_batch_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "process: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "process: sequence must be >= 0" {})))
  (let [process-number (str (str/upper-case jurisdiction) "-PROCESS-" (zero-pad sequence 6))
        record {"record_id" process-number
                "kind" "process-record-draft"
                "refinery_batch_id" refinery-batch-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "process_number" process-number
     "certificate" (unsigned-certificate "RefineryProcess" process-number process-number)}))

(defn register-yield-record
  "Validate + construct the PRODUCT-YIELD registration DRAFT -- the
  operator's own legal act of finalizing and yielding real refined
  product from a processed batch (custody transfer of on-spec product,
  yield reconciliation). Pure function -- does not touch any real
  refinery-accounting system; it builds the RECORD an operator would
  keep. `refining.governor` independently re-verifies the batch's own
  yield-rate and evidence completeness, and blocks a double-yield of
  the same batch, before this is ever allowed to commit."
  [refinery-batch-id jurisdiction sequence]
  (when-not (and refinery-batch-id (not= refinery-batch-id ""))
    (throw (ex-info "yield: refinery_batch_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "yield: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "yield: sequence must be >= 0" {})))
  (let [yield-number (str (str/upper-case jurisdiction) "-YIELD-" (zero-pad sequence 6))
        record {"record_id" yield-number
                "kind" "yield-record-draft"
                "refinery_batch_id" refinery-batch-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "yield_number" yield-number
     "certificate" (unsigned-certificate "ProductYield" yield-number yield-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
