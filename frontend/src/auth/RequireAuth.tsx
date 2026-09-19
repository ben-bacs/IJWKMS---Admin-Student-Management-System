import type { PropsWithChildren } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from './AuthContext'

type RequireAuthProps = PropsWithChildren<{ permission?: string }>

export function RequireAuth({ children, permission }: RequireAuthProps) {
  const auth = useAuth()
  const location = useLocation()

  if (auth.status === 'loading') {
    return <div className="page"><div className="notice" role="status">Checking your session…</div></div>
  }
  if (auth.status === 'anonymous') {
    return <Navigate to="/login" state={{ from: location.pathname }} replace />
  }
  if (permission && !auth.hasPermission(permission)) {
    return (
      <div className="page">
        <header className="page-header">
          <p className="eyebrow">Access restricted</p>
          <h1>This workspace is not assigned to you.</h1>
          <p>Your account is signed in, but it does not have the required permission.</p>
        </header>
      </div>
    )
  }
  return children
}
