const cacheName = 'CPack-v1'

let BK_STATIC_URL = '/ui/' // 默认值
let configReady = null // 用于等待配置的Promise

// 1. 创建一个“配置就绪”的Promise
const configReadyPromise = new Promise((resolve) => {
    configReady = resolve
})

self.addEventListener('message', (event) => {
    console.log('Service Worker 收到消息:', event.data)
    if (event.data && event.data.type === 'SET_CONFIG') {
        BK_STATIC_URL = event.data.config.BK_STATIC_URL
        console.log('配置已更新，BK_STATIC_URL:', BK_STATIC_URL)
        if (configReady) {
            configReady()
            configReady = null
        }
    }
});

self.addEventListener('install', (event) => {
    console.log('Service Worker 安装开始，BK_STATIC_URL（初始值）:', BK_STATIC_URL)

    event.waitUntil(
        (async () => {
            await configReadyPromise

            // 这里开始执行原本的缓存逻辑
            const cacheName = 'my-cache-v1'
            const cache = await caches.open(cacheName)
            const urlsToCache = [
                BK_STATIC_URL + 'fonts/bk_icons_linear.eot',
                BK_STATIC_URL + 'fonts/bk_icons_linear.ttf',
                BK_STATIC_URL + 'fonts/bk_icons_linear.woff'
            ]
            await cache.addAll(urlsToCache)
            console.log('字体文件缓存完成')
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
