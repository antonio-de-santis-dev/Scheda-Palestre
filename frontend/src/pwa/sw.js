/* Static resources only. Never store authenticated HTML/API data in shared browser caches. */
const CACHE = 'gymplanner-__BUILD_ID__';
self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(CACHE).then((cache) => cache.add('/offline.html')).then(() => self.skipWaiting()));
});
self.addEventListener('activate', (event) => {
  event.waitUntil(caches.keys().then((keys) => Promise.all(keys.filter((key) => key.startsWith('gymplanner-') && key !== CACHE).map((key) => caches.delete(key)))).then(() => self.clients.claim()));
});
self.addEventListener('fetch', (event) => {
  const request = event.request; const url = new URL(request.url);
  if (request.method !== 'GET' || url.origin !== self.location.origin || url.pathname.startsWith('/api/') || url.pathname.startsWith('/actuator/')) return;
  if (request.mode === 'navigate') {
    event.respondWith(fetch(request).catch(() => caches.match('/offline.html'))); return;
  }
  if (url.pathname.startsWith('/assets/')) {
    event.respondWith(caches.match(request).then((cached) => cached || fetch(request).then((response) => {
      if (response.ok && response.type === 'basic') { const copy = response.clone(); event.waitUntil(caches.open(CACHE).then((cache) => cache.put(request, copy))); }
      return response;
    })));
  }
});
self.addEventListener('push', (event) => {
  // Ignore server-controlled URLs/text: keep lock-screen content generic and navigation same-origin.
  event.waitUntil(self.registration.showNotification('GymPlanner', {
    body: 'Il recupero è terminato. Puoi riprendere l’allenamento.', icon: '/icons/icon-192.png', tag: 'gymplanner-rest',
  }));
});
self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  event.waitUntil(self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then(async (clients) => {
    const client = clients.find((item) => new URL(item.url).origin === self.location.origin);
    if (client) { await client.navigate('/app/today'); return client.focus(); }
    return self.clients.openWindow('/app/today');
  }));
});
