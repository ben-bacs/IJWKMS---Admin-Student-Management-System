import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App'

describe('App', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('renders the foundation landing page and primary navigation', () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(apiResponse(
      { error: { code: 'UNAUTHENTICATED', message: 'Authentication is required.' } },
      401,
    )))

    render(
      <MemoryRouter>
        <App />
      </MemoryRouter>,
    )

    expect(screen.getByRole('heading', { name: /clear records/i })).toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: /primary/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /sign in/i })).toHaveAttribute('href', '/login')
  })

  it('signs in through the CSRF-protected session API', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input)
      if (path.endsWith('/me')) {
        return apiResponse({ error: { code: 'UNAUTHENTICATED' } }, 401)
      }
      if (path.endsWith('/csrf')) {
        return apiResponse({ headerName: 'X-XSRF-TOKEN', token: 'csrf-token' })
      }
      if (path.endsWith('/login') && init?.method === 'POST') {
        expect(new Headers(init.headers).get('X-XSRF-TOKEN')).toBe('csrf-token')
        return apiResponse({
          user: {
            id: '00000000-0000-0000-0000-000000000001',
            username: 'admin',
            displayName: 'Administrator',
            status: 'ACTIVE',
            roles: ['SYSTEM_ADMIN'],
            permissions: ['identity.users.read'],
          },
          expiresAt: '2026-09-20T08:00:00Z',
        })
      }
      throw new Error(`Unexpected request: ${path}`)
    })
    vi.stubGlobal('fetch', fetchMock)

    render(
      <MemoryRouter initialEntries={['/login']}>
        <App />
      </MemoryRouter>,
    )

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/v1/auth/me', expect.anything()))
    fireEvent.change(screen.getByLabelText(/username/i), { target: { value: 'admin' } })
    fireEvent.change(screen.getByLabelText(/password/i), { target: { value: 'Secure-Password-2026' } })
    fireEvent.click(screen.getByRole('button', { name: /sign in securely/i }))

    expect(await screen.findByRole('heading', { name: /run academic operations/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /sign out/i })).toBeInTheDocument()
  })

  it('loads the self-scoped student portal and exposes every workflow', async () => {
    const student = {
      id: 'student-1', studentNumber: '2026-0001', firstName: 'Ada', lastName: 'Student',
      status: 'ACTIVE', cohortYear: 2026,
      programAssignment: { programCode: 'BSCS', curriculumVersionId: 'curriculum-1', curriculumVersionCode: '2026' },
    }
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const path = String(input)
      if (path.endsWith('/auth/me')) return apiResponse({ id: 'user-1', username: 'ada', displayName: 'Ada Student', status: 'ACTIVE', roles: ['STUDENT'], permissions: ['grade.read', 'success.read'] })
      if (path.endsWith('/students/me')) return apiResponse(student)
      if (path.endsWith('/profile')) return apiResponse({ contactNumber: '09•••••••••' })
      if (path.includes('/enrollments/students/')) return apiResponse({ items: [], totalElements: 0 })
      if (path.includes('/grades?')) return apiResponse({ items: [], totalElements: 0 })
      if (path.endsWith('/gwa')) return apiResponse({ weightedGwa: null, totalUnits: 0, eligibleGradeCount: 0 })
      if (path.endsWith('/advising-alerts') || path.endsWith('/advising-notes') || path.endsWith('/auth/sessions')) return apiResponse([])
      if (path.includes('/curriculum/versions/')) return apiResponse({ items: [], totalElements: 0 })
      throw new Error(`Unexpected request: ${path}`)
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<MemoryRouter initialEntries={['/student/dashboard']}><App /></MemoryRouter>)

    expect(await screen.findByRole('heading', { name: /welcome back, ada/i })).toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: /student portal/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /active sessions/i })).toHaveAttribute('href', '/student/sessions')
    expect(screen.getByRole('link', { name: /report download/i })).toHaveAttribute('href', '/student/reports')
    expect(window.localStorage.length).toBe(0)
  })
})

function apiResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}
