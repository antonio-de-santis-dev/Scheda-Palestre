import { http } from '../shared/api/http';
export const pushSupported = () => window.isSecureContext && 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window;
export async function disablePush() {
  if (!pushSupported()) return;
  const registration = await navigator.serviceWorker.getRegistration();
  const subscription = await registration?.pushManager.getSubscription();
  if (!subscription) return;
  try { await http.post('/api/me/push/unsubscribe', { endpoint: subscription.endpoint }); }
  finally { await subscription.unsubscribe(); }
}
export async function enablePush(publicKey: string) {
  const permission = await Notification.requestPermission();
  if (permission !== 'granted') throw new Error('Consenti le notifiche nelle impostazioni del browser per attivarle.');
  const registration = await navigator.serviceWorker.getRegistration();
  if (!registration?.active) throw new Error('Attendi il caricamento dell’app e riprova.');
  const bytes = Uint8Array.from(atob(publicKey.replace(/-/g, '+').replace(/_/g, '/')), (char) => char.charCodeAt(0));
  const subscription = await registration.pushManager.getSubscription() ?? await registration.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: bytes });
  try { await http.post('/api/me/push/subscribe', subscription.toJSON()); }
  catch (error) { await subscription.unsubscribe(); throw error; }
}
