import test from 'node:test'
import assert from 'node:assert/strict'
import {
    resolveRelativePath,
    parsePreviewContext,
    prepareJsxSource,
    rewriteBareImports,
    normalizeMarkdownText
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
