(ns energy.facts-test
  (:require [clojure.test :refer [deftest is]]
            [energy.facts :as facts]))

(deftest jpn-has-a-spec-basis
  (is (some? (facts/spec-basis "JPN")))
  (is (string? (:provenance (facts/spec-basis "JPN")))))

(deftest can-has-a-spec-basis
  ;; Canada's electricity regulation is genuinely federal/provincial-split
  ;; (Canada Energy Regulator Act, ss.247/261 -- international and
  ;; federally-designated interprovincial power lines only). Confirm the
  ;; entry exists, is properly cited, and honestly discloses that split
  ;; rather than forcing a single-national-regulator shape.
  (is (some? (facts/spec-basis "CAN")))
  (is (string? (:provenance (facts/spec-basis "CAN"))))
  (is (facts/required-evidence-satisfied? "CAN" (facts/evidence-checklist "CAN")))
  (is (string? (:jurisdiction-note (facts/spec-basis "CAN")))
      "split federal/provincial jurisdiction must be disclosed, not hidden"))

(deftest unknown-jurisdiction-has-no-fabricated-spec-basis
  (is (nil? (facts/spec-basis "ATL"))))

(deftest coverage-never-reports-a-missing-jurisdiction-as-covered
  (let [report (facts/coverage ["JPN" "ATL" "GBR"])]
    (is (= 2 (:covered report)))
    (is (= ["ATL"] (:missing-jurisdictions report)))
    (is (= ["GBR" "JPN"] (:covered-jurisdictions report)))))

(deftest required-evidence-satisfied-needs-every-item
  (let [all (facts/evidence-checklist "JPN")]
    (is (facts/required-evidence-satisfied? "JPN" all))
    (is (not (facts/required-evidence-satisfied? "JPN" (rest all))))
    (is (not (facts/required-evidence-satisfied? "ATL" all)) "no spec-basis -> never satisfied")))
