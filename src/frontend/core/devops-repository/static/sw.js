const cacheName = 'CPack-v1'

// Installing Service Worker
self.addEventListener('install', e => {
    e.waitUntil(
        (async () => {
            const cache = await caches.open(cacheName)
            await cache.addAll([
                '/ui/fonts/bk_icons_linear.eot',
                '/ui/fonts/bk_icons_linear.ttf',
                '/ui/fonts/bk_icons_linear.woff'
            ])
        })()
    )
})

// Fetching content using Service Worker
// self.addEventListener('fetch', e => {
//     e.respondWith(
//         (async () => {
//             const r = await caches.match(e.request)
//             console.log(`[Service Worker] Fetching resource: ${e.request.url}`)
//             if (r) return r
//             const response = await fetch(e.request)
//             const cache = await caches.open(cacheName)
//             console.log(
//                 `[Service Worker] Caching new resource: ${e.request.url}`
//             )
//             cache.put(e.request, response.clone())
//             return response
//         })()
//     )
// })
