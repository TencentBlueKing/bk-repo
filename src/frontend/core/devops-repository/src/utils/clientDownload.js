const PING_TIMEOUT_MS = 400
const DEFAULT_ACK_TIMEOUT_WARM_MS = 15000
const DEFAULT_PING_WAIT_COLD_MS = 10000
const CLIENT_DISPLAY_NAME = 'BKArtifacts 客户端'

const UPGRADE_FAIL_REASONS = ['version_too_low', 'client_unavailable']

/** HTTPS 远程页访问 127.0.0.1 受 Private Network Access 约束 */
function isHttpsPrivateNetworkPage () {
    return location.protocol === 'https:'
        && location.hostname !== 'localhost'
        && location.hostname !== '127.0.0.1'
}

async function probeLoopbackReachable (port) {
    const controller = new AbortController()
    const timer = setTimeout(() => controller.abort(), PING_TIMEOUT_MS)
    try {
        const response = await fetch(`http://127.0.0.1:${port}/ping`, {
            method: 'OPTIONS',
            mode: 'cors',
            credentials: 'omit',
            signal: controller.signal
        })
        return response.status === 204 || response.status === 403 || response.ok
    } catch {
        return false
    } finally {
        clearTimeout(timer)
    }
}

async function probeClientPingResult (port) {
    const pingUrl = `http://127.0.0.1:${port}/ping`
    let loopbackReachable = false
    let ping = null

    if (isHttpsPrivateNetworkPage()) {
        loopbackReachable = await probeLoopbackReachable(port)
    }

    try {
        const response = await fetchLocal(pingUrl, PING_TIMEOUT_MS)
        if (response.ok) {
            ping = await response.json()
            loopbackReachable = true
        } else if (response.status === 403 || response.status === 404) {
            loopbackReachable = true
        }
    } catch {
        // ponytail: 连接失败时保留 preflight 探测结果
    }

    return { ping, loopbackReachable }
}

export const CLIENT_DOWNLOAD_HANDLED = 'handled'
export const CLIENT_DOWNLOAD_CANCELLED = 'cancelled'
export const CLIENT_DOWNLOAD_FAILED = 'failed'

let activeWaitToken = null

function checkBrowser () {
    const isOpera = !!window.opera || navigator.userAgent.indexOf(' OPR/') >= 0
    const ua = navigator.userAgent.toLowerCase()
    return {
        isOpera,
        isFirefox: typeof InstallTrigger !== 'undefined',
        isSafari: (~ua.indexOf('safari') && !~ua.indexOf('chrome'))
            || Object.prototype.toString.call(window.HTMLElement).indexOf('Constructor') > 0,
        isChrome: !!window.chrome && !isOpera
    }
}

function createHiddenIframe (target, uri) {
    const iframe = document.createElement('iframe')
    iframe.src = uri
    iframe.id = 'hiddenIframe'
    iframe.style.display = 'none'
    target.appendChild(iframe)
    return iframe
}

function getTopWindow () {
    let target = window
    while (target !== target.parent) {
        target = target.parent
    }
    return target
}

function sleep (ms) {
    return new Promise(resolve => setTimeout(resolve, ms))
}

function generateRid () {
    if (typeof crypto !== 'undefined' && crypto.randomUUID) {
        return crypto.randomUUID()
    }
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
        const r = Math.random() * 16 | 0
        const v = c === 'x' ? r : (r & 0x3 | 0x8)
        return v.toString(16)
    })
}

function isConfigPlaceholder (value) {
    return !value || /^__BK_REPO_.*__$/.test(value)
}

function resolveAckTimeoutMs (configValue, defaultMs) {
    if (isConfigPlaceholder(configValue)) return defaultMs
    const parsed = Number(configValue)
    if (!Number.isFinite(parsed) || parsed <= 0) return defaultMs
    return parsed
}

function getAckTimeoutMs () {
    return resolveAckTimeoutMs(
        BK_ARTIFACT_CLIENT_ACK_TIMEOUT_WARM,
        DEFAULT_ACK_TIMEOUT_WARM_MS
    )
}

function getPingWaitColdMs () {
    return resolveAckTimeoutMs(
        BK_ARTIFACT_CLIENT_PING_WAIT_COLD,
        DEFAULT_PING_WAIT_COLD_MS
    )
}

function parseVersionParts (version) {
    return String(version).split('.').map((part) => parseInt(part, 10) || 0)
}

function compareVersion (left, right) {
    const a = parseVersionParts(left)
    const b = parseVersionParts(right)
    const len = Math.max(a.length, b.length)
    for (let i = 0; i < len; i++) {
        const diff = (a[i] || 0) - (b[i] || 0)
        if (diff !== 0) return diff
    }
    return 0
}

async function fetchLocal (url, timeoutMs = 150) {
    const controller = new AbortController()
    const timer = setTimeout(() => controller.abort(), timeoutMs)
    try {
        return await fetch(url, {
            signal: controller.signal,
            mode: 'cors',
            credentials: 'omit'
        })
    } finally {
        clearTimeout(timer)
    }
}

async function probeClientPing (port) {
    const { ping } = await probeClientPingResult(port)
    return ping
}

function resolveNoPingFailReason (loopbackReachable) {
    if (isHttpsPrivateNetworkPage() && !loopbackReachable) {
        return 'loopback_unreachable'
    }
    return 'client_unavailable'
}

async function probeClientStatus (statusUrl) {
    try {
        const response = await fetchLocal(statusUrl)
        if (!response.ok) return null
        return await response.json()
    } catch (e) {
        return null
    }
}

function isClientPingSupported (ping, batch) {
    if (!ping || typeof ping.version !== 'string') return false
    const caps = Array.isArray(ping.capabilities) ? ping.capabilities : []
    if (!caps.includes('deeplink-status-v1')) return false
    if (batch && !caps.includes('batch-download-v1')) return false
    if (!batch && !caps.includes('download-v1')) return false
    if (!isConfigPlaceholder(BK_ARTIFACT_MIN_CLIENT_VERSION)
        && compareVersion(ping.version, BK_ARTIFACT_MIN_CLIENT_VERSION) < 0) {
        return false
    }
    return true
}

function getClientRejectMessage (ping, batch) {
    const caps = Array.isArray(ping?.capabilities) ? ping.capabilities : []
    const min = BK_ARTIFACT_MIN_CLIENT_VERSION
    if (ping?.version && !isConfigPlaceholder(min)
        && compareVersion(ping.version, min) < 0) {
        return `${CLIENT_DISPLAY_NAME}版本过低（当前 ${ping.version}，需要 ${min} 及以上），请升级后重试`
    }
    if (!caps.includes('deeplink-status-v1')) {
        if (ping?.version) {
            return `${CLIENT_DISPLAY_NAME}版本过低（当前 ${ping.version}），请升级后重试`
        }
        return `当前${CLIENT_DISPLAY_NAME}版本过低，不支持网页拉起下载，请升级后重试`
    }
    if (batch) {
        return `当前${CLIENT_DISPLAY_NAME}版本过低，不支持批量网页下载，请升级后重试`
    }
    return `当前${CLIENT_DISPLAY_NAME}版本过低或不支持网页拉起下载，请升级至最新版本后重试`
}

function getFailedReasonMessage (reason, ping, batch) {
    if (reason === 'version_too_low') {
        return getClientRejectMessage(ping, batch)
    }
    switch (reason) {
    case 'not_logged_in':
        return `${CLIENT_DISPLAY_NAME}未登录，请登录客户端后重试`
    case 'public_mode':
        return `${CLIENT_DISPLAY_NAME}处于公开模式，请切换账号模式后重试`
    case 'invalid_url':
        return '下载链接无效，请刷新页面后重试'
    case 'forbidden':
        return '当前账号无下载权限，请确认客户端登录账号与网页一致'
    case 'invalid_save_path':
        return `${CLIENT_DISPLAY_NAME}未配置有效下载路径，请在设置中配置后重试`
    case 'client_unavailable':
        return `未检测到支持网页拉起下载的${CLIENT_DISPLAY_NAME}，请安装或升级至最新版本后重试`
    case 'loopback_unreachable':
        return `无法连接本机${CLIENT_DISPLAY_NAME}。请确认已安装最新版本；`
            + '若已安装，请在浏览器中允许网页访问本地网络（Private Network Access），'
            + '或改用下方「浏览器下载」'
    case 'timeout':
        if (isHttpsPrivateNetworkPage()) {
            return `无法确认${CLIENT_DISPLAY_NAME}是否已完成下载。请查看客户端传输列表；`
                + '若已有任务可关闭此窗口，否则请检查登录状态、浏览器本地网络权限，或改用浏览器下载'
        }
        return `${CLIENT_DISPLAY_NAME}未完成下载任务，请确认已登录客户端后重试`
    default:
        return `${CLIENT_DISPLAY_NAME}无法完成下载，请重试`
    }
}

export function getClientInstallUrl () {
    if (isConfigPlaceholder(BK_ARTIFACT_CLIENT_INSTALL_URL)) {
        return ''
    }
    return BK_ARTIFACT_CLIENT_INSTALL_URL
}

export function abortActiveClientDownload () {
    activeWaitToken = null
}

function buildDownloadContext (context) {
    const subPath = context.subPath === '/' ? '/' : context.subPath
    return { ...context, subPath }
}

function buildSchemeDownloadUrl (row, context, rid) {
    const { projectId, repoName } = buildDownloadContext(context)
    const encodedPath = encodeURIComponent(row.fullPath)
    const params = new URLSearchParams({ rid })
    return `${BK_ARTIFACT_SCHEME}download/${projectId}/${repoName}${encodedPath}?${params.toString()}`
}

function buildSchemeBatchDownloadUrl (paths, context, rid) {
    const { subPath, origin, projectId, repoName } = buildDownloadContext(context)
    const batchUrl = `${origin}${subPath}web/generic/batch/${projectId}/${repoName}`
    const params = new URLSearchParams({
        action: 'batch_download',
        url: batchUrl,
        paths: JSON.stringify(paths),
        rid
    })
    return `${BK_ARTIFACT_SCHEME}?${params.toString()}`
}

function launchClientScheme (schemeUrl) {
    const browser = checkBrowser()
    if (browser.isFirefox || browser.isSafari || browser.isChrome) {
        let iframe = document.querySelector('#hiddenIframe')
        if (!iframe) {
            iframe = createHiddenIframe(document.body, 'about:blank')
        }
        try {
            iframe.contentWindow.location.href = schemeUrl
        } catch (e) {
            // ponytail: 唤起失败由用户重试或改用浏览器下载
        }
        return
    }
    getTopWindow().location.href = schemeUrl
}

function isClientHandledState (state) {
    return state === 'queued' || state === 'downloading'
}

export function isClientDownloadUpgradeReason (reason) {
    return UPGRADE_FAIL_REASONS.includes(reason)
}

export function shouldShowInstallOnClientDownloadFail (reason) {
    return reason === 'loopback_unreachable' || reason === 'client_unavailable'
}

async function waitClientDownload (rid, { port, waitToken, batch, ping, running }) {
    const interval = Number(BK_ARTIFACT_CLIENT_ACK_INTERVAL) || 200
    const queuedTimeoutMs = getAckTimeoutMs()
    const pingWaitColdMs = getPingWaitColdMs()
    const statusUrl = `http://127.0.0.1:${port}/deeplink/status?rid=${encodeURIComponent(rid)}`
    let lastPing = ping || null
    let queuedWaitStartedAt = lastPing ? Date.now() : null
    const startedAt = Date.now()
    let loopCount = 0

    while (true) {
        if (waitToken && activeWaitToken !== waitToken) {
            return { outcome: 'cancelled', ping: lastPing }
        }

        if (!lastPing) {
            if (Date.now() - startedAt > pingWaitColdMs) {
                const probeResult = await probeClientPingResult(port)
                if (probeResult.ping) {
                    lastPing = probeResult.ping
                    if (!isClientPingSupported(lastPing, batch)) {
                        return { outcome: 'failed', reason: 'version_too_low', ping: lastPing }
                    }
                    queuedWaitStartedAt = Date.now()
                } else {
                    return {
                        outcome: 'failed',
                        reason: resolveNoPingFailReason(probeResult.loopbackReachable),
                        ping: null
                    }
                }
            }
            if (loopCount > 0 && loopCount % 5 === 0) {
                const probeResult = await probeClientPingResult(port)
                if (probeResult.ping) {
                    lastPing = probeResult.ping
                    if (!isClientPingSupported(lastPing, batch)) {
                        return { outcome: 'failed', reason: 'version_too_low', ping: lastPing }
                    }
                    queuedWaitStartedAt = Date.now()
                }
            }
        } else {
            if (Date.now() - queuedWaitStartedAt > queuedTimeoutMs) {
                if (!isClientPingSupported(lastPing, batch)) {
                    return { outcome: 'failed', reason: 'version_too_low', ping: lastPing }
                }
                return { outcome: 'failed', reason: 'timeout', ping: lastPing }
            }

            const status = await probeClientStatus(statusUrl)
            if (isClientHandledState(status?.state)) {
                return { outcome: 'handled', ping: lastPing }
            }
            if (status?.state === 'failed') {
                return { outcome: 'failed', reason: status.reason, ping: lastPing }
            }
        }

        loopCount += 1
        await sleep(interval)
    }
}

export async function prepareClientDownload ({ row, paths, context }) {
    const batch = !!paths?.length
    const port = Number(BK_ARTIFACT_LOCAL_PORT)
    const ping = await probeClientPing(port)

    if (ping && !isClientPingSupported(ping, batch)) {
        return {
            blocked: true,
            message: getClientRejectMessage(ping, batch),
            row,
            paths
        }
    }

    const rid = generateRid()
    const schemeUrl = batch
        ? buildSchemeBatchDownloadUrl(paths, context, rid)
        : buildSchemeDownloadUrl(row, context, rid)

    return {
        blocked: false,
        running: !!ping,
        loopbackUncertain: !ping && isHttpsPrivateNetworkPage(),
        rid,
        schemeUrl,
        ping,
        batch,
        row,
        paths
    }
}

export async function startClientDownloadWait (pending) {
    activeWaitToken = {}
    const waitToken = activeWaitToken
    const port = Number(BK_ARTIFACT_LOCAL_PORT)

    launchClientScheme(pending.schemeUrl)
    try {
        const outcome = await waitClientDownload(pending.rid, {
            port,
            waitToken,
            batch: pending.batch,
            ping: pending.ping,
            running: pending.running
        })
        if (outcome.outcome === 'handled') {
            return { status: CLIENT_DOWNLOAD_HANDLED }
        }
        if (outcome.outcome === 'cancelled') {
            return { status: CLIENT_DOWNLOAD_CANCELLED }
        }
        return {
            status: CLIENT_DOWNLOAD_FAILED,
            message: getFailedReasonMessage(outcome.reason, outcome.ping, pending.batch),
            reason: outcome.reason
        }
    } finally {
        if (activeWaitToken === waitToken) {
            activeWaitToken = null
        }
    }
}
