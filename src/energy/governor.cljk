(ns energy.governor
  "Grid Policy Governor -- the independent compliance layer that earns
  the Energy Advisor the right to commit. The LLM has no notion of
  grid-interconnection/tariff law, whether a site's own measured
  battery state-of-charge actually stays within its own recorded safe
  operating-range bounds, whether a grid-instability flag against the
  site has actually stayed unresolved, or when an act stops being a
  draft and becomes a real-world battery dispatch or settlement
  finalization, so this MUST be a separate system able to *reject* a
  proposal and fall back to HOLD -- the community-energy-operator
  analog of `cloud-itonami-isic-6512`'s CasualtyGovernor.

  Six checks, in priority order, ALL HARD violations: a human approver
  CANNOT override them (you don't get to approve your way past a
  fabricated grid-policy spec-basis, incomplete evidence, an out-of-
  range battery state-of-charge, an unresolved grid-instability flag,
  or a double dispatch/finalization). The confidence/actuation gate is
  SOFT: it asks a human to look (low confidence / actuation), and the
  human may approve -- but see `energy.phase`: for `:stake :actuation/
  dispatch-battery`/`:actuation/finalize-settlement` (a real grid-
  safety/business-critical act) NO phase ever allows auto-commit
  either. Two independent layers agree that actuation is always a
  human call.

    1. Spec-basis                  -- did the tariff proposal cite an
                                       OFFICIAL source (`energy.
                                       facts`), or invent one?
    2. Evidence incomplete         -- for `:actuation/dispatch-
                                       battery`/`:actuation/finalize-
                                       settlement`, has the site
                                       actually been verified with a
                                       full site-telemetry-provenance-
                                       record/interconnection-
                                       agreement-record/tariff-
                                       schedule-record/settlement-
                                       source-data-record evidence
                                       checklist on file?
    3. Battery state-of-charge out
       of range                      -- for `:actuation/dispatch-
                                       battery`, INDEPENDENTLY
                                       recompute whether the site's
                                       own battery state-of-charge
                                       falls outside its own recorded
                                       safe operating-range bounds
                                       (`energy.registry/battery-soc-
                                       out-of-range?`) -- needs no
                                       proposal inspection or stored-
                                       verdict lookup at all. The
                                       FIFTH instance of this fleet's
                                       two-sided range check family
                                       (`testlab.governor/within-
                                       tolerance-violations`/
                                       `conservation.governor/body-
                                       condition-out-of-range-
                                       violations`/`water.governor/
                                       contaminant-level-out-of-range-
                                       violations`/`aerospace.
                                       governor/assembly-tolerance-
                                       out-of-range-violations`
                                       established the first four).
    4. Grid instability flag
       unresolved                     -- reported by THIS proposal
                                       itself (a `:demand/screen` that
                                       just found one), or already on
                                       file for the site (`:demand/
                                       screen`/`:actuation/finalize-
                                       settlement`). Evaluated
                                       UNCONDITIONALLY (not scoped to a
                                       specific op), the SAME
                                       discipline `casualty.governor/
                                       sanctions-violations`/...
                                       (thirty-two prior siblings,
                                       most recently `fab.governor/
                                       process-defect-flag-unresolved-
                                       violations`)... established --
                                       the THIRTY-THIRD distinct
                                       application of this exact
                                       discipline, and the FIRST
                                       specifically for a grid-
                                       instability-flag concept (grep-
                                       verified absent from every prior
                                       sibling's `governor.cljc` before
                                       this docstring was written).
                                       Exercised in tests/demo via
                                       `:demand/screen` DIRECTLY, not
                                       via the actuation op against an
                                       unscreened site -- see this ns's
                                       own test suite.
    5. Confidence floor / actuation
       gate                          -- LLM confidence below threshold,
                                       OR the op is `:actuation/
                                       dispatch-battery`/`:actuation/
                                       finalize-settlement` (REAL grid-
                                       safety/business-critical acts)
                                       -> escalate.

  Two more guards, double-dispatch/double-finalization prevention, are
  enforced but NOT listed as numbered HARD checks above because they
  need no upstream comparison at all -- `already-dispatched-
  violations`/`already-settled-violations` refuse to dispatch a
  battery action/finalize a settlement for the SAME site twice, off
  dedicated `:battery-dispatched?`/`:settlement-finalized?` facts
  (never a `:status` value) -- the SAME 'check a dedicated boolean,
  not status' discipline every prior sibling governor's guards
  establish, informed by `cloud-itonami-isic-6492`'s status-lifecycle
  bug (ADR-2607071320)."
  (:require [energy.facts :as facts]
            [energy.registry :as registry]
            [energy.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Dispatching a real battery charge/discharge or switching action and
  finalizing a real tariff/settlement report are the two real-world
  actuation events this actor performs -- a two-member set, matching
  every prior dual-actuation sibling's shape. Both are POSITIVE
  actuations (issuing/finalizing a record), matching this fleet's
  majority actuation shape."
  #{:actuation/dispatch-battery :actuation/finalize-settlement})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A `:tariff/verify` (or actuation) proposal with no spec-basis
  citation is a HARD violation -- never invent a jurisdiction's grid-
  interconnection/tariff requirements."
  [{:keys [op]} proposal]
  (when (contains? #{:tariff/verify :actuation/dispatch-battery :actuation/finalize-settlement} op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-spec-basis
          :detail "公式spec-basisの引用が無い提案は系統連系・料金要件として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:actuation/dispatch-battery`/`:actuation/finalize-settlement`,
  the jurisdiction's required site-telemetry-provenance-record/
  interconnection-agreement-record/tariff-schedule-record/settlement-
  source-data-record evidence must actually be satisfied -- do not
  trust the advisor's self-reported confidence alone."
  [{:keys [op subject]} st]
  (when (contains? #{:actuation/dispatch-battery :actuation/finalize-settlement} op)
    (let [s (store/site st subject)
          verification (store/tariff-verification-of st subject)]
      (when-not (and verification
                     (facts/required-evidence-satisfied?
                      (:jurisdiction s) (:checklist verification)))
        [{:rule :evidence-incomplete
          :detail "法域の必要書類(設備遠隔計測出所記録/系統連系契約記録/料金表記録/精算元データ記録等)が充足していない状態での提案"}]))))

(defn- battery-soc-out-of-range-violations
  "For `:actuation/dispatch-battery`, INDEPENDENTLY recompute whether
  the site's own battery state-of-charge falls outside its own
  recorded safe operating-range bounds via `energy.registry/battery-
  soc-out-of-range?` -- needs no proposal inspection or stored-verdict
  lookup at all, since its inputs are permanent ground-truth fields
  already on the site."
  [{:keys [op subject]} st]
  (when (= op :actuation/dispatch-battery)
    (let [s (store/site st subject)]
      (when (registry/battery-soc-out-of-range? s)
        [{:rule :battery-soc-out-of-range
          :detail (str subject " のSOC(" (:battery-soc-percent s)
                      "%)が安全範囲[" (:soc-min-safe s) "," (:soc-max-safe s) "]を逸脱")}]))))

(defn- grid-instability-flag-unresolved-violations
  "An unresolved grid-instability flag -- reported by THIS proposal
  (e.g. a `:demand/screen` that itself just found one), or already on
  file in the store for the site (`:demand/screen`/`:actuation/
  finalize-settlement`) -- is a HARD, un-overridable hold. Evaluated
  UNCONDITIONALLY (not scoped to a specific op) so the screening op
  itself can HARD-hold on its own finding."
  [{:keys [op subject]} proposal st]
  (let [hit-in-proposal? (= :unresolved (get-in proposal [:value :verdict]))
        site-id (when (contains? #{:demand/screen :actuation/finalize-settlement} op) subject)
        hit-on-file? (and site-id (= :unresolved (:verdict (store/instability-screen-of st site-id))))]
    (when (or hit-in-proposal? hit-on-file?)
      [{:rule :grid-instability-flag-unresolved
        :detail "未解決の系統不安定フラグがある状態での精算確定提案は進められない"}])))

(defn- already-dispatched-violations
  "For `:actuation/dispatch-battery`, refuses to dispatch a battery
  action for the SAME site twice, off a dedicated `:battery-
  dispatched?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :actuation/dispatch-battery)
    (when (store/site-already-dispatched? st subject)
      [{:rule :already-dispatched
        :detail (str subject " は既に蓄電池動作実行済み")}])))

(defn- already-settled-violations
  "For `:actuation/finalize-settlement`, refuses to finalize a
  settlement for the SAME site twice, off a dedicated `:settlement-
  finalized?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :actuation/finalize-settlement)
    (when (store/site-already-settled? st subject)
      [{:rule :already-settled
        :detail (str subject " は既に精算確定済み")}])))

(defn check
  "Censors an Energy Advisor proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (battery-soc-out-of-range-violations request st)
                           (grid-instability-flag-unresolved-violations request proposal st)
                           (already-dispatched-violations request st)
                           (already-settled-violations request st)))
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
