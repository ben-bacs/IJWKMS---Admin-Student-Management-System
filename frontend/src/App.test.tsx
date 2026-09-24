import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App'

describe('App', () => {
  afterEach(() => {
    cleanup()
    vi.unstubAllGlobals()
  })

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

    expect(await screen.findByRole('heading', { name: /academic operations, one governed workspace/i })).toBeInTheDocument()
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

  it('permission-gates every administration workspace and confirms sensitive actions', async () => {
    const permissions = [
      'student.read', 'student.write', 'academics.read', 'academics.manage', 'curriculum.read', 'curriculum.manage',
      'course.offering.read', 'course.offering.manage', 'enrollment.read', 'enrollment.create', 'enrollment.drop',
      'grade.read', 'grade.submit', 'success.read', 'success.evaluate', 'success.manage', 'advising.note.write',
      'identity.users.read', 'identity.users.manage', 'identity.roles.manage', 'audit.events.read',
    ]
    let statusMutation = false
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input)
      if (path.endsWith('/auth/me')) return apiResponse({ id: 'admin-1', username: 'admin', displayName: 'System Administrator', status: 'ACTIVE', roles: ['SYSTEM_ADMIN'], permissions })
      if (path.includes('/students?')) return apiResponse({ items: [{ id: 'student-1', studentNumber: '2026-0001', firstName: 'Ada', lastName: 'Student', status: 'ACTIVE', cohortYear: 2026 }], totalElements: 1 })
      if (path.endsWith('/auth/csrf')) return apiResponse({ headerName: 'X-XSRF-TOKEN', token: 'csrf-token' })
      if (path.endsWith('/students/student-1/status') && init?.method === 'PATCH') {
        statusMutation = true
        return apiResponse({ id: 'student-1', studentNumber: '2026-0001', firstName: 'Ada', lastName: 'Student', status: 'LEAVE', cohortYear: 2026, version: 1 })
      }
      throw new Error(`Unexpected request: ${path}`)
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<MemoryRouter initialEntries={['/admin/students']}><App /></MemoryRouter>)

    expect(await screen.findByRole('cell', { name: '2026-0001' })).toBeInTheDocument()
    const nav = screen.getByRole('navigation', { name: /administration portal/i })
    expect(within(nav).getByRole('link', { name: /organizations/i })).toHaveAttribute('href', '/admin/organizations')
    expect(within(nav).getByRole('link', { name: /grading oversight/i })).toHaveAttribute('href', '/admin/grading')
    expect(within(nav).getByRole('link', { name: /^audit$/i })).toHaveAttribute('href', '/admin/audit')

    const lifecycle = screen.getByRole('heading', { name: /change student status/i }).closest('section')!
    fireEvent.change(within(lifecycle).getByLabelText(/student id/i), { target: { value: 'student-1' } })
    fireEvent.change(within(lifecycle).getByLabelText(/new status/i), { target: { value: 'LEAVE' } })
    fireEvent.change(within(lifecycle).getByLabelText(/current version/i), { target: { value: '0' } })
    fireEvent.click(within(lifecycle).getByRole('button', { name: /change student status/i }))
    expect(statusMutation).toBe(false)
    fireEvent.click(within(lifecycle).getByRole('button', { name: /confirm action/i }))
    await waitFor(() => expect(statusMutation).toBe(true))
    expect(await screen.findByText(/completed successfully/i)).toBeInTheDocument()
  })
})

function apiResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}
