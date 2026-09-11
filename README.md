# cloud-itonami-isic-3512

Open Business Blueprint for **ISIC Rev.5 3512**: transmission and
distribution of electric power, focused on community renewable-energy
operations.

This repository publishes a community-energy actor -- site intake,
grid-interconnection/tariff verification, grid-instability screening,
battery dispatch and settlement finalization -- as an OSS business
that any qualified energy operator can fork, deploy, run, improve and
sell, so schools, cooperatives, municipalities and small firms can run
solar/storage assets, meter energy flows and publish auditable
community-benefit reports without renting a closed SaaS.

Built on this workspace's
[`langgraph-clj`](https://github.com/com-junkawasaki/langgraph-clj)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, Datomic/in-mem checkpoints) -- the same actor pattern as
every prior actor in this fleet
([`cloud-itonami-isic-6511`](https://github.com/cloud-itonami/cloud-itonami-isic-6511),
[`6512`](https://github.com/cloud-itonami/cloud-itonami-isic-6512),
[`6621`](https://github.com/cloud-itonami/cloud-itonami-isic-6621),
[`6622`](https://github.com/cloud-itonami/cloud-itonami-isic-6622),
[`6629`](https://github.com/cloud-itonami/cloud-itonami-isic-6629),
[`6520`](https://github.com/cloud-itonami/cloud-itonami-isic-6520),
[`6530`](https://github.com/cloud-itonami/cloud-itonami-isic-6530),
[`6820`](https://github.com/cloud-itonami/cloud-itonami-isic-6820),
[`6612`](https://github.com/cloud-itonami/cloud-itonami-isic-6612),
[`6492`](https://github.com/cloud-itonami/cloud-itonami-isic-6492),
[`6920`](https://github.com/cloud-itonami/cloud-itonami-isic-6920),
[`6611`](https://github.com/cloud-itonami/cloud-itonami-isic-6611),
[`7120`](https://github.com/cloud-itonami/cloud-itonami-isic-7120),
[`8620`](https://github.com/cloud-itonami/cloud-itonami-isic-8620),
[`8530`](https://github.com/cloud-itonami/cloud-itonami-isic-8530),
[`9200`](https://github.com/cloud-itonami/cloud-itonami-isic-9200),
[`7500`](https://github.com/cloud-itonami/cloud-itonami-isic-7500),
[`9603`](https://github.com/cloud-itonami/cloud-itonami-isic-9603),
[`9521`](https://github.com/cloud-itonami/cloud-itonami-isic-9521),
[`9321`](https://github.com/cloud-itonami/cloud-itonami-isic-9321),
[`8730`](https://github.com/cloud-itonami/cloud-itonami-isic-8730),
[`9102`](https://github.com/cloud-itonami/cloud-itonami-isic-9102),
[`9103`](https://github.com/cloud-itonami/cloud-itonami-isic-9103),
[`9602`](https://github.com/cloud-itonami/cloud-itonami-isic-9602),
[`9000`](https://github.com/cloud-itonami/cloud-itonami-isic-9000),
[`8890`](https://github.com/cloud-itonami/cloud-itonami-isic-8890),
[`8610`](https://github.com/cloud-itonami/cloud-itonami-isic-8610),
[`9311`](https://github.com/cloud-itonami/cloud-itonami-isic-9311),
[`8510`](https://github.com/cloud-itonami/cloud-itonami-isic-8510),
[`9412`](https://github.com/cloud-itonami/cloud-itonami-isic-9412),
[`6491`](https://github.com/cloud-itonami/cloud-itonami-isic-6491),
[`8720`](https://github.com/cloud-itonami/cloud-itonami-isic-8720),
[`8521`](https://github.com/cloud-itonami/cloud-itonami-isic-8521),
[`6619`](https://github.com/cloud-itonami/cloud-itonami-isic-6619),
[`3600`](https://github.com/cloud-itonami/cloud-itonami-isic-3600),
[`6190`](https://github.com/cloud-itonami/cloud-itonami-isic-6190),
[`3030`](https://github.com/cloud-itonami/cloud-itonami-isic-3030),
[`3830`](https://github.com/cloud-itonami/cloud-itonami-isic-3830),
[`7020`](https://github.com/cloud-itonami/cloud-itonami-isic-7020),
[`9420`](https://github.com/cloud-itonami/cloud-itonami-isic-9420),
[`9491`](https://github.com/cloud-itonami/cloud-itonami-isic-9491),
[`2610`](https://github.com/cloud-itonami/cloud-itonami-isic-2610)) --
the THIRD infrastructure/utility vertical in this fleet (after
`3600`'s water-safety operations and `6190`'s telecom access). Here it
is **Energy Advisor ⊣ Grid Policy Governor**.

> **Why an actor layer at all?** An LLM is great at drafting a site-
> intake summary, normalizing records, and checking whether a site's
> own measured battery state-of-charge actually stays within its own
> recorded safe operating-range bounds -- but it has **no notion of
> which jurisdiction's grid-interconnection/tariff requirements are
> official, no license to dispatch a real battery charge/discharge
> action or finalize a real tariff/settlement report, and no way to
> know on its own whether a grid-instability flag against the site has
> actually stayed unresolved**. Letting it dispatch a battery action
> or finalize a settlement directly invites fabricated grid-policy
> citations, a battery being charged/discharged outside its own safe
> operating range, and an unresolved grid-instability concern being
> quietly settled over -- and liability, and grid-safety/financial
> risk, for whoever runs it. This project seals the Energy Advisor
> into a single node and wraps it with an independent **Grid Policy
> Governor**, a human **approval workflow**, and an immutable **audit
> ledger**.

## Scope: what this actor does and does not do

This actor covers site intake through grid-interconnection/tariff
verification, grid-instability screening, battery dispatch and
settlement finalization. It does **not**, by itself, hold any
interconnection agreement or operating license required to run a
community energy operation in a given jurisdiction, and it does not
claim to. It also does **not** model a real grid-control/SCADA system,
a real demand-forecasting/optimization engine, or the actual
electrical engineering itself -- no direct hardware dispatch protocol
(see `energy.facts`'s own docstring for the honest simplification this
makes: a starting catalog of grid-interconnection/tariff authorities,
not a survey of every jurisdiction's grid-code variant). Whoever
deploys and operates a live instance (a licensed energy operator)
supplies any jurisdiction-specific interconnection agreement, the real
electrical/grid engineering and the real SCADA/forecasting tooling
integrations, and bears that jurisdiction's liability -- the software
supplies the governed, spec-cited, audited execution scaffold so that
operator does not have to build the compliance layer from scratch for
every new site.

### Actuation

**Dispatching a real battery charge/discharge or switching action or
finalizing a real tariff/settlement report is never autonomous, at
any phase, by construction.** Two independent layers enforce this
(`energy.governor`'s `:actuation/dispatch-battery`/`:actuation/
finalize-settlement` high-stakes gate and `energy.phase`'s phase
table, which never puts `:actuation/dispatch-battery`/`:actuation/
finalize-settlement` in any phase's `:auto` set) -- see `energy.
phase`'s docstring and `test/energy/phase_test.cljk`'s `dispatch-
battery-never-auto-at-any-phase`/`finalize-settlement-never-auto-at-
any-phase`. The actor may draft, check and recommend; a human energy
operator is always the one who actually dispatches a battery action
or finalizes a settlement. Like `6512`/`6622`/`6520`/`6530`/`6820`/
`6920`/`6611`/`8530`/`9200`/`9521`/`8730`/`9102`/`9103`/`8890`/`8610`/
`8510`/`9412`/`8720`/`8521`/`6619`/`3600`/`6190`/`3030`/`3830`/`9420`/
`9491`/`2610`, this actor has TWO actuation events, both POSITIVE
(issuing/finalizing a real record), matching the majority pattern in
this fleet (`3600`/`6190` are the fleet's two NEGATIVE-actuation
exceptions).

## The core contract

```
site intake + jurisdiction facts (energy.facts, spec-cited)
        |
        v
   ┌──────────────┐   proposal      ┌───────────────────────┐
   │ Energy       │ ─────────────▶ │ Grid                          │  (independent system)
   │ Advisor      │  + citations    │ Policy Governor:              │
   │ (sealed)     │                 │ spec-basis · evidence-       │
   └──────────────┘         commit ◀────┼──────────▶ hold │ incomplete ·
                                 │             │           │ battery-soc-out-
                           record + ledger  escalate ─▶ human   of-range (two-
                                             (ALWAYS for         sided range) ·
                                              :actuation/dispatch-      grid-instability-
                                              battery /                 flag-unresolved
                                              :actuation/finalize-       (unconditional) ·
                                              settlement)                already-dispatched/
                                                                          -settled
```

**The Energy Advisor never dispatches a battery action or finalizes a
settlement the Grid Policy Governor would reject, and never does so
without a human sign-off.** Hard violations (fabricated grid-policy
requirements; unsupported evidence; a battery state-of-charge out of
its own safe-range bounds; an unresolved grid-instability flag; a
double dispatch or finalization) force **hold** and *cannot* be
approved past; a clean dispatch/settlement proposal still always
routes to a human.

## Run

```bash
kbb -M:dev:run     # walk one clean dual-actuation lifecycle + five HARD-hold cases through the actor
kbb -M:dev:test    # governor contract · phase invariants · store parity · registry conformance · facts coverage
kbb -M:lint        # clj-kondo (errors fail; CI mirrors this)
```

A live sample of the operator console (robotics safety console, shared
template) is rendered in
[docs/samples/operator-console.html](docs/samples/operator-console.html)
-- pure-data HTML output of `kotoba.robotics.ui`.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here a grid-tending robot
performs switching, panel inspection and meter connection at the
community renewable site under the actor, gated by the independent
**Grid Policy Governor**. The governor never dispatches hardware
itself; `:high`/`:safety-critical` actions (such as operating near
live equipment, at height or near the grid) require human sign-off.

## Open business

This repository is not only source code. It is a public, forkable
business model:

| Layer | What is open |
|---|---|
| OSS core | Actor runtime, Grid Policy Governor, battery-dispatch + settlement draft records, audit ledger |
| Business blueprint | Customer, offer, pricing, unit economics, sales motion |
| Operator playbook | How to fork, license, deploy and support the service in a jurisdiction |
| Trust controls | Governance, security reporting, actuation invariant, audit requirements |

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md) to start this as an
open business on itonami.cloud, and
[`docs/adr/0001-architecture.md`](docs/adr/0001-architecture.md) for the
full architecture and decision record.

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`3512`). This vertical's site records are practice-specific rather
than a shared cross-operator data contract, so `energy.*` runs on the
generic robotics/telemetry/optimization/dmn/bpmn/audit-ledger/forms
stack only -- no bespoke domain capability lib to reference at all.

## Layout

| File | Role |
|---|---|
| `src/energy/store.cljk` | **Store** protocol -- `MemStore` ‖ `DatomicStore` (`langchain.db`) + append-only audit ledger + separate battery-dispatch/settlement history. No dynamically-filed sub-record -- both actuation ops act directly on a pre-seeded site, and the double-actuation guards check dedicated `:battery-dispatched?`/`:settlement-finalized?` booleans rather than a `:status` value |
| `src/energy/registry.cljk` | Battery-dispatch + settlement draft records, plus `battery-soc-out-of-range?` -- the FIFTH instance of this fleet's two-sided range check family (`testlab`/`conservation`/`water`/`aerospace` established the first four) |
| `src/energy/facts.cljk` | Per-jurisdiction grid-interconnection/tariff catalog with an official spec-basis citation per entry, honest coverage reporting |
| `src/energy/energyadvisor.cljk` | **Energy Advisor** -- `mock-advisor` ‖ `llm-advisor`; intake/verification/instability-screening/battery-dispatch/settlement proposals |
| `src/energy/governor.cljk` | **Grid Policy Governor** -- 4 HARD checks (spec-basis · evidence-incomplete · battery-soc-out-of-range, pure ground-truth two-sided-range recompute · grid-instability-flag-unresolved, unconditional evaluation, the THIRTY-THIRD grounding of this discipline and FIRST specifically for a grid-instability-flag concept) + already-dispatched/already-settled guards + 1 soft (confidence/actuation gate) |
| `src/energy/phase.cljk` | **Phase 0→3** -- read-only → assisted intake → assisted verify → supervised (both battery dispatch and settlement finalization always human; site intake is the ONLY auto-eligible op, no direct capital risk) |
| `src/energy/operation.cljk` | **OperationActor** -- langgraph-clj StateGraph |
| `src/energy/sim.cljk` | demo driver |
| `test/energy/*_test.clj` | governor contract · phase invariants · store parity · registry conformance · facts coverage |

## Business-process coverage (honest)

This actor covers site intake through grid-interconnection/tariff
verification, grid-instability screening, battery dispatch and
settlement finalization -- the core governed lifecycle this
blueprint's own `docs/business-model.md` names as its Offer:

| Covered | Not covered (out of scope for this R0) |
|---|---|
| Site intake + per-jurisdiction grid-interconnection/tariff checklisting, HARD-gated on an official spec-basis citation (`:site/intake`/`:tariff/verify`) | Real grid-control/SCADA system integration, real demand-forecasting/optimization engine (see `energy.facts`'s docstring) |
| Grid-instability screening, evaluated unconditionally so the screening op itself can HARD-hold on its own finding (`:demand/screen`) | The actual electrical/grid engineering itself |
| Battery dispatch, HARD-gated on full evidence and battery-SOC sufficiency, plus a double-dispatch guard (`:actuation/dispatch-battery`) | Interconnection-agreement application processes themselves |
| Settlement finalization, HARD-gated on full evidence and a double-finalization guard (`:actuation/finalize-settlement`) | |
| Immutable audit ledger for every intake/verification/screening/dispatch/finalization decision | |

Extending coverage is additive: add the next gate (e.g. a demand-
response-event check) as its own governed op with its own HARD checks
and tests, following the SAME "an independent governor re-verifies
against the actor's own records before any real-world act" pattern
this repo's flagship op already establishes.

## Jurisdiction coverage (honest)

`energy.facts/coverage` reports how many requested jurisdictions
actually have an official spec-basis in `energy.facts/catalog` --
currently 4 seeded (JPN, USA, GBR, DEU) out of ~194 jurisdictions
worldwide. This is a starting catalog to prove the governor contract
end-to-end, not a claim of global coverage. Adding a jurisdiction is
additive: one map entry in `energy.facts/catalog`, citing a real
official source -- never fabricate a jurisdiction's requirements to
make coverage look bigger.

## Maturity

`:implemented` -- `Energy Advisor` + `Grid Policy Governor` run as
real, tested code (see `Run` above), promoted from the originally-
published `:blueprint`-tier scaffold, modeled closely on the forty-
eight prior actors' architecture. See `docs/adr/0001-architecture.md`
for the history and design.

## License

Code and implementation templates are AGPL-3.0-or-later.
