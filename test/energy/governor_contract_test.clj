(ns energy.governor-contract-test
  "The governor contract as executable tests -- the community-energy-
  operator analog of `cloud-itonami-isic-6512`'s `casualty.governor-
  contract-test`. The single invariant under test:

    Energy Advisor never dispatches a battery action or finalizes a
    settlement the Grid Policy Governor would reject, `:actuation/
    dispatch-battery`/`:actuation/finalize-settlement` NEVER auto-
    commit at any phase, `:site/intake` (no direct capital risk) MAY
    auto-commit when clean, and every decision (commit OR hold) leaves
    exactly one ledger fact."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [energy.store :as store]
            [energy.operation :as op]))

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(def operator {:actor-id "op-1" :actor-role :energy-operator :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn- verify!
  "Walks `subject` through verify -> approve, leaving a tariff
  verification on file. Uses distinct thread-ids per call site by
  suffixing `tid-prefix`."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-verify") {:op :tariff/verify :subject subject} operator)
  (approve! actor (str tid-prefix "-verify")))

(defn- screen!
  "Walks `subject` through grid-instability screening -> approve,
  leaving a screening on file. Only safe to call for a site whose
  instability status has already resolved -- an unresolved flag HARD-
  holds the screen itself (see
  `grid-instability-is-held-and-unoverridable`)."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-screen") {:op :demand/screen :subject subject} operator)
  (approve! actor (str tid-prefix "-screen")))

(deftest clean-intake-auto-commits
  (let [[db actor] (fresh)
        res (exec-op actor "t1"
                  {:op :site/intake :subject "site-1"
                   :patch {:id "site-1" :site-name "Sakura Community Solar+Storage"}} operator)]
    (is (= :commit (get-in res [:state :disposition])))
    (is (= "Sakura Community Solar+Storage" (:site-name (store/site db "site-1"))) "SSoT actually updated")
    (is (= 1 (count (store/ledger db))))))

(deftest tariff-verify-always-needs-approval
  (testing "verify is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "t2" {:op :tariff/verify :subject "site-1"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t2")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (some? (store/tariff-verification-of db "site-1")))))))

(deftest fabricated-jurisdiction-is-held
  (testing "a tariff/verify proposal with no official spec-basis -> HOLD, never reaches a human"
    (let [[db actor] (fresh)
          res (exec-op actor "t3"
                    {:op :tariff/verify :subject "site-1" :no-spec? true} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:no-spec-basis} (-> (store/ledger db) first :basis)))
      (is (nil? (store/tariff-verification-of db "site-1")) "no verification written"))))

(deftest dispatch-battery-without-verification-is-held
  (testing "actuation/dispatch-battery before any tariff verification -> HOLD (evidence incomplete)"
    (let [[db actor] (fresh)
          res (exec-op actor "t4" {:op :actuation/dispatch-battery :subject "site-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:evidence-incomplete} (-> (store/ledger db) first :basis))))))

(deftest battery-soc-out-of-range-is-held
  (testing "a site whose own battery state-of-charge falls outside its own safe-range bounds -> HOLD"
    (let [[db actor] (fresh)
          _ (verify! actor "t5pre" "site-3")
          res (exec-op actor "t5" {:op :actuation/dispatch-battery :subject "site-3"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:battery-soc-out-of-range} (-> (store/ledger db) last :basis)))
      (is (empty? (store/dispatch-history db))))))

(deftest grid-instability-is-held-and-unoverridable
  (testing "an unresolved grid-instability flag on a site -> HOLD, and never reaches request-approval -- exercised via :demand/screen DIRECTLY, not via the actuation op against an unscreened site (see this actor's governor ns docstring / parksafety's ADR-2607071922 Decision 5 / eldercare's, museum's, conservation's, salon's, entertainment's, casework's, hospital's, facility's, school's, association's, leasing's, behavioral's, secondary's, card's, water's, telecom's, aerospace's, recovery's, consulting's, union's, congregation's and fab's ADR-0001s)"
    (let [[db actor] (fresh)
          res (exec-op actor "t6" {:op :demand/screen :subject "site-4"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:grid-instability-flag-unresolved} (-> (store/ledger db) first :basis)))
      (is (nil? (store/instability-screen-of db "site-4")) "no clearance written"))))

(deftest dispatch-battery-always-escalates-then-human-decides
  (testing "a clean, fully-verified, in-range site still ALWAYS interrupts for human approval -- actuation/dispatch-battery is never auto"
    (let [[db actor] (fresh)
          _ (verify! actor "t7pre" "site-1")
          r1 (exec-op actor "t7" {:op :actuation/dispatch-battery :subject "site-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, dispatch record drafted"
        (let [r2 (approve! actor "t7")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:battery-dispatched? (store/site db "site-1"))))
          (is (= 1 (count (store/dispatch-history db))) "one draft dispatch record"))))))

(deftest finalize-settlement-always-escalates-then-human-decides
  (testing "a clean, fully-verified, resolved-flag site still ALWAYS interrupts for human approval -- actuation/finalize-settlement is never auto"
    (let [[db actor] (fresh)
          _ (verify! actor "t8pre" "site-1")
          _ (screen! actor "t8pre2" "site-1")
          r1 (exec-op actor "t8" {:op :actuation/finalize-settlement :subject "site-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, settlement record drafted"
        (let [r2 (approve! actor "t8")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:settlement-finalized? (store/site db "site-1"))))
          (is (= 1 (count (store/settlement-history db))) "one draft settlement record"))))))

(deftest dispatch-battery-double-dispatch-is-held
  (testing "dispatching the same site's battery action twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (verify! actor "t9pre" "site-1")
          _ (exec-op actor "t9a" {:op :actuation/dispatch-battery :subject "site-1"} operator)
          _ (approve! actor "t9a")
          res (exec-op actor "t9" {:op :actuation/dispatch-battery :subject "site-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-dispatched} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/dispatch-history db))) "still only the one earlier dispatch"))))

(deftest finalize-settlement-double-finalization-is-held
  (testing "finalizing the same site's settlement twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (verify! actor "t10pre" "site-1")
          _ (screen! actor "t10pre2" "site-1")
          _ (exec-op actor "t10a" {:op :actuation/finalize-settlement :subject "site-1"} operator)
          _ (approve! actor "t10a")
          res (exec-op actor "t10" {:op :actuation/finalize-settlement :subject "site-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-settled} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/settlement-history db))) "still only the one earlier finalization"))))

;; ───────────── Additive: site <-> feeder power-supply linkage (ADR-2800000500) ─────────────
;;
;; The generation-side half of the entirely optional, no-shared-code
;; isic-3512 <-> isic-3510 cross-actor contract -- registers which
;; downstream feeder this community-energy site supplies.

(deftest register-power-supply-always-needs-approval-then-links-the-site
  (testing "a directory fact, never auto -- see energy.phase"
    (let [[db actor] (fresh)
          res (exec-op actor "p1"
                    {:op :supply/register-power-supply :subject "site-2"
                     :patch {:id "site-2"
                             :power-supply/id "ps-community-1"
                             :power-supply/source-actor "cloud-itonami-isic-3512"
                             :power-supply/feeder-ref "feeder-1"
                             :power-supply/capacity-mw 3.2
                             :power-supply/agreement-start-iso "2026-05-01"}}
                    operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "p1")
            site (store/site db "site-2")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (= "ps-community-1" (:power-supply/id site)))
        (is (= "cloud-itonami-isic-3512" (:power-supply/source-actor site)))
        (is (= 3.2 (:power-supply/capacity-mw site)))))))

(deftest register-power-supply-disabled-before-phase-2
  (testing "phase 1 does not yet enable this op -> HOLD, :phase-disabled"
    (let [[db actor] (fresh)
          res (exec-op actor "p2"
                    {:op :supply/register-power-supply :subject "site-2" :patch {}}
                    (assoc operator :phase 1))]
      (is (= :hold (get-in res [:state :disposition])))
      (is (nil? (:power-supply/id (store/site db "site-2")))))))

(deftest every-decision-leaves-one-ledger-fact
  (testing "write-only-through-ledger: N operations -> N ledger facts"
    (let [[db actor] (fresh)]
      (exec-op actor "a" {:op :site/intake :subject "site-1"
                          :patch {:id "site-1" :site-name "Sakura Community Solar+Storage"}} operator)
      (exec-op actor "b" {:op :tariff/verify :subject "site-1" :no-spec? true} operator)
      (is (= 2 (count (store/ledger db)))
          "one commit + one hold, both recorded"))))
