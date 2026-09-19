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
})

function apiResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}
