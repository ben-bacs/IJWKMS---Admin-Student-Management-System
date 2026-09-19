import { useAuth } from '../auth/AuthContext'

export function AdministrationPage() {
  const auth = useAuth()

  return (
    <div className="page">
      <header className="page-header">
        <p className="eyebrow">Institutional workspace</p>
        <h1>Run academic operations with confidence.</h1>
        <p>
          {auth.user?.displayName}, your assigned roles are {auth.user?.roles.join(', ')}.
          Authorized teams will manage records, offerings, grading, reports, and audit trails here.
        </p>
      </header>
      <div className="notice">
        Access to this workspace is permission-gated on the server and reflected in the navigation.
      </div>
    </div>
  )
}
