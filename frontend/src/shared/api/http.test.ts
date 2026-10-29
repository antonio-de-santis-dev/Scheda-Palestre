import { http as mswHttp, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { problem, server } from '../../test/server';
import { http } from './http';

describe('CSRF bootstrap', () => {
  it('reports bootstrap HTTP failures and never sends the mutation without a token', async () => {
    let writes = 0;
    server.use(
      mswHttp.get('*/api/auth/csrf', () => problem(503, 'UNAVAILABLE')),
      mswHttp.post('*/api/test-write', () => { writes++; return HttpResponse.json({}); }),
    );
    await expect(http.post('/api/test-write', {})).rejects.toMatchObject({ status: 503, code: 'UNAVAILABLE' });
    expect(writes).toBe(0);
    // A failed bootstrap must not poison subsequent requests.
    server.use(mswHttp.get('*/api/auth/csrf', () => HttpResponse.json({}, {
      headers: { 'Set-Cookie': 'XSRF-TOKEN=recovered; Path=/' },
    })));
    await http.post('/api/test-write', {});
    expect(writes).toBe(1);
  });

  it('turns bootstrap network failures into the same ApiError as ordinary requests', async () => {
    server.use(mswHttp.get('*/api/auth/csrf', () => HttpResponse.error()));
    await expect(http.post('/api/test-write', {})).rejects.toMatchObject({ status: 0, code: 'NETWORK_ERROR' });
  });
});

describe('request boundaries', () => {
  it('rejects external destinations before transmitting a request or CSRF token', async () => {
    await expect(http.get('https://other.example/api/test')).rejects.toThrow('application origin');
  });

  it('does not send a cancelled query', async () => {
    let reads = 0;
    server.use(mswHttp.get('*/api/test-read', () => { reads++; return HttpResponse.json({}); }));
    const controller = new AbortController();
    controller.abort();
    await expect(http.get('/api/test-read', undefined, controller.signal)).rejects.toMatchObject({ name: 'AbortError' });
    expect(reads).toBe(0);
  });
});
