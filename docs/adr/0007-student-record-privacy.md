# ADR-0007: Privacy-separated student profiles and historical assignments

Status: Accepted

## Context

Student academic identity is broadly useful to authorized academic teams, while address, contact, and emergency information needs a narrower privacy boundary. Program and curriculum changes must also remain explainable after later enrollment and grading phases add academic history.

## Decision

Store private profile data in a one-to-one table separate from the student academic summary. Apply distinct profile permissions and linked-user object authorization at the API method boundary. Never include profile fields in student list or summary responses.

Represent program and curriculum placement as dated assignment rows with at most one active row. A change closes the existing row and appends its replacement. Student deactivation is a lifecycle transition and does not remove the record.

## Consequences

Privacy reviews can reason about a small set of profile endpoints and permissions. Academic consumers can use student summaries without receiving unnecessary private fields. Assignment history remains stable for downstream enrollment, grading, advising, and reporting. Writes require additional lifecycle and optimistic-lock validation.
