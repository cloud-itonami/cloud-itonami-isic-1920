(ns refining.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2607189300,
  Wave5 rollout ledger): this repo's `docs/samples/operator-console.html`
  was previously a hand-typed static placeholder with no generator at
  all. This namespace drives the REAL actor stack (`refining.operation` ->
  `refining.governor` -> `refining.store`) through a scenario adapted from
  this repo's own `refining.sim` demo driver (`clojure -M:dev:run`,
  confirmed by actually running it before this file was written --
  every id and disposition below matches `refining.store/demo-data`'s
  seeded batches and `refining.governor`'s own documented checks exactly,
  so it was safe to reuse rather than author from scratch), covering the
  phase-3 auto-commit `:batch/intake`, the full escalate+approve lifecycle
  for one batch across `:assay/verify` / `:unit/process` / `:product/yield`
  -- the latter two ALWAYS escalate, never auto, at any phase -- and six
  distinct HARD-hold reasons that never reach a human, plus the two
  double-actuation guards, and rendered deterministically -- no invented
  numbers, no timestamps in the page content, byte-identical across
  reruns against the same seed (verified by diffing two consecutive runs
  before shipping).

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [jp-go-dds.skin]
            [kotoba.lang.text :as str]
            [refining.store :as store]
            [refining.operation :as op]
            [langgraph.graph :as g]))

;; ----------------------------- harness (unchanged across every repo
;; in this cluster -- do not rewrite, only copy) -----------------------

(def ^:private operator
  {:actor-id "op-1" :actor-role :shift-superintendent :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}}
          {:thread-id tid :resume? true}))

(defn run-demo!
  "Runs a fresh seeded store through a scenario mixing every disposition
  this actor can reach, using ONLY real batch ids from
  `refining.store/demo-data`:

  batch-1 (Negishi/JPN, clean) walks the full clean lifecycle: a
  `:batch/intake` directory-normalization patch is a phase-3,
  no-capital-risk auto-commit (governor clean, `:batch/intake` is the
  ONLY op in phase 3's `:auto` set); `:assay/verify` (JPN has a real
  spec-basis in `refining.facts`) ALWAYS escalates (not auto-eligible
  at any phase) and is approved by a human shift superintendent, after
  which `:unit/process` and `:product/yield` -- the two REAL-WORLD
  actuation events this actor performs (charging and running a real
  distillation/reformer unit / finalizing on-spec product custody
  transfer) -- ALSO ALWAYS escalate (the governor's own `high-stakes`
  gate AND the phase table agree, independently, that actuation is
  never auto, at any phase) and are each approved, producing one draft
  process record (`JPN-PROCESS-000000`) and one draft yield record
  (`JPN-YIELD-000000`).

  Then six DISTINCT HARD-hold reasons, none of which ever reach a
  human (a human approver cannot override a HARD violation), each
  isolating exactly ONE refinery-safety failure mode:
    - batch-2 (jurisdiction ATL, not in `refining.facts/catalog`):
      `:assay/verify` HARD-holds on `:no-spec-basis` -- the advisor
      may not invent a jurisdiction's refinery process-safety
      requirements.
    - batch-3 (unit temp 450.0 ℃ outside [350.0, 400.0] ℃): assayed
      +approved first so evidence is on file and this hold is isolated
      to the temperature check alone, then `:unit/process` HARD-holds
      on `:unit-temp-out-of-range`.
    - batch-4 (unit pressure 6.0 MPa > max 5.0 MPa): assayed+approved,
      then `:unit/process` HARD-holds on `:unit-pressure-out-of-range`.
    - batch-5 (`:contamination-flag-raised? true`, unresolved): assayed
      +approved, then `:unit/process` HARD-holds on
      `:contamination-flag-unresolved`.
    - batch-6 (`:flare-operational? false`): assayed+approved, then
      `:unit/process` HARD-holds on `:flare-system-inoperational`.
    - batch-7 (yield actual 0.70 < required 0.85): assayed+approved and
      process-approved (process checks are clean), then
      `:product/yield` HARD-holds on `:yield-rate-insufficient`.

  Finally the two double-actuation guards on the already-actuated
  batch-1: re-`:unit/process` HARD-holds on `:already-processed`;
  re-`:product/yield` HARD-holds on `:already-yielded`.

  Returns the resulting store -- every field `render` below reads is
  real governor/store output, not a hand-typed copy."
  []
  (let [db (store/seed-db)
        actor (op/build db)]

    ;; batch-1: clean directory-normalization patch -- phase-3
    ;; auto-commit, no capital risk yet.
    (exec! actor "b1-intake" {:op :batch/intake :subject "batch-1"
                               :patch {:id "batch-1" :operator "Akita Petroleum Co"}})

    ;; batch-1: per-jurisdiction assay/verification (JPN has a real
    ;; spec-basis) -- ALWAYS escalates, approved by a human.
    (exec! actor "b1-assay" {:op :assay/verify :subject "batch-1"})
    (approve! actor "b1-assay")

    ;; batch-1: REAL unit process (charges and runs a distillation/
    ;; reformer unit) -- ALWAYS escalates regardless of phase or
    ;; confidence, approved by a human shift superintendent.
    (exec! actor "b1-process" {:op :unit/process :subject "batch-1"})
    (approve! actor "b1-process")

    ;; batch-1: REAL product yield (on-spec product custody transfer)
    ;; -- ALWAYS escalates, approved by a human.
    (exec! actor "b1-yield" {:op :product/yield :subject "batch-1"})
    (approve! actor "b1-yield")

    ;; batch-2 (ATL): no official spec-basis in refining.facts -> HARD
    ;; hold on :no-spec-basis, never reaches a human.
    (exec! actor "b2-assay" {:op :assay/verify :subject "batch-2"})

    ;; batch-3: assay JPN first (clean escalate+approve) so evidence is
    ;; on file and the unit-temp hold below is isolated.
    (exec! actor "b3-assay" {:op :assay/verify :subject "batch-3"})
    (approve! actor "b3-assay")
    (exec! actor "b3-process" {:op :unit/process :subject "batch-3"})

    ;; batch-4: assay+approve, then unit pressure 6.0 MPa outside
    ;; [1.0, 5.0] MPa -> HARD hold on :unit-pressure-out-of-range.
    (exec! actor "b4-assay" {:op :assay/verify :subject "batch-4"})
    (approve! actor "b4-assay")
    (exec! actor "b4-process" {:op :unit/process :subject "batch-4"})

    ;; batch-5: assay+approve, then unresolved contamination flag ->
    ;; HARD hold on :contamination-flag-unresolved.
    (exec! actor "b5-assay" {:op :assay/verify :subject "batch-5"})
    (approve! actor "b5-assay")
    (exec! actor "b5-process" {:op :unit/process :subject "batch-5"})

    ;; batch-6: assay+approve, then flare inoperational -> HARD hold on
    ;; :flare-system-inoperational.
    (exec! actor "b6-assay" {:op :assay/verify :subject "batch-6"})
    (approve! actor "b6-assay")
    (exec! actor "b6-process" {:op :unit/process :subject "batch-6"})

    ;; batch-7: assay+approve, process+approve (process checks clean),
    ;; then yield rate 0.70 < required 0.85 -> HARD hold on
    ;; :yield-rate-insufficient.
    (exec! actor "b7-assay" {:op :assay/verify :subject "batch-7"})
    (approve! actor "b7-assay")
    (exec! actor "b7-process" {:op :unit/process :subject "batch-7"})
    (approve! actor "b7-process")
    (exec! actor "b7-yield" {:op :product/yield :subject "batch-7"})

    ;; Double-actuation guards on already-actuated batch-1.
    (exec! actor "b1-process-again" {:op :unit/process :subject "batch-1"})
    (exec! actor "b1-yield-again" {:op :product/yield :subject "batch-1"})

    db))

;; ----------------------------- rendering -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- last-fact-for [ledger subject-id]
  (last (filter #(= (:subject %) subject-id) ledger)))

(defn- status-cell [ledger subject-id]
  (let [f (last-fact-for ledger subject-id)]
    (cond
      (nil? f) "<span class=\"muted\">no activity</span>"
      (= :committed (:t f)) "<span class=\"ok\">committed</span>"
      (= :approval-granted (:t f)) "<span class=\"ok\">approved &amp; committed</span>"
      (= :governor-hold (:t f))
      (let [rule (-> f :violations first :rule)]
        (str "<span class=\"critical\">HARD hold &middot; " (esc (name (or rule :unknown))) "</span>"))
      (= :approval-requested (:t f)) "<span class=\"warn\">awaiting approval</span>"
      :else "<span class=\"muted\">in progress</span>")))

(defn- batch-row [ledger {:keys [id refinery-name operator jurisdiction
                                  processed? yield-finalized?]}]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc id) (esc refinery-name) (esc operator) (esc jurisdiction)
          (if processed? "<span class=\"ok\">processed</span>" "not processed")
          (if yield-finalized? "<span class=\"ok\">yielded</span>" "not yielded")
          (status-cell ledger id)))

(defn- ledger-row [{:keys [t op subject disposition basis]}]
  (format "        <tr><td>%s</td><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc (name t)) (esc (name (or op :n-a))) (esc subject)
          (esc (or (some->> basis (map #(if (keyword? %) (name %) %)) (str/join ", "))
                   (some-> disposition name) ""))))

(defn- record-row [prefix {:strs [record_id refinery_batch_id jurisdiction kind immutable]}]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc prefix) (esc record_id) (esc refinery_batch_id) (esc jurisdiction)
          (if immutable "<span class=\"ok\">immutable draft</span>" (esc kind))))

(def ^:private action-gate-rows
  ;; Static description of this actor's own op contract
  ;; (`refining.governor`/`refining.phase`) -- documentation of fixed
  ;; behavior, not runtime telemetry, so it is legitimately
  ;; hand-described rather than derived from a live run.
  ["        <tr><td><code>:batch/intake</code></td><td><span class=\"ok\">phase-3 auto-commit when clean, no capital risk yet -- the ONLY auto-eligible op in this domain</span></td></tr>"
   "        <tr><td><code>:assay/verify</code></td><td><span class=\"warn\">ALWAYS human approval &middot; spec-basis independently checked against <code>refining.facts</code>, never fabricated</span></td></tr>"
   "        <tr><td><code>:unit/process</code></td><td><span class=\"warn\">ALWAYS human approval &middot; real-world actuation (charges and runs a real distillation/reformer unit) &middot; unit-temp window, unit-pressure window, contamination flag, flare operational and double-process guard ALL independently re-verified, never auto at any phase</span></td></tr>"
   "        <tr><td><code>:product/yield</code></td><td><span class=\"warn\">ALWAYS human approval &middot; real-world actuation (on-spec product custody transfer) &middot; evidence completeness + yield-rate + double-yield guard independently enforced, never auto at any phase</span></td></tr>"])

(defn render
  "Renders the full operator-console.html document from a store `db`
  that has already run `run-demo!` (or any other real scenario)."
  [db]
  (let [ledger (vec (store/ledger db))
        batches (store/all-refinery-batches db)
        batch-rows (str/join "\n" (map (partial batch-row ledger) batches))
        ledger-rows (str/join "\n" (map ledger-row ledger))
        process-rows (str/join "\n" (map (partial record-row "process") (store/process-history db)))
        yield-rows (str/join "\n" (map (partial record-row "yield") (store/yield-history db)))]
    (str
     "<html><head><meta charset=\"utf-8\"><title>cloud-itonami-isic-1920 &middot; petroleum refining</title><style>"
     (jp-go-dds.skin/dds+skin)
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Petroleum refining (ISIC 1920) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · unit process/product yield always human-approved</span>\n"
     "</header>\n"
     "<main>\n"
     "  <section class=\"card\">\n"
     "    <h2>Refinery batches</h2>\n"
     "    <p class=\"muted\">Demo snapshot — build-time-generated from <code>refining.store</code> via <code>refining.render-html</code> (<code>clojure -M:dev:render-html</code>), regenerated nightly.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Batch</th><th>Refinery</th><th>Operator</th><th>Jurisdiction</th><th>Unit process</th><th>Product yield</th><th>Last op status</th></tr></thead>\n"
     "      <tbody>\n"
     batch-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Draft process / yield records</h2>\n"
     "    <p class=\"muted\">Unsigned drafts only — the operator's own act of signing is outside this actor's authority (see README <code>Actuation</code>).</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Kind</th><th>Record id</th><th>Batch</th><th>Jurisdiction</th><th>Status</th></tr></thead>\n"
     "      <tbody>\n"
     process-rows (when (seq process-rows) "\n")
     yield-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Action gate (Refinery Safety Governor)</h2>\n"
     "    <p class=\"muted\">HARD holds cannot be overridden by a human approver. Jurisdiction spec-basis, unit-temperature window, unit-pressure window, yield rate, contamination flag and flare operational ground truth are independently recomputed, never trusted from the advisor's proposal; a real unit process or product yield is always a human shift superintendent's call, at every rollout phase.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Gate</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" action-gate-rows) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (this run)</h2>\n"
     "    <p class=\"muted\">Append-only decision-fact log — every proposal, hold and commit this scenario produced.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Fact</th><th>Op</th><th>Subject</th><th>Basis</th></tr></thead>\n"
     "      <tbody>\n"
     ledger-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "</main>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        db (run-demo!)
        html (render db)]
    (spit out html)
    (println "wrote" out "(" (count (store/ledger db)) "ledger facts,"
             (count (store/process-history db)) "process drafts,"
             (count (store/yield-history db)) "yield drafts )")))
