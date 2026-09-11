(ns energy.registry-test
  (:require [clojure.test :refer [deftest is]]
            [energy.registry :as r]))

;; ----------------------------- battery-soc-out-of-range? -----------------------------

(deftest not-out-of-range-when-within-bounds
  (is (not (r/battery-soc-out-of-range? {:battery-soc-percent 55.0 :soc-min-safe 10.0 :soc-max-safe 90.0})))
  (is (not (r/battery-soc-out-of-range? {:battery-soc-percent 10.0 :soc-min-safe 10.0 :soc-max-safe 90.0})))
  (is (not (r/battery-soc-out-of-range? {:battery-soc-percent 90.0 :soc-min-safe 10.0 :soc-max-safe 90.0}))))

(deftest out-of-range-when-below-minimum-or-above-maximum
  (is (r/battery-soc-out-of-range? {:battery-soc-percent 5.0 :soc-min-safe 10.0 :soc-max-safe 90.0}))
  (is (r/battery-soc-out-of-range? {:battery-soc-percent 95.0 :soc-min-safe 10.0 :soc-max-safe 90.0})))

(deftest out-of-range-is-false-on-missing-fields
  (is (not (r/battery-soc-out-of-range? {})))
  (is (not (r/battery-soc-out-of-range? {:battery-soc-percent 95.0}))))

;; ----------------------------- register-battery-dispatch -----------------------------

(deftest dispatch-is-a-draft-not-a-real-dispatch
  (let [result (r/register-battery-dispatch "site-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest dispatch-assigns-dispatch-number
  (let [result (r/register-battery-dispatch "site-1" "JPN" 7)]
    (is (= (get result "dispatch_number") "JPN-DSP-000007"))
    (is (= (get-in result ["record" "site_id"]) "site-1"))
    (is (= (get-in result ["record" "kind"]) "battery-dispatch-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest dispatch-validation-rules
  (is (thrown? Exception (r/register-battery-dispatch "" "JPN" 0)))
  (is (thrown? Exception (r/register-battery-dispatch "site-1" "" 0)))
  (is (thrown? Exception (r/register-battery-dispatch "site-1" "JPN" -1))))

;; ----------------------------- register-settlement -----------------------------

(deftest settlement-is-a-draft-not-a-real-finalization
  (let [result (r/register-settlement "site-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest settlement-assigns-settlement-number
  (let [result (r/register-settlement "site-1" "JPN" 3)]
    (is (= (get result "settlement_number") "JPN-SET-000003"))
    (is (= (get-in result ["record" "site_id"]) "site-1"))
    (is (= (get-in result ["record" "kind"]) "settlement-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest settlement-validation-rules
  (is (thrown? Exception (r/register-settlement "" "JPN" 0)))
  (is (thrown? Exception (r/register-settlement "site-1" "" 0)))
  (is (thrown? Exception (r/register-settlement "site-1" "JPN" -1))))

(deftest history-is-append-only
  (let [c1 (r/register-battery-dispatch "site-1" "JPN" 0)
        hist (r/append [] c1)
        c2 (r/register-battery-dispatch "site-2" "JPN" 1)
        hist2 (r/append hist c2)]
    (is (= 2 (count hist2)))
    (is (= "JPN-DSP-000000" (get-in hist2 [0 "record_id"])))
    (is (= "JPN-DSP-000001" (get-in hist2 [1 "record_id"])))))
