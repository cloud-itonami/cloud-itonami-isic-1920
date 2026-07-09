(ns refining.facts
  "Per-jurisdiction midstream refinery-safety regulatory catalog -- the
  G2-style spec-basis table the Refinery Safety Governor checks every
  `:assay/verify` proposal against ('did the advisor cite an OFFICIAL
  public source for this jurisdiction's refinery process-safety /
  pressure-relief / major-accident-hazard requirements, or did it invent
  one?').

  Each entry below is a REAL jurisdiction with a REAL refinery / process-
  safety regime: Japan's METI / 消防庁 jurisdiction over high-pressure gas
  and petroleum-complex disaster prevention, the US OSHA Process Safety
  Management standard plus API RP 750, the UK HSE / Environment Agency
  COMAH regime, and the Norwegian Petroleum Safety Authority's Activities
  / Framework Regulations. The required-evidence set (crude-assay record,
  unit-integrity inspection, pressure-relief device test record) mirrors
  the process-safety information and mechanical-integrity evidence a
  regulator actually demands before a refinery unit is charged and run;
  these are the records the Texas City BP explosion (2005) and the
  Buncefield fire (2005) after-action regimes made non-negotiable.

  Coverage is reported HONESTLY (see `coverage`), the same discipline
  every sibling actor's `facts` namespace uses: a jurisdiction not in
  this table has NO spec-basis, full stop -- the advisor must not
  fabricate one, and the governor holds if it tries.")

(def catalog
  "iso3 -> requirement map. `:required-evidence` is the process-safety /
  mechanical-integrity evidence set (crude-assay record, unit-integrity
  inspection, pressure-relief device test record); `:legal-basis` /
  `:owner-authority` / `:provenance` are the G2 citation the governor
  requires before any `:assay/verify` proposal can commit."
  {"JPN" {:name "JPN"
          :owner-authority "経済産業省 (METI) / 消防庁"
          :legal-basis "高圧ガス保安法; 石油コンビナート等災害防止法"
          :provenance "https://www.meti.go.jp/policy/safety_security/"
          :required-evidence ["crude-assay record"
                              "unit-integrity inspection"
                              "pressure-relief device test record"]}
   "USA" {:name "USA"
          :owner-authority "OSHA / EPA"
          :legal-basis "Process Safety Management (29 C.F.R. §1910.119); API RP 750"
          :provenance "https://www.osha.gov/laws-regs/standardinterpretations/1910.119"
          :required-evidence ["crude-assay record"
                              "unit-integrity inspection"
                              "pressure-relief device test record"]}
   "GBR" {:name "GBR"
          :owner-authority "Health and Safety Executive (HSE) / Environment Agency"
          :legal-basis "Control of Major Accident Hazards (COMAH) Regulations 2015"
          :provenance "https://www.hse.gov.uk/comah/"
          :required-evidence ["crude-assay record"
                              "unit-integrity inspection"
                              "pressure-relief device test record"]}
   "NOR" {:name "NOR"
          :owner-authority "Petroleum Safety Authority Norway (PSA)"
          :legal-basis "Activities Regulations; Framework Regulations"
          :provenance "https://www.ptil.no/en/regulations/"
          :required-evidence ["crude-assay record"
                              "unit-integrity inspection"
                              "pressure-relief device test record"]}})

(defn spec-basis
  "The jurisdiction's requirement map, or nil -- nil means NO spec-basis,
  and the governor must hold any proposal that tries to process a
  refinery batch or yield product on it."
  [iso3]
  (get catalog iso3))

(defn coverage
  "Honest coverage report: how many of the requested jurisdictions
  actually have a spec-basis entry. Never report a missing jurisdiction
  as covered."
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-1920 R0: " (count catalog)
                 " jurisdictions seeded with an official spec-basis. "
                 "This is a starting catalog, not a survey of all ~194 "
                 "jurisdictions -- extend `refining.facts/catalog`, "
                 "never fabricate a jurisdiction's requirements.")})))

(defn required-evidence-satisfied?
  "Does `submitted` (a set/coll of evidence keywords or strings) satisfy
  every evidence item listed for `iso3`? Missing spec-basis -> never
  satisfied."
  [iso3 submitted]
  (when-let [{:keys [required-evidence]} (spec-basis iso3)]
    (let [need (count required-evidence)
          have (count (filter (set submitted) required-evidence))]
      (= need have))))

(defn evidence-checklist [iso3]
  (:required-evidence (spec-basis iso3) []))
