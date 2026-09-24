# Permission-gated administration portal

The administration portal exposes the academic operations delivered in Phases 2–7 without requiring operators to issue API requests manually. Navigation and forms are derived from the authenticated user's effective permissions. This improves usability but does not replace authorization: every API endpoint independently checks the persisted permission or object-scope policy.

## Workspaces

The responsive portal includes overview, students, organization units, programs, curricula, courses, academic years and terms, offerings, enrollments, grading oversight, advising, users and roles, report previews, audit events, and effective-access settings. Read-only users see the records and actions assigned to them; management forms are omitted when their required permission is absent.

Mutating forms use the API's optimistic-lock versions where applicable. Student, course, offering, enrollment, grade, standing, account-status, and role lifecycle changes require an explicit confirmation step before the request is sent. Success and safe public error messages are presented in the workspace, while correlation identifiers remain available through the API error contract for operational support.

## Audit oversight

`GET /api/v1/audit/events` requires `audit.events.read` and returns a paginated, newest-first event summary. Operators may filter by action text and outcome. The response includes actor display name, target, outcome, correlation identifier, and occurrence time but excludes secret, credential, token, and network-hash data.

## Browser data handling

The portal uses the existing HttpOnly session cookie and keeps loaded academic records in React memory only. It does not persist student, grade, advising, or audit data in local or session storage. Refreshing or closing the page clears the displayed operational data.

## Verification

Frontend component coverage verifies permission-derived navigation, authorized record loading, and the confirmation boundary for a sensitive lifecycle action. PostgreSQL integration coverage verifies audit filtering for an authorized administrator and denial for a registrar without the audit permission.
