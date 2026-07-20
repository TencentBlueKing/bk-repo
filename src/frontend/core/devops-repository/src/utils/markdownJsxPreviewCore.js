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

const ESM_CDN_PREFIX = 'https://esm.sh/'

function toEsmCdnUrl (specifier) {
    if (/^(https?:|data:)/i.test(specifier)) {
        return specifier
    }
    if (specifier.startsWith('.') || specifier.startsWith('/')) {
        throw new Error(`Relative import is not supported in JSX preview: ${specifier}`)
    }
    return `${ESM_CDN_PREFIX}${specifier}`
}

export function rewriteBareImports (source) {
    let result = source.replace(
        /\bfrom\s+(['"])([^'"]+)\1/g,
        (match, quote, specifier) => `from ${quote}${toEsmCdnUrl(specifier)}${quote}`
    )
    result = result.replace(
        /^\s*import\s+(['"])([^'"]+)\1\s*;?\s*$/gm,
        (match, quote, specifier) => `import ${quote}${toEsmCdnUrl(specifier)}${quote};`
    )
    result = result.replace(
        /\bimport\s*\(\s*(['"])([^'"]+)\1\s*\)/g,
        (match, quote, specifier) => `import(${quote}${toEsmCdnUrl(specifier)}${quote})`
    )
    return result
}

/**
 * Babel standalone `preset-react` defaults to classic runtime
 * (`React.createElement`). User JSX often omits `import React`
 * (new JSX transform style). Detect whether `React` is already bound.
 */
export function hasReactBinding (source) {
    if (!source) {
        return false
    }
    return /^\s*import\s+React(?:\s*,|\s+from\b)/m.test(source)
        || /^\s*import\s+\*\s+as\s+React\s+from\b/m.test(source)
}

export function ensureReactImport (source) {
    if (hasReactBinding(source)) {
        return source
    }
    return `import React from '${ESM_CDN_PREFIX}react';\n${source}`
}

export function prepareJsxSource (jsxSource) {
    if (!jsxSource || !jsxSource.trim()) {
        throw new Error('JSX file is empty')
    }
    if (/\brequire\s*\(/.test(jsxSource)) {
        throw new Error('require() is not supported in JSX preview; use ESM import instead')
    }
    // Inject React into preview host scope; do not require user files to import it.
    let source = ensureReactImport(rewriteBareImports(jsxSource))
    if (/export\s+default\s+/m.test(source)) {
        source = source.replace(/export\s+default\s+/m, 'const __PREVIEW_COMPONENT__ = ')
    }
    if (!source.includes('__PREVIEW_COMPONENT__')) {
        throw new Error('Expected `export default` React component')
    }
    source = `${source}

import { createRoot as __createPreviewRoot } from '${ESM_CDN_PREFIX}react-dom/client';
const __previewRoot = __createPreviewRoot(document.getElementById('root'));
__previewRoot.render(React.createElement(__PREVIEW_COMPONENT__));
`
    return source.replace(/<\/script/gi, '<\\/script')
}

export function rewriteMarkdownImageUrls (html, resolveAssetUrl) {
    if (!resolveAssetUrl || !html) {
        return html
    }
    return html.replace(/<img([^>]*?)src=(["'])([^"']+)\2([^>]*)>/gi, (match, before, quote, src, after) => {
        const resolved = resolveAssetUrl(src)
        if (!resolved) {
            return match
        }
        return `<img${before}src=${quote}${resolved}${quote}${after}>`
    })
}

export function buildJsxSandboxSrcdoc (jsxSource, libBase) {
    const preparedSource = prepareJsxSource(jsxSource)
    return `<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline' 'unsafe-eval' ${libBase} https: blob:; connect-src https:; style-src 'unsafe-inline' https:; img-src data: blob: https:; font-src data: https:;">
  <script src="${libBase}babel.min.js"><\/script>
  <style>
    body { margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; }
    pre.error { color: #ea3636; white-space: pre-wrap; padding: 16px; }
  </style>
</head>
<body>
  <div id="root"></div>
  <script>
    function __showPreviewError(error) {
      var message = error && error.message ? error.message : String(error);
      document.body.innerHTML = '<pre class="error">' + message + '</pre>';
    }
    function __copyTextFallback(text) {
      var textarea = document.createElement('textarea');
      textarea.value = text == null ? '' : String(text);
      textarea.setAttribute('readonly', '');
      textarea.style.position = 'fixed';
      textarea.style.left = '-9999px';
      document.body.appendChild(textarea);
      textarea.select();
      var ok = false;
      try {
        ok = document.execCommand('copy');
      } finally {
        document.body.removeChild(textarea);
      }
      if (!ok) {
        return Promise.reject(new Error('Clipboard copy is not available in this preview sandbox'));
      }
      return Promise.resolve();
    }
    if (navigator.clipboard && typeof navigator.clipboard.writeText === 'function') {
      var __nativeWriteText = navigator.clipboard.writeText.bind(navigator.clipboard);
      navigator.clipboard.writeText = function (text) {
        return __nativeWriteText(text).catch(function () {
          return __copyTextFallback(text);
        });
      };
    } else {
      navigator.clipboard = {
        writeText: __copyTextFallback
      };
    }
    window.addEventListener('error', function (event) {
      __showPreviewError(event.error || event.message);
    });
    window.addEventListener('unhandledrejection', function (event) {
      __showPreviewError(event.reason);
    });
  <\/script>
  <script type="text/babel" data-type="module" data-presets="react">
${preparedSource}
  <\/script>
</body>
</html>`
}
