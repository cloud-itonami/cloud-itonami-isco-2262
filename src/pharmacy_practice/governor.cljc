(ns pharmacy-practice.governor
  "PharmacyPracticeGovernor — the independent safety/traceability layer
  for the ISCO-08 2262 independent pharmacy-practice (pharmacist) actor.
  The Dispensing Advisor proposes actions (verification, dispense); it
  has no notion of patient provenance, prior-verification state, or
  controlled-substance risk, so this MUST be a separate system able to
  *reject* a proposal and fall back to HOLD — the itonami-actor pattern
  (independent Governor gates a proposing actor) applied to this
  occupation.

  Charter (mirrors ADR-2607011000 robotics premise + ADR-2607012000
  cloud-itonami-isco): the actor never dispatches a robot action or writes an
  operating record the governor refuses. A dispense of a controlled
  substance ALWAYS requires human sign-off — it can never be
  auto-approved. A dispense with no prior verified prescriber
  verification for that drug is HARD-blocked, unconditionally (a
  cross-record check, distinct from the ISCO-08 3213 pharmacy-support
  actor's allergy-conflict field check).

  HARD invariants for :pharmacy-practice/propose:
    1. Patient provenance      — a verification or dispense must
       reference a registered patient.
    2. No-actuation            — the proposal must not directly mutate a
       verification/dispense record outside the record-verification!/
       record-dispense! path (effect must be :propose, never a raw store
       write).
    3. Verification-required   — a dispense must have at least one prior
       verification record for the same drug with `prescriber-verified?`
       true; otherwise it is unconditionally HELD regardless of
       safety-class or confidence.
    4. Controlled-substance safety — a dispense with `controlled-
       substance?` true always requires :high or higher safety-class,
       forcing human sign-off; it is never auto-approved regardless of
       confidence.
  SOFT:
    5. Confidence floor → escalate."
  (:require [pharmacy-practice.store :as store]))

(def confidence-floor 0.6)
(def safety-classes [:none :low :medium :high :safety-critical])

(defn- safety-rank [safety-class]
  (let [idx (.indexOf safety-classes safety-class)]
    (if (neg? idx) 0 idx)))

(defn- verified-for-drug? [verifications drug]
  (some #(and (= drug (:drug %)) (:prescriber-verified? %)) verifications))

(defn- hard-violations [{:keys [patient-fn verifications-fn]} proposal]
  (let [{:keys [kind patient-id drug controlled-substance? safety-class effect]} proposal
        found-patient (patient-fn patient-id)]
    (cond-> []
      (nil? found-patient)
      (conj {:rule :no-patient :detail (str "未登録 patient " patient-id)})

      (not= :propose effect)
      (conj {:rule :no-actuation :detail "effect は :propose のみ許可（直接書込禁止）"})

      (and (= kind :dispense) found-patient
           (not (verified-for-drug? (verifications-fn patient-id) drug)))
      (conj {:rule :verification-required
             :detail "prescriber-verified? な verification が無い drug は dispense 不可"})

      (and (= kind :dispense) controlled-substance?
           (< (safety-rank (or safety-class :none)) (safety-rank :high)))
      (conj {:rule :controlled-substance-safety
             :detail "controlled-substance? な dispense は :high 以上の safety-class が必須"}))))

(defn assess
  "Assess a proposal against `env` (a map with `:patient-fn`/
  `:verifications-fn` lookups, decoupled from any concrete Store so this
  stays pure). Returns
  `{:decision :proceed|:hold|:human-approval :violations [...] :confidence n}`."
  [env proposal]
  (let [violations (hard-violations env proposal)
        safety-class (or (:safety-class proposal) :none)
        confidence (or (:confidence proposal) 1.0)]
    (cond
      (seq violations)
      {:decision :hold :violations violations :confidence confidence}

      (>= (safety-rank safety-class) (safety-rank :high))
      {:decision :human-approval :violations [] :confidence confidence}

      (< confidence confidence-floor)
      {:decision :human-approval :violations [] :confidence confidence
       :reason :low-confidence}

      :else
      {:decision :proceed :violations [] :confidence confidence})))

(defn env-for-store
  "Build the decoupled env map `assess` needs from a concrete
  `pharmacy-practice.store/Store` implementation."
  [store]
  {:patient-fn #(store/patient store %)
   :verifications-fn #(store/verifications-of store %)})
