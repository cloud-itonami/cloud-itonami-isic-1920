(ns refining.sim
  "Demo driver -- `clojure -M:dev:run`. Walks a clean refinery batch
  through intake -> assay verification -> unit process (escalate/
  approve/commit) -> product yield (escalate/approve/commit), then
  shows HARD-hold scenarios: a jurisdiction with no spec-basis, a unit
  temperature outside its safe window, a unit pressure outside its safe
  window, a yield rate below the required fraction, an unresolved
  contamination flag, an inoperational flare (overpressure-relief path
  unavailable), a double process, and a double yield.

  Like every sibling actor's new checks, this actor's refinery-safety
  checks (`unit-temp-out-of-range?`, `unit-pressure-out-of-range?`,
  `yield-rate-insufficient?`, `contamination-flag-unresolved?`,
  `flare-system-inoperational?`) are evaluated directly at
  `:unit/process`/`:product/yield` time rather than via a separate
  screening op -- a real process/yield decision validates unit
  temperature, unit pressure, yield rate, the contamination flag and
  the flare at the point of the act itself, not as a discrete pre-
  screening ceremony. Each check is still exercised directly and
  independently below, one batch per HARD-hold scenario, following the
  SAME 'exercise the failure mode directly, never only via a happy-path
  actuation' discipline `parksafety`'s ADR-2607071922 Decision 5 and
  every sibling since establish."
  (:require [langgraph.graph :as g]
            [refining.store :as store]
            [refining.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :shift-superintendent :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        actor (op/build db)]
    (println "== batch/intake batch-1 (JPN, clean) ==")
    (println (exec-op actor "t1" {:op :batch/intake :subject "batch-1"
                                  :patch {:id "batch-1" :operator "Akita Petroleum Co"}} operator))

    (println "== assay/verify batch-1 (escalates -- human approves) ==")
    (println (exec-op actor "t2" {:op :assay/verify :subject "batch-1"} operator))
    (println (approve! actor "t2"))

    (println "== unit/process batch-1 (always escalates -- :unit/process) ==")
    (let [r (exec-op actor "t3" {:op :unit/process :subject "batch-1"} operator)]
      (println r)
      (println "-- human shift superintendent approves --")
      (println (approve! actor "t3")))

    (println "== product/yield batch-1 (always escalates -- :product/yield) ==")
    (let [r (exec-op actor "t4" {:op :product/yield :subject "batch-1"} operator)]
      (println r)
      (println "-- human shift superintendent approves --")
      (println (approve! actor "t4")))

    (println "== assay/verify batch-2 (no spec-basis -> HARD hold) ==")
    (println (exec-op actor "t5" {:op :assay/verify :subject "batch-2"} operator))

    (println "== assay/verify batch-3 (escalates -- human approves; sets up the unit-temp test) ==")
    (println (exec-op actor "t6" {:op :assay/verify :subject "batch-3"} operator))
    (println (approve! actor "t6"))

    (println "== unit/process batch-3 (unit temp out of range -> HARD hold) ==")
    (println (exec-op actor "t7" {:op :unit/process :subject "batch-3"} operator))

    (println "== assay/verify batch-4 (escalates -- human approves; sets up the unit-pressure test) ==")
    (println (exec-op actor "t8" {:op :assay/verify :subject "batch-4"} operator))
    (println (approve! actor "t8"))

    (println "== unit/process batch-4 (unit pressure out of range -> HARD hold) ==")
    (println (exec-op actor "t9" {:op :unit/process :subject "batch-4"} operator))

    (println "== assay/verify batch-5 (escalates -- human approves; sets up the contamination test) ==")
    (println (exec-op actor "t10" {:op :assay/verify :subject "batch-5"} operator))
    (println (approve! actor "t10"))

    (println "== unit/process batch-5 (contamination flag unresolved -> HARD hold) ==")
    (println (exec-op actor "t11" {:op :unit/process :subject "batch-5"} operator))

    (println "== assay/verify batch-6 (escalates -- human approves; sets up the flare test) ==")
    (println (exec-op actor "t12" {:op :assay/verify :subject "batch-6"} operator))
    (println (approve! actor "t12"))

    (println "== unit/process batch-6 (flare inoperational -> HARD hold) ==")
    (println (exec-op actor "t13" {:op :unit/process :subject "batch-6"} operator))

    (println "== assay/verify batch-7 (escalates -- human approves; sets up the yield-rate test) ==")
    (println (exec-op actor "t14" {:op :assay/verify :subject "batch-7"} operator))
    (println (approve! actor "t14"))

    (println "== unit/process batch-7 (escalates -- human approves; clean on process checks) ==")
    (let [r (exec-op actor "t15" {:op :unit/process :subject "batch-7"} operator)]
      (println r)
      (println (approve! actor "t15")))

    (println "== product/yield batch-7 (yield rate insufficient -> HARD hold) ==")
    (println (exec-op actor "t16" {:op :product/yield :subject "batch-7"} operator))

    (println "== unit/process batch-1 AGAIN (double-process -> HARD hold) ==")
    (println (exec-op actor "t17" {:op :unit/process :subject "batch-1"} operator))

    (println "== product/yield batch-1 AGAIN (double-yield -> HARD hold) ==")
    (println (exec-op actor "t18" {:op :product/yield :subject "batch-1"} operator))

    (println "== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "== draft process records ==")
    (doseq [r (store/process-history db)] (println r))

    (println "== draft yield records ==")
    (doseq [r (store/yield-history db)] (println r))))
