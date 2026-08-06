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
    import 'highlight.js/styles/github.min.css'
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
    line-height: 1.7;
    color: #313238;
    word-break: break-word;
    min-height: 100%;
    padding: 0;
}
.markdown-preview-body ::v-deep img {
    max-width: 100%;
}
.markdown-preview-body ::v-deep pre {
    overflow: auto;
    padding: 12px;
    border-radius: 4px;
    background: #f5f7fa;
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
