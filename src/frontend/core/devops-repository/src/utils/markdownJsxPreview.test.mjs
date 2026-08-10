import test from 'node:test'
import assert from 'node:assert/strict'
import {
    resolveRelativePath,
    parsePreviewContext,
    prepareJsxSource,
    rewriteBareImports,
    rewriteMarkdownImageUrls,
    normalizeMarkdownText,
    normalizeCodeText,
    resolveMonacoLanguage,
    resolvePreviewViewMode,
    buildJsxSandboxSrcdoc
} from './markdownJsxPreviewCore.js'

test('resolveRelativePath resolves sibling and parent paths', () => {
    assert.equal(resolveRelativePath('/docs/guide/README.md', './img.png'), '/docs/guide/img.png')
    assert.equal(resolveRelativePath('/docs/guide/README.md', '../assets/logo.png'), '/docs/assets/logo.png')
    assert.equal(resolveRelativePath('/docs/guide/README.md', 'https://example.com/a.png'), 'https://example.com/a.png')
    assert.equal(resolveRelativePath('/a.md', '../../etc/passwd'), null)
})

test('parsePreviewContext reads artifact info from extraParam', () => {
    const extraParam = JSON.stringify({
        projectId: 'demo',
        repoName: 'generic-local',
        artifactUri: '/docs/readme.md'
    })
    assert.deepEqual(parsePreviewContext({
        extraParam
    }), {
        projectId: 'demo',
        repoName: 'generic-local',
        filePath: '/docs/readme.md'
    })
})

test('rewriteBareImports maps packages to esm.sh', () => {
    const source = rewriteBareImports(
        'import React, { useState } from "react"\nimport { Copy } from "lucide-react"\nimport "side-effect-pkg"'
    )
    assert.match(source, /from "https:\/\/esm\.sh\/react"/)
    assert.match(source, /from "https:\/\/esm\.sh\/lucide-react"/)
    assert.match(source, /import "https:\/\/esm\.sh\/side-effect-pkg"/)
})

test('rewriteBareImports rejects relative imports', () => {
    assert.throws(
        () => rewriteBareImports('import Foo from "./foo"'),
        /Relative import is not supported/
    )
})

test('prepareJsxSource keeps package imports and mounts default export', () => {
    const source = prepareJsxSource(
        'import React, { useState } from "react"\nimport { Copy } from "lucide-react"\nexport default function Hello () { return <div>Hi</div> }'
    )
    assert.match(source, /from "https:\/\/esm\.sh\/react"/)
    assert.match(source, /from "https:\/\/esm\.sh\/lucide-react"/)
    assert.match(source, /const __PREVIEW_COMPONENT__ = function Hello/)
    assert.match(source, /createRoot/)
    assert.match(source, /React\.createElement\(__PREVIEW_COMPONENT__\)/)
    assert.equal((source.match(/import\s+React\b/g) || []).length, 1)
})

test('prepareJsxSource injects React when user JSX omits the import', () => {
    const source = prepareJsxSource(
        'export default function DeltaForceHome () { return <div>Hi</div> }'
    )
    assert.match(source, /^import React from 'https:\/\/esm\.sh\/react';/m)
    assert.match(source, /const __PREVIEW_COMPONENT__ = function DeltaForceHome/)
    assert.match(source, /React\.createElement\(__PREVIEW_COMPONENT__\)/)
})

test('prepareJsxSource injects React when only named hooks are imported', () => {
    const source = prepareJsxSource(
        'import { useState } from "react"\nexport default function App () { const [n] = useState(0); return <div>{n}</div> }'
    )
    assert.match(source, /import React from 'https:\/\/esm\.sh\/react';/)
    assert.match(source, /from "https:\/\/esm\.sh\/react"/)
    assert.equal((source.match(/import\s+React\b/g) || []).length, 1)
})

test('prepareJsxSource rejects require', () => {
    assert.throws(
        () => prepareJsxSource('const x = require("react")\nexport default () => <div />'),
        /require\(\) is not supported/
    )
})

test('normalizeMarkdownText decodes legacy base64 payload', () => {
    const encoded = Buffer.from('# Title', 'utf8').toString('base64')
    assert.equal(normalizeMarkdownText(encoded, (value) => Buffer.from(value, 'base64').toString('utf8')), '# Title')
})

test('rewriteMarkdownImageUrls rewrites double and single quoted src', () => {
    const resolve = (src) => (src.startsWith('./') ? `/resolved/${src.slice(2)}` : src)
    assert.equal(
        rewriteMarkdownImageUrls('<img src="./a.png" alt="a">', resolve),
        '<img src="/resolved/a.png" alt="a">'
    )
    assert.equal(
        rewriteMarkdownImageUrls("<img alt='b' src='./b.png'>", resolve),
        "<img alt='b' src='/resolved/b.png'>"
    )
    assert.equal(
        rewriteMarkdownImageUrls('<img src="https://example.com/c.png">', resolve),
        '<img src="https://example.com/c.png">'
    )
})

test('normalizeCodeText always base64-decodes code payload', () => {
    const decode = (value) => Buffer.from(value, 'base64').toString('utf8')
    const encoded = Buffer.from('public class Main {}', 'utf8').toString('base64')
    assert.equal(normalizeCodeText(encoded, decode), 'public class Main {}')
})

test('normalizeCodeText falls back to raw text when decode fails', () => {
    assert.equal(
        normalizeCodeText('not-valid-base64!!!', () => {
            throw new Error('decode failed')
        }),
        'not-valid-base64!!!'
    )
})

test('resolveMonacoLanguage maps CODE suffixes to monaco language ids', () => {
    assert.equal(resolveMonacoLanguage('Main.java'), 'java')
    assert.equal(resolveMonacoLanguage('app.py'), 'python')
    assert.equal(resolveMonacoLanguage('script.python'), 'python')
    assert.equal(resolveMonacoLanguage('main.go'), 'go')
    assert.equal(resolveMonacoLanguage('index.js'), 'javascript')
    assert.equal(resolveMonacoLanguage('page.html'), 'html')
    assert.equal(resolveMonacoLanguage('styles.css'), 'css')
    assert.equal(resolveMonacoLanguage('run.sh'), 'shell')
    assert.equal(resolveMonacoLanguage('conf.yaml'), 'yaml')
    assert.equal(resolveMonacoLanguage('conf.yml'), 'yaml')
    assert.equal(resolveMonacoLanguage('data.json'), 'json')
    assert.equal(resolveMonacoLanguage('query.sql'), 'sql')
    assert.equal(resolveMonacoLanguage('lib.cpp'), 'cpp')
    assert.equal(resolveMonacoLanguage('lib.h'), 'c')
    assert.equal(resolveMonacoLanguage('lib.c'), 'c')
    assert.equal(resolveMonacoLanguage('App.cs'), 'csharp')
    assert.equal(resolveMonacoLanguage('app.rb'), 'ruby')
    assert.equal(resolveMonacoLanguage('index.php'), 'php')
    assert.equal(resolveMonacoLanguage('mod.lua'), 'lua')
    assert.equal(resolveMonacoLanguage('page.aspx'), 'plaintext')
    assert.equal(resolveMonacoLanguage('page.jsp'), 'plaintext')
    assert.equal(resolveMonacoLanguage('page.ftl'), 'plaintext')
    assert.equal(resolveMonacoLanguage('notes.txt'), 'plaintext')
    assert.equal(resolveMonacoLanguage('payload.xml'), 'xml')
    assert.equal(resolveMonacoLanguage('config.ini'), 'ini')
    assert.equal(resolveMonacoLanguage('run.bat'), 'bat')
})

test('resolvePreviewViewMode reads view from query and defaults to preview', () => {
    assert.equal(resolvePreviewViewMode({ query: { view: 'source' } }), 'source')
    assert.equal(resolvePreviewViewMode({ query: { view: 'preview' } }), 'preview')
    assert.equal(resolvePreviewViewMode({ query: {} }), 'preview')
    assert.equal(resolvePreviewViewMode({ query: { view: 'other' } }), 'preview')
    assert.equal(resolvePreviewViewMode({}), 'preview')
})

test('resolvePreviewViewMode reads view from extraParam payload', () => {
    assert.equal(
        resolvePreviewViewMode({ extraParam: JSON.stringify({ view: 'source' }) }),
        'source'
    )
    assert.equal(
        resolvePreviewViewMode({
            query: { view: 'preview' },
            extraParam: JSON.stringify({ view: 'source' })
        }),
        'preview'
    )
})

test('buildJsxSandboxSrcdoc shims fragment navigation and sandbox APIs', () => {
    const srcdoc = buildJsxSandboxSrcdoc(
        'export default function App () { return <a href="#modes">modes</a> }',
        '/ui/libs/'
    )
    assert.match(srcdoc, /a\[href\^="#"]/)
    assert.match(srcdoc, /scrollIntoView/)
    assert.match(srcdoc, /defineProperty\(document, 'cookie'/)
    assert.match(srcdoc, /defineProperty\(navigator, 'serviceWorker'/)
    assert.match(srcdoc, /__isSandboxCapabilityError/)
})
