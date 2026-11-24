// Service Worker: hält die App für den Offline-Betrieb vor.
// Version und Dateiliste werden beim Bauen eingesetzt (Gradle-Task "webApp").
var CACHE = '__VERSION__';
var DATEIEN = [/*DATEIEN*/];

self.addEventListener('install', function (e) {
  e.waitUntil(caches.open(CACHE).then(function (c) { return c.addAll(DATEIEN); }).then(function () { return self.skipWaiting(); }));
});

self.addEventListener('activate', function (e) {
  e.waitUntil(caches.keys().then(function (namen) {
    return Promise.all(namen.filter(function (n) { return n !== CACHE; }).map(function (n) { return caches.delete(n); }));
  }).then(function () { return self.clients.claim(); }));
});

self.addEventListener('fetch', function (e) {
  if (e.request.method !== 'GET') return;
  e.respondWith(caches.match(e.request, { ignoreSearch: true }).then(function (treffer) {
    return treffer || fetch(e.request);
  }));
});
