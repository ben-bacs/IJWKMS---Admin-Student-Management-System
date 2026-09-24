export class ApiError extends Error {
  readonly status: number
  readonly correlationId: string | null

  constructor(
    message: string,
    status: number,
    correlationId: string | null,
  ) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.correlationId = correlationId
  }
}

type ApiErrorEnvelope = {
  error?: {
    code?: string
    message?: string
    correlationId?: string | null
  }
}

type CsrfToken = {
  headerName: string
  token: string
}

let csrfTokenRequest: Promise<CsrfToken> | null = null

async function csrfToken(): Promise<CsrfToken> {
  csrfTokenRequest ??= apiRequest<CsrfToken>('/api/v1/auth/csrf')
  try {
    return await csrfTokenRequest
  } catch (error) {
    csrfTokenRequest = null
    throw error
  }
}

export async function apiRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = init.method?.toUpperCase() ?? 'GET'
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')

  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const csrf = await csrfToken()
    headers.set(csrf.headerName, csrf.token)
  }

  const response = await fetch(path, {
    ...init,
    credentials: 'include',
    headers,
  })

  if (!response.ok) {
    let message = `Request failed with status ${response.status}`
    let correlationId = response.headers.get('X-Correlation-ID')

    try {
      const problem = (await response.json()) as ApiErrorEnvelope
      message = problem.error?.message ?? message
      correlationId = problem.error?.correlationId ?? correlationId
    } catch {
      // Preserve a safe, status-only message for non-JSON responses.
    }

    throw new ApiError(message, response.status, correlationId)
  }

  if (response.status === 204) {
    return undefined as T
  }

  return response.json() as Promise<T>
}

export function jsonRequest(method: string, body?: unknown): RequestInit {
  return {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  }
}
