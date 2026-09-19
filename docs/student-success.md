# Explainable academic standing and student success

Phase 7 evaluates academic support signals with versioned deterministic rules. The initial engine uses failed-course count, term GWA thresholds, and consecutive cumulative-GWA decline. Each standing stores the exact policy, input facts, evaluator, and evaluation time. Every triggered alert repeats those facts with a stable rule code, rule version, severity, and plain-language explanation.

Possible outcomes are `GOOD_STANDING`, `WATCH`, `PROBATION`, `SUSPENSION_REVIEW`, and `UNDETERMINED`. `WATCH` is explicitly reachable at its configured boundary, correcting the legacy at-risk defect. Missing eligible grades produce `UNDETERMINED`; no opaque model supplies or alters a decision.

Advisers progress alerts through open, acknowledged, in-progress, and resolved or dismissed states. Each transition is appended to alert history and audited. Manual standing overrides require a reason, actor, timestamp, and optimistic version. Adviser assignments constrain note authorship. Student APIs return only `STUDENT_VISIBLE` notes; `ADVISER_ONLY` content is filtered at the query boundary.
