(ns pharmacy-practice.store
  "SSoT for the ISCO-08 2262 independent pharmacy-practice
  (pharmacist-led) sole-proprietor actor, behind a `Store` protocol so
  the backend is a swap (MemStore default ‖ a real Datomic/kotoba-server
  backend, per the itonami actor pattern). Distinct from the ISCO-08
  3213 pharmacy-technician actor (`pharmacy-support.*`): a pharmacist
  owns final clinical verification and dispensing authority, not just
  fill-preparation, so the domain and hard invariants differ (prescriber
  verification gate rather than allergy-conflict gate).

  Domain = independent pharmacy practice:

    patient                    — a registered patient (patientId)
    prescription-verification  — a prescriber-verification event under a
                                 patient (verificationId, patientId,
                                 drug, prescriberVerified? boolean)
    dispense                   — a dispense event under a patient
                                 (dispenseId, patientId, drug, quantity,
                                 controlledSubstance? boolean)

  The append-only records are the operating ledger: a verification or
  dispense must reference a registered patient, and these records are
  never mutated in place, only appended.")

(defprotocol Store
  (patient [st patient-id])
  (verifications-of [st patient-id])
  (dispenses-of [st patient-id])
  (register-patient! [st patient])
  (record-verification! [st verification])
  (record-dispense! [st dispense]))

(defrecord MemStore [state]
  Store
  (patient [_ patient-id]
    (get-in @state [:patients patient-id]))
  (verifications-of [_ patient-id]
    (filter #(= patient-id (:patient-id %)) (:verifications @state)))
  (dispenses-of [_ patient-id]
    (filter #(= patient-id (:patient-id %)) (:dispenses @state)))
  (register-patient! [_ patient]
    (swap! state assoc-in [:patients (:patient-id patient)] patient))
  (record-verification! [_ verification]
    (swap! state update :verifications (fnil conj []) verification))
  (record-dispense! [_ dispense]
    (swap! state update :dispenses (fnil conj []) dispense)))

(defn mem-store
  ([] (mem-store {}))
  ([seed]
   (->MemStore (atom (merge {:patients {} :verifications [] :dispenses []} seed)))))
