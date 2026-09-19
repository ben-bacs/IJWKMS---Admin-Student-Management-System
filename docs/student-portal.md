# Secure student portal

Phase 8 exposes the student lifecycle through a responsive self-service workspace: dashboard, profile, active enrollment, grades, academic history, curriculum progress, standing, notifications, privacy/data, active sessions, and personal report download.

The portal discovers the linked student through `/api/v1/students/me` and uses that server-authorized identifier for subsequent calls. Changing a browser URL cannot expand access because each academic endpoint repeats object-level authorization. API errors are rendered from the safe public error envelope; internal exceptions are never displayed.

Academic responses remain in React memory and are not copied into `localStorage` or `sessionStorage`. The personal JSON report is assembled only from already-authorized responses in an ephemeral browser `Blob`, then immediately releases its object URL. Adviser-only notes are filtered by the server before the portal receives them.

Active-session review exposes device user-agent and timestamps but not IP hashes or session tokens. Revocation is scoped by both session ID and authenticated user ID. Revoking the current session signs the browser out; revoking another session leaves the current workflow intact.
