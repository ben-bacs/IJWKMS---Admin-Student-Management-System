import { useAuth } from '../auth/AuthContext'

export function StudentPortalPage() {
  const auth = useAuth()

  return (
    <div className="page">
      <header className="page-header">
        <p className="eyebrow">Student experience</p>
        <h1>Your academic path, in one place.</h1>
        <p>
          Welcome, {auth.user?.displayName}. Enrollment, grades, curriculum progress, and advising
          will come together here.
        </p>
      </header>
      <div className="notice">
        Your session is active. Student records and private profile APIs are now scoped to your linked identity.
      </div>
    </div>
  )
}
