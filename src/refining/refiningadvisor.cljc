(ns refining.refiningadvisor
  "RefiningAdvisor client -- the *contained intelligence node* for the
  petroleum-refining actor.

  It normalizes batch intake, drafts a per-jurisdiction process-safety /
  mechanical-integrity evidence checklist (assay verification), drafts
  the unit-process action, and drafts the product-yield action.
  CRITICAL: it is a smart-but-untrusted advisor. It returns a *proposal*
  (with a rationale + the fields it cited), never a committed record or
  a real process/yield. Every output is censored downstream by
  `refining.governor` before anything touches the SSoT, and
  `:unit/process`/`:product/yield` proposals NEVER auto-commit at any
  phase -- see README `Actuation`.

  Like every sibling actor's advisor, this is a deterministic mock so
  the actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the spec-basis gate
     :cites      [kw|str ..]    ; facts/sources the LLM used -- SCANNED too
     :effect     kw             ; how a commit would mutate the SSoT
     :stake      kw|nil         ; :unit/process | :product/yield | nil
     :confidence 0..1}"
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [clojure.string :as str]
            [refining.facts :as facts]
            [refining.registry :as registry]
            [refining.store :as store]
            [langchain.model :as model]))

(defn- normalize-intake
  "Directory upsert -- the LLM only normalizes/validates the patch; it
  does not invent the refinery name, operator or jurisdiction. High
  confidence, low stakes."
  [_db {:keys [patch]}]
  {:summary    (str "バッチ記録更新: " (pr-str (keys patch)))
   :rationale  "入力 patch の正規化のみ。新規事実の生成なし。"
   :cites      (vec (keys patch))
   :effect     :batch/upsert
   :value      patch
   :stake      nil
   :confidence 0.97})

(defn- assess-assay
  "Per-jurisdiction process-safety / mechanical-integrity evidence
  checklist draft (assay verification). `:no-spec?` injects the failure
  mode we must defend against: proposing a checklist for a jurisdiction
  with NO official spec-basis in `refining.facts` -- the Refinery Safety
  Governor must reject this (never invent a jurisdiction's requirements)."
  [db {:keys [subject no-spec?]}]
  (let [b (store/refinery-batch db subject)
        iso3 (if no-spec? "ATL" (:jurisdiction b))
        sb (facts/spec-basis iso3)]
    (if (nil? sb)
      {:summary    (str iso3 " の公式spec-basisが見つかりません")
       :rationale  "refining.facts に未登録の法域。要件を推測で作らない。"
       :cites      []
       :effect     :assay/set
       :value      {:jurisdiction iso3 :checklist [] :spec-basis nil}
       :stake      nil
       :confidence 0.9}
      {:summary    (str iso3 " (" (:owner-authority sb) ") 向け必要書類 "
                        (count (:required-evidence sb)) " 件を提案")
       :rationale  (str "公式ソース: " (:provenance sb) " / 法的根拠: " (:legal-basis sb))
       :cites      [(:legal-basis sb) (:provenance sb)]
       :effect     :assay/set
       :value      {:jurisdiction iso3
                    :checklist (:required-evidence sb)
                    :spec-basis (:provenance sb)
                    :legal-basis (:legal-basis sb)}
       :stake      nil
       :confidence 0.9})))

(defn- propose-process
  "Draft the actual UNIT-PROCESS action -- charging and running a real
  refinery unit (a distillation/reformer/cracker unit processing a
  batch of crude). ALWAYS `:stake :unit/process` -- this is a REAL-
  WORLD act (an autonomous refinery-unit robot physically actuates the
  unit's valves / heat input, or an operator does), never a draft the
  actor may auto-run. See README `Actuation`: no phase ever adds this
  op to a phase's `:auto` set (`refining.phase`); the governor also
  always escalates on `:unit/process`. Two independent layers agree,
  deliberately."
  [db {:keys [subject]}]
  (let [b (store/refinery-batch db subject)
        temp-ok? (and b (not (registry/unit-temp-out-of-range?
                              (:unit-temp-celsius-actual b)
                              (:unit-temp-min b)
                              (:unit-temp-max b))))
        pressure-ok? (and b (not (registry/unit-pressure-out-of-range?
                                  (:unit-pressure-mpa-actual b)
                                  (:unit-pressure-min b)
                                  (:unit-pressure-max b))))
        flare-ok? (and b (true? (:flare-operational? b)))
        contamination-clear? (and b (or (not (:contamination-flag-raised? b))
                                        (:contamination-flag-resolved? b)))]
    {:summary    (str subject " 向け処理提案"
                      (when b (str " (operator=" (:operator b) ")")))
     :rationale  (if b
                   (str "temp-in-window?=" temp-ok?
                        " pressure-in-window?=" pressure-ok?
                        " flare-operational?=" flare-ok?
                        " contamination-clear?=" contamination-clear?)
                   "batchが見つかりません")
     :cites      (if b [subject] [])
     :effect     :unit/mark-processed
     :value      {:refinery-batch-id subject}
     :stake      :unit/process
     :confidence (if (and temp-ok? pressure-ok? flare-ok? contamination-clear?)
                   0.9 0.3)}))

(defn- propose-yield
  "Draft the actual PRODUCT-YIELD action -- finalizing and yielding
  real refined product from a processed batch (custody transfer of
  on-spec product, yield reconciliation). ALWAYS `:stake :product/
  yield` -- this is a REAL-WORLD act (real on-spec product volumes move
  into custody / sales), never a draft the actor may auto-run. See
  README `Actuation`: no phase ever adds this op to a phase's `:auto`
  set (`refining.phase`); the governor also always escalates on
  `:product/yield`. Two independent layers agree, deliberately."
  [db {:keys [subject]}]
  (let [b (store/refinery-batch db subject)
        processed? (and b (:processed? b))
        yields (:target-yields b)
        yield-ok? (and b (not (registry/yield-rate-insufficient?
                               (:actual yields) (:required yields))))]
    {:summary    (str subject " 向け製品歩留提案"
                      (when b (str " (operator=" (:operator b) ")")))
     :rationale  (if b
                   (str "processed?=" processed?
                        " yield-rate-ok?=" yield-ok?)
                   "batchが見つかりません")
     :cites      (if b [subject] [])
     :effect     :product/mark-yielded
     :value      {:refinery-batch-id subject}
     :stake      :product/yield
     :confidence (if (and processed? yield-ok?) 0.9 0.3)}))

(defn infer
  "Route a request to the right proposal generator.
  request: {:op kw :subject id ...op-specific...}"
  [db {:keys [op] :as request}]
  (case op
    :batch/intake       (normalize-intake db request)
    :assay/verify       (assess-assay db request)
    :unit/process       (propose-process db request)
    :product/yield      (propose-yield db request)
    {:summary "未対応の操作" :rationale (str op) :cites []
     :effect :noop :stake nil :confidence 0.0}))

;; ----------------------------- Advisor protocol -----------------------------

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

(defn mock-advisor
  "The deterministic advisor (the `infer` logic above). Default everywhere."
  [] (reify Advisor (-advise [_ st req] (infer st req))))

(def ^:private system-prompt
  (str "あなたは地域石油精製事業者の精製・製品歩留エージェントの助言者です。"
       "与えられた事実のみに基づき、提案を1つだけEDNマップで返します。"
       "説明や前置きは一切書かず、EDNだけを出力します。\n"
       "キー: :summary(人向けドラフト) :rationale(根拠/必ず事実から) "
       ":cites(使った事実キーのベクタ) "
       ":effect(:batch/upsert|:assay/set|:unit/mark-processed|"
       ":product/mark-yielded) "
       ":stake(:unit/process か :product/yield か nil) :confidence(0..1)。\n"
       "重要: 登録されていない法域の精製安全要件を絶対に創作してはいけません。"
       "spec-basisが無い場合は :cites を空にし confidence を上げないこと。"
       "ユニット温度・ユニット圧力・収率・フレア稼働状態・汚染フラグの状態を偽って報告してはいけません。"))

(defn- facts-for [st {:keys [op subject]}]
  (case op
    :assay/verify  {:batch (store/refinery-batch st subject)}
    :unit/process  {:batch (store/refinery-batch st subject)}
    :product/yield {:batch (store/refinery-batch st subject)}
    {:batch (store/refinery-batch st subject)}))

(defn- parse-proposal
  "Parse the model's EDN proposal defensively. Any parse/shape failure
  yields a safe low-confidence noop so the Refinery Safety Governor
  escalates/holds -- an LLM hiccup can never auto-process a batch or
  auto-yield product."
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
  {:t          :refiningadvisor-proposal
   :op         (:op request)
   :subject    (:subject request)
   :summary    (:summary proposal)
   :rationale  (:rationale proposal)
   :cites      (:cites proposal)
   :confidence (:confidence proposal)})
