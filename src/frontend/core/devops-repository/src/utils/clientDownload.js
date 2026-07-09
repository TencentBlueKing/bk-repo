// ponytail: 仅 blur 判定协议框；无协议框时持续轮询至绝对超时；focus 后 5s 宽限
const DIALOG_IDLE_MS = 30000
const DIALOG_GRACE_MS = 5000
const ABSOLUTE_MAX_MS = 60000
const DIALOG_POLL_MS = 3000

let activeWaitToken = null

function checkBrowser () {
    const isOpera = !!window.opera || navigator.userAgent.indexOf(' OPR/') >= 0
    const ua = navigator.userAgent.toLowerCase()
    return {
        isOpera,
        isFirefox: typeof InstallTrigger !== 'undefined',
        isSafari: (~ua.indexOf('safari') && !~ua.indexOf('chrome'))
            || Object.prototype.toString.call(window.HTMLElement).indexOf('Constructor') > 0,
        isIOS: /iPad|iPhone|iPod/.test(navigator.userAgent) && !window.MSStream,
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

async function fetchLocal (url) {
    const controller = new AbortController()
    const timer = setTimeout(() => controller.abort(), 150)
    try {
        return await fetch(url, { signal: controller.signal })
    } finally {
        clearTimeout(timer)
    }
}

async function pollAck (ackUrl) {
    try {
        const response = await fetchLocal(ackUrl)
        return response.ok
    } catch (e) {
        return false
    }
}

function cancelActiveWait () {
    activeWaitToken = null
}

function buildDownloadContext (context) {
    const subPath = context.subPath === '/' ? '/' : context.subPath
    return { ...context, subPath }
}

/**
 * @param {Object} row - 制品行数据，含 fullPath
 * @param {Object} context - { projectId, repoName, origin, subPath }
 * @param {string} rid
 * @returns {string} scheme URL
 */
export function buildSchemeDownloadUrl (row, context, rid) {
    const { subPath, origin, projectId, repoName } = buildDownloadContext(context)
    const genericUrl = `${origin}${subPath}web/generic/${projectId}/${repoName}${row.fullPath}`
    const params = new URLSearchParams({
        action: 'download',
        url: genericUrl,
        rid
    })
    return `${BK_ARTIFACT_SCHEME}?${params.toString()}`
}

/**
 * @param {string[]} paths
 * @param {Object} context - { projectId, repoName, origin, subPath }
 * @param {string} rid
 * @returns {string} scheme URL
 */
export function buildSchemeBatchDownloadUrl (paths, context, rid) {
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

/**
 * @param {string} schemeUrl
 */
export function launchScheme (schemeUrl) {
    const browser = checkBrowser()
    if (browser.isFirefox || browser.isSafari || browser.isChrome) {
        let iframe = document.querySelector('#hiddenIframe')
        if (!iframe) {
            iframe = createHiddenIframe(document.body, 'about:blank')
        }
        try {
            iframe.contentWindow.location.href = schemeUrl
        } catch (e) {
            // ponytail: 唤起失败由后续 ack/超时降级，不向上抛
        }
        return
    }
    getTopWindow().location.href = schemeUrl
}

/**
 * 无协议框 → 持续高频轮询至绝对超时
 * 有协议框未操作 → 低频探测，30s 后降级
 * 用户 focus（打开/取消）→ 5s 宽限，无 ack 降级
 *
 * @param {string} rid
 * @param {{ port: number, timeout: number, interval: number, waitToken: object }} options
 * @returns {Promise<boolean>}
 */
export async function waitClientAck (rid, options) {
    const port = Number(options.port)
    const interval = Number(options.interval) || 200
    const quickMs = Number(options.timeout) || 3000
    const waitToken = options.waitToken
    const ackUrl = `http://127.0.0.1:${port}/deeplink/ack?rid=${encodeURIComponent(rid)}`

    const startedAt = Date.now()
    const absoluteUntil = startedAt + ABSOLUTE_MAX_MS
    const quickUntil = startedAt + quickMs

    let dialogSeen = false
    let dialogSeenAt = 0
    let userDecided = false
    let userDecidedAt = 0
    let lastDialogPollAt = 0

    const topWin = getTopWindow()
    const onBlur = () => {
        if (!dialogSeen) {
            dialogSeen = true
            dialogSeenAt = Date.now()
        }
    }
    const onFocus = () => {
        if (dialogSeen) {
            userDecided = true
            userDecidedAt = Date.now()
        }
    }
    topWin.addEventListener('blur', onBlur)
    topWin.addEventListener('focus', onFocus)

    try {
        while (Date.now() < absoluteUntil) {
            if (waitToken && activeWaitToken !== waitToken) {
                return false
            }

            const now = Date.now()

            if (userDecided && now - userDecidedAt >= DIALOG_GRACE_MS) {
                return false
            }
            if (dialogSeen && !userDecided && now - dialogSeenAt >= DIALOG_IDLE_MS) {
                return false
            }

            const inQuickPhase = now < quickUntil
            const inGracePhase = userDecided && now - userDecidedAt < DIALOG_GRACE_MS
            const noDialog = !dialogSeen
            const dialogWaiting = dialogSeen && !userDecided
            const shouldPoll = inQuickPhase || inGracePhase || noDialog
                || (dialogWaiting && (!lastDialogPollAt || now - lastDialogPollAt >= DIALOG_POLL_MS))

            if (shouldPoll) {
                if (dialogWaiting) {
                    lastDialogPollAt = now
                }
                if (await pollAck(ackUrl)) {
                    return true
                }
            }

            const fastPoll = inQuickPhase || inGracePhase || noDialog
            await sleep(fastPoll ? interval : 200)
        }
        return false
    } finally {
        topWin.removeEventListener('blur', onBlur)
        topWin.removeEventListener('focus', onFocus)
    }
}

/**
 * @param {Object} row - 制品行数据，含 fullPath
 * @param {Object} context - { projectId, repoName, origin, subPath }
 * @returns {Promise<boolean>} true=客户端已接管，false=应降级
 */
export async function tryClientDownload (row, context) {
    const rid = generateRid()
    const schemeUrl = buildSchemeDownloadUrl(row, context, rid)
    cancelActiveWait()
    const waitToken = {}
    activeWaitToken = waitToken
    launchScheme(schemeUrl)
    try {
        return await waitClientAck(rid, {
            port: Number(BK_ARTIFACT_LOCAL_PORT),
            timeout: Number(BK_ARTIFACT_CLIENT_ACK_TIMEOUT),
            interval: Number(BK_ARTIFACT_CLIENT_ACK_INTERVAL),
            waitToken
        })
    } finally {
        if (activeWaitToken === waitToken) {
            activeWaitToken = null
        }
    }
}

/**
 * @param {string[]} paths
 * @param {Object} context - { projectId, repoName, origin, subPath }
 * @returns {Promise<boolean>} true=客户端已接管，false=应降级
 */
export async function tryClientBatchDownload (paths, context) {
    const rid = generateRid()
    const schemeUrl = buildSchemeBatchDownloadUrl(paths, context, rid)
    cancelActiveWait()
    const waitToken = {}
    activeWaitToken = waitToken
    launchScheme(schemeUrl)
    try {
        return await waitClientAck(rid, {
            port: Number(BK_ARTIFACT_LOCAL_PORT),
            timeout: Number(BK_ARTIFACT_CLIENT_ACK_TIMEOUT),
            interval: Number(BK_ARTIFACT_CLIENT_ACK_INTERVAL),
            waitToken
        })
    } finally {
        if (activeWaitToken === waitToken) {
            activeWaitToken = null
        }
    }
}
