import { XMindEmbedViewer } from 'xmind-embed-viewer'

function getViewerStyles (container) {
    const rect = container && container.getBoundingClientRect
        ? container.getBoundingClientRect()
        : null
    return {
        width: '100%',
        height: rect && rect.height > 0 ? `${Math.floor(rect.height)}px` : '100vh',
        border: 'none',
        display: 'block'
    }
}

function fitMapWhenReady (viewer) {
    const fit = () => {
        try {
            if (typeof viewer.setFitMap === 'function') {
                viewer.setFitMap()
            }
        } catch (e) {
            // ignore fit failures from embed viewer
        }
    }
    if (typeof viewer.addEventListener === 'function') {
        viewer.addEventListener('map-ready', fit)
    }
    // Some builds emit ready before listener attaches; retry after load settles.
    setTimeout(fit, 300)
    setTimeout(fit, 1000)
}

export function createOrUpdateXmindViewer (existingViewer, container, fileBuffer) {
    let viewer = existingViewer
    const styles = getViewerStyles(container)
    if (!viewer) {
        viewer = new XMindEmbedViewer({
            el: container,
            theme: 'light',
            region: 'cn',
            styles
        })
        fitMapWhenReady(viewer)
    } else if (typeof viewer.setStyles === 'function') {
        viewer.setStyles(styles)
    }
    viewer.load(fileBuffer)
    // load() may not re-fire map-ready for every build; force fit after load.
    setTimeout(() => {
        try {
            if (typeof viewer.setFitMap === 'function') {
                viewer.setFitMap()
            }
        } catch (e) {
            // ignore
        }
    }, 500)
    return viewer
}
