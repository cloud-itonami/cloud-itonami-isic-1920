# Operator Guide

## First Deployment
1. Register operators, refineries, refinery batches, and shift
   superintendents.
2. Import batch, assay, and yield history.
3. Seed the per-jurisdiction spec-basis catalog (`refining.facts`) for
   the jurisdictions you actually operate in, citing real official
   sources only.
4. Run read-only spec-basis validation per jurisdiction.
5. Configure contamination-flag escalation and refinery-accounting
   accounts.
6. Publish a dry-run yield and audit export.

## Minimum Production Controls
- spec-basis validation before any assay, process, or yield
- full process-safety / mechanical-integrity evidence (crude-assay
  record, unit-integrity inspection, pressure-relief device test record)
  before any process
- unit-temperature, unit-pressure, flare (overpressure-relief path) and
  contamination-flag checks before any process
- yield-rate check before any yield
- contamination-flag escalation gate
- audit export for every process, yield, and hold
- backup manual unit-process and product-yield process

## A Day in the Life: Intake → Verify → Process → Yield → Audit

Community Petroleum Refining (ISIC 1920, `cloud-itonami-isic-1920`)
runs on the same intake / advise / govern / decide / commit-or-hold loop
as every itonami blueprint, but here the loop is concrete: a regional
refiner needs to bring a batch (say, a crude charge for a distillation
unit at the Negishi refinery) from intake through assay verification to
a unit process and a product yield. Walking through one batch, end to
end:

1. **Intake.** The operator books the batch through `:forms`: refinery
   name, operator, jurisdiction, and the batch's own physical record (API
   gravity in, sulfur in, target yields {required, actual}, measured unit
   temperature with its safe window [min, max], unit pressure with its
   safe window [min, max], flare-operational status, contamination-flag
   status). This creates a batch record at `:batch/intake` status. The
   RefiningAdvisor only normalizes the patch; it does not invent the
   refinery name, operator, jurisdiction, or any physical value.
2. **Verify.** The RefiningAdvisor drafts a per-jurisdiction process-
   safety / mechanical-integrity evidence checklist (`:assay/verify`)
   from `refining.facts`, citing the jurisdiction's official spec-basis
   (owner authority, legal basis, provenance) and listing the required
   evidence (crude-assay record, unit-integrity inspection, pressure-
   relief device test record). The `:refinery-safety-governor` sign-off
   gate must clear: it checks the jurisdiction actually has an official
   spec-basis on file (never invent one). A jurisdiction with no
   spec-basis is a HARD hold at the governor node -- it never even
   reaches a human. This assay always escalates to a human for approval;
   it is never auto.
3. **Process.** Before the unit can be charged and run, the
   `:refinery-safety-governor` sign-off gate runs the full HARD check
   set against the batch's own ground truth: the spec-basis exists, the
   evidence checklist is complete, the measured unit temperature is
   inside `[min, max]`, the measured unit pressure is inside `[min, max]`,
   the flare is operational (overpressure-relief path available), no
   contamination flag is open, and the batch has not already been
   processed. Any failure is a HARD hold that a human cannot override.
   If every check is clean, the proposal STILL always escalates to a
   human shift superintendent -- a `:unit/process` never auto-commits at
   any phase. On approval, the process record is drafted
   (`<JURISDICTION>-PROCESS-000001`) and the batch's `:processed?` flag
   is set.
4. **Yield.** Once the batch has actually been processed, the product
   period is yielded (`:product/yield`): on-spec product finalization
   and custody transfer. The governor re-checks the spec-basis, the
   evidence completeness, the realized yield fraction against its
   required fraction, and that this batch's product has not already been
   yielded. As with the process, a clean yield STILL always escalates to
   a human shift superintendent -- `:product/yield` never auto-commits.
   On approval the yield record is drafted (`<JURISDICTION>-YIELD-000001`)
   and the batch's `:yield-finalized?` flag is set.
5. **Audit.** The assay, the process sign-off, the process record, the
   yield sign-off, and the yield record are all appended to the
   `:audit-ledger` -- immutable and exportable, so a custody or
   specification dispute can be traced back to the exact spec-basis
   citation, evidence checklist, and superintendent sign-off that
   authorized the process and yield. If something is wrong with the
   batch (a temperature excursion, a pressure anomaly, a contamination
   concern), that gets raised as a contamination flag and routed through
   the escalation gate instead of being silently suppressed -- a process
   for that batch then waits on governor sign-off of the flag's
   resolution.

Any deviation from this loop is exactly what the Trust Controls in
`docs/business-model.md` exist to catch: a batch assayed against a
fabricated spec-basis, a process started with incomplete evidence or
outside the unit-temperature / unit-pressure window, a process started
with an inoperational flare or a suppressed contamination flag, a yield
finalized below its required fraction, or a yield posted without a human
sign-off.

## Feel the Decision Gate: `kbb -M:dev:run`

This vertical has no companion playable prototype yet (unlike the
freight sibling's `itonami/freight-dispatch` game). The fastest hands-on
way to feel why the `:refinery-safety-governor` gate exists is the
bundled demo (`kbb -M:dev:run`), which walks one clean batch through
intake → verify → process → yield (each process/yield pausing for human
approval) and then exercises every HARD-hold failure mode in isolation:

- a jurisdiction with no official spec-basis → HOLD (`:no-spec-basis`),
- a measured unit temperature outside the safe window → HOLD
  (`:unit-temp-out-of-range`),
- a measured unit pressure outside the safe window → HOLD
  (`:unit-pressure-out-of-range`),
- a realized yield fraction below the required fraction → HOLD
  (`:yield-rate-insufficient`),
- an unresolved contamination flag → HOLD (`:contamination-flag-unresolved`),
- an inoperational flare (overpressure-relief path unavailable) → HOLD
  (`:flare-system-inoperational`),
- a double process of the same batch → HOLD (`:already-processed`),
- a double yield of the same batch → HOLD (`:already-yielded`).

Each HOLD settles at the governor node and never reaches a human
approver -- the same failure mode the audit ledger is built to catch and
the minimum production controls above are built to prevent. It is not a
substitute for those controls, but it is the fastest way for a new
operator (or a reviewer) to feel, hands-on, why the gate exists before
touching a real deployment.

## Certification
Certified operators must prove spec-basis-grounded assay verification,
evidence-backed process readiness (unit temperature, unit pressure, flare,
contamination flag), yield-rate sufficiency, and human review for every
process- and yield-affecting action.
