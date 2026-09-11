(ns energy.sim
  "Demo driver -- `clojure -M:dev:run`. Walks a clean site through
  intake -> tariff verification -> grid-instability screening ->
  battery-dispatch proposal (always escalates) -> human approval ->
  commit, then through settlement proposal (always escalates) ->
  human approval -> commit, then shows five HARD holds (a
  jurisdiction with no spec-basis, an out-of-range battery state-of-
  charge, an unresolved grid-instability flag screened directly via
  `:demand/screen` [never via an actuation op against an unscreened
  site -- see this actor's own governor ns docstring / the lesson
  `parksafety`'s ADR-2607071922 Decision 5, `eldercare`'s, `museum`'s,
  `conservation`'s, `salon`'s, `entertainment`'s, `casework`'s,
  `hospital`'s, `facility`'s, `school`'s, `association`'s, `leasing`'s,
  `behavioral`'s, `secondary`'s, `card`'s, `water`'s, `telecom`'s,
  `aerospace`'s, `recovery`'s, `consulting`'s, `union`'s,
  `congregation`'s and `fab`'s ADR-0001s already recorded], and a
  double battery-dispatch/settlement of an already-processed site)
  that never reach a human at all, and prints the audit ledger + the
  draft battery-dispatch and settlement records."
  (:require [langgraph.graph :as g]
            [energy.store :as store]
            [energy.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :energy-operator :phase 3})

(defn- exec! [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        actor (op/build db)]
    (println "== site/intake site-1 (JPN, clean; SOC within safe range, no instability flag) ==")
    (println (exec! actor "t1" {:op :site/intake :subject "site-1"
                                :patch {:id "site-1" :site-name "Sakura Community Solar+Storage"}} operator))

    (println "== tariff/verify site-1 (escalates -- human approves) ==")
    (println (exec! actor "t2" {:op :tariff/verify :subject "site-1"} operator))
    (println (approve! actor "t2"))

    (println "== demand/screen site-1 (clean; escalates -- human approves) ==")
    (println (exec! actor "t3" {:op :demand/screen :subject "site-1"} operator))
    (println (approve! actor "t3"))

    (println "== actuation/dispatch-battery site-1 (always escalates -- actuation/dispatch-battery) ==")
    (let [r (exec! actor "t4" {:op :actuation/dispatch-battery :subject "site-1"} operator)]
      (println r)
      (println "-- human energy operator approves --")
      (println (approve! actor "t4")))

    (println "== actuation/finalize-settlement site-1 (always escalates -- actuation/finalize-settlement) ==")
    (let [r (exec! actor "t5" {:op :actuation/finalize-settlement :subject "site-1"} operator)]
      (println r)
      (println "-- human energy operator approves --")
      (println (approve! actor "t5")))

    (println "== tariff/verify site-2 (no spec-basis -> HARD hold) ==")
    (println (exec! actor "t6" {:op :tariff/verify :subject "site-2" :no-spec? true} operator))

    (println "== tariff/verify site-3 (escalates -- human approves; sets up the out-of-range SOC test) ==")
    (println (exec! actor "t7" {:op :tariff/verify :subject "site-3"} operator))
    (println (approve! actor "t7"))

    (println "== actuation/dispatch-battery site-3 (95.0 outside [10.0,90.0] SOC -> HARD hold) ==")
    (println (exec! actor "t8" {:op :actuation/dispatch-battery :subject "site-3"} operator))

    (println "== demand/screen site-4 (unresolved -> HARD hold, never reaches a human) ==")
    (println (exec! actor "t9" {:op :demand/screen :subject "site-4"} operator))

    (println "== actuation/dispatch-battery site-1 AGAIN (double-dispatch -> HARD hold) ==")
    (println (exec! actor "t10" {:op :actuation/dispatch-battery :subject "site-1"} operator))

    (println "== actuation/finalize-settlement site-1 AGAIN (double-finalization -> HARD hold) ==")
    (println (exec! actor "t11" {:op :actuation/finalize-settlement :subject "site-1"} operator))

    (println "== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "== draft battery-dispatch records ==")
    (doseq [r (store/dispatch-history db)] (println r))

    (println "== draft settlement records ==")
    (doseq [r (store/settlement-history db)] (println r))))
