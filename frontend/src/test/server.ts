import { setupServer } from 'msw/node';
import { http, HttpResponse } from 'msw';

/** MSW server shared by all component tests; each test registers its own handlers. */
export const server = setupServer(
  // Secondary data loaded by several pages: empty unless a test overrides it.
  http.get('*/api/admin/assignments/recommended-duration-ended', () => HttpResponse.json([])),
  http.get('*/api/me/schedules', () => HttpResponse.json([])),
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
