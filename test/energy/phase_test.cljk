(ns energy.phase-test
  "The phase table as executable tests. The invariant this repo cannot
  regress on: `:actuation/dispatch-battery`/`:actuation/finalize-
  settlement` must NEVER be a member of any phase's `:auto` set."
  (:require [clojure.test :refer [deftest is testing]]
            [energy.phase :as phase]))

(deftest dispatch-battery-never-auto-at-any-phase
  (testing "structural invariant: no phase, now or in the future entries, auto-commits a real battery dispatch"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :actuation/dispatch-battery))
          (str "phase " n " must not auto-commit :actuation/dispatch-battery")))))

(deftest finalize-settlement-never-auto-at-any-phase
  (testing "structural invariant: no phase auto-commits a real settlement finalization"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :actuation/finalize-settlement))
          (str "phase " n " must not auto-commit :actuation/finalize-settlement")))))

(deftest demand-screen-never-auto-at-any-phase
  (testing "screening carries no direct capital risk, but is still never auto-eligible, matching every sibling screening op in this fleet"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :demand/screen))
          (str "phase " n " must not auto-commit :demand/screen")))))

(deftest phase-0-is-fully-read-only
  (is (empty? (:writes (get phase/phases 0)))))

(deftest phase-3-auto-commits-only-no-capital-risk-ops
  (testing ":site/intake carries no direct capital risk -- auto-eligible; it is the ONLY auto-eligible op in this domain"
    (is (= #{:site/intake} (:auto (get phase/phases 3))))))

(deftest gate-hold-always-wins
  (is (= :hold (:disposition (phase/gate 3 {:op :site/intake} :hold)))))

(deftest gate-escalates-a-clean-non-auto-write
  (is (= :escalate (:disposition (phase/gate 3 {:op :actuation/dispatch-battery} :commit))))
  (is (= :escalate (:disposition (phase/gate 3 {:op :actuation/finalize-settlement} :commit)))))

(deftest gate-holds-a-write-disabled-in-this-phase
  (is (= :hold (:disposition (phase/gate 0 {:op :site/intake} :commit)))))

;; ───────────── Additive: site <-> feeder power-supply linkage (ADR-2800000500) ─────────────

(deftest register-power-supply-enabled-from-phase-2
  (is (contains? (:writes (get phase/phases 2)) :supply/register-power-supply))
  (is (not (contains? (:writes (get phase/phases 1)) :supply/register-power-supply))))

(deftest register-power-supply-never-auto-at-any-phase
  (testing "a directory fact about an already-agreed arrangement, but still never auto-eligible -- the SAME posture :tariff/verify/:demand/screen have"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :supply/register-power-supply))
          (str "phase " n " must not auto-commit :supply/register-power-supply")))))

(deftest phase-3-auto-set-is-unchanged-by-power-supply-addition
  (is (= #{:site/intake} (:auto (get phase/phases 3)))))

(deftest gate-escalates-a-clean-power-supply-registration
  (is (= :escalate (:disposition (phase/gate 3 {:op :supply/register-power-supply} :commit)))))
