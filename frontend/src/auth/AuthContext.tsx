import {
  createContext,
  type PropsWithChildren,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react'
import { ApiError, apiRequest, jsonRequest } from '../api/client'

export type User = {
  id: string
  username: string
  displayName: string
  status: 'ACTIVE' | 'LOCKED' | 'DISABLED'
  roles: string[]
  permissions: string[]
}

type AuthState =
  | { status: 'loading'; user: null }
  | { status: 'anonymous'; user: null }
  | { status: 'authenticated'; user: User }

type AuthContextValue = AuthState & {
  login: (username: string, password: string) => Promise<User>
  logout: () => Promise<void>
  hasPermission: (permission: string) => boolean
}

type LoginResponse = { user: User; expiresAt: string }

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: PropsWithChildren) {
  const [state, setState] = useState<AuthState>({ status: 'loading', user: null })

  useEffect(() => {
    let active = true
    apiRequest<User>('/api/v1/auth/me')
      .then((user) => {
        if (active) setState({ status: 'authenticated', user })
      })
      .catch((error: unknown) => {
        if (!active) return
        if (error instanceof ApiError && error.status === 401) {
          setState({ status: 'anonymous', user: null })
          return
        }
        setState({ status: 'anonymous', user: null })
      })
    return () => {
      active = false
    }
  }, [])

  const login = useCallback(async (username: string, password: string) => {
    const response = await apiRequest<LoginResponse>(
      '/api/v1/auth/login',
      jsonRequest('POST', { username, password }),
    )
    setState({ status: 'authenticated', user: response.user })
    return response.user
  }, [])

  const logout = useCallback(async () => {
    try {
      await apiRequest<void>('/api/v1/auth/logout', jsonRequest('POST'))
    } finally {
      setState({ status: 'anonymous', user: null })
    }
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({
      ...state,
      login,
      logout,
      hasPermission: (permission) => state.user?.permissions.includes(permission) ?? false,
    }),
    [state, login, logout],
  )

  return <AuthContext value={value}>{children}</AuthContext>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used inside AuthProvider')
  return context
}
