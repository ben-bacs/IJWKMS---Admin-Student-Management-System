import { type FormEvent, useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'

export function SignInPage() {
  const auth = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  if (auth.status === 'authenticated') {
    return <Navigate to={auth.hasPermission('identity.users.read') ? '/admin' : '/student'} replace />
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      const user = await auth.login(username, password)
      const requestedPath = (location.state as { from?: unknown } | null)?.from
      const safePath = typeof requestedPath === 'string' && requestedPath.startsWith('/')
        ? requestedPath
        : null
      navigate(safePath ?? (user.permissions.includes('identity.users.read') ? '/admin' : '/student'), {
        replace: true,
      })
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Sign in could not be completed.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="page auth-page">
      <header className="page-header">
        <p className="eyebrow">Identity and access</p>
        <h1>Sign in</h1>
        <p>Use your assigned institutional account. Sessions are secured in an HttpOnly cookie.</p>
      </header>
      <form className="auth-form surface" onSubmit={submit}>
        <label>
          Username
          <input
            autoComplete="username"
            maxLength={100}
            required
            value={username}
            onChange={(event) => setUsername(event.target.value)}
          />
        </label>
        <label>
          Password
          <input
            autoComplete="current-password"
            maxLength={128}
            required
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
        </label>
        {error && <div className="form-error" role="alert">{error}</div>}
        <button className="button" disabled={submitting || auth.status === 'loading'} type="submit">
          {submitting ? 'Signing in…' : 'Sign in securely'}
        </button>
        <p className="form-help">No default credentials are embedded in the application.</p>
      </form>
    </div>
  )
}
