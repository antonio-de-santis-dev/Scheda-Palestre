/// <reference types="node" />
import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
import { describe, expect, it, vi } from 'vitest';
function worker() {
  const handlers: Record<string, (event: Record<string, unknown>) => void> = {};
  const offline = new Response('offline');
  const caches = { open: vi.fn().mockResolvedValue({ add: vi.fn().mockResolvedValue(undefined), put: vi.fn() }), match: vi.fn().mockResolvedValue(offline), keys: vi.fn().mockResolvedValue([]), delete: vi.fn() };
  const fetch = vi.fn().mockRejectedValue(new Error('offline'));
  const self = { location: { origin: 'https://gym.test' }, addEventListener: (name: string, handler: typeof handlers[string]) => { handlers[name] = handler; }, skipWaiting: vi.fn(), clients: { claim: vi.fn(), matchAll: vi.fn().mockResolvedValue([]), openWindow: vi.fn() }, registration: { showNotification: vi.fn().mockResolvedValue(undefined) } };
  runInNewContext(readFileSync('src/pwa/sw.js', 'utf8'), { self, caches, fetch, URL });
  return { handlers, caches, fetch, self, offline };
}
describe('service worker privacy and offline fallback', () => {
  it('never intercepts APIs, writes or cross-origin resources', () => {
    const w = worker(); const respondWith = vi.fn();
    for (const [url, method] of [['https://gym.test/api/me/profile', 'GET'], ['https://gym.test/assets/a.js', 'POST'], ['https://other.test/assets/a.js', 'GET']]) {
      w.handlers.fetch!({ request: { url, method, mode: 'cors' }, respondWith });
    }
    expect(respondWith).not.toHaveBeenCalled(); expect(w.caches.open).not.toHaveBeenCalled();
  });
  it('falls back to a generic offline document without caching authenticated HTML', async () => {
    const w = worker(); let response: Promise<Response> | undefined;
    w.handlers.fetch!({ request: { url: 'https://gym.test/app/history', method: 'GET', mode: 'navigate' }, respondWith: (value: Promise<Response>) => { response = value; } });
    expect(await response).toBe(w.offline); expect(w.caches.match).toHaveBeenCalledWith('/offline.html'); expect(w.caches.open).not.toHaveBeenCalled();
  });
  it('shows generic notifications and opens only the app origin', async () => {
    const w = worker(); let pending: Promise<unknown> | undefined;
    const waitUntil = (value: Promise<unknown>) => { pending = value; };
    w.handlers.push!({ data: { json: () => ({ url: 'https://evil.test', body: 'private data' }) }, waitUntil });
    await pending; expect(w.self.registration.showNotification).toHaveBeenCalledWith('GymPlanner', expect.objectContaining({ tag: 'gymplanner-rest' }));
    expect(JSON.stringify(w.self.registration.showNotification.mock.calls)).not.toContain('private data');
    w.handlers.notificationclick!({ notification: { close: vi.fn() }, waitUntil });
    await pending; expect(w.self.clients.openWindow).toHaveBeenCalledWith('/app/today');
  });
});
