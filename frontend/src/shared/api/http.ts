import { ApiError, type ProblemDetails } from '../errors/ApiError';

/**
 * Thin fetch wrapper for the same-origin backend:
 * - session cookie (HttpOnly) sent automatically;
 * - CSRF double-submit: reads the XSRF-TOKEN cookie and echoes it in X-XSRF-TOKEN;
 * - request timeout, uniform ApiError built from RFC 9457 Problem Details;
 * - global notification when the session is missing/expired (401).
 */

const CSRF_COOKIE = 'XSRF-TOKEN';
const CSRF_HEADER = 'X-XSRF-TOKEN';
const TIMEOUT_MS = 15_000;
const UNSAFE = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

type Listener = () => void;
const unauthorizedListeners = new Set<Listener>();

/** Registers a callback invoked whenever the backend answers 401 (session missing/expired). */
export function onUnauthorized(listener: Listener): () => void {
  unauthorizedListeners.add(listener);
  return () => unauthorizedListeners.delete(listener);
}

function readCookie(name: string): string | null {
  const match = document.cookie.split('; ').find((c) => c.startsWith(`${name}=`));
  return match ? decodeURIComponent(match.slice(name.length + 1)) : null;
}

let csrfRequest: Promise<void> | null = null;

async function fetchCsrf(): Promise<void> {
  csrfRequest ??= fetch(buildUrl('/api/auth/csrf'), { credentials: 'same-origin', headers: { Accept: 'application/json' } })
    .then(() => undefined)
    .finally(() => {
      csrfRequest = null;
    });
  return csrfRequest;
}

async function ensureCsrfToken(): Promise<string | null> {
  let token = readCookie(CSRF_COOKIE);
  if (!token) {
    await fetchCsrf();
    token = readCookie(CSRF_COOKIE);
  }
  return token;
}

export type QueryValue = string | number | boolean | null | undefined;

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  body?: unknown;
  query?: Record<string, QueryValue>;
  signal?: AbortSignal;
}

export function buildUrl(path: string, query?: Record<string, QueryValue>): string {
  const absolute = new URL(path, window.location.origin).toString();
  if (!query) {
    return absolute;
  }
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== null && value !== '') {
      params.set(key, String(value));
    }
  }
  const qs = params.toString();
  return qs ? `${absolute}?${qs}` : absolute;
}

async function toApiError(response: Response): Promise<ApiError> {
  let problem: ProblemDetails | null = null;
  const type = response.headers.get('Content-Type') ?? '';
  if (type.includes('json')) {
    try {
      problem = (await response.json()) as ProblemDetails;
    } catch {
      problem = null;
    }
  }
  return ApiError.fromProblem(response.status, problem);
}

async function send(path: string, options: RequestOptions, retryCsrf: boolean): Promise<Response> {
  const method = options.method ?? 'GET';
  const headers: Record<string, string> = { Accept: 'application/json, application/problem+json' };
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  if (UNSAFE.has(method)) {
    const token = await ensureCsrfToken();
    if (token) {
      headers[CSRF_HEADER] = token;
    }
  }
  const timeout = AbortSignal.timeout(TIMEOUT_MS);
  const signal = options.signal ? AbortSignal.any([options.signal, timeout]) : timeout;

  let response: Response;
  try {
    response = await fetch(buildUrl(path, options.query), {
      method,
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      credentials: 'same-origin',
      signal,
    });
  } catch (error) {
    if (options.signal?.aborted) {
      throw error;
    }
    throw ApiError.network(timeout.aborted);
  }

  if (response.status === 403 && retryCsrf && UNSAFE.has(method)) {
    const clone = response.clone();
    const err = await toApiError(clone);
    if (err.code === 'CSRF_INVALID') {
      await fetchCsrf();
      return send(path, options, false);
    }
  }
  return response;
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const response = await send(path, options, true);
  if (!response.ok) {
    const error = await toApiError(response);
    if (response.status === 401 && path !== '/api/auth/login') {
      unauthorizedListeners.forEach((listener) => listener());
    }
    throw error;
  }
  if (response.status === 204 || response.headers.get('Content-Length') === '0') {
    return undefined as T;
  }
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export const http = {
  get: <T>(path: string, query?: Record<string, QueryValue>, signal?: AbortSignal) =>
    apiRequest<T>(path, { method: 'GET', query, signal }),
  post: <T>(path: string, body?: unknown) => apiRequest<T>(path, { method: 'POST', body }),
  put: <T>(path: string, body?: unknown) => apiRequest<T>(path, { method: 'PUT', body }),
  del: <T>(path: string) => apiRequest<T>(path, { method: 'DELETE' }),
};
