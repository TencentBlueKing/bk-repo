import test from 'node:test'
import assert from 'node:assert/strict'
import { buildHtmlSandboxSrcdoc } from './htmlFilePreviewCore.js'

test('buildHtmlSandboxSrcdoc keeps original stylesheet script and img urls', () => {
    const input = [
        '<link rel="stylesheet" href="./app.css">',
        '<script src="./app.js"></script>',
        '<img src="./logo.png" alt="logo">'
    ].join('')
    const srcdoc = buildHtmlSandboxSrcdoc(input, { assetOrigin: 'https://repo.example.com/' })
    assert.match(srcdoc, /href="\.\/app\.css"/)
    assert.match(srcdoc, /src="\.\/app\.js"/)
    assert.match(srcdoc, /src="\.\/logo\.png"/)
})

test('buildHtmlSandboxSrcdoc injects csp and blocks non-hash navigation', () => {
    const srcdoc = buildHtmlSandboxSrcdoc(
        '<h1 id="top">Hello</h1><a href="https://evil.example">x</a><a href="#top">top</a>',
        { assetOrigin: 'https://repo.example.com/bkrepo/' }
    )
    assert.match(srcdoc, /Content-Security-Policy/)
    assert.match(srcdoc, /script-src 'unsafe-inline' https:\/\/repo\.example\.com\/bkrepo\//)
    const csp = srcdoc.match(/Content-Security-Policy" content="([^"]+)"/)[1]
    assert.doesNotMatch(csp, /(?:^|[\s;])https:(?:[\s;]|$)/)
    assert.match(srcdoc, /closest\('a\[href\]'\)/)
    assert.match(srcdoc, /href\.charAt\(0\) === '#'/)
    assert.match(srcdoc, /<h1 id="top">Hello<\/h1>/)
})

test('buildHtmlSandboxSrcdoc injects into existing head', () => {
    const srcdoc = buildHtmlSandboxSrcdoc(
        '<!DOCTYPE html><html><head><title>t</title></head><body>Hi</body></html>',
        { assetOrigin: 'https://repo.example.com/' }
    )
    assert.match(srcdoc, /<head><title>t<\/title><meta http-equiv="Content-Security-Policy"/)
    assert.match(srcdoc, /<\/script><\/head><body>Hi<\/body>/)
})
