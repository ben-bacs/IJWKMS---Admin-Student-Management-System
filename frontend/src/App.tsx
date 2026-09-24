import { Navigate, Route, Routes } from 'react-router-dom'
import './App.css'
import { AppShell } from './app/AppShell'
import { AuthProvider } from './auth/AuthContext'
import { RequireAuth } from './auth/RequireAuth'
import { AdministrationPage } from './pages/AdministrationPage'
import { ADMIN_ENTRY_PERMISSIONS } from './pages/admin/adminConfig'
import { LandingPage } from './pages/LandingPage'
import { SignInPage } from './pages/SignInPage'
import { StudentPortalPage } from './pages/StudentPortalPage'

function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route element={<AppShell />}>
          <Route index element={<LandingPage />} />
          <Route path="login" element={<SignInPage />} />
          <Route path="student/*" element={<RequireAuth><StudentPortalPage /></RequireAuth>} />
          <Route
            path="admin/*"
            element={(
              <RequireAuth anyPermissions={ADMIN_ENTRY_PERMISSIONS}>
                <AdministrationPage />
              </RequireAuth>
            )}
          />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Routes>
    </AuthProvider>
  )
}

export default App
