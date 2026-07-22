(ns refining.facts-test
  (:require [clojure.test :refer [deftest is]]
            [refining.facts :as facts]))

(deftest jpn-has-a-spec-basis
  (is (some? (facts/spec-basis "JPN")))
  (is (string? (:provenance (facts/spec-basis "JPN")))))

(deftest bra-has-a-spec-basis
  ;; Brazil / ANP (Agência Nacional do Petróleo, Gás Natural e
  ;; Biocombustíveis) -- refining-authorization regime under the Lei do
  ;; Petróleo (Lei nº 9.478/1997, art. 8º, V) and Resolução ANP nº 852/2021
  (is (some? (facts/spec-basis "BRA")))
  (is (string? (:provenance (facts/spec-basis "BRA"))))
  (is (= "Agência Nacional do Petróleo, Gás Natural e Biocombustíveis (ANP)"
         (:owner-authority (facts/spec-basis "BRA")))))

(deftest all-five-seeded-jurisdictions-have-required-evidence
  ;; every seeded refinery jurisdiction actually has a real required-
  ;; evidence set (crude-assay record, unit-integrity inspection,
  ;; pressure-relief device test record) reported honestly here
  (doseq [iso3 ["JPN" "USA" "GBR" "NOR" "BRA"]]
    (is (seq (facts/evidence-checklist iso3)) (str iso3 " required-evidence"))
    (is (every? string? (facts/evidence-checklist iso3))
        (str iso3 " required-evidence are strings"))))

(deftest unknown-jurisdiction-has-no-fabricated-spec-basis
  (is (nil? (facts/spec-basis "ATL"))))

(deftest coverage-never-reports-a-missing-jurisdiction-as-covered
  (let [report (facts/coverage ["JPN" "ATL" "GBR" "BRA"])]
    (is (= 3 (:covered report)))
    (is (= ["ATL"] (:missing-jurisdictions report)))
    (is (= ["BRA" "GBR" "JPN"] (:covered-jurisdictions report)))))

(deftest required-evidence-satisfied-needs-every-item
  (let [all (facts/evidence-checklist "JPN")]
    (is (facts/required-evidence-satisfied? "JPN" all))
    (is (not (facts/required-evidence-satisfied? "JPN" (rest all))))
    (is (not (facts/required-evidence-satisfied? "ATL" all)) "no spec-basis -> never satisfied")))
