import { XMindEmbedViewer } from 'xmind-embed-viewer'

function viewportStyles () {
    return {
        width: `${Math.max(window.innerWidth, 1)}px`,
        height: `${Math.max(window.innerHeight, 1)}px`,
        border: '0',
        display: 'block',
        position: 'absolute',
        left: '0',
        top: '0'
    }
}

function getIframe (viewer) {
    try {
        return viewer && viewer.iframeController && viewer.iframeController.getIframe
            ? viewer.iframeController.getIframe()
            : null
    } catch (e) {
        return null
    }
}

function forceIframeFullscreen (viewer) {
    const iframe = getIframe(viewer)
    if (!iframe) {
        return
    }
    const width = Math.max(window.innerWidth, 1)
    const height = Math.max(window.innerHeight, 1)
    iframe.setAttribute('width', String(width))
    iframe.setAttribute('height', String(height))
    iframe.style.cssText = [
        `width:${width}px`,
        `height:${height}px`,
        'border:0',
        'display:block',
        'position:absolute',
        'left:0',
        'top:0',
        'max-width:none',
        'max-height:none'
    ].join(';')
}

function safeFit (viewer) {
    forceIframeFullscreen(viewer)
    try {
        if (typeof viewer.setStyles === 'function') {
            viewer.setStyles(viewportStyles())
        }
    } catch (e) {
        // ignore
    }
    try {
        if (typeof viewer.setFitMap === 'function') {
            // setFitMap returns a Promise via channel emit; keep calling until view settles
            const result = viewer.setFitMap()
            if (result && typeof result.catch === 'function') {
                result.catch(() => {})
            }
        }
    } catch (e) {
        // ignore
    }
}

function startFitLoop (viewer) {
    if (viewer.__bkrepoFitTimer) {
        clearInterval(viewer.__bkrepoFitTimer)
    }
    let ticks = 0
    // Keep fitting for a few seconds: embed canvas often lays out after map-ready.
    viewer.__bkrepoFitTimer = setInterval(() => {
        ticks += 1
        safeFit(viewer)
        if (ticks >= 20) {
            clearInterval(viewer.__bkrepoFitTimer)
            viewer.__bkrepoFitTimer = null
        }
    }, 250)
    safeFit(viewer)
}

function lockPageScroll () {
    const html = document.documentElement
    const body = document.body
    if (!html || !body) {
        return () => {}
    }
    const prev = {
        htmlOverflow: html.style.overflow,
        bodyOverflow: body.style.overflow,
        bodyHeight: body.style.height
    }
    html.style.overflow = 'hidden'
    body.style.overflow = 'hidden'
    body.style.height = '100%'
    return () => {
        html.style.overflow = prev.htmlOverflow
        body.style.overflow = prev.bodyOverflow
        body.style.height = prev.bodyHeight
    }
}

export function createOrUpdateXmindViewer (existingViewer, container, fileBuffer) {
    destroyXmindViewer(existingViewer)
    if (container) {
        container.innerHTML = ''
    }

    const unlockScroll = lockPageScroll()
    const styles = viewportStyles()

    // Do not pass file to constructor — map-ready can fire before listeners attach.
    const viewer = new XMindEmbedViewer({
        el: container,
        region: 'cn',
        isPitchModeDisabled: true,
        styles
    })
    viewer.__bkrepoUnlockScroll = unlockScroll

    const onReady = () => startFitLoop(viewer)
    viewer.addEventListener('map-ready', onReady)
    viewer.addEventListener('sheets-load', () => setTimeout(onReady, 50))
    viewer.addEventListener('zoom-change', () => {
        // After first zoom events, nudge fit once more.
        if (!viewer.__bkrepoZoomFitted) {
            viewer.__bkrepoZoomFitted = true
            setTimeout(() => safeFit(viewer), 100)
        }
    })

    if (typeof ResizeObserver !== 'undefined' && container) {
        const observer = new ResizeObserver(() => safeFit(viewer))
        observer.observe(container)
        viewer.__bkrepoResizeObserver = observer
    }

    forceIframeFullscreen(viewer)
    viewer.load(fileBuffer)

    // Fallback if ready events never arrive.
    setTimeout(() => {
        if (!viewer.__bkrepoFitTimer) {
            startFitLoop(viewer)
        }
    }, 2500)

    return viewer
}

export function destroyXmindViewer (viewer) {
    if (!viewer) {
        return
    }
    if (viewer.__bkrepoFitTimer) {
        clearInterval(viewer.__bkrepoFitTimer)
        viewer.__bkrepoFitTimer = null
    }
    if (viewer.__bkrepoResizeObserver) {
        viewer.__bkrepoResizeObserver.disconnect()
        viewer.__bkrepoResizeObserver = null
    }
    if (typeof viewer.__bkrepoUnlockScroll === 'function') {
        viewer.__bkrepoUnlockScroll()
        viewer.__bkrepoUnlockScroll = null
    }
    try {
        const iframe = getIframe(viewer)
        if (iframe && iframe.parentNode) {
            iframe.parentNode.removeChild(iframe)
        }
    } catch (e) {
        // ignore cleanup failures
    }
}
