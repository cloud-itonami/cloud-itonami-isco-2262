# cloud-itonami-isco-2262

Open Occupation Blueprint for **ISCO-08 2262**: Pharmacists.

This repository designs a forkable OSS business for an independent licensed pharmacist: a dispensing robot performs counting, packaging and labeling under a governor-gated actor, so the practice keeps its own dispensing and counseling records instead of renting a closed pharmacy SaaS.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here a dispensing robot performs pill counting, packaging and labeling under pharmacist supervision under an actor that proposes
actions and an independent **Pharmacist Governor** that gates them. The governor never
dispatches hardware itself; `:high`/`:safety-critical` actions (such as
dispensing controlled substances, dosage changes, or drug interaction overrides) require human sign-off.

A live sample of the operator console (robotics safety console, shared template) is rendered in [docs/samples/operator-console.html](docs/samples/operator-console.html) — pure-data HTML output of `kotoba.robotics.ui`.

## Core Contract

```text
prescription + patient history + interaction check
        |
        v
Dispensing Advisor -> Pharmacist Governor -> dispense, or human sign-off
        |
        v
robot actions (gated) + operating records + audit ledger
```

No automated advice can dispatch a robot action the governor refuses, suppress
an operating record, or disclose sensitive data without governor approval and
audit evidence.

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISCO-08 `2262`). Required capabilities:

- :robotics
- :identity
- :forms
- :dmn
- :bpmn
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## Reference implementation

`src/pharmacy_practice/{store,governor}.cljc` is a minimal but real
implementation of the Core Contract above (pure cljc, no external deps).
Distinct from the ISCO-08 3213 pharmacy-technician actor
(`cloud-itonami-isco-3213`'s `pharmacy-support.*`, allergy-conflict
gated): a pharmacist owns final clinical verification and dispensing
authority, so this actor is gated on a different, cross-record
invariant.

- `pharmacy-practice.store` — `Store` protocol + `MemStore`: registered
  patients, prescriber verifications, dispenses. A verification/dispense
  can only be recorded against a registered patient (patient
  provenance).
- `pharmacy-practice.governor` — `PharmacyPracticeGovernor`: `assess`
  gates a proposal against the patient env. Hard invariants force
  `:hold` (no patient, direct-write instead of `:propose`, or — the
  distinguishing invariant — a dispense with **no prior verified
  prescriber-verification record for that drug**, checked across the
  patient's verification history, unconditionally); a
  `:controlled-substance?` dispense always requires `:high`+
  safety-class and thus `:human-approval` — it can never be
  auto-approved; low-confidence proposals also escalate.

```bash
clojure -M:test   # 8 tests, 14 assertions, green
```

This is what backs this repo's `:maturity :implemented` entry in
[`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation) —
the 21st `cloud-itonami-isco-*` occupation to reach that tier, after
`cloud-itonami-isco-6112`, `-2221`, `-7126`, `-4321`, `-9312`, `-5322`,
`-8332`, `-1321`, `-3253`, `-6210`, `-5223`, `-7231`, `-8121`, `-9111`,
`-2512`, `-1120`, `-4110`, `-3213`, `-5153` and `-7411` (ADR-2607012000).

## License

AGPL-3.0-or-later.
