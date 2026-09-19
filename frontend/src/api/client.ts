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

export async function apiRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    credentials: 'include',
    headers: {
      Accept: 'application/json',
      ...init?.headers,
    },
  })

  if (!response.ok) {
    let message = `Request failed with status ${response.status}`

    try {
      const problem = (await response.json()) as { message?: string }
      message = problem.message ?? message
    } catch {
      // Preserve a safe, status-only message for non-JSON responses.
    }

    throw new ApiError(message, response.status, response.headers.get('X-Correlation-ID'))
  }

  if (response.status === 204) {
    return undefined as T
  }

  return response.json() as Promise<T>
}
