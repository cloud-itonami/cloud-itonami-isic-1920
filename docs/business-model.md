# Business Model: Community Petroleum Refining

## Classification
- Repository: `cloud-itonami-isic-1920`
- ISIC Rev.5: `1920` — petroleum refining
- Domain: `midstream/refining`
- Social impact: crew safety, environmental protection, transparency
- Governor: `:refinery-safety-governor`
- License: AGPL-3.0-or-later

## Scope
This actor covers batch intake through per-jurisdiction refinery process-
safety / mechanical-integrity regulatory assessment (assay verification),
unit processing (charging and running a distillation/reformer unit), and
product yield (finalizing and custody-transferring on-spec refined
product) for a community refiner. It does **not**, by itself, hold any
operating licence or operating authority required to run a refining
business in a given jurisdiction, perform the actual physical unit
changeover / catalyst handling, or judge refinery economics (yield-
blending and margin optimization is a follow-up slice, not this R0).
Whoever deploys a live instance supplies the jurisdiction-specific
operating authority, the real refinery-unit-robot and DCS / refinery-
information-system integrations, and bears that jurisdiction's liability
-- the software supplies the governed, spec-cited, audited execution
scaffold so the operator does not have to build the compliance layer
from scratch.

## Customer
- regional and community refiners and refinery operators
- independent operators and marginal/standalone-refinery operators
  leaving closed DCS / refinery-AI SaaS
- national-oil-company subsidiaries running community refineries
- customers, insurers and regulators who need an auditable, spec-cited
  refinery batch record

## Offer
- batch intake and directory management
- per-jurisdiction refinery process-safety / mechanical-integrity
  regulatory assessment (assay verification) with an official spec-basis
  citation
- unit processing (charging and running a unit) gated on full evidence,
  an in-window unit temperature and pressure, an operational flare
  (overpressure-relief path), and no open contamination flag
- product yield (on-spec product finalization, custody transfer) gated
  on a sufficient yield rate with double-yield prevention
- evidence checklisting (crude-assay record, unit-integrity inspection,
  pressure-relief device test record)
- contamination-flag and exception workflows
- role-based access and immutable audit ledger

## Revenue
- self-host setup fee
- managed hosting subscription per operator / refinery
- support retainer with SLA
- DCS and refinery-information-system integration

## The `:refinery-safety-governor` Decision Rule

This blueprint's `:itonami.blueprint/governor` is
`:refinery-safety-governor`. It is the single authority that stands
between "a unit could be charged and run" and "a unit is allowed to
run," and between "a yield could be finalized" and "it is allowed to
be yielded." Every rule it enforces is traceable to the domain
(Community Petroleum Refining, ISIC 1920) and to the three
`:social-impact` tags in `blueprint.edn` (`:safety`,
`:environmental-protection`, `:transparency`).

This is the rule the companion contract test
(`test/refining/governor_contract_test.clj`) encodes end-to-end: the
RefiningAdvisor never processes a batch or yields product the Refinery
Safety Governor would reject, `:unit/process` and `:product/yield`
NEVER auto-commit at any phase, `:batch/intake` (no direct capital
risk) MAY auto-commit when clean, and every decision (commit OR hold)
leaves exactly one ledger fact.

**Authorizes a unit process (`:unit/process`) or product yield
(`:product/yield`) only when ALL of the following hold:**

1. **An official spec-basis citation exists for the jurisdiction** -- the
   governor will not authorize any `:assay/verify`, `:unit/process`, or
   `:product/yield` proposal whose jurisdiction has no entry in the
   `refining.facts` catalog (`:no-spec-basis`). This is the direct
   enforcement of `:transparency`: a jurisdiction whose refinery
   process-safety / major-accident-hazard requirements cannot be traced
   to an OFFICIAL public source is never guessed. The advisor must not
   fabricate a jurisdiction's requirements.
2. **The jurisdiction's required evidence is fully on file** -- for a
   process or yield the batch's jurisdiction must have been assayed with
   a complete process-safety / mechanical-integrity evidence checklist
   on record: the crude-assay record, the unit-integrity inspection, and
   the pressure-relief device test record (`:evidence-incomplete`). This
   protects `:safety` and `:environmental-protection`: a unit that
   cannot prove its process-safety information and mechanical-integrity
   basis never runs.
3. **The measured unit temperature stays inside its declared safe window
   `[min, max]`** -- the governor INDEPENDENTLY re-verifies the batch's
   own recorded unit operating temperature against its two-sided safe
   envelope (`refining.registry/unit-temp-out-of-range?`, the aerospace
   two-sided-tolerance discipline applied to a refinery unit). Below
   `min` fails to reach conversion / leaves light ends under-
   fractionated; above `max` risks thermal runaway, coking, and
   overpressure of the column -- the precursor to a refinery fire or
   explosion (`:unit-temp-out-of-range`).
4. **The measured unit pressure stays inside its declared safe window
   `[min, max]`** -- the governor INDEPENDENTLY re-verifies the batch's
   own recorded unit operating pressure against its two-sided safe
   envelope (`refining.registry/unit-pressure-out-of-range?`, the
   aerospace two-sided-tolerance discipline). Below `min` indicates loss
   of containment direction / upset feed; above `max` risks vessel
   rupture and overpressure relief through the flare (`:unit-pressure-
   out-of-range`).
5. **The realized yield fraction meets the required fraction** -- for a
   `:product/yield`, the governor INDEPENDENTLY re-verifies the realized
   product yield fraction against its required fraction
   (`refining.registry/yield-rate-insufficient?`, the fabrication
   measured-ratio-vs-rated-minimum discipline). A yield below its
   required fraction cannot be finalized as on-spec product (`:yield-
   rate-insufficient`).
6. **No unresolved contamination flag is open on the batch** -- a
   contamination flag raised by this proposal itself or already on file,
   and not yet resolved, is a hard, un-overridable hold
   (`:contamination-flag-unresolved`). A contaminated charge never runs;
   contamination concerns cannot be silently suppressed to force a
   process through.
7. **The flare (the overpressure-relief path) is operational** -- the
   governor INDEPENDENTLY re-verifies the flare is operational
   (`refining.registry`-grounded, off the `:flare-operational?` fact).
   The Texas City BP isomerization unit explosion (2005) root cause was
   a raffinate splitter tower liquid-level and pressure-relief venting
   to a blowdown stack: the relief path MUST be available before a unit
   is charged (`:flare-system-inoperational`).
8. **The batch has not already been processed, and product has not
   already been yielded** -- a double process of the same batch is
   refused off a dedicated `:processed?` fact, and a double yield off a
   dedicated `:yield-finalized?` fact (never a `:status` value), the
   double-actuation guard every sibling actor in this fleet enforces
   (`:already-processed` / `:already-yielded`).

**Rejects (HOLD, un-overridable, never even reaches a human) when any of
the above fail.** A proposal with no spec-basis, incomplete evidence, a
unit temperature outside its window, a unit pressure outside its window,
a yield rate below the required fraction, an unresolved contamination
flag, an inoperational flare, or a double process/yield is held at the
governor node -- a human approver cannot override these, by construction.

**Always escalates to a human (never auto-commits) for `:unit/process`
and `:product/yield`**, even when every check above is clean. Charging
and running a real refinery unit (a distillation/reformer unit
processing a batch of crude) and finalizing/yielding real refined
product (real on-spec product volumes moving into custody / sales) are
the two real-world actuation events this actor performs; both are
always a human shift superintendent's call. This is enforced by TWO
independent layers that agree on purpose: the governor's confidence /
actuation SOFT gate (a `:unit/process` / `:product/yield` stake always
escalates) and `refining.phase`'s phase table, which never puts either
op in any phase's `:auto` set. The `:environmental-protection` tag is
enforced upstream of the governor, in the assay-verification evidence
step and the flare/contamination gates -- the governor's job is
process/yield authorization integrity, not yield-blending optimization.

## Required Technologies

`blueprint.edn`'s `:itonami.blueprint/required-technologies` for this business,
and what each one is actually load-bearing for here (not a generic capability
list):

| Technology | What it is FOR in Community Petroleum Refining |
|---|---|
| `:robotics` | The autonomous refinery-unit robot that performs the physical actuation of a distillation/reformer unit (valve / heat input). The governor never dispatches hardware itself: a process-clearing action must have cleared the same sign-off a human shift superintendent would need (see Robotics Premise). |
| `:identity` | Operator, shift-superintendent, and crew identity plus role-based access, so the governor's sign-off is tied to *who* authorized a process or yield, not just *that* someone did. |
| `:forms` | Structured intake for batch booking, per-jurisdiction evidence capture (crude-assay record, unit-integrity inspection, pressure-relief device test record), and contamination-flag submission -- the data the Decision Rule above actually evaluates comes in through these forms. |
| `:dmn` | Encodes the `:refinery-safety-governor` Decision Rule itself (spec-basis, evidence completeness, the three physical range checks, the contamination flag, the flare gate, the double-actuation guards, the actuation gate) as an evaluable decision table rather than code buried in application logic -- this is what makes the governor auditable and swappable per-deployment. |
| `:bpmn` | Orchestrates the intake -> verify -> process -> yield -> audit loop end-to-end (see `docs/operator-guide.md`) across batch intake, assay verification, unit processing, and product yield, including the contamination-flag escalation gate. |
| `:audit-ledger` | The immutable record of every assay, process, yield, contamination flag, and hold -- this is what "an auditable, spec-cited batch record for every process and yield" (Trust Controls, below) actually means in practice, and the evidence an operator needs if a process or yield is later disputed by a customer or regulator. |
| `:optimization` | Yield-blending and margin optimization -- selects the product slate and swing cut for a refinery. This R0 build deliberately scopes optimization OUT (see README `Business-process coverage`); the capability is correctly marked required, the integration is a follow-up slice. |

There is NO bespoke `:refining` capability library in this stack (unlike
the freight sibling's `:logistics`): the refinery-safety range checks
(unit-temperature window, unit-pressure window, yield-rate vs required
fraction) are self-contained pure functions in `refining.registry`, on
top of the generic robotics/identity/forms/dmn/bpmn/audit-ledger stack
(see Capability layer).

## Trust Controls
- a jurisdiction with no official spec-basis can never be assayed,
  processed, or yielded against
- a process never starts with incomplete process-safety / mechanical-
  integrity evidence
- a process never starts outside the unit-temperature window, outside the
  unit-pressure window, with an inoperational flare, or with an open
  contamination flag
- a yield never finalizes below its required yield fraction
- contamination flags cannot be silently suppressed
- the same batch can never be processed or yielded twice
- a process or yield never auto-commits; both always need a human shift
  superintendent
- every process and yield (commit OR hold) leaves exactly one immutable
  ledger fact
- batch, assay, and yield data stays outside Git

## Implementation notes (`:implemented`)

The Decision Rule above is implemented faithfully by `refining.governor`
as nine HARD checks (a human approver cannot override them) plus one
SOFT gate:

- `spec-basis-violations` -- the spec-basis check above, evaluated on
  every `:assay/verify`, `:unit/process`, and `:product/yield`.
- `evidence-incomplete-violations` -- the evidence-completeness check
  above, for `:unit/process` / `:product/yield`.
- `unit-temp-out-of-range-violations` -- the two-sided unit-temperature
  window above, an honest reapplication of the aerospace two-sided-
  tolerance discipline to a refinery unit; evaluated unconditionally on
  every `:unit/process`.
- `unit-pressure-out-of-range-violations` -- the two-sided unit-pressure
  window above, an honest reapplication of the aerospace two-sided-
  tolerance discipline; evaluated unconditionally on every
  `:unit/process`.
- `yield-rate-insufficient-violations` -- the yield-rate check above,
  an honest reapplication of the fabrication measured-ratio-vs-rated-
  minimum discipline; evaluated on every `:product/yield`.
- `contamination-flag-unresolved-violations` -- the open-contamination-
  flag check above (the same open-flag-unresolved discipline the
  freight sibling's delivery-exception-unresolved check establishes);
  evaluated unconditionally on every `:unit/process`.
- `flare-system-inoperational-violations` -- the flare (overpressure-
  relief path) gate above, grounded in the Texas City BP explosion
  root; evaluated unconditionally on every `:unit/process`.
- `already-processed-violations` / `already-yielded-violations` -- the
  double-actuation guards above, off dedicated `:processed?` /
  `:yield-finalized?` booleans (never a `:status` value), the same
  discipline every sibling governor's guards establish.
- the confidence floor / actuation SOFT gate -- low confidence, OR a
  `:unit/process` / `:product/yield` stake, escalates to a human; and
  `refining.phase` independently never auto-commits either op at any
  phase.

`:unit/process` and `:product/yield` are the two real-world actuation
events (`#{:unit/process :product/yield}`), applied SEQUENTIALLY to the
SAME batch (process first, yield later) rather than the retail
sibling's `:kind`-distinguished alternative-action shape -- the same
sequential dual-actuation shape the repair-shop and quarrying clusters
use. Neither ever auto-commits at any phase. Yield-blending and margin
optimization (the `:optimization` line above) is a follow-up slice,
not in this R0 build -- see README `Business-process coverage`.

## Capability layer

Unlike `cloud-itonami-isic-4920` (which wraps a pre-existing bespoke
capability library `kotoba-lang/logistics`), this vertical is
SELF-CONTAINED: there is no `kotoba-lang/refining` to delegate
refinery-safety validation to. The unit-temperature / unit-pressure /
yield-rate range checks live as pure functions in `refining.registry`
and are re-verified independently by the governor, rather than wrapping
an external capability library's own validated function -- the same
'reuse a capability's own validated function' discipline, here applied
to this vertical's OWN pure registry functions.

## Jurisdiction coverage (honest)

`refining.facts/catalog` currently seeds 4 jurisdictions with an
official spec-basis, each a REAL regime: Japan (METI / 消防庁, 高圧ガス
保安法 and 石油コンビナート等災害防止法), the United States (OSHA Process
Safety Management, 29 C.F.R. §1910.119, plus API RP 750), the United
Kingdom (HSE / Environment Agency, COMAH Regulations 2015), and Norway
(Petroleum Safety Authority, Activities Regulations and Framework
Regulations). The required-evidence set (crude-assay record, unit-
integrity inspection, pressure-relief device test record) mirrors the
process-safety information and mechanical-integrity evidence these
regimes made non-negotiable after Texas City (2005) and Buncefield
(2005). This is a starting catalog to prove the governor contract
end-to-end, not a claim of global coverage (4 of ~194 jurisdictions
worldwide). Adding a jurisdiction is additive: one map entry in
`refining.facts/catalog`, citing a real official source -- never
fabricate a jurisdiction's requirements to make coverage look bigger.

## Maturity

`:implemented` -- `RefiningAdvisor` + `Refinery Safety Governor` run as
real, tested code (`clojure -M:dev:test`: 39 tests / 204 assertions, 0
failures; lint clean), promoted from the originally-published
`:blueprint`-tier scaffold, following the SAME governed-actor
architecture as the other prior actors across this fleet, with its own
distinct, independently-named governor and its own self-contained
refinery-safety range checks. See `docs/adr/0001-architecture.md` for
the history and design.

## Robotics Premise

`blueprint.edn` sets `:itonami.blueprint/robotics true`. In this domain an
autonomous refinery-unit robot performs the physical actuation of a
distillation/reformer unit (valve / heat input), under the actor, gated
by the independent **Refinery Safety Governor**. The governor never
dispatches hardware itself: a process-clearing action must have cleared
the same sign-off a human shift superintendent would need. A robot may
turn the valve / set the heat input, but only after the governor (every
HARD check clean) and a human superintendent both agree it is safe to --
the same operating-state-machine-gated-by-governor premise every
cloud-itonami vertical restates (ADR-2607011000): the blueprint declares
`:robotics true`, the README names the robot that performs the physical
act, and the Refinery Safety Governor is the independent gate that
robot's command must pass.
