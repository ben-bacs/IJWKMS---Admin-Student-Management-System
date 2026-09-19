import { createContext, type PropsWithChildren, useContext } from 'react'

export type AuthState = {
  status: 'anonymous'
  user: null
}

const anonymousState: AuthState = { status: 'anonymous', user: null }
const AuthContext = createContext<AuthState>(anonymousState)

export function AuthProvider({ children }: PropsWithChildren) {
  return <AuthContext value={anonymousState}>{children}</AuthContext>
}

// Phase 2 will replace this stable anonymous state with server-backed sessions.
// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  return useContext(AuthContext)
}
