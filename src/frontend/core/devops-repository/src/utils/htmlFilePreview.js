import {
    buildHtmlSandboxSrcdoc as buildHtmlSandboxSrcdocCore
} from '@repository/utils/htmlFilePreviewCore'

export {
    buildHtmlSandboxSrcdoc as buildHtmlSandboxSrcdocCore
} from '@repository/utils/htmlFilePreviewCore'

export function getPreviewAssetOrigin () {
    return `${location.origin}${window.BK_SUBPATH || ''}`
}

export function buildHtmlSandboxSrcdoc (htmlSource, { assetOrigin } = {}) {
    return buildHtmlSandboxSrcdocCore(htmlSource, {
        assetOrigin: assetOrigin || getPreviewAssetOrigin()
    })
}
