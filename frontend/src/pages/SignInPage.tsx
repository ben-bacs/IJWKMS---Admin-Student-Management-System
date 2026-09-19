import { useAuth } from '../auth/AuthContext'

export function SignInPage() {
  const auth = useAuth()

  return (
    <div className="page">
      <header className="page-header">
        <p className="eyebrow">Identity and access</p>
        <h1>Sign in</h1>
        <p>Secure, role-aware authentication arrives in Phase 2.</p>
      </header>
      <div className="notice" role="status">
        Current session: <strong>{auth.status}</strong>. No default accounts or credentials are
        embedded in this application.
      </div>
    </div>
  )
}
