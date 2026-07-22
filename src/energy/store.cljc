(ns energy.store
  "SSoT for the energy actor, behind a `Store` protocol so the backend
  is a swap, not a rewrite -- the same seam every prior `cloud-
  itonami-isic-*` actor in this fleet uses:

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api` (see langchain.kotoba-db).

  Both implement the same protocol and pass the same contract
  (test/energy/store_contract_test.clj), which is the whole point: the
  actor, the Grid Policy Governor and the audit ledger never know
  which SSoT they run on.

  Like every prior dual-actuation sibling, this actor has TWO
  actuation events (dispatching a battery action, finalizing a
  settlement) acting on the SAME entity (a site), each with its OWN
  history collection, sequence counter and dedicated double-
  actuation-guard boolean (`:battery-dispatched?`/`:settlement-
  finalized?`, never a `:status` value) -- the same discipline every
  prior sibling governor's guards establish, informed by `cloud-
  itonami-isic-6492`'s status-lifecycle bug (ADR-2607071320).

  The ledger stays append-only on every backend: 'which site was
  screened for an unresolved grid-instability flag, which battery
  action was dispatched, which settlement was finalized, on what
  jurisdictional basis, approved by whom' is always a query over an
  immutable log -- the audit trail a community trusting an energy
  operator needs, and the evidence an operator needs if a dispatch or
  settlement decision is later disputed.

  ── Additive: site <-> feeder power-supply linkage ──

  A site record MAY also carry `:power-supply/id`/`:power-supply/
  source-actor`/`:power-supply/feeder-ref`/`:power-supply/capacity-mw`/
  `:power-supply/agreement-start-iso` -- the SAME no-shared-code, flat
  cross-actor wire shape `cloud-itonami-isic-3510`'s own `grid.store`
  ns docstring describes for its `feeder` entity, naming WHICH
  downstream electric-distribution-utility feeder this site supplies.
  See `energy.energyadvisor/register-power-supply` and superproject
  ADR-2800000500. Entirely optional and additive -- `:site/upsert`
  (already this actor's generic site directory-patch effect) carries
  these fields when present; a site with none of them behaves exactly
  as it did before this addition."
  (:require [energy.registry :as registry]
            [langchain.db :as d]
            [langchain-store.core :as ls]))

(defprotocol Store
  (site [s id])
  (all-sites [s])
  (instability-screen-of [s site-id] "committed grid-instability-flag screening verdict for a site, or nil")
  (tariff-verification-of [s site-id] "committed tariff verification, or nil")
  (ledger [s])
  (dispatch-history [s] "the append-only battery-dispatch history (energy.registry drafts)")
  (settlement-history [s] "the append-only settlement history (energy.registry drafts)")
  (next-dispatch-sequence [s jurisdiction] "next dispatch-number sequence for a jurisdiction")
  (next-settlement-sequence [s jurisdiction] "next settlement-number sequence for a jurisdiction")
  (site-already-dispatched? [s site-id] "has this site's battery action already been dispatched?")
  (site-already-settled? [s site-id] "has this site's settlement already been finalized?")
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-sites [s sites] "replace/seed the site directory (map id->site)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained site set covering both actuation lifecycles
  (dispatching a battery action, finalizing a settlement) so the
  actor + tests run offline."
  []
  {:sites
   {"site-1" {:id "site-1" :site-name "Sakura Community Solar+Storage"
              :battery-soc-percent 55.0 :soc-min-safe 10.0 :soc-max-safe 90.0
              :grid-instability-flag-unresolved? false
              :battery-dispatched? false :settlement-finalized? false
              :jurisdiction "JPN" :status :intake}
    "site-2" {:id "site-2" :site-name "Atlantis Co-op Site"
              :battery-soc-percent 55.0 :soc-min-safe 10.0 :soc-max-safe 90.0
              :grid-instability-flag-unresolved? false
              :battery-dispatched? false :settlement-finalized? false
              :jurisdiction "ATL" :status :intake}
    "site-3" {:id "site-3" :site-name "鈴木コミュニティ発電所"
              :battery-soc-percent 95.0 :soc-min-safe 10.0 :soc-max-safe 90.0
              :grid-instability-flag-unresolved? false
              :battery-dispatched? false :settlement-finalized? false
              :jurisdiction "JPN" :status :intake}
    "site-4" {:id "site-4" :site-name "田中蓄電所"
              :battery-soc-percent 55.0 :soc-min-safe 10.0 :soc-max-safe 90.0
              :grid-instability-flag-unresolved? true
              :battery-dispatched? false :settlement-finalized? false
              :jurisdiction "JPN" :status :intake}}})

;; ----------------------------- shared commit logic -----------------------------

(defn- dispatch-battery!
  "Backend-agnostic `:site/mark-dispatched` -- looks up the site via
  the protocol and drafts the battery-dispatch record, and returns
  {:result .. :site-patch ..} for the caller to persist."
  [s site-id]
  (let [st (site s site-id)
        seq-n (next-dispatch-sequence s (:jurisdiction st))
        result (registry/register-battery-dispatch site-id (:jurisdiction st) seq-n)]
    {:result result
     :site-patch {:battery-dispatched? true
                 :dispatch-number (get result "dispatch_number")}}))

(defn- finalize-settlement!
  "Backend-agnostic `:site/mark-settled` -- looks up the site via the
  protocol and drafts the settlement record, and returns {:result ..
  :site-patch ..} for the caller to persist."
  [s site-id]
  (let [st (site s site-id)
        seq-n (next-settlement-sequence s (:jurisdiction st))
        result (registry/register-settlement site-id (:jurisdiction st) seq-n)]
    {:result result
     :site-patch {:settlement-finalized? true
                 :settlement-number (get result "settlement_number")}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (site [_ id] (get-in @a [:sites id]))
  (all-sites [_] (sort-by :id (vals (:sites @a))))
  (instability-screen-of [_ id] (get-in @a [:instability-screens id]))
  (tariff-verification-of [_ site-id] (get-in @a [:verifications site-id]))
  (ledger [_] (:ledger @a))
  (dispatch-history [_] (:dispatches @a))
  (settlement-history [_] (:settlements @a))
  (next-dispatch-sequence [_ jurisdiction] (get-in @a [:dispatch-sequences jurisdiction] 0))
  (next-settlement-sequence [_ jurisdiction] (get-in @a [:settlement-sequences jurisdiction] 0))
  (site-already-dispatched? [_ site-id] (boolean (get-in @a [:sites site-id :battery-dispatched?])))
  (site-already-settled? [_ site-id] (boolean (get-in @a [:sites site-id :settlement-finalized?])))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :site/upsert
      (swap! a update-in [:sites (:id value)] merge value)

      :verification/set
      (swap! a assoc-in [:verifications (first path)] payload)

      :instability-screen/set
      (swap! a assoc-in [:instability-screens (first path)] payload)

      :site/mark-dispatched
      (let [site-id (first path)
            {:keys [result site-patch]} (dispatch-battery! s site-id)
            jurisdiction (:jurisdiction (site s site-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:dispatch-sequences jurisdiction] (fnil inc 0))
                       (update-in [:sites site-id] merge site-patch)
                       (update :dispatches registry/append result))))
        result)

      :site/mark-settled
      (let [site-id (first path)
            {:keys [result site-patch]} (finalize-settlement! s site-id)
            jurisdiction (:jurisdiction (site s site-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:settlement-sequences jurisdiction] (fnil inc 0))
                       (update-in [:sites site-id] merge site-patch)
                       (update :settlements registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-sites [s sites] (when (seq sites) (swap! a assoc :sites sites)) s))

(defn seed-db
  "A MemStore seeded with the demo site set. The deterministic
  default."
  []
  (->MemStore (atom (assoc (demo-data)
                           :verifications {} :instability-screens {} :ledger [] :dispatch-sequences {}
                           :dispatches [] :settlement-sequences {} :settlements []))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  Map/compound values (verification/instability-screen payloads,
  ledger facts, dispatch/settlement records) are stored as EDN strings
  so `langchain.db` doesn't expand them into sub-entities -- the same
  convention every sibling actor's store uses. The identity-schema
  builder, EDN-blob codec and seq-keyed event-log read/append are the
  shared kotoba-lang/langchain-store machinery (ADR-2607141600) -- the
  seam ~190 actors hand-roll; this store keeps only its domain wiring."
  (ls/identity-schema
   [:site/id :verification/site-id :instability-screen/site-id
    :ledger/seq :dispatch/seq :settlement/seq
    :dispatch-sequence/jurisdiction :settlement-sequence/jurisdiction]))

(defn- site->tx
  "Additive: `:power-supply/*` (see `energy.energyadvisor/register-
  power-supply`, superproject ADR-2800000500) is OPTIONAL -- a site
  with none of these fields round-trips exactly as it did before this
  addition."
  [{:keys [id site-name battery-soc-percent soc-min-safe soc-max-safe
          grid-instability-flag-unresolved?
          battery-dispatched? settlement-finalized?
          jurisdiction status dispatch-number settlement-number]
   :as site}]
  (cond-> {:site/id id}
    site-name                                (assoc :site/site-name site-name)
    battery-soc-percent                       (assoc :site/battery-soc-percent battery-soc-percent)
    soc-min-safe                              (assoc :site/soc-min-safe soc-min-safe)
    soc-max-safe                              (assoc :site/soc-max-safe soc-max-safe)
    (some? grid-instability-flag-unresolved?) (assoc :site/grid-instability-flag-unresolved? grid-instability-flag-unresolved?)
    (some? battery-dispatched?)               (assoc :site/battery-dispatched? battery-dispatched?)
    (some? settlement-finalized?)             (assoc :site/settlement-finalized? settlement-finalized?)
    jurisdiction                              (assoc :site/jurisdiction jurisdiction)
    status                                    (assoc :site/status status)
    dispatch-number                           (assoc :site/dispatch-number dispatch-number)
    settlement-number                         (assoc :site/settlement-number settlement-number)
    (:power-supply/id site)
    (assoc :site/power-supply-id (:power-supply/id site))
    (:power-supply/source-actor site)
    (assoc :site/power-supply-source-actor (:power-supply/source-actor site))
    (:power-supply/feeder-ref site)
    (assoc :site/power-supply-feeder-ref (:power-supply/feeder-ref site))
    (some? (:power-supply/capacity-mw site))
    (assoc :site/power-supply-capacity-mw (:power-supply/capacity-mw site))
    (:power-supply/agreement-start-iso site)
    (assoc :site/power-supply-agreement-start-iso (:power-supply/agreement-start-iso site))))

(def ^:private site-pull
  [:site/id :site/site-name :site/battery-soc-percent :site/soc-min-safe :site/soc-max-safe
   :site/grid-instability-flag-unresolved? :site/battery-dispatched? :site/settlement-finalized?
   :site/jurisdiction :site/status :site/dispatch-number :site/settlement-number
   :site/power-supply-id :site/power-supply-source-actor :site/power-supply-feeder-ref
   :site/power-supply-capacity-mw :site/power-supply-agreement-start-iso])

(defn- pull->site [m]
  (when (:site/id m)
    (cond-> {:id (:site/id m) :site-name (:site/site-name m)
             :battery-soc-percent (:site/battery-soc-percent m)
             :soc-min-safe (:site/soc-min-safe m)
             :soc-max-safe (:site/soc-max-safe m)
             :grid-instability-flag-unresolved? (boolean (:site/grid-instability-flag-unresolved? m))
             :battery-dispatched? (boolean (:site/battery-dispatched? m))
             :settlement-finalized? (boolean (:site/settlement-finalized? m))
             :jurisdiction (:site/jurisdiction m) :status (:site/status m)
             :dispatch-number (:site/dispatch-number m) :settlement-number (:site/settlement-number m)}
      (:site/power-supply-id m)
      (assoc :power-supply/id (:site/power-supply-id m))
      (:site/power-supply-source-actor m)
      (assoc :power-supply/source-actor (:site/power-supply-source-actor m))
      (:site/power-supply-feeder-ref m)
      (assoc :power-supply/feeder-ref (:site/power-supply-feeder-ref m))
      (some? (:site/power-supply-capacity-mw m))
      (assoc :power-supply/capacity-mw (:site/power-supply-capacity-mw m))
      (:site/power-supply-agreement-start-iso m)
      (assoc :power-supply/agreement-start-iso (:site/power-supply-agreement-start-iso m)))))

(defrecord DatomicStore [conn]
  Store
  (site [_ id]
    (pull->site (d/pull (d/db conn) site-pull [:site/id id])))
  (all-sites [_]
    (->> (d/q '[:find [?id ...] :where [?e :site/id ?id]] (d/db conn))
         (map #(pull->site (d/pull (d/db conn) site-pull [:site/id %])))
         (sort-by :id)))
  (instability-screen-of [_ id]
    (ls/dec* (d/q '[:find ?p . :in $ ?sid
                :where [?k :instability-screen/site-id ?sid] [?k :instability-screen/payload ?p]]
              (d/db conn) id)))
  (tariff-verification-of [_ site-id]
    (ls/dec* (d/q '[:find ?p . :in $ ?sid
                :where [?a :verification/site-id ?sid] [?a :verification/payload ?p]]
              (d/db conn) site-id)))
  (ledger [_] (ls/read-stream conn :ledger/seq :ledger/fact))
  (dispatch-history [_] (ls/read-stream conn :dispatch/seq :dispatch/record))
  (settlement-history [_] (ls/read-stream conn :settlement/seq :settlement/record))
  (next-dispatch-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :dispatch-sequence/jurisdiction ?j] [?e :dispatch-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (next-settlement-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :settlement-sequence/jurisdiction ?j] [?e :settlement-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (site-already-dispatched? [s site-id]
    (boolean (:battery-dispatched? (site s site-id))))
  (site-already-settled? [s site-id]
    (boolean (:settlement-finalized? (site s site-id))))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :site/upsert
      (d/transact! conn [(site->tx value)])

      :verification/set
      (d/transact! conn [{:verification/site-id (first path) :verification/payload (ls/enc payload)}])

      :instability-screen/set
      (d/transact! conn [{:instability-screen/site-id (first path) :instability-screen/payload (ls/enc payload)}])

      :site/mark-dispatched
      (let [site-id (first path)
            {:keys [result site-patch]} (dispatch-battery! s site-id)
            jurisdiction (:jurisdiction (site s site-id))
            next-n (inc (next-dispatch-sequence s jurisdiction))]
        (d/transact! conn
                     [(site->tx (assoc site-patch :id site-id))
                      {:dispatch-sequence/jurisdiction jurisdiction :dispatch-sequence/next next-n}
                      {:dispatch/seq (count (dispatch-history s)) :dispatch/record (ls/enc (get result "record"))}])
        result)

      :site/mark-settled
      (let [site-id (first path)
            {:keys [result site-patch]} (finalize-settlement! s site-id)
            jurisdiction (:jurisdiction (site s site-id))
            next-n (inc (next-settlement-sequence s jurisdiction))]
        (d/transact! conn
                     [(site->tx (assoc site-patch :id site-id))
                      {:settlement-sequence/jurisdiction jurisdiction :settlement-sequence/next next-n}
                      {:settlement/seq (count (settlement-history s)) :settlement/record (ls/enc (get result "record"))}])
        result)
      nil)
    s)
  (append-ledger! [s fact]
    (ls/append-blob! conn :ledger/seq :ledger/fact (count (ledger s)) fact)
    fact)
  (with-sites [s sites]
    (when (seq sites) (d/transact! conn (mapv site->tx (vals sites)))) s))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `data`
  ({:sites ..}); empty when omitted."
  ([] (datomic-store {}))
  ([{:keys [sites]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-sites s sites))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo site set -- the Datomic-backed
  analog of `seed-db`, used to prove protocol parity."
  []
  (datomic-store (demo-data)))
