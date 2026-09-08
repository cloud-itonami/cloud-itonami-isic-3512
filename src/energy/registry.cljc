(ns energy.registry
  "Pure-function battery-dispatch + settlement-finalization record
  construction -- an append-only community-energy-operator book-of-
  record draft.

  Like every sibling actor's registry, there is no single
  international check-digit standard for a battery-dispatch or
  settlement reference number -- every operator/jurisdiction assigns
  its own reference format. This namespace does NOT invent one; it
  builds a jurisdiction-scoped sequence number and validates the
  record's required fields, the same honest, non-fabricating
  discipline `energy.facts` uses.

  `battery-soc-out-of-range?` is the FIFTH instance of this fleet's
  two-sided range check family (`testlab.registry/within-tolerance?`
  established the first, `conservation.registry/body-condition-out-
  of-range?` the second, `water.registry/contaminant-level-out-of-
  range?` the third, `aerospace.registry/assembly-tolerance-out-of-
  range?` the fourth), applying the SAME lo/hi bounds-comparison shape
  to a site's own measured battery state-of-charge against the site's
  own recorded safe operating-range bounds.

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real grid-control/SCADA system. It builds the RECORD an
  operator would keep, not the act of dispatching the battery or
  finalizing the settlement itself (that is `energy.operation`'s
  `:actuation/dispatch-battery`/`:actuation/finalize-settlement`,
  always human-gated -- see README `Actuation`)."
  (:require [kotoba.lang.text :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is the
  operator's own act, not this actor's. See README `Actuation`."
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

(defn battery-soc-out-of-range?
  "Does `site`'s own `:battery-soc-percent` fall outside its own
  `[:soc-min-safe :soc-max-safe]` safe operating-range bounds? A pure
  ground-truth check against the site's own permanent fields -- no
  upstream comparison needed. The FIFTH instance of this fleet's two-
  sided range check family (see ns docstring)."
  [{:keys [battery-soc-percent soc-min-safe soc-max-safe]}]
  (and (number? battery-soc-percent) (number? soc-min-safe) (number? soc-max-safe)
       (or (< battery-soc-percent soc-min-safe) (> battery-soc-percent soc-max-safe))))

(defn register-battery-dispatch
  "Validate + construct the BATTERY-DISPATCH registration DRAFT -- the
  operator's own act of dispatching a real battery charge/discharge or
  switching action at a community renewable-energy site. Pure
  function -- does not touch any real grid-control/SCADA system; it
  builds the RECORD an operator would keep. `energy.governor`
  independently re-verifies the site's own battery-state-of-charge
  sufficiency against its own safe operating-range bounds, and blocks
  a double-dispatch for the same site, before this is ever allowed to
  commit."
  [site-id jurisdiction sequence]
  (when-not (and site-id (not= site-id ""))
    (throw (ex-info "battery-dispatch: site_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "battery-dispatch: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "battery-dispatch: sequence must be >= 0" {})))
  (let [dispatch-number (str (str/upper jurisdiction) "-DSP-" (zero-pad sequence 6))
        record {"record_id" dispatch-number
                "kind" "battery-dispatch-draft"
                "site_id" site-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "dispatch_number" dispatch-number
     "certificate" (unsigned-certificate "BatteryDispatch" dispatch-number dispatch-number)}))

(defn register-settlement
  "Validate + construct the SETTLEMENT registration DRAFT -- the
  operator's own act of finalizing a real tariff/settlement report for
  a community renewable-energy site. Pure function -- does not touch
  any real grid-control/SCADA system; it builds the RECORD an operator
  would keep. `energy.governor` independently re-verifies the site's
  own grid-instability-flag resolution status, and blocks a double-
  finalization for the same site, before this is ever allowed to
  commit."
  [site-id jurisdiction sequence]
  (when-not (and site-id (not= site-id ""))
    (throw (ex-info "settlement: site_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "settlement: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "settlement: sequence must be >= 0" {})))
  (let [settlement-number (str (str/upper jurisdiction) "-SET-" (zero-pad sequence 6))
        record {"record_id" settlement-number
                "kind" "settlement-draft"
                "site_id" site-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "settlement_number" settlement-number
     "certificate" (unsigned-certificate "Settlement" settlement-number settlement-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
