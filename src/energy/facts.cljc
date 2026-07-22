(ns energy.facts
  "Per-jurisdiction grid-interconnection/tariff-policy regulatory
  catalog -- the G2-style spec-basis table the Grid Policy Governor
  checks every tariff/verify proposal against ('did the advisor cite
  an OFFICIAL public source for this jurisdiction's grid-
  interconnection and tariff requirements, or did it invent one?').

  Coverage is reported HONESTLY (see `coverage`), the same discipline
  every sibling actor's `facts` namespace uses: a jurisdiction not in
  this table has NO spec-basis, full stop -- the advisor must not
  fabricate one, and the governor holds if it tries.

  Seed values are drawn from each jurisdiction's official grid-
  interconnection/tariff regulator (see `:provenance`); they are a
  STARTING catalog, not a from-scratch survey of all ~194
  jurisdictions. Extending coverage is additive: add one map to
  `catalog`, cite a real source, done -- never invent a jurisdiction's
  requirements to make coverage look bigger.")

(def catalog
  "iso3 -> requirement map. `:required-evidence` mirrors the generic
  site-telemetry-provenance-record/interconnection-agreement-record/
  tariff-schedule-record/settlement-source-data-record evidence set
  submitted in some form; `:legal-basis` / `:owner-authority` /
  `:provenance` are the G2 citation the governor requires before any
  `:tariff/verify` proposal can commit."
  {"JPN" {:name "Japan"
          :owner-authority "経済産業省資源エネルギー庁 (ANRE, Agency for Natural Resources and Energy)"
          :legal-basis "電気事業法 (Electricity Business Act) / 系統連系規程"
          :national-spec "分散型電源の系統連系および料金・精算に関する要件"
          :provenance "https://www.enecho.meti.go.jp/category/electricity_and_gas/electric/"
          :required-evidence ["設備遠隔計測出所記録 (site-telemetry-provenance-record)"
                              "系統連系契約記録 (interconnection-agreement-record)"
                              "料金表記録 (tariff-schedule-record)"
                              "精算元データ記録 (settlement-source-data-record)"]}
   "USA" {:name "United States"
          :owner-authority "Federal Energy Regulatory Commission (FERC) / North American Electric Reliability Corporation (NERC)"
          :legal-basis "Federal Power Act / NERC Reliability Standards"
          :national-spec "Distributed-energy-resource interconnection and settlement requirements"
          :provenance "https://www.ferc.gov/electric-transmission"
          :required-evidence ["Site-telemetry-provenance record"
                              "Interconnection-agreement record"
                              "Tariff-schedule record"
                              "Settlement-source-data record"]}
   "GBR" {:name "United Kingdom"
          :owner-authority "Office of Gas and Electricity Markets (Ofgem)"
          :legal-basis "Distribution Connection and Use of System Agreement (DCUSA) / Grid Code"
          :national-spec "Distributed-generation connection and settlement requirements"
          :provenance "https://www.ofgem.gov.uk/energy-policy-and-regulation/policy-and-regulatory-programmes/connections"
          :required-evidence ["Site-telemetry-provenance record"
                              "Interconnection-agreement record"
                              "Tariff-schedule record"
                              "Settlement-source-data record"]}
   "DEU" {:name "Germany"
          :owner-authority "Bundesnetzagentur"
          :legal-basis "Energiewirtschaftsgesetz (EnWG) / VDE-AR-N technische Anschlussregeln"
          :national-spec "Netzanschluss- und Abrechnungsanforderungen für dezentrale Erzeugungsanlagen"
          :provenance "https://www.bundesnetzagentur.de/DE/Fachthemen/ElektrizitaetundGas/Netzanschluss/start.html"
          :required-evidence ["Anlagenfernmessnachweis (site-telemetry-provenance-record)"
                              "Netzanschlussvertrag (interconnection-agreement-record)"
                              "Tarifblattaufzeichnung (tariff-schedule-record)"
                              "Abrechnungsquelldatennachweis (settlement-source-data-record)"]}
   "CAN" {:name "Canada"
          :owner-authority "Canada Energy Regulator (CER, formerly the National Energy Board) -- but ONLY for international and federally-designated interprovincial power lines; see :jurisdiction-note"
          :legal-basis "Canadian Energy Regulator Act (S.C. 2019, c. 28, s. 10; R.S.C. chapter C-15.1), Part 4 \"International and Interprovincial Power Lines\": s.247 (prohibition on constructing/operating an international power line without a permit or certificate), s.248 (permit issuance), s.261 (Governor in Council designation + certificate requirement for an interprovincial power line), s.262 (certificate issuance); definitions of \"international power line\" and \"interprovincial power line\" are in s.2 (Interpretation)"
          :national-spec "A person must not construct or operate an international power line except under a s.248 permit or s.262 certificate (s.247); a person must not construct or operate an interprovincial power line that the Governor in Council has DESIGNATED by order under s.261 except under a s.262 certificate -- undesignated interprovincial lines and purely in-province transmission/distribution are outside this federal spec-basis (see :jurisdiction-note)"
          :provenance "https://laws-lois.justice.gc.ca/eng/acts/C-15.1/page-14.html (Part 4, ss.247-262, fetched and read verbatim 2026-07-21/22); https://laws-lois.justice.gc.ca/eng/acts/C-15.1/page-1.html (s.2 definitions of \"international power line\" / \"interprovincial power line\"); https://www.cer-rec.gc.ca/en/about/who-we-are-what-we-do/index.html (CER's own statement: \"we keep watch over the companies operating oil and gas pipelines and electrical power lines that cross a national, provincial, or territorial border\")"
          :required-evidence ["Site-telemetry-provenance record"
                              "Interconnection-agreement record"
                              "Tariff-schedule record"
                              "Settlement-source-data record"]
          :jurisdiction-note "HONEST GAP DISCLOSURE (not a fabrication -- a documented scope limit): Canada's electricity regulation is genuinely federal/provincial-split, unlike the single-national-regulator jurisdictions elsewhere in this catalog. The federal CER's confirmed statutory authority (fetched and read this session, verbatim quotes above) reaches ONLY (a) international power lines (electricity facilities crossing the Canada border, s.2/s.247/s.248) and (b) interprovincial power lines that the Governor in Council has specifically DESIGNATED by order under s.261 -- NOT every line crossing a provincial boundary is automatically federally regulated. The large majority of Canadian electricity generation, in-province transmission, and distribution (e.g. Ontario, Alberta, and the other provinces' own utilities) is regulated by that province's own regulator (e.g. Ontario Energy Board, Alberta Utilities Commission) under provincial statutes. This session could NOT independently fetch-and-verify a specific provincial regulator's statute or website (oeb.ca was unreachable from this environment: curl exit 28 / connection timeout on 2026-07-21), so NO provincial :legal-basis or :owner-authority claim is made here -- this entry's spec-basis is scoped strictly to the confirmed federal CER authority above. Do not treat this entry as a spec-basis for purely in-province generation/transmission/distribution."}})

(defn spec-basis
  "The jurisdiction's requirement map, or nil -- nil means NO spec-basis,
  and the governor must hold any proposal that tries to dispatch a
  battery action or finalize a settlement on it."
  [iso3]
  (get catalog iso3))

(defn coverage
  "Honest coverage report: how many of the requested jurisdictions actually
  have a spec-basis entry. Never report a missing jurisdiction as covered."
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-3512 R0: " (count catalog)
                 " jurisdictions seeded with an official spec-basis. "
                 "This is a starting catalog, not a survey of all ~194 "
                 "jurisdictions -- extend `energy.facts/catalog`, "
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
