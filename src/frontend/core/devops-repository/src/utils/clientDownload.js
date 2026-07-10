// ponytail: 客户端端口可达则持续等 ack；仅端口不可达 quickMs 超时或用户回焦放弃时降级
const DIALOG_GRACE_MS = 5000
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

async function probeClient (ackUrl) {
    try {
        const response = await fetchLocal(ackUrl)
        return { reachable: true, acked: response.ok }
    } catch (e) {
        return { reachable: false, acked: false }
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
    const transPath = encodeURIComponent(row.fullPath)
    const genericUrl = `${origin}${subPath}web/generic/${projectId}/${repoName}/${transPath}`
        + `?download=true&x-bkrepo-project-id=${projectId}`
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

function bindDialogListeners (state) {
    const markDialogSeen = () => {
        state.dialogSeen = true
    }
    const onBlur = () => markDialogSeen()
    const onFocus = () => {
        if (state.dialogSeen) {
            state.userDecided = true
            state.userDecidedAt = Date.now()
        }
    }
    const onVisibilityChange = () => {
        if (document.hidden) {
            markDialogSeen()
        }
    }
    const wins = [window]
    const topWin = getTopWindow()
    if (topWin !== window) {
        wins.push(topWin)
    }
    for (const win of wins) {
        win.addEventListener('blur', onBlur)
        win.addEventListener('focus', onFocus)
    }
    document.addEventListener('visibilitychange', onVisibilityChange)
    return () => {
        for (const win of wins) {
            win.removeEventListener('blur', onBlur)
            win.removeEventListener('focus', onFocus)
        }
        document.removeEventListener('visibilitychange', onVisibilityChange)
    }
}

/**
 * ack 成功 → 客户端已接管
 * 客户端端口不可达且超过 quickMs → 降级（未安装）
 * 用户回焦后 grace 内无 ack → 降级（放弃/取消）
 * 客户端端口可达、用户仍在操作 → 持续等待，不降级
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
    const quickUntil = startedAt + quickMs

    const state = {
        dialogSeen: false,
        userDecided: false,
        userDecidedAt: 0
    }
    let clientReachable = false
    let lastSlowPollAt = 0
    const unbindDialogListeners = bindDialogListeners(state)

    try {
        while (true) {
            if (waitToken && activeWaitToken !== waitToken) {
                return false
            }

            const now = Date.now()
            const { userDecided, userDecidedAt } = state

            if (userDecided && now - userDecidedAt >= DIALOG_GRACE_MS) {
                return false
            }

            const inQuickPhase = now < quickUntil
            const inGracePhase = userDecided && now - userDecidedAt < DIALOG_GRACE_MS
            const slowWait = clientReachable && !userDecided
            const shouldPoll = inQuickPhase || inGracePhase || !clientReachable
                || (slowWait && (!lastSlowPollAt || now - lastSlowPollAt >= DIALOG_POLL_MS))

            if (shouldPoll) {
                if (slowWait) {
                    lastSlowPollAt = now
                }
                const { reachable, acked } = await probeClient(ackUrl)
                if (reachable) {
                    clientReachable = true
                }
                if (acked) {
                    return true
                }
                if (!clientReachable && now - startedAt >= quickMs) {
                    return false
                }
            }

            const fastPoll = inQuickPhase || inGracePhase || !clientReachable
            await sleep(fastPoll ? interval : DIALOG_POLL_MS)
        }
    } finally {
        unbindDialogListeners()
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
