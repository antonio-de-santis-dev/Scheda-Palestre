import { setupServer } from 'msw/node';
import { http, HttpResponse } from 'msw';

/** MSW server shared by all component tests; each test registers its own handlers. */
export const server = setupServer(
  http.get('*/api/auth/csrf', () =>
    HttpResponse.json(
      { headerName: 'X-XSRF-TOKEN', token: 'test-token' },
      { headers: { 'Set-Cookie': 'XSRF-TOKEN=test-token; Path=/' } },
    ),
  ),
);

export function problem(status: number, code: string, extra: Record<string, unknown> = {}) {
  return HttpResponse.json(
    { status, code, title: 'Error', detail: code, ...extra },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  );
}
