# ADR-0001: RefiningAdvisor ⊣ Refinery Safety Governor architecture

## Status

Accepted. `cloud-itonami-isic-1920` promoted from `:blueprint` to
`:implemented` in the `kotoba-lang/industry` registry.

## Context

`cloud-itonami-isic-1920` publishes an OSS business blueprint for
community petroleum refining (batch intake, per-jurisdiction refinery
process-safety / mechanical-integrity regulatory assessment (assay
verification), unit process, and product yield). Like every prior actor
in this fleet, the blueprint alone is not an implementation: this ADR
records the governed-actor architecture that promotes it to real, tested
code, following the same langgraph StateGraph + independent Governor +
Phase 0->3 rollout pattern established by `cloud-itonami-isic-6511`
(life insurance) and applied across many prior siblings.

Like `cloud-itonami-isic-0162` and `cloud-itonami-isic-0810`
(quarrying), this vertical has NO bespoke domain capability library in
`kotoba-lang` to wrap (verified: no `kotoba-lang/refining`-style repo
exists, and `kotoba-lang/robotics` is the generic cross-cutting
robotics contract every cloud-itonami vertical already uses, not a
domain-specific library for this vertical). This build therefore uses
self-contained domain logic -- the same pattern the majority of this
fleet's actors use, and the explicit differentiator from
`cloud-itonami-isic-4920` (which wraps a pre-existing
`kotoba-lang/logistics` library). The refinery-safety range checks
(unit-temperature window, unit-pressure window, yield-rate vs required
fraction) live as pure functions in `refining.registry` and are
re-verified independently by the governor.

This blueprint's own `:itonami.blueprint/governor` keyword,
`:refinery-safety-governor`, is grep-verified UNIQUE fleet-wide -- no
naming-collision precedent question, a fresh independent build.

## Decision

### Decision 1: fresh governor identity, no reuse precedent needed

`:refinery-safety-governor` is grep-verified unique across every
`blueprint.edn` in this fleet. This build follows the SAME governed-
actor architecture as every prior actor, but with its own distinct
governor identity.

### Decision 2: self-contained domain logic (no `kotoba-lang/refining` to wrap)

Unlike `cloud-itonami-isic-4920` (freight, which delegates tracking-
number validation to a real, pre-existing `kotoba-lang/logistics`
capability library), this refining vertical has NO pre-existing
refinery capability library to delegate refinery-safety validation to.
The three physical range checks (unit-temperature window, unit-pressure
window, yield-rate vs required fraction) are therefore pure functions
defined in `refining.registry` and called directly by
`refining.governor` -- the SAME 'reuse a capability's own validated
function' discipline `retailops.governor`'s ean13 check establishes for
a capability library, here applied to this vertical's OWN pure registry
functions rather than a separate library. No literal code is shared
with any sibling (different domain), but the discipline is the same.

### Decision 3: dual-actuation shape, SEQUENTIAL on the SAME `refinery-batch` entity

Unlike the retail sibling's `order` entity (distinguished by `:kind`,
alternative sale-or-reorder actions), this vertical's `process` and
`yield` actuation events apply SEQUENTIALLY to the SAME
`refinery-batch` -- a unit process happens first (crude charged and run
through the distillation/reformer unit), product yield happens later
(on-spec product finalized and custody-transferred), on the same batch
record. This matches the repair-shop cluster's `ticket` and the
quarrying cluster's `extraction` shape (two real-world acts, in order,
on one entity). `high-stakes` is `#{:unit/process :product/yield}`;
neither ever auto-commits at any phase.

### Decision 4: the refinery-safety physical range-check suite -- honest reapplications of established fleet disciplines

The three physical range checks the governor runs are each an honest
reapplication of an established fleet discipline to a refining value,
documented as such rather than claimed as novel inventions (the same
convention `cloud-itonami-isic-0162`'s Decision 3 establishes for
`dose-matches-claim?`):

- `unit-temp-out-of-range?` reapplies the **aerospace two-sided-
  tolerance** discipline to a refinery unit: the unit's measured
  operating temperature must stay inside its declared safe window
  `[min, max]`. Below `min` fails to reach conversion / leaves light
  ends under-fractionated; above `max` risks thermal runaway, coking,
  and overpressure of the column (a refinery fire / explosion
  precursor).
- `unit-pressure-out-of-range?` reapplies the **aerospace two-sided-
  tolerance** discipline to a refinery unit operating pressure: the
  unit's measured operating pressure must stay inside its declared safe
  window `[min, max]`. Above `max` risks vessel rupture and overpressure
  relief through the flare.
- `yield-rate-insufficient?` reapplies the **fabrication measured-ratio-
  vs-rated-minimum** discipline (the inverse of the measured-exceeds-
  limit pattern: a yield too LOW is the failure) to the realized product
  yield fraction vs its required fraction.

Each returns `true` when the value is provably OUTSIDE the safe
envelope; the conservative refinery-safety choice, missing data is a
violation (cannot verify safe to process / yield). The two-sided
temperature and pressure checks are evaluated UNCONDITIONALLY on every
`:unit/process`; the yield check on every `:product/yield`. No new
unconditional-evaluation ordinals are claimed: every check in this
suite is a discipline-reapplication, documented per Decision 3 of
`cloud-itonami-isic-0162`.

### Decision 5: `contamination-flag-unresolved?` and `flare-system-inoperational?` -- domain-rooted HARD holds

- An contamination flag raised by the proposal itself or already on
  file, and not yet resolved, is a HARD, un-overridable hold. This
  reuses the SAME open-flag-unresolved discipline the freight sibling's
  `delivery-exception-unresolved?` check (and the parksafety sibling's
  flag checks) establish -- an open concern cannot be silently
  suppressed to force a process through. Evaluated UNCONDITIONALLY on
  every `:unit/process`.
- An inoperational flare (the overpressure-relief path unavailable) is a
  HARD, un-overridable hold on every `:unit/process`, grounded directly
  in the Texas City BP isomerization unit explosion (2005) root cause:
  a raffinate splitter tower liquid-level and pressure-relief venting to
  a blowdown stack. The relief path MUST be available before a unit is
  charged. This is a genuinely refinery-specific gate (no direct
  sibling precedent), documented honestly as such.

### Decision 6: dedicated double-actuation-guard booleans

`:processed?` / `:yield-finalized?` are dedicated booleans on the
`refinery-batch` record, never a single `:status` value -- the same
discipline every prior governor's guards establish, informed by
`cloud-itonami-isic-6492`'s real status-lifecycle bug (ADR-2607071320).

### Decision 7: Store protocol, MemStore + DatomicStore parity

`refining.store/Store` is implemented by both `MemStore` (atom-backed,
default for dev/tests/demo) and `DatomicStore` (`langchain.db`-backed),
proven to satisfy the same contract in
`test/refining/store_contract_test.cljk`. The ledger stays append-only
on every backend: which batch was screened for a unit temperature
outside its window, a unit pressure outside its window, an insufficient
yield rate, an inoperational flare (overpressure-relief path
unavailable), or an unresolved contamination flag, which batch had a
unit processed, which product was yielded, on what jurisdictional
basis, approved by whom -- always a query over an immutable log.

### Decision 8: Phase 0->3 with `:unit/process`/`:product/yield` NEVER auto

`refining.phase`'s phase table puts `:batch/intake` (no direct capital
risk) in phase 3's `:auto` set as its only member; `:unit/process` and
`:product/yield` are deliberately ABSENT from every phase's `:auto` set,
including phase 3 -- a permanent structural fact. `refining.governor`'s
high-stakes gate enforces the same invariant independently: two layers
agree that actuation is always a human shift superintendent's call.

### Decision 9: mock + LLM advisor pair

`refining.refiningadvisor` provides a deterministic `mock-advisor`
(default, runs offline) and an `llm-advisor` backed by a
`langchain.model/ChatModel`. The LLM advisor's EDN proposal is parsed
defensively: any parse/shape failure yields a safe low-confidence noop
so the governor escalates/holds -- an LLM hiccup can never auto-process
a batch or auto-yield product.

## Alternatives considered

- **Wrapping a bespoke `kotoba-lang/refining` capability library.**
  Considered and explicitly ruled out: no such library exists, and
  `kotoba-lang/robotics` is generic, not refining-specific. Forcing a
  false capability-library integration would be dishonest; this build
  correctly uses self-contained domain logic instead.
- **A `:kind`-distinguished entity** (matching the retail sibling's
  `order` shape). Rejected: process and yield happen SEQUENTIALLY on the
  SAME batch in this domain, not as alternative actions -- the repair-
  shop / quarrying cluster's sequential shape is the honest match here.
- **Claiming genuinely-new unconditional-evaluation ordinals for the
  physical range checks.** Rejected: the temperature and pressure
  checks each reapplies the established aerospace two-sided-tolerance
  discipline to a new domain, and the yield check reapplies the
  fabrication ratio discipline. Per `cloud-itonami-isic-0162` Decision
  3's convention, these are documented as honest discipline-
  reapplications, not claimed as novel inventions -- the same honesty
  discipline that forbids fabricating coverage also forbids
  over-claiming novelty. (The flare-system-inoperational gate is
  genuinely refinery-specific and documented as such.)
- **Building yield-blending / margin optimization in this R0.** Rejected
  in favor of a scoped R0 slice (the `:optimization` capability is
  correctly marked required, the integration is a follow-up),
  consistent with this fleet's 'extending coverage is additive'
  convention.

## Consequences

- Establishes the refinery-safety physical range-check suite as honest
  reapplications of established fleet disciplines (two-sided-tolerance,
  ratio/rated-minimum, open-flag-unresolved) plus a refinery-specific
  flare/overpressure-relief gate -- the flare gate is the one genuinely
  domain-new check, documented as such.
- `MemStore` || `DatomicStore` parity is proven by
  `test/refining/store_contract_test.cljk`.
- 39 tests / 204 assertions pass; lint is clean; the demo
  (`clojure -M:dev:run`) walks one clean process + yield lifecycle, plus
  eight HARD-hold scenarios (no spec-basis, unit temperature, unit
  pressure, yield rate, contamination flag, flare, double process,
  double yield), end-to-end.
- `blueprint.edn` required no field-sync fixes (already correct) -- only
  the `:maturity` flip itself.

## References

- `cloud-itonami-isic-6511/docs/adr/0001-architecture.md` (origin of the
  general governed-actor architecture pattern)
- `cloud-itonami-isic-4920/docs/adr/0001-architecture.md` (freight
  sibling; contrast: wraps a pre-existing `kotoba-lang/logistics`
  capability library)
- `cloud-itonami-isic-0162/docs/adr/0001-architecture.md` (origin of the
  'honest reapplication, documented as such' convention this build
  follows for its physical range checks)
- 高圧ガス保安法; 石油コンビナート等災害防止法 (Japan, METI / 消防庁)
- OSHA Process Safety Management of Highly Hazardous Chemicals, 29 C.F.R.
  §1910.119; API RP 750 (US)
- Control of Major Accident Hazards (COMAH) Regulations 2015 (UK, HSE /
  Environment Agency)
- Activities Regulations (Aktivitetsforskriftenen); Framework Regulations
  (Norway, Petroleum Safety Authority)
- U.S. Chemical Safety and Hazard Investigation Board, Texas City BP
  refinery explosion (2005) -- root cause: raffinate splitter overfilling
  and pressure-relief venting to a blowdown stack (the flare /
  overpressure-relief-path gate this governor's
  `flare-system-inoperational` check is grounded in)
- Buncefield fire (2005) -- the after-action COMAH / process-safety
  evidence regime this catalog's required-evidence set mirrors
