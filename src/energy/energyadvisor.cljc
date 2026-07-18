(ns energy.energyadvisor
  "Energy Advisor client -- the *contained intelligence node* for the
  community-energy actor.

  It normalizes site-intake, drafts a per-jurisdiction grid-
  interconnection/tariff evidence checklist, screens sites for an
  unresolved grid-instability flag, drafts the battery-dispatch
  action, and drafts the settlement-finalization action. CRITICAL: it
  is a smart-but-untrusted advisor. It returns a *proposal* (with a
  rationale + the fields it cited), never a committed record or a
  real battery dispatch/settlement finalization. Every output is
  censored downstream by `energy.governor` before anything touches
  the SSoT, and `:actuation/dispatch-battery`/`:actuation/finalize-
  settlement` proposals NEVER auto-commit at any phase -- see README
  `Actuation`.

  Like every sibling actor's advisor, this is a deterministic mock so
  the actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the spec-basis gate
     :cites      [kw|str ..]    ; facts/sources the LLM used -- SCANNED too
     :effect     kw             ; how a commit would mutate the SSoT
     :stake      kw|nil         ; :actuation/dispatch-battery | :actuation/finalize-settlement | nil
     :confidence 0..1}"
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [clojure.string :as str]
            [energy.facts :as facts]
            [energy.registry :as registry]
            [energy.store :as store]
            [langchain.model :as model]))

(defn- normalize-intake
  "Directory upsert -- the LLM only normalizes/validates the patch; it
  does not invent the site, SOC figures or jurisdiction. High
  confidence, low stakes."
  [_db {:keys [patch]}]
  {:summary    (str "サイト記録更新: " (pr-str (keys patch)))
   :rationale  "入力 patch の正規化のみ。新規事実の生成なし。"
   :cites      (vec (keys patch))
   :effect     :site/upsert
   :value      patch
   :stake      nil
   :confidence 0.97})

(defn- verify-tariff
  "Per-jurisdiction grid-interconnection/tariff evidence checklist
  draft. `:no-spec?` injects the failure mode we must defend against:
  proposing a checklist for a jurisdiction with NO official spec-basis
  in `energy.facts` -- the Grid Policy Governor must reject this
  (never invent a jurisdiction's requirements)."
  [db {:keys [subject no-spec?]}]
  (let [s (store/site db subject)
        iso3 (if no-spec? "ATL" (:jurisdiction s))
        sb (facts/spec-basis iso3)]
    (if (nil? sb)
      {:summary    (str iso3 " の公式spec-basisが見つかりません")
       :rationale  "energy.facts に未登録の法域。要件を推測で作らない。"
       :cites      []
       :effect     :verification/set
       :value      {:jurisdiction iso3 :checklist [] :spec-basis nil}
       :stake      nil
       :confidence 0.9}
      {:summary    (str iso3 " (" (:owner-authority sb) ") 向け必要書類 "
                        (count (:required-evidence sb)) " 件を提案")
       :rationale  (str "公式ソース: " (:provenance sb) " / 法的根拠: " (:legal-basis sb))
       :cites      [(:legal-basis sb) (:provenance sb)]
       :effect     :verification/set
       :value      {:jurisdiction iso3
                    :checklist (:required-evidence sb)
                    :spec-basis (:provenance sb)
                    :legal-basis (:legal-basis sb)}
       :stake      nil
       :confidence 0.9})))

(defn- screen-instability
  "Grid-instability screening draft. `:grid-instability-flag-
  unresolved?` on the site record injects the failure mode: the Grid
  Policy Governor must HOLD, un-overridably, on any unresolved flag."
  [db {:keys [subject]}]
  (let [s (store/site db subject)]
    (cond
      (nil? s)
      {:summary "対象サイト記録が見つかりません" :rationale "no site record"
       :cites [] :effect :instability-screen/set :value {:site-id subject :verdict :unknown}
       :stake nil :confidence 0.0}

      (true? (:grid-instability-flag-unresolved? s))
      {:summary    (str (:site-name s) ": 未解決の系統不安定フラグを検出")
       :rationale  "スクリーニングが未解決の系統不安定フラグを検出。人手確認とホールドが必須。"
       :cites      [:instability-check]
       :effect     :instability-screen/set
       :value      {:site-id subject :verdict :unresolved}
       :stake      nil
       :confidence 0.95}

      :else
      {:summary    (str (:site-name s) ": 未解決の系統不安定フラグなし")
       :rationale  "系統不安定スクリーニング完了。"
       :cites      [:instability-check]
       :effect     :instability-screen/set
       :value      {:site-id subject :verdict :resolved}
       :stake      nil
       :confidence 0.9})))

(defn- propose-battery-dispatch
  "Draft the actual BATTERY-DISPATCH action -- dispatching a real
  battery charge/discharge or switching action at a community
  renewable-energy site. ALWAYS `:stake :actuation/dispatch-battery`
  -- this is a REAL-WORLD grid-safety-critical act, never a draft the
  actor may auto-run. See README `Actuation`: no phase ever adds this
  op to a phase's `:auto` set (`energy.phase`); the governor also
  always escalates on `:actuation/dispatch-battery`. Two independent
  layers agree, deliberately."
  [db {:keys [subject]}]
  (let [s (store/site db subject)]
    {:summary    (str subject " 向け蓄電池動作提案"
                      (when s (str " (site=" (:site-name s) ")")))
     :rationale  (if s
                   (str "battery-soc-percent=" (:battery-soc-percent s)
                        " safe-range=[" (:soc-min-safe s) "," (:soc-max-safe s) "]")
                   "サイト記録が見つかりません")
     :cites      (if s [subject] [])
     :effect     :site/mark-dispatched
     :value      {:site-id subject}
     :stake      :actuation/dispatch-battery
     :confidence (if (and s (not (registry/battery-soc-out-of-range? s))) 0.9 0.3)}))

(defn- propose-settlement
  "Draft the actual SETTLEMENT action -- finalizing a real tariff/
  settlement report for a community renewable-energy site. ALWAYS
  `:stake :actuation/finalize-settlement` -- this is a REAL-WORLD
  business-critical act, never a draft the actor may auto-run. See
  README `Actuation`: no phase ever adds this op to a phase's `:auto`
  set (`energy.phase`); the governor also always escalates on
  `:actuation/finalize-settlement`. Two independent layers agree,
  deliberately."
  [db {:keys [subject]}]
  (let [s (store/site db subject)]
    {:summary    (str subject " 向け精算確定提案"
                      (when s (str " (site=" (:site-name s) ")")))
     :rationale  (if s
                   "jurisdiction-evidence-checklist referenced"
                   "サイト記録が見つかりません")
     :cites      (if s [subject] [])
     :effect     :site/mark-settled
     :value      {:site-id subject}
     :stake      :actuation/finalize-settlement
     :confidence (if s 0.9 0.3)}))

(defn- register-power-supply
  "Site power-supply-SOURCE registration -- draft the ADMINISTRATIVE
  linkage of a community renewable-energy site to its OWN committed
  `:power-supply` record (`:power-supply/id`/`:power-supply/source-
  actor` -- this site's own generation actor, \"cloud-itonami-isic-
  3512\" -- /`:power-supply/feeder-ref`/`:power-supply/capacity-mw`/
  `:power-supply/agreement-start-iso`), naming WHICH downstream
  electric-distribution-utility feeder (`cloud-itonami-isic-3510`)
  this site supplies -- see superproject ADR-2800000500. Reuses the
  SAME low-stakes normalize-only shape as `normalize-intake`
  (`:site/upsert`): registering a supply linkage is a directory fact
  about an already-agreed arrangement, never a real-time battery-
  dispatch decision. `:power-supply/*` is entirely OPTIONAL on a site
  record; a site with none of these fields behaves exactly as before
  this addition."
  [_db {:keys [patch]}]
  {:summary    (str "サイト電力供給先登録: " (pr-str (keys patch)))
   :rationale  "入力patchの正規化のみ。新規事実の生成なし -- このサイト自身の確定record(:power-supply/*)をフィーダーへの供給先として紐付けるだけ。"
   :cites      (vec (keys patch))
   :effect     :site/upsert
   :value      patch
   :stake      nil
   :confidence 0.95})

(defn infer
  "Route a request to the right proposal generator.
  request: {:op kw :subject id ...op-specific...}"
  [db {:keys [op] :as request}]
  (case op
    :site/intake                        (normalize-intake db request)
    :tariff/verify                      (verify-tariff db request)
    :demand/screen                      (screen-instability db request)
    :actuation/dispatch-battery         (propose-battery-dispatch db request)
    :actuation/finalize-settlement      (propose-settlement db request)
    :supply/register-power-supply       (register-power-supply db request)
    {:summary "未対応の操作" :rationale (str op) :cites []
     :effect :noop :stake nil :confidence 0.0}))

;; ----------------------------- Advisor protocol -----------------------------

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

(defn mock-advisor
  "The deterministic advisor (the `infer` logic above). Default everywhere."
  [] (reify Advisor (-advise [_ st req] (infer st req))))

(def ^:private system-prompt
  (str "あなたはコミュニティ再生可能エネルギー事業者の蓄電池動作・精算確定エージェントの助言者です。"
       "与えられた事実のみに基づき、提案を1つだけEDNマップで返します。説明や前置きは"
       "一切書かず、EDNだけを出力します。\n"
       "キー: :summary(人向けドラフト) :rationale(根拠/必ず事実から) "
       ":cites(使った事実キーのベクタ) "
       ":effect(:site/upsert|:verification/set|:instability-screen/set|"
       ":site/mark-dispatched|:site/mark-settled) "
       ":stake(:actuation/dispatch-battery か :actuation/finalize-settlement か nil) :confidence(0..1)。\n"
       "重要: 登録されていない法域の要件を絶対に創作してはいけません。"
       "spec-basisが無い場合は :cites を空にし confidence を上げないこと。"))

(defn- facts-for [st {:keys [op subject]}]
  (case op
    :tariff/verify                    {:site (store/site st subject)}
    :demand/screen                    {:site (store/site st subject)}
    :actuation/dispatch-battery       {:site (store/site st subject)}
    :actuation/finalize-settlement    {:site (store/site st subject)}
    :supply/register-power-supply     {:site (store/site st subject)}
    {:site (store/site st subject)}))

(defn- parse-proposal
  "Parse the model's EDN proposal defensively. Any parse/shape failure
  yields a safe low-confidence noop so the Grid Policy Governor
  escalates/holds -- an LLM hiccup can never auto-dispatch a battery
  action or auto-finalize a settlement."
  [content]
  (let [p (try (edn/read-string (str/trim (str content)))
               (catch #?(:clj Exception :cljs :default) _ nil))]
    (if (map? p)
      (-> p
          (update :cites #(vec (or % [])))
          (update :confidence #(if (number? %) (double %) 0.0))
          (update :effect #(or % :noop)))
      {:summary "LLM応答を解釈できませんでした" :rationale (str content)
       :cites [] :effect :noop :stake nil :confidence 0.0})))

(defn llm-advisor
  "An advisor backed by a `langchain.model/ChatModel` (real inference)."
  ([chat-model] (llm-advisor chat-model {}))
  ([chat-model gen-opts]
   (reify Advisor
     (-advise [_ st req]
       (let [msgs [{:role :system :content system-prompt}
                   {:role :user :content (str "操作: " (:op req)
                                              "\n対象: " (:subject req)
                                              "\n事実: " (pr-str (facts-for st req)))}]
             resp (model/-generate chat-model msgs gen-opts)]
         (parse-proposal (:content resp)))))))

(defn trace
  "Decision-grounded audit record -- persisted to the :audit channel."
  [request proposal]
  {:t          :energyadvisor-proposal
   :op         (:op request)
   :subject    (:subject request)
   :summary    (:summary proposal)
   :rationale  (:rationale proposal)
   :cites      (:cites proposal)
   :confidence (:confidence proposal)})
