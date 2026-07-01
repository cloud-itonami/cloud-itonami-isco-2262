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

## License

AGPL-3.0-or-later.
