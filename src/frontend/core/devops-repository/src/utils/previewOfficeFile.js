import axios from 'axios'

const BASE_URL = `${location.origin}` + window.BK_SUBPATH

/**
 * 预览专用 sessionStorage key（需求 5.6）。
 * 仅在预览组件入口写入；不写 cookie，避免跨 tab 串号。
 */
export const PREVIEW_TOKEN_STORAGE_KEY = 'previewToken'

/**
 * 从当前 URL 读取 ?token=...，写入 sessionStorage（如有则覆盖）。
 * 在预览组件 created() 中调用，幂等。
 *
 * 重要：若当前 URL **未携带 token**，必须清空 sessionStorage 中的旧 token，
 * 否则上一次预览残留的 token 会被自动注入到当前请求中，导致：
 *   1) 跨文件越权：A 文件的 token 被复用到 B 文件，绕过路径级 ACL；
 *   2) 登录态降权：已登录用户的请求被误打 Temporary 头，反而走 token 校验链路；
 *   3) 与后端 TemporaryTokenAuthHandler 的严格路径校验冲突，正常文件 403/404。
 */
export function capturePreviewTokenFromUrl () {
    try {
        const search = window.location.search || ''
        const params = new URLSearchParams(search)
        const token = params.get('token')
        if (token && token.length > 0) {
            window.sessionStorage.setItem(PREVIEW_TOKEN_STORAGE_KEY, token)
        } else {
            // URL 未带 token：清空残留，确保本次预览走登录态而非旧 token
            window.sessionStorage.removeItem(PREVIEW_TOKEN_STORAGE_KEY)
        }
    } catch (e) {
        // sessionStorage 不可用时静默失败，回退到登录态行为
    }
}

/**
 * 从 sessionStorage 读取预览 token，无则返回 ''。
 */
export function readPreviewToken () {
    try {
        return window.sessionStorage.getItem(PREVIEW_TOKEN_STORAGE_KEY) || ''
    } catch (e) {
        return ''
    }
}

/**
 * 预览专用 axios 实例 —— 仅作用于本文件中的 5 个调用；不污染其他模块（需求 5.10）。
 *
 * 拦截器行为（需求 5.7）：
 *  - 若 sessionStorage 存在 previewToken，则【双通道注入】：
 *      1) 注入 `Authorization: Temporary <token>` 头；
 *      2) 同时把 `token` 注入到 query 参数中，作为兜底。
 *    原因：网关（vhosts/bkrepo.web.conf）会执行
 *        `proxy_set_header authorization "$bkrepo_authorization";`
 *    这会把前端原始的 Authorization 头**覆盖**成网关计算出的内部值，
 *    导致后端 TemporaryTokenAuthHandler 拿不到 Temporary token。
 *    而 query string 在 `proxy_pass http://$target/$2;` 时由 nginx 自动透传（$args），
 *    所以使用 `?token=xxx` 兜底是稳定可靠的。
 *  - 若不存在 token，则保持现有 withCredentials + xsrfCookieName 的登录态行为（与改造前一致）。
 */
const previewAxios = axios.create()
previewAxios.interceptors.request.use((config) => {
    const token = readPreviewToken()
    if (token) {
        // 1) 头部注入（部分网关链路可能保留）
        config.headers = config.headers || {}
        config.headers['Authorization'] = `Temporary ${token}`
        // 2) query 参数兜底注入（避免被网关 proxy_set_header 覆盖头部导致丢失）
        config.params = config.params || {}
        if (!config.params.token) {
            config.params.token = token
        }
    }
    return config
})

/**
 * 拼接预览相关的兜底直链（需求 5.8）：
 * 仅用于 PDF.js worker 二次拉取等无法走 axios 的场景；
 * 当前 axios+blob 路径不要调用此函数，避免重复拼接 ?token=。
 */
export function appendPreviewTokenToUrl (url) {
    const token = readPreviewToken()
    if (!token) return url
    const sep = url.indexOf('?') === -1 ? '?' : '&'
    return `${url}${sep}token=${encodeURIComponent(token)}`
}

// 后端返回的类型为 application/octet-stream;charset=UTF-8
export function customizePreviewOfficeFile (projectId, repoName, fullPath) {
    const url = projectId + '/' + repoName + fullPath
    return previewAxios({
        url: BASE_URL + 'web/generic/' + url,
        method: 'get',
        // 注意，此处需要设置下载的文件的返回类型为二进制，即 blob
        responseType: 'blob',
        withCredentials: true,
        xsrfCookieName: (MODE_CONFIG === 'ci' || MODE_CONFIG === 'saas') ? 'bk_token' : 'bkrepo_ticket', // 注入csrfToken
        xsrfHeaderName: 'X-CSRFToken' // 注入csrfToken
    })
}

export function getPreviewLocalOfficeFileInfo (projectId, repoName, fullPath) {
    const url = projectId + '/' + repoName + fullPath
    return previewAxios({
        url: BASE_URL + 'web/preview/api/file/getPreviewInfo/' + url,
        method: 'get',
        withCredentials: true,
        xsrfCookieName: (MODE_CONFIG === 'ci' || MODE_CONFIG === 'saas') ? 'bk_token' : 'bkrepo_ticket', // 注入csrfToken
        xsrfHeaderName: 'X-CSRFToken' // 注入csrfToken
    })
}

export function getPreviewRemoteOfficeFileInfo (extraParam) {
    return previewAxios({
        url: BASE_URL + 'web/preview/api/file/getPreviewInfo/',
        method: 'get',
        params: {
            extraParam: extraParam
        },
        withCredentials: true,
        xsrfCookieName: (MODE_CONFIG === 'ci' || MODE_CONFIG === 'saas') ? 'bk_token' : 'bkrepo_ticket', // 注入csrfToken
        xsrfHeaderName: 'X-CSRFToken' // 注入csrfToken
    })
}

export function customizePreviewLocalOfficeFile (projectId, repoName, fullPath) {
    const url = projectId + '/' + repoName + fullPath
    return previewAxios({
        url: BASE_URL + 'web/preview/api/file/onlinePreview/' + url,
        method: 'get',
        // 注意，此处需要设置下载的文件的返回类型为二进制，即 blob
        responseType: 'blob',
        withCredentials: true,
        xsrfCookieName: (MODE_CONFIG === 'ci' || MODE_CONFIG === 'saas') ? 'bk_token' : 'bkrepo_ticket', // 注入csrfToken
        xsrfHeaderName: 'X-CSRFToken' // 注入csrfToken
    })
}

export function customizePreviewRemoteOfficeFile (extraParam) {
    return previewAxios({
        url: BASE_URL + 'web/preview/api/file/onlinePreview/',
        method: 'get',
        params: {
            extraParam: extraParam
        },
        // 注意，此处需要设置下载的文件的返回类型为二进制，即 blob
        responseType: 'blob',
        withCredentials: true,
        xsrfCookieName: (MODE_CONFIG === 'ci' || MODE_CONFIG === 'saas') ? 'bk_token' : 'bkrepo_ticket', // 注入csrfToken
        xsrfHeaderName: 'X-CSRFToken' // 注入csrfToken
    })
}
