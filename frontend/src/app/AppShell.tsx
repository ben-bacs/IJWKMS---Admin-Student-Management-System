import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

export function AppShell() {
  const auth = useAuth()

  return (
    <div className="app-shell">
      <header className="site-header">
        <NavLink className="brand" to="/">
          <span className="brand-mark" aria-hidden="true">I</span>
          <span>IJWKMS Next</span>
        </NavLink>
        <nav className="site-nav" aria-label="Primary navigation">
          <NavLink to="/" end>Overview</NavLink>
          {auth.status === 'authenticated' && <NavLink to="/student">Student</NavLink>}
          {auth.status === 'authenticated' && auth.hasPermission('identity.users.read') && (
            <NavLink to="/admin">Administration</NavLink>
          )}
          {auth.status === 'authenticated' ? (
            <button className="nav-button" type="button" onClick={() => void auth.logout()}>
              Sign out
            </button>
          ) : (
            <NavLink to="/login">Sign in</NavLink>
          )}
        </nav>
      </header>

      <main>
        <Outlet />
      </main>

      <footer className="site-footer">
        <span>
          {auth.status === 'authenticated' ? `Signed in as ${auth.user.displayName}` : 'IJWKMS Next · Modernization in progress'}
        </span>
        <span>Secure by design · API first</span>
      </footer>
    </div>
  )
}
