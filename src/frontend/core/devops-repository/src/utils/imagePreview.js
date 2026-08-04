/**
 * Resolve whether image preview should hide Viewer chrome.
 * Recognizes query.purePreview as 1 / true (case-insensitive).
 */
export function isPurePreviewEnabled (query) {
    if (!query || query.purePreview == null || query.purePreview === '') {
        return false
    }
    const normalized = String(query.purePreview).toLowerCase()
    return normalized === '1' || normalized === 'true'
}

/**
 * Build Viewer.js options for inline image preview.
 * Pure mode hides toolbar, fullscreen button, title, navbar, and tooltip;
 * gestures (zoom / drag / keyboard) stay enabled.
 */
export function buildImageViewerOptions ({ purePreview = false } = {}) {
    const options = {
        inline: true
    }
    if (purePreview) {
        options.toolbar = false
        options.button = false
        options.title = false
        options.navbar = false
        options.tooltip = false
        // Keep interactions enabled while chrome is hidden.
        options.zoomable = true
        options.movable = true
        options.keyboard = true
    }
    return options
}
