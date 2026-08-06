function buildHtmlPreviewGuardScript () {
    return `<script>
(function () {
  document.addEventListener('click', function (event) {
    var anchor = event.target && event.target.closest ? event.target.closest('a[href]') : null;
    if (!anchor) {
      return;
    }
    var href = anchor.getAttribute('href');
    if (!href) {
      return;
    }
    if (href.charAt(0) === '#') {
      if (href === '#') {
        event.preventDefault();
        return;
      }
      if (event.defaultPrevented) {
        return;
      }
      var id = decodeURIComponent(href.slice(1));
      var target = document.getElementById(id) || document.getElementsByName(id)[0];
      if (!target) {
        event.preventDefault();
        return;
      }
      event.preventDefault();
      target.scrollIntoView({ behavior: 'smooth', block: 'start' });
      return;
    }
    event.preventDefault();
    event.stopPropagation();
  }, true);
})();
<\/script>`
}

function buildCspMeta (assetOrigin) {
    const origin = assetOrigin || "'none'"
    const content = [
        "default-src 'none'",
        `script-src 'unsafe-inline' ${origin}`,
        `style-src 'unsafe-inline' ${origin}`,
        `img-src data: blob: ${origin}`,
        `font-src data: ${origin}`
    ].join('; ')
    return `<meta http-equiv="Content-Security-Policy" content="${content}">`
}

/**
 * Build sandboxed srcdoc for a single HTML file.
 * Keeps original asset URLs as-is; injects CSP and hash-only link handling.
 */
export function buildHtmlSandboxSrcdoc (htmlSource, { assetOrigin } = {}) {
    const source = htmlSource || ''
    const cspMeta = buildCspMeta(assetOrigin)
    const guardScript = buildHtmlPreviewGuardScript()
    const injected = `${cspMeta}${guardScript}`

    if (/<\/head>/i.test(source)) {
        return source.replace(/<\/head>/i, `${injected}</head>`)
    }
    if (/<head\b[^>]*>/i.test(source)) {
        return source.replace(/<head\b[^>]*>/i, (match) => `${match}${injected}`)
    }
    if (/<html\b[^>]*>/i.test(source)) {
        return source.replace(/<html\b[^>]*>/i, (match) => `${match}<head>${injected}</head>`)
    }
    return `<!DOCTYPE html><html><head><meta charset="utf-8">${injected}</head><body>${source}</body></html>`
}
