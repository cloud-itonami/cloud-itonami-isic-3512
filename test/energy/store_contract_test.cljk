(ns energy.store-contract-test
  "The Store contract, run against BOTH backends. Proving MemStore and
  the Datomic-backed (langchain.db) store satisfy the same contract is
  what makes 'swap the SSoT for Datomic / kotoba-server' a configuration
  change, not a rewrite -- see `cloud-itonami-isic-6511`'s
  `underwriting.store-contract-test` for the same pattern on the sibling
  actor."
  (:require [clojure.test :refer [deftest is testing]]
            [energy.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "Sakura Community Solar+Storage" (:site-name (store/site s "site-1"))))
      (is (= "JPN" (:jurisdiction (store/site s "site-1"))))
      (is (= 55.0 (:battery-soc-percent (store/site s "site-1"))))
      (is (= 10.0 (:soc-min-safe (store/site s "site-1"))))
      (is (= 90.0 (:soc-max-safe (store/site s "site-1"))))
      (is (false? (:grid-instability-flag-unresolved? (store/site s "site-1"))))
      (is (= 95.0 (:battery-soc-percent (store/site s "site-3"))))
      (is (true? (:grid-instability-flag-unresolved? (store/site s "site-4"))))
      (is (false? (:battery-dispatched? (store/site s "site-1"))))
      (is (false? (:settlement-finalized? (store/site s "site-1"))))
      (is (= ["site-1" "site-2" "site-3" "site-4"]
             (mapv :id (store/all-sites s))))
      (is (nil? (store/instability-screen-of s "site-1")))
      (is (nil? (store/tariff-verification-of s "site-1")))
      (is (= [] (store/ledger s)))
      (is (= [] (store/dispatch-history s)))
      (is (= [] (store/settlement-history s)))
      (is (zero? (store/next-dispatch-sequence s "JPN")))
      (is (zero? (store/next-settlement-sequence s "JPN")))
      (is (false? (store/site-already-dispatched? s "site-1")))
      (is (false? (store/site-already-settled? s "site-1"))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "partial upsert merges, preserving untouched fields"
        (store/commit-record! s {:effect :site/upsert
                                 :value {:id "site-1" :site-name "Sakura Community Solar+Storage"}})
        (is (= "Sakura Community Solar+Storage" (:site-name (store/site s "site-1"))))
        (is (= 55.0 (:battery-soc-percent (store/site s "site-1"))) "unrelated field preserved"))
      (testing "verification / instability-screen payloads commit and read back"
        (store/commit-record! s {:effect :verification/set :path ["site-1"]
                                 :payload {:jurisdiction "JPN" :checklist ["a" "b"]}})
        (is (= {:jurisdiction "JPN" :checklist ["a" "b"]} (store/tariff-verification-of s "site-1")))
        (store/commit-record! s {:effect :instability-screen/set :path ["site-1"]
                                 :payload {:site-id "site-1" :verdict :resolved}})
        (is (= {:site-id "site-1" :verdict :resolved} (store/instability-screen-of s "site-1"))))
      (testing "battery dispatch drafts a record and advances the sequence"
        (store/commit-record! s {:effect :site/mark-dispatched :path ["site-1"]})
        (is (= "JPN-DSP-000000" (get (first (store/dispatch-history s)) "record_id")))
        (is (= "battery-dispatch-draft" (get (first (store/dispatch-history s)) "kind")))
        (is (true? (:battery-dispatched? (store/site s "site-1"))))
        (is (= 1 (count (store/dispatch-history s))))
        (is (= 1 (store/next-dispatch-sequence s "JPN")))
        (is (true? (store/site-already-dispatched? s "site-1")))
        (is (false? (store/site-already-dispatched? s "site-2"))))
      (testing "settlement drafts a record and advances the sequence"
        (store/commit-record! s {:effect :site/mark-settled :path ["site-1"]})
        (is (= "JPN-SET-000000" (get (first (store/settlement-history s)) "record_id")))
        (is (= "settlement-draft" (get (first (store/settlement-history s)) "kind")))
        (is (true? (:settlement-finalized? (store/site s "site-1"))))
        (is (= 1 (count (store/settlement-history s))))
        (is (= 1 (store/next-settlement-sequence s "JPN")))
        (is (true? (store/site-already-settled? s "site-1")))
        (is (false? (store/site-already-settled? s "site-2"))))
      (testing "ledger is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))))

(deftest site-power-supply-linkage-write-parity
  (testing "additive: a site MAY carry the :power-supply/* record fields naming which downstream feeder it supplies (superproject ADR-2800000500), on BOTH backends"
    (doseq [[label s] (backends)]
      (testing label
        (store/commit-record! s {:effect :site/upsert
                                 :value {:id "site-2"
                                         :power-supply/id "ps-community-1"
                                         :power-supply/source-actor "cloud-itonami-isic-3512"
                                         :power-supply/feeder-ref "feeder-1"
                                         :power-supply/capacity-mw 3.2
                                         :power-supply/agreement-start-iso "2026-05-01"}})
        (let [site (store/site s "site-2")]
          (is (= "ps-community-1" (:power-supply/id site)))
          (is (= "cloud-itonami-isic-3512" (:power-supply/source-actor site)))
          (is (= "feeder-1" (:power-supply/feeder-ref site)))
          (is (= 3.2 (:power-supply/capacity-mw site)))
          (is (= "2026-05-01" (:power-supply/agreement-start-iso site))))))))

(deftest site-without-power-supply-linkage-is-unaffected
  (testing "a site that never registers a :power-supply/* linkage behaves exactly as before this addition"
    (doseq [[label s] (backends)]
      (testing label
        (let [site (store/site s "site-1")]
          (is (nil? (:power-supply/id site)))
          (is (nil? (:power-supply/source-actor site)))
          (is (= "Sakura Community Solar+Storage" (:site-name site))))))))

(deftest datomic-empty-store-is-usable
  (let [s (store/datomic-store)]
    (is (nil? (store/site s "nope")))
    (is (= [] (store/all-sites s)))
    (is (= [] (store/ledger s)))
    (is (= [] (store/dispatch-history s)))
    (is (= [] (store/settlement-history s)))
    (is (zero? (store/next-dispatch-sequence s "JPN")))
    (is (zero? (store/next-settlement-sequence s "JPN")))
    (store/with-sites s {"x" {:id "x" :site-name "n" :battery-soc-percent 55.0
                             :soc-min-safe 10.0 :soc-max-safe 90.0
                             :grid-instability-flag-unresolved? false
                             :battery-dispatched? false :settlement-finalized? false
                             :jurisdiction "JPN" :status :intake}})
    (is (= "n" (:site-name (store/site s "x"))))))
