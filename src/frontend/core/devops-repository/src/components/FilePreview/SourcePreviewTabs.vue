<template>
    <div class="source-preview-tabs">
        <div
            v-show="activeView === 'preview'"
            class="source-preview-tabs__panel source-preview-tabs__panel--preview"
        >
            <div
                v-if="previewError"
                class="source-preview-tabs__error"
            >
                {{ previewError }}
            </div>
            <div
                v-else-if="fileKind === 'markdown'"
                class="markdown-preview-body"
                v-html="previewHtml"
            />
            <iframe
                v-else-if="fileKind === 'jsx' && jsxSrcdoc"
                class="jsx-preview-iframe"
                :srcdoc="jsxSrcdoc"
                sandbox="allow-scripts"
                allow="clipboard-write"
                title="jsx-preview"
            />
            <iframe
                v-else-if="fileKind === 'html' && htmlSrcdoc"
                class="html-preview-iframe"
                :srcdoc="htmlSrcdoc"
                sandbox="allow-scripts"
                title="html-preview"
            />
        </div>
        <div
            v-show="activeView === 'source'"
            ref="editorContainer"
            class="source-preview-tabs__panel source-preview-tabs__panel--source"
        />
    </div>
</template>
<script>
    import * as monaco from 'monaco-editor'
    import hljs from 'highlight.js/lib/core'
    import javascript from 'highlight.js/lib/languages/javascript'
    import python from 'highlight.js/lib/languages/python'
    import bash from 'highlight.js/lib/languages/bash'
    import json from 'highlight.js/lib/languages/json'
    import xml from 'highlight.js/lib/languages/xml'
    import kotlin from 'highlight.js/lib/languages/kotlin'
    import java from 'highlight.js/lib/languages/java'
    import 'highlight.js/styles/github-dark.min.css'
    import {
        buildHtmlSandboxSrcdoc,
        buildJsxSandboxSrcdoc,
        getMonacoLanguage,
        getPreviewFileKind,
        normalizeCodeText,
        normalizeMarkdownText,
        renderMarkdownToSafeHtml
    } from '@repository/utils/markdownJsxPreview'

    hljs.registerLanguage('javascript', javascript)
    hljs.registerLanguage('python', python)
    hljs.registerLanguage('bash', bash)
    hljs.registerLanguage('json', json)
    hljs.registerLanguage('xml', xml)
    hljs.registerLanguage('kotlin', kotlin)
    hljs.registerLanguage('java', java)

    export default {
        name: 'SourcePreviewTabs',
        props: {
            filePath: {
                type: String,
                required: true
            },
            sourceText: {
                type: String,
                default: ''
            },
            resolveAssetUrl: {
                type: Function,
                default: null
            },
            viewMode: {
                type: String,
                default: 'preview',
                validator (value) {
                    return value === 'preview' || value === 'source'
                }
            }
        },
        data () {
            return {
                previewHtml: '',
                previewError: '',
                jsxSrcdoc: '',
                htmlSrcdoc: '',
                editor: null
            }
        },
        computed: {
            fileKind () {
                return getPreviewFileKind(this.filePath)
            },
            activeView () {
                if (this.fileKind === 'code') {
                    return 'source'
                }
                return this.viewMode === 'source' ? 'source' : 'preview'
            },
            normalizedSource () {
                if (this.fileKind === 'markdown') {
                    return normalizeMarkdownText(this.sourceText)
                }
                if (this.fileKind === 'code' || this.fileKind === 'html') {
                    return normalizeCodeText(this.sourceText)
                }
                return this.sourceText
            }
        },
        watch: {
            sourceText: {
                immediate: true,
                handler () {
                    this.renderPreview()
                    this.updateEditorValue()
                }
            },
            filePath () {
                this.renderPreview()
                this.recreateEditor()
            },
            activeView (view) {
                if (view === 'source') {
                    this.$nextTick(() => this.ensureEditor())
                } else {
                    this.renderPreview()
                }
            }
        },
        mounted () {
            if (this.activeView === 'source') {
                this.$nextTick(() => this.ensureEditor())
            }
        },
        beforeDestroy () {
            this.disposeEditor()
        },
        methods: {
            async renderPreview () {
                this.previewError = ''
                this.previewHtml = ''
                this.jsxSrcdoc = ''
                this.htmlSrcdoc = ''
                if (this.fileKind === 'code' || this.activeView !== 'preview' || !this.normalizedSource) {
                    return
                }
                try {
                    if (this.fileKind === 'markdown') {
                        this.previewHtml = renderMarkdownToSafeHtml(this.normalizedSource, {
                            resolveAssetUrl: this.resolveAssetUrl,
                            highlight: hljs
                        })
                    } else if (this.fileKind === 'jsx') {
                        this.jsxSrcdoc = buildJsxSandboxSrcdoc(this.normalizedSource)
                    } else if (this.fileKind === 'html') {
                        this.htmlSrcdoc = buildHtmlSandboxSrcdoc(this.normalizedSource)
                    }
                } catch (error) {
                    this.previewError = error && error.message ? error.message : String(error)
                }
            },
            ensureEditor () {
                if (this.editor || !this.$refs.editorContainer) {
                    if (this.editor) {
                        this.editor.layout()
                    }
                    return
                }
                this.editor = monaco.editor.create(this.$refs.editorContainer, {
                    value: this.normalizedSource,
                    language: getMonacoLanguage(this.filePath),
                    automaticLayout: true,
                    theme: 'vs-dark',
                    minimap: { enabled: true },
                    readOnly: true,
                    lineNumbers: 'on'
                })
            },
            updateEditorValue () {
                if (this.editor) {
                    this.editor.setValue(this.normalizedSource)
                }
            },
            recreateEditor () {
                this.disposeEditor()
                this.$nextTick(() => {
                    if (this.activeView === 'source') {
                        this.ensureEditor()
                    }
                })
            },
            disposeEditor () {
                if (this.editor) {
                    this.editor.dispose()
                    this.editor = null
                }
            }
        }
    }
</script>
<style lang="scss" scoped>
/*
 * Markdown 内容样式对照清单（对齐 weterm2 agent 主聊天 assistant 气泡）
 * 真源：weterm2/src/renderer/css/agent/chat.css → .chat-msg-bubble / .chat-msg-text
 * 正文字体：weterm2/src/renderer/css/index.css → body/.app（对话气泡继承此栈）
 * 字重策略：根 400（与对话默认一致；靠 PingFangSC-Medium / PingFang SC Medium 切面呈 Medium）
 * 标题/strong 700 对齐 .chat-msg-text
 * 链接强调色：weterm2/.../agent/base.css → --agent-accent: #5C8EF2
 * 范围：内容排版+色板+字体族；容器宽/padding 保持文件预览现状；不含 .user 变体
 * 内容选择器放在下方非 scoped 块，避免 scoped + v-html 穿透失效
 *
 * | 元素 | 期望值 |
 * | --- | --- |
 * | 正文字体 | PingFangSC-Medium, 'PingFang SC', Avenir, Helvetica, Arial, sans-serif |
 * | 正文字重 | 400（与对话一致；勿用 500 以免比气泡更粗） |
 * | 根字号 / 行高 / 正文色 | 14px / 1.6 / #111827 |
 * | h1 / h2 / h3 | 18px / 16px / 14px，色 #1e293b，字重 700 |
 * | inline code | 12px，#e01e5a，背景 rgba(0,0,0,.06)；字体 SF Mono/Menlo/Monaco；字重 400 |
 * | pre | 深底 #1e293b，浅字 #e2e8f0，12px，圆角 8px |
 * | table | 13px，边框 #e5e7eb |
 * | a | #5C8EF2 |
 * | hljs | github-dark（配合深色 pre） |
 */
.source-preview-tabs {
    display: flex;
    flex-direction: column;
    width: 100%;
    height: 100%;
    background: #fff;
}
.source-preview-tabs__panel {
    flex: 1;
    min-height: 0;
    width: 100%;
    height: 100%;
}
.source-preview-tabs__panel--preview {
    overflow: auto;
    padding: 0;
}
.source-preview-tabs__panel--source {
    height: 100%;
    margin: 0;
    border: 0;
    border-radius: 0;
    overflow: hidden;
}
.markdown-preview-body {
    box-sizing: border-box;
    width: 85%;
    max-width: 1200px;
    margin: 0 auto;
    padding: 24px;
    font-family: PingFangSC-Medium, 'PingFang SC', Avenir, Helvetica, Arial, sans-serif;
    font-size: 14px;
    font-weight: 400;
    line-height: 1.6;
    color: #111827;
    word-break: break-word;
    min-height: 100%;
    overflow-x: auto;
    -webkit-font-smoothing: antialiased;
    -moz-osx-font-smoothing: grayscale;
}
@media (max-width: 767px) {
    .markdown-preview-body {
        width: 100%;
        max-width: none;
        padding: 16px;
    }
}
.jsx-preview-iframe {
    width: 100%;
    height: 100%;
    border: 0;
    display: block;
}
.html-preview-iframe {
    width: 100%;
    height: 100%;
    border: 0;
    display: block;
}
.source-preview-tabs__error {
    color: #ea3636;
    white-space: pre-wrap;
}
</style>
<style lang="scss">
.markdown-preview-body {
    font-family: PingFangSC-Medium, 'PingFang SC', Avenir, Helvetica, Arial, sans-serif;
    font-weight: 400;
    -webkit-font-smoothing: antialiased;
    -moz-osx-font-smoothing: grayscale;

    img {
        max-width: 100%;
    }

    p {
        margin: 0 0 8px;
        font-weight: 400;

        &:last-child {
            margin-bottom: 0;
        }
    }

    strong {
        font-weight: 700;
        color: #1e293b;
    }

    em {
        font-style: italic;
    }

    code {
        background: rgba(0, 0, 0, 0.06);
        padding: 1px 5px;
        border-radius: 4px;
        font-size: 12px;
        font-family: 'SF Mono', 'Menlo', 'Monaco', monospace;
        font-weight: 400;
        color: #e01e5a;
    }

    pre {
        overflow-x: auto;
        margin: 8px 0;
        padding: 12px 14px;
        border-radius: 8px;
        background: #1e293b;
        color: #e2e8f0;
        font-size: 12px;
        font-weight: 400;
        line-height: 1.5;

        code {
            background: none;
            padding: 0;
            color: inherit;
            font-size: inherit;
            font-weight: inherit;
        }
    }

    pre code.hljs,
    pre .hljs {
        background: transparent;
    }

    blockquote {
        border-left: 3px solid #cbd5e1;
        margin: 8px 0;
        padding: 4px 12px;
        color: #64748b;
        font-weight: 400;
    }

    ul,
    ol {
        margin: 6px 0;
        padding-left: 20px;
    }

    ul {
        list-style-type: disc;
    }

    ol {
        list-style-type: decimal;
    }

    ul ul,
    ol ul {
        list-style-type: circle;
    }

    ul ul ul,
    ol ol ul {
        list-style-type: square;
    }

    li {
        margin: 2px 0;
        display: list-item;
        list-style: inherit;
        font-weight: 400;
    }

    li.task-list-item {
        list-style-type: none;
        margin-left: -20px;
    }

    hr {
        border: 0;
        border-top: 1px solid #e2e8f0;
        margin: 12px 0;
    }

    a {
        color: #5c8ef2;
        text-decoration: none;
        font-weight: 400;

        &:hover {
            text-decoration: underline;
            color: #5c8ef2;
        }
    }

    h1,
    h2,
    h3,
    h4,
    h5,
    h6 {
        margin: 12px 0 6px;
        font-family: inherit;
        font-weight: 700;
        color: #1e293b;
    }

    h1 {
        font-size: 18px;
    }

    h2 {
        font-size: 16px;
    }

    h3 {
        font-size: 14px;
    }

    table {
        width: 100%;
        border-collapse: collapse;
        margin: 8px 0;
        font-size: 13px;
        font-weight: 400;
        line-height: 1.5;
    }

    th,
    td {
        padding: 6px 10px;
        border: 1px solid #e5e7eb;
        text-align: left;
        word-break: break-word;
        overflow-wrap: break-word;
    }

    th {
        background: #f8fafc;
        font-weight: 600;
        color: #1e293b;
    }

    tr:nth-child(even) {
        background: #fafbfc;
    }

    tr:hover {
        background: #f1f5f9;
    }
}
</style>
