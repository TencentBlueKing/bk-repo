const ABSOLUTE_URL_PATTERN = /^(https?:|data:|mailto:)/i

export function normalizePreviewPath (filePath) {
    if (!filePath) {
        return ''
    }
    const normalized = filePath.replace(/\\/g, '/')
    return normalized.startsWith('/') ? normalized : `/${normalized}`
}

export function resolveRelativePath (baseFilePath, relativePath) {
    if (!relativePath || ABSOLUTE_URL_PATTERN.test(relativePath)) {
        return relativePath
    }
    const baseDir = normalizePreviewPath(baseFilePath).replace(/\/[^/]*$/, '') || '/'
    const segments = `${baseDir}/${relativePath}`.split('/')
    const resolved = []
    for (const segment of segments) {
        if (!segment || segment === '.') {
            continue
        }
        if (segment === '..') {
            if (resolved.length === 0) {
                return null
            }
            resolved.pop()
            continue
        }
        resolved.push(segment)
    }
    return `/${resolved.join('/')}`
}

export function parsePreviewContext ({ projectId, repoName, filePath, extraParam }) {
    const context = {
        projectId: projectId || '',
        repoName: repoName || '',
        filePath: normalizePreviewPath(filePath)
    }
    if (!extraParam || extraParam === '0') {
        return context
    }
    try {
        const decoded = typeof extraParam === 'string' && extraParam.includes('%')
            ? decodeURIComponent(extraParam)
            : extraParam
        const payload = JSON.parse(decoded)
        if (payload.projectId) {
            context.projectId = payload.projectId
        }
        if (payload.repoName) {
            context.repoName = payload.repoName
        }
        if (payload.artifactUri) {
            context.filePath = normalizePreviewPath(payload.artifactUri)
        } else if (payload.fullPath) {
            context.filePath = normalizePreviewPath(payload.fullPath)
        } else if (payload.url) {
            const match = payload.url.match(/\/generic\/([^/]+)\/([^/]+)(\/.*)?$/)
            if (match) {
                context.projectId = context.projectId || match[1]
                context.repoName = context.repoName || match[2]
                context.filePath = normalizePreviewPath(match[3] || '')
            }
        }
    } catch (e) {
        // ignore malformed extraParam
    }
    return context
}

export function normalizeMarkdownText (text, decodeBase64) {
    if (!text) {
        return ''
    }
    const trimmed = text.trim()
    if (!trimmed || trimmed.includes('\n#') || trimmed.startsWith('#')) {
        return text
    }
    if (/^[A-Za-z0-9+/=\s]+$/.test(trimmed) && typeof decodeBase64 === 'function') {
        try {
            const decoded = decodeBase64(trimmed)
            if (decoded && (decoded.includes('#') || decoded.includes('```') || decoded.includes('*'))) {
                return decoded
            }
        } catch (e) {
            // keep raw text for backward compatibility
        }
    }
    return text
}

export function prepareJsxSource (jsxSource) {
    if (!jsxSource || !jsxSource.trim()) {
        throw new Error('JSX file is empty')
    }
    if (/^\s*import\s+/m.test(jsxSource) || /\brequire\s*\(/.test(jsxSource)) {
        throw new Error('import/require is not supported in JSX preview')
    }
    let source = jsxSource
    if (/export\s+default\s+/m.test(source)) {
        source = source.replace(/export\s+default\s+/m, 'const __PREVIEW_COMPONENT__ = ')
    } else {
        const namedExport = source.match(/export\s+default\s+(\w+)\s*;?\s*$/)
        if (namedExport) {
            source = source.replace(/export\s+default\s+(\w+)\s*;?\s*$/, 'const __PREVIEW_COMPONENT__ = $1')
        }
    }
    if (!source.includes('__PREVIEW_COMPONENT__')) {
        throw new Error('Expected `export default` React component')
    }
    return source.replace(/<\/script/gi, '<\\/script')
}

export function rewriteMarkdownImageUrls (html, resolveAssetUrl) {
    if (!resolveAssetUrl || !html) {
        return html
    }
    return html.replace(/<img([^>]*?)src="([^"]+)"([^>]*)>/gi, (match, before, src, after) => {
        const resolved = resolveAssetUrl(src)
        if (!resolved) {
            return match
        }
        return `<img${before}src="${resolved}"${after}>`
    })
}

export function buildJsxSandboxSrcdoc (jsxSource, libBase) {
    const preparedSource = prepareJsxSource(jsxSource)
    return `<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline' 'unsafe-eval' ${libBase}; style-src 'unsafe-inline';">
  <script src="${libBase}babel.min.js"><\/script>
  <script src="${libBase}react.production.min.js"><\/script>
  <script src="${libBase}react-dom.production.min.js"><\/script>
  <style>
    body { margin: 0; padding: 16px; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; }
    pre.error { color: #ea3636; white-space: pre-wrap; }
  </style>
</head>
<body>
  <div id="root"></div>
  <script type="text/babel" data-presets="react">
${preparedSource}
  </script>
  <script type="text/babel">
    try {
      const Component = typeof __PREVIEW_COMPONENT__ !== 'undefined' ? __PREVIEW_COMPONENT__ : null;
      if (!Component) {
        throw new Error('Expected export default React component');
      }
      ReactDOM.render(React.createElement(Component), document.getElementById('root'));
    } catch (error) {
      document.body.innerHTML = '<pre class="error">' + (error && error.message ? error.message : String(error)) + '</pre>';
    }
  </script>
</body>
</html>`
}
