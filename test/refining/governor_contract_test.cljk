(ns refining.governor-contract-test
  "The governor contract as executable tests. The single invariant
  under test:

    RefiningAdvisor never processes a refinery batch or yields product
    the Refinery Safety Governor would reject, `:unit/process`/
    `:product/yield` NEVER auto-commit at any phase, `:batch/intake`
    (no direct capital risk) MAY auto-commit when clean, and every
    decision (commit OR hold) leaves exactly one ledger fact."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [refining.store :as store]
            [refining.operation :as op]))

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(def operator {:actor-id "op-1" :actor-role :shift-superintendent :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn- verify!
  "Walks `subject` through assay verify -> approve, leaving an assay on
  file. Uses distinct thread-ids per call site by suffixing `tid-prefix`."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-verify") {:op :assay/verify :subject subject} operator)
  (approve! actor (str tid-prefix "-verify")))

(deftest clean-intake-auto-commits
  (let [[db actor] (fresh)
        res (exec-op actor "t1"
                  {:op :batch/intake :subject "batch-1"
                   :patch {:id "batch-1" :operator "Akita Petroleum Co"}} operator)]
    (is (= :commit (get-in res [:state :disposition])))
    (is (= "Akita Petroleum Co" (:operator (store/refinery-batch db "batch-1"))) "SSoT actually updated")
    (is (= 1 (count (store/ledger db))))))

(deftest assay-verify-always-needs-approval
  (testing "assay verify is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "t2" {:op :assay/verify :subject "batch-1"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t2")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (some? (store/assay-of db "batch-1")))))))

(deftest fabricated-jurisdiction-is-held
  (testing "an assay/verify proposal with no official spec-basis -> HOLD, never reaches a human"
    (let [[db actor] (fresh)
          res (exec-op actor "t3"
                    {:op :assay/verify :subject "batch-2"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:no-spec-basis} (-> (store/ledger db) first :basis)))
      (is (nil? (store/assay-of db "batch-2")) "no assay written"))))

(deftest unit-process-without-assay-is-held
  (testing "unit/process before any assay verification -> HOLD (evidence incomplete)"
    (let [[db actor] (fresh)
          res (exec-op actor "t4" {:op :unit/process :subject "batch-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:evidence-incomplete} (-> (store/ledger db) first :basis))))))

(deftest unit-temp-out-of-range-is-held-and-unoverridable
  (testing "a measured unit temperature outside the safe window -> HOLD, and never reaches request-approval (the aerospace two-sided-tolerance discipline applied to a refinery unit)"
    (let [[db actor] (fresh)
          _ (verify! actor "t5pre" "batch-3")
          res (exec-op actor "t5" {:op :unit/process :subject "batch-3"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:unit-temp-out-of-range} (-> (store/ledger db) last :basis)))
      (is (empty? (store/process-history db))))))

(deftest unit-pressure-out-of-range-is-held-and-unoverridable
  (testing "a measured unit pressure outside the safe window -> HOLD, and never reaches request-approval (the aerospace two-sided-tolerance discipline applied to a refinery unit pressure)"
    (let [[db actor] (fresh)
          _ (verify! actor "t6pre" "batch-4")
          res (exec-op actor "t6" {:op :unit/process :subject "batch-4"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:unit-pressure-out-of-range} (-> (store/ledger db) last :basis)))
      (is (empty? (store/process-history db))))))

(deftest contamination-flag-unresolved-is-held-and-unoverridable
  (testing "an unresolved contamination flag -> HOLD, and never reaches request-approval -- a contaminated charge never runs"
    (let [[db actor] (fresh)
          _ (verify! actor "t7pre" "batch-5")
          res (exec-op actor "t7" {:op :unit/process :subject "batch-5"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:contamination-flag-unresolved} (-> (store/ledger db) last :basis)))
      (is (empty? (store/process-history db))))))

(deftest flare-system-inoperational-is-held-and-unoverridable
  (testing "an inoperational flare (overpressure-relief path unavailable) -> HOLD, and never reaches request-approval -- the Texas City BP explosion root: the relief path MUST be available before a unit is charged"
    (let [[db actor] (fresh)
          _ (verify! actor "t8pre" "batch-6")
          res (exec-op actor "t8" {:op :unit/process :subject "batch-6"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:flare-system-inoperational} (-> (store/ledger db) last :basis)))
      (is (empty? (store/process-history db))))))

(deftest yield-rate-insufficient-is-held-and-unoverridable
  (testing "a realized yield fraction below the required fraction -> HOLD, and never reaches request-approval (the fabrication measured-ratio-vs-rated-minimum discipline, applied to product yield)"
    (let [[db actor] (fresh)
          _ (verify! actor "t9pre" "batch-7")
          res (exec-op actor "t9" {:op :product/yield :subject "batch-7"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:yield-rate-insufficient} (-> (store/ledger db) last :basis)))
      (is (empty? (store/yield-history db))))))

(deftest unit-process-always-escalates-then-human-decides
  (testing "a clean, fully-assayed, in-window-temperature, in-window-pressure, flare-operational, no-contamination-flag batch still ALWAYS interrupts for human approval -- :unit/process is never auto"
    (let [[db actor] (fresh)
          _ (verify! actor "t10pre" "batch-1")
          r1 (exec-op actor "t10" {:op :unit/process :subject "batch-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, process record drafted"
        (let [r2 (approve! actor "t10")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:processed? (store/refinery-batch db "batch-1"))))
          (is (= 1 (count (store/process-history db))) "one draft process record"))))))

(deftest product-yield-always-escalates-then-human-decides
  (testing "a clean, fully-assayed, already-processed batch still ALWAYS interrupts for human approval -- :product/yield is never auto"
    (let [[db actor] (fresh)
          _ (verify! actor "t11pre" "batch-1")
          _ (exec-op actor "t11process" {:op :unit/process :subject "batch-1"} operator)
          _ (approve! actor "t11process")
          r1 (exec-op actor "t11" {:op :product/yield :subject "batch-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, yield record drafted"
        (let [r2 (approve! actor "t11")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:yield-finalized? (store/refinery-batch db "batch-1"))))
          (is (= 1 (count (store/yield-history db))) "one draft yield record"))))))

(deftest unit-process-double-process-is-held
  (testing "processing the same batch twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (verify! actor "t12pre" "batch-1")
          _ (exec-op actor "t12a" {:op :unit/process :subject "batch-1"} operator)
          _ (approve! actor "t12a")
          res (exec-op actor "t12" {:op :unit/process :subject "batch-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-processed} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/process-history db))) "still only the one earlier process"))))

(deftest product-yield-double-yield-is-held
  (testing "yielding the same batch's product twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (verify! actor "t13pre" "batch-1")
          _ (exec-op actor "t13process" {:op :unit/process :subject "batch-1"} operator)
          _ (approve! actor "t13process")
          _ (exec-op actor "t13a" {:op :product/yield :subject "batch-1"} operator)
          _ (approve! actor "t13a")
          res (exec-op actor "t13" {:op :product/yield :subject "batch-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-yielded} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/yield-history db))) "still only the one earlier yield"))))

(deftest every-decision-leaves-one-ledger-fact
  (testing "write-only-through-ledger: N operations -> N ledger facts"
    (let [[db actor] (fresh)]
      (exec-op actor "a" {:op :batch/intake :subject "batch-1"
                          :patch {:id "batch-1" :operator "Akita Petroleum Co"}} operator)
      (exec-op actor "b" {:op :assay/verify :subject "batch-2"} operator)
      (is (= 2 (count (store/ledger db)))
          "one commit + one hold, both recorded"))))
