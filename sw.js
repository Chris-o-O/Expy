// Offline cache for Expy. Bump VERSION whenever a file changes: phones then install the new
// version in the background, and the app offers a one-tap "Mettre à jour".
const VERSION = 'expy-v1.8.1';
const SHELL = ['./', 'index.html', 'manifest.webmanifest', 'fonts/bricolage.woff2', 'icons/icon-192.png', 'icons/icon-512.png', 'icons/icon-maskable-512.png'];

self.addEventListener('install', (e) => {
  // Fetch fresh copies (bypass the HTTP cache). No skipWaiting here: the new version waits for the user's tap.
  e.waitUntil(caches.open(VERSION).then((c) => c.addAll(SHELL.map((u) => new Request(u, { cache: 'reload' })))));
});
self.addEventListener('message', (e) => { if (e.data && e.data.type === 'SKIP_WAITING') self.skipWaiting(); });
self.addEventListener('activate', (e) => {
  e.waitUntil(caches.keys().then((keys) => Promise.all(keys.filter((k) => k !== VERSION).map((k) => caches.delete(k)))).then(() => self.clients.claim()));
});
// Cache first: the app opens instantly and offline, always on the version this worker installed.
self.addEventListener('fetch', (e) => {
  const req = e.request;
  if (req.method !== 'GET' || new URL(req.url).origin !== self.location.origin) return;
  const isPage = req.mode === 'navigate';
  e.respondWith(caches.open(VERSION).then(async (cache) => {
    const cached = await cache.match(isPage ? 'index.html' : req, { ignoreSearch: true });
    if (cached) return cached;
    try {
      const res = await fetch(req);
      if (res && res.ok) cache.put(isPage ? 'index.html' : req, res.clone());
      return res;
    } catch (err) {
      return isPage ? (await cache.match('index.html')) || Response.error() : Response.error();
    }
  }));
});
