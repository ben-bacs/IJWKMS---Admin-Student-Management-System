import { NavLink, Outlet } from 'react-router-dom'

export function AppShell() {
  return (
    <div className="app-shell">
      <header className="site-header">
        <NavLink className="brand" to="/">
          <span className="brand-mark" aria-hidden="true">I</span>
          <span>IJWKMS Next</span>
        </NavLink>
        <nav className="site-nav" aria-label="Primary navigation">
          <NavLink to="/" end>Overview</NavLink>
          <NavLink to="/student">Student</NavLink>
          <NavLink to="/admin">Administration</NavLink>
          <NavLink to="/login">Sign in</NavLink>
        </nav>
      </header>

      <main>
        <Outlet />
      </main>

      <footer className="site-footer">
        <span>IJWKMS Next · Modernization in progress</span>
        <span>Secure by design · API first</span>
      </footer>
    </div>
  )
}
