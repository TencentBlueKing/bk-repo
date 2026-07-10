import DOMPurify from 'dompurify'
import { marked } from 'marked'
import { Base64 } from 'js-base64'
import { appendPreviewTokenToUrl } from '@repository/utils/previewOfficeFile'
import { isJsx, isMarkdown } from '@repository/utils/file'
import {
    buildJsxSandboxSrcdoc as buildJsxSandboxSrcdocCore,
    normalizeMarkdownText as normalizeMarkdownTextCore,
    normalizePreviewPath,
    resolveRelativePath,
    rewriteMarkdownImageUrls
} from '@repository/utils/markdownJsxPreviewCore'

export {
    normalizePreviewPath,
    parsePreviewContext,
    resolveRelativePath,
    rewriteMarkdownImageUrls
} from '@repository/utils/markdownJsxPreviewCore'

export { prepareJsxSource } from '@repository/utils/markdownJsxPreviewCore'

export function buildGenericAssetUrl ({ projectId, repoName, resolvedPath }) {
    if (!projectId || !repoName || !resolvedPath) {
        return null
    }
    const path = normalizePreviewPath(resolvedPath)
    const base = `${location.origin}${window.BK_SUBPATH}web/generic/${projectId}/${repoName}${path}?download=true`
    return appendPreviewTokenToUrl(base)
}

export function createAssetResolver (context) {
    return (relativePath) => {
        const resolved = resolveRelativePath(context.filePath, relativePath)
        if (!resolved || /^(https?:|data:|mailto:)/i.test(resolved)) {
            return resolved
        }
        return buildGenericAssetUrl({
            projectId: context.projectId,
            repoName: context.repoName,
            resolvedPath: resolved
        })
    }
}

export function normalizeMarkdownText (text) {
    return normalizeMarkdownTextCore(text, (value) => Base64.decode(value))
}

export function renderMarkdownToSafeHtml (markdown, { resolveAssetUrl, highlight } = {}) {
    marked.setOptions({
        gfm: true,
        breaks: true,
        highlight: (code, language) => {
            if (!highlight) {
                return code
            }
            if (language && highlight.getLanguage(language)) {
                return highlight.highlight(code, { language }).value
            }
            return highlight.highlightAuto(code).value
        }
    })
    const rawHtml = marked.parse(markdown)
    const htmlWithAssets = rewriteMarkdownImageUrls(rawHtml, resolveAssetUrl)
    return DOMPurify.sanitize(htmlWithAssets)
}

export function getPreviewLibBaseUrl () {
    return `${location.origin}${window.BK_SUBPATH}ui/libs/`
}

export function buildJsxSandboxSrcdoc (jsxSource) {
    return buildJsxSandboxSrcdocCore(jsxSource, getPreviewLibBaseUrl())
}

export function getMonacoLanguage (filePath) {
    return isMarkdown(filePath) ? 'markdown' : 'javascriptreact'
}

export function getPreviewFileKind (filePath) {
    if (isMarkdown(filePath)) {
        return 'markdown'
    }
    if (isJsx(filePath)) {
        return 'jsx'
    }
    return null
}
