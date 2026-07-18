(ns energy.phase
  "Phase 0->3 staged rollout -- the community-energy-operator analog
  of `cloud-itonami-isic-6512`'s `casualty.phase`.

    Phase 0  read-only        -- no writes, still governor-gated.
    Phase 1  assisted-intake  -- site intake allowed, every write
                                 needs human approval.
    Phase 2  assisted-verify  -- adds tariff verification + grid-
                                 instability screening writes, still
                                 approval.
    Phase 3  supervised auto  -- governor-clean, high-confidence
                                 `:site/intake` (no capital risk yet)
                                 may auto-commit. `:actuation/
                                 dispatch-battery`/`:actuation/
                                 finalize-settlement` NEVER auto-
                                 commit, at any phase.

  `:actuation/dispatch-battery`/`:actuation/finalize-settlement` are
  deliberately ABSENT from every phase's `:auto` set, including phase
  3 -- a permanent structural fact, not a rollout milestone still to
  come. Dispatching a real battery charge/discharge or switching
  action and finalizing a real tariff/settlement report are the two
  real-world acts this actor performs; both are always a human
  operator's call. `energy.governor`'s `:actuation/dispatch-battery`/
  `:actuation/finalize-settlement` high-stakes gate enforces the same
  invariant independently -- two layers, not one, agree on this.
  `:demand/screen` is likewise never auto-eligible, at any phase --
  the same posture every sibling's screening op has. Phase 3's `:auto`
  set here has only ONE member (`:site/intake`) -- this domain has no
  separate no-capital-risk 'file' lifecycle distinct from the site
  record itself.

  ── Additive: site <-> feeder power-supply linkage (ADR-2800000500) ──

  `:supply/register-power-supply` joins phase 2's `:writes` alongside
  `:tariff/verify`/`:demand/screen`, naming which downstream electric-
  distribution-utility feeder (`cloud-itonami-isic-3510`) this site
  supplies. Deliberately ABSENT from every phase's `:auto` set,
  including phase 3 -- phase 3's `:auto` set here remains the SAME
  single-member `#{:site/intake}` this addition does not touch.")

(def read-ops  #{})
(def write-ops #{:site/intake :tariff/verify :demand/screen
                 :actuation/dispatch-battery :actuation/finalize-settlement
                 :supply/register-power-supply})

;; NOTE the invariant: `:actuation/dispatch-battery`/`:actuation/
;; finalize-settlement` are members of `write-ops` (governor-gated
;; like any write) but are NEVER members of any phase's `:auto` set
;; below. Do not add them there.
(def phases
  "phase -> {:label .. :writes <ops allowed to write> :auto <ops allowed to
  auto-commit when governor-clean>}."
  {0 {:label "read-only"        :writes #{}                                                          :auto #{}}
   1 {:label "assisted-intake"  :writes #{:site/intake}                                              :auto #{}}
   2 {:label "assisted-verify"  :writes #{:site/intake :tariff/verify :demand/screen
                                          :supply/register-power-supply}                             :auto #{}}
   3 {:label "supervised-auto"  :writes write-ops
      :auto #{:site/intake}}})

(def default-phase 3)

(defn gate
  "Adjust a governor disposition for the rollout phase. Returns
  {:disposition kw :reason kw|nil}.

  - a governor HOLD always stays HOLD (compliance wins).
  - a write op not yet enabled in this phase -> HOLD (:phase-disabled).
  - a write op enabled but not auto-eligible -> ESCALATE (:phase-approval),
    even if the governor was clean.
  - `:actuation/dispatch-battery`/`:actuation/finalize-settlement` are
    never auto-eligible at any phase, so they always escalate once
    the governor clears them (or hold if the governor doesn't)."
  [phase {:keys [op]} governor-disposition]
  (let [{:keys [writes auto]} (get phases phase (get phases default-phase))]
    (cond
      (= :hold governor-disposition)       {:disposition :hold :reason nil}
      (contains? read-ops op)              {:disposition governor-disposition :reason nil}
      (not (contains? writes op))          {:disposition :hold :reason :phase-disabled}
      (and (= :commit governor-disposition)
           (not (contains? auto op)))      {:disposition :escalate :reason :phase-approval}
      :else                                {:disposition governor-disposition :reason nil})))

(defn verdict->disposition
  "Map a Grid Policy Governor verdict to a base disposition before the
  phase gate."
  [verdict]
  (cond (:hard? verdict) :hold
        (:escalate? verdict) :escalate
        :else :commit))
