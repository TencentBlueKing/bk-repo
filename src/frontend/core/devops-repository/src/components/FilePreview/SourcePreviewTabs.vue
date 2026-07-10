<template>
    <div class="source-preview-tabs">
        <div class="source-preview-tabs__header">
            <button
                type="button"
                class="source-preview-tabs__tab"
                :class="{ 'is-active': activeTab === 'preview' }"
                @click="activeTab = 'preview'"
            >
                {{ $t('previewTab') }}
            </button>
            <button
                type="button"
                class="source-preview-tabs__tab"
                :class="{ 'is-active': activeTab === 'source' }"
                @click="activeTab = 'source'"
            >
                {{ $t('sourceTab') }}
            </button>
        </div>
        <div
            v-show="activeTab === 'preview'"
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
        </div>
        <div
            v-show="activeTab === 'source'"
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
        buildJsxSandboxSrcdoc,
        getMonacoLanguage,
        getPreviewFileKind,
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
            }
        },
        data () {
            return {
                activeTab: 'preview',
                previewHtml: '',
                previewError: '',
                jsxSrcdoc: '',
                editor: null
            }
        },
        computed: {
            fileKind () {
                return getPreviewFileKind(this.filePath)
            },
            normalizedSource () {
                if (this.fileKind === 'markdown') {
                    return normalizeMarkdownText(this.sourceText)
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
            activeTab (tab) {
                if (tab === 'source') {
                    this.$nextTick(() => this.ensureEditor())
                }
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
                if (!this.normalizedSource) {
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
                    }
                } catch (error) {
                    this.previewError = error && error.message ? error.message : String(error)
                    this.activeTab = 'source'
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
                    if (this.activeTab === 'source') {
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
.source-preview-tabs__header {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    padding: 12px 16px 12px;
}
.source-preview-tabs__tab {
    border: 1px solid #c4c6cc;
    background: #fff;
    color: #313238;
    padding: 4px 12px;
    border-radius: 2px;
    cursor: pointer;
}
.source-preview-tabs__tab.is-active {
    color: #3a84ff;
    border-color: #3a84ff;
}
.source-preview-tabs__panel {
    flex: 1;
    min-height: 0;
    margin-top: 8px;
}
.source-preview-tabs__panel--preview {
    overflow: auto;
    padding: 8px 24px 24px;
}
.source-preview-tabs__panel--source {
    height: calc(100vh - 68px);
    margin: 0 16px 16px;
    border: 1px solid #dcdee5;
    border-radius: 2px;
    overflow: hidden;
}
.markdown-preview-body {
    line-height: 1.7;
    color: #313238;
    word-break: break-word;
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
    height: calc(100vh - 68px);
    border: 0;
}
.source-preview-tabs__error {
    color: #ea3636;
    white-space: pre-wrap;
}
</style>
