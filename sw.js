// Offline cache for Expy. Bump VERSION when files change.
const VERSION = 'expy-v1.1.0';
const SHELL = ['./', 'index.html', 'manifest.webmanifest', 'fonts/archivo.woff2', 'fonts/plex-mono-400.woff2', 'fonts/plex-mono-500.woff2', 'icons/icon-192.png', 'icons/icon-512.png', 'icons/icon-maskable-512.png'];

self.addEventListener('install', (e) => {
  e.waitUntil(caches.open(VERSION).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting()));
});
self.addEventListener('activate', (e) => {
  e.waitUntil(caches.keys().then((keys) => Promise.all(keys.filter((k) => k !== VERSION).map((k) => caches.delete(k)))).then(() => self.clients.claim()));
});
// Cache first, so the app opens instantly and offline; refresh the page in the background
// so a new version shows on the next launch.
self.addEventListener('fetch', (e) => {
  const req = e.request;
  if (req.method !== 'GET' || new URL(req.url).origin !== self.location.origin) return;
  const isPage = req.mode === 'navigate';
  e.respondWith(caches.open(VERSION).then(async (cache) => {
    const cached = await cache.match(isPage ? 'index.html' : req, { ignoreSearch: true });
    const network = fetch(req).then((res) => {
      if (res && res.ok) cache.put(isPage ? 'index.html' : req, res.clone());
      return res;
    }).catch(() => null);
    if (cached) { e.waitUntil(network); return cached; }
    return (await network) || (isPage ? cache.match('index.html') : Response.error());
  }));
});
