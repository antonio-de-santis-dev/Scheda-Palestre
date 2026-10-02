import { describe, expect, it, vi, afterEach } from 'vitest';
import { enablePush, disablePush } from './push';
import { http as api } from '../shared/api/http';
vi.mock('../shared/api/http', () => ({ http: { post: vi.fn() } }));
afterEach(() => { vi.unstubAllGlobals(); vi.mocked(api.post).mockReset(); });
function browser(permission = 'granted') {
  const subscription = { endpoint: 'https://fcm.googleapis.com/fcm/send/test', toJSON: () => ({ endpoint: 'test', keys: {} }), unsubscribe: vi.fn().mockResolvedValue(true) };
  const pushManager = { getSubscription: vi.fn().mockResolvedValue(subscription), subscribe: vi.fn().mockResolvedValue(subscription) };
  vi.stubGlobal('Notification', { requestPermission: vi.fn().mockResolvedValue(permission) });
  vi.stubGlobal('PushManager', function () {}); vi.stubGlobal('isSecureContext', true);
  vi.stubGlobal('navigator', { serviceWorker: { getRegistration: vi.fn().mockResolvedValue({ active: true, pushManager }) } });
  return subscription;
}
describe('push subscription', () => {
  it('registers only after explicit permission', async () => {
    browser(); vi.mocked(api.post).mockResolvedValue(undefined);
    await enablePush('BA=='); expect(api.post).toHaveBeenCalledWith('/api/me/push/subscribe', { endpoint: 'test', keys: {} });
  });
  it('permission denial sends no subscription', async () => {
    browser('denied'); vi.mocked(api.post).mockClear();
    await expect(enablePush('BA==')).rejects.toThrow('Consenti'); expect(api.post).not.toHaveBeenCalled();
  });
  it('rolls back a browser subscription when server registration fails', async () => {
    const subscription = browser(); vi.mocked(api.post).mockRejectedValue(new Error('Failure'));
    await expect(enablePush('BA==')).rejects.toThrow('Failure'); expect(subscription.unsubscribe).toHaveBeenCalled();
  });
  it('unsubscribes locally even if the server is unreachable', async () => {
    const subscription = browser(); vi.mocked(api.post).mockRejectedValue(new Error('Offline'));
    await expect(disablePush()).rejects.toThrow('Offline'); expect(subscription.unsubscribe).toHaveBeenCalled();
  });
});
