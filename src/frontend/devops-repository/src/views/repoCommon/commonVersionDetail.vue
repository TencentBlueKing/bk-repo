<template>
    <bk-tab class="common-version-container" type="unborder-card" :active.sync="tabName" v-bkloading="{ isLoading }">
        <template #setting>
            <bk-button v-if="!metadataMap.forbidStatus && repoType !== 'docker'"
                outline class="mr10" @click="$emit('download')">{{$t('download')}}</bk-button>
            <operation-list class="mr20"
                :list="operationBtns">
                <bk-button icon="ellipsis"></bk-button>
            </operation-list>
        </template>
        <bk-tab-panel v-if="detail.basic" name="basic" :label="$t('baseInfo')">
            <div class="version-base-info base-info display-block" :data-title="$t('baseInfo')">
                <div class="package-name grid-item">
                    <label>{{$t('artifactName')}}</label>
                    <span class="flex-1 flex-align-center text-overflow">
                        <span class="text-overflow" :title="packageName">{{ packageName }}</span>
                        <span v-if="detail.basic.groupId" class="ml5 repo-tag"> {{ detail.basic.groupId }} </span>
                    </span>
                </div>
                <div class="grid-item"
                    v-for="{ name, label, value } in detailInfoMap"
                    :key="name">
                    <label>{{ label }}</label>
                    <span class="flex-1 flex-align-center text-overflow">
                        <span class="text-overflow" :title="value">{{ value }}</span>
                        <template v-if="name === 'version'">
                            <span class="ml5 repo-tag"
                                v-for="tag in detail.basic.stageTag"
                                :key="tag">
                                {{ tag }}
                            </span>
                            <scan-tag v-if="showRepoScan" class="ml10" :status="metadataMap.scanStatus"></scan-tag>
                            <forbid-tag class="ml10"
                                v-if="metadataMap.forbidStatus"
                                v-bind="metadataMap">
                            </forbid-tag>
                        </template>
                    </span>
                </div>
                <div class="package-description grid-item">
                    <label>{{ $t('description') }}</label>
                    <span class="flex-1 text-overflow" :title="detail.basic.description">{{ detail.basic.description || '' }}</span>
                </div>
            </div>
            <div class="version-base-info base-info-guide display-block" :data-title="$t('useTips')">
                <div class="sub-section" v-for="block in articleInstall[0].main" :key="block.subTitle">
                    <div class="mb10">{{ block.subTitle }}</div>
                    <code-area class="mb20" v-if="block.codeList && block.codeList.length" :code-list="block.codeList"></code-area>
                </div>
            </div>
            <div class="version-base-info base-info-checksums display-block" data-title="Checksums">
                <div v-if="detail.basic.sha256" class="grid-item">
                    <label>SHA256</label>
                    <span class="flex-1 text-overflow" :title="detail.basic.sha256">{{ detail.basic.sha256 }}</span>
                </div>
                <div v-if="detail.basic.md5" class="grid-item">
                    <label>MD5</label>
                    <span class="flex-1 text-overflow" :title="detail.basic.md5">{{ detail.basic.md5 }}</span>
                </div>
            </div>
        </bk-tab-panel>
        <bk-tab-panel v-if="detail.basic.readme" name="readme" :label="$t('readMe')">
            <div class="version-detail-readme" v-html="readmeContent"></div>
        </bk-tab-panel>
        <bk-tab-panel v-if="detail.metadata" name="metadata" :label="$t('metaData')">
            <div class="version-metadata display-block" :data-title="$t('metaData')">
                <bk-table
                    :data="detail.metadata.filter(m => !m.system)"
                    :outer-border="false"
                    :row-border="false"
                    size="small">
                    <template #empty>
                        <empty-data ex-style="margin-top:130px;"></empty-data>
                    </template>
                    <!-- <bk-table-column :label="$t('key')" prop="key" show-overflow-tooltip></bk-table-column>
                    <bk-table-column :label="$t('value')" prop="value" show-overflow-tooltip></bk-table-column> -->
                    <bk-table-column :label="$t('metadata')">
                        <template #default="{ row }">
                            <metadata-tag :metadata="row" />
                        </template>
                    </bk-table-column>
                    <bk-table-column :label="$t('description')" prop="description" show-overflow-tooltip></bk-table-column>
                </bk-table>
            </div>
        </bk-tab-panel>
        <bk-tab-panel v-if="detail.manifest" name="manifest" label="Manifest">
            <div class="version-metadata display-block" data-title="Manifest">
                <bk-table
                    :data="Object.entries(detail.manifest)"
                    :outer-border="false"
                    :row-border="false"
                    size="small">
                    <template #empty>
                        <empty-data ex-style="margin-top:130px;"></empty-data>
                    </template>
                    <bk-table-column :label="$t('key')" prop="0" show-overflow-tooltip></bk-table-column>
                    <bk-table-column :label="$t('value')" prop="1" show-overflow-tooltip></bk-table-column>
                </bk-table>
            </div>
        </bk-tab-panel>
        <bk-tab-panel v-if="detail.layers" name="layers" label="Layers">
            <div class="version-layers display-block" data-title="Layers">
                <div class="block-header grid-item">
                    <label>ID</label>
                    <span class="pl40">{{ $t('size') }}</span>
                </div>
                <div class="grid-item" v-for="layer in detail.layers" :key="layer.digest">
                    <label class="text-overflow" :title="layer.digest">{{ layer.digest }}</label>
                    <span class="pl40">{{ convertFileSize(layer.size) }}</span>
                </div>
            </div>
        </bk-tab-panel>
        <bk-tab-panel v-if="detail.history" name="history" label="IMAGE HISTORY">
            <div class="version-history">
                <div class="version-history-left">
                    <div class="version-history-code hover-btn"
                        v-for="(code, index) in detail.history"
                        :key="index"
                        :class="{ select: selectedHistory.created_by === code.created_by }"
                        @click="selectedHistory = code">
                        {{code.created_by}}
                    </div>
                </div>
                <div class="version-history-right">
                    <header class="version-history-header">Command</header>
                    <code-area class="mt20"
                        :show-line-number="false"
                        :code-list="[selectedHistory.created_by]">
                    </code-area>
                </div>
            </div>
        </bk-tab-panel>
        <bk-tab-panel v-if="detail.dependencyInfo" name="dependencyInfo" :label="$t('dependencies')">
            <article class="version-dependencies">
                <section class="version-dependencies-main display-block"
                    v-for="type in ['dependencies', 'devDependencies', 'dependents']"
                    :key="type"
                    :data-title="type">
                    <template v-if="detail.dependencyInfo[type].length">
                        <template
                            v-for="{ name, version } in detail.dependencyInfo[type]">
                            <div class="version-dependencies-key text-overflow" :key="name" :title="name">{{ name }}</div>
                            <div v-if="type !== 'dependents'" class="version-dependencies-value text-overflow" :key="name + version" :title="version">{{ version }}</div>
                        </template>
                    </template>
                    <empty-data v-else class="version-dependencies-empty"></empty-data>
                </section>
            </article>
        </bk-tab-panel>
    </bk-tab>
</template>
<script>
    import CodeArea from '@repository/components/CodeArea'
    import OperationList from '@repository/components/OperationList'
    import ScanTag from '@repository/views/repoScan/scanTag'
    import forbidTag from '@repository/components/ForbidTag'
    import metadataTag from '@repository/views/repoCommon/metadataTag'
    import { mapState, mapActions } from 'vuex'
    import { convertFileSize, formatDate } from '@repository/utils'
    import repoGuideMixin from '@repository/views/repoCommon/repoGuideMixin'
    export default {
        name: 'commonVersionDetail',
        components: {
            CodeArea,
            OperationList,
            ScanTag,
            forbidTag,
            metadataTag
        },
        mixins: [repoGuideMixin],
        data () {
            return {
                tabName: 'basic',
                isLoading: false,
                detail: {
                    basic: {
                        readme: ''
                    }
                },
                readmeContent: '',
                selectedHistory: {},
                metadata: {
                    show: false,
                    loading: false,
                    key: '',
                    value: ''
                },
                rules: {
                    key: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + this.$t('key'),
                            trigger: 'blur'
                        }
                    ],
                    value: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + this.$t('value'),
                            trigger: 'blur'
                        }
                    ]
                }
            }
        },
        computed: {
            ...mapState(['userList', 'permission', 'scannerSupportPackageType']),
            detailInfoMap () {
                return [
                    { name: 'version', label: this.$t('version') },
                    { name: 'os', label: 'OS/ARCH' },
                    { name: 'fullPath', label: this.$t('path') },
                    { name: 'size', label: this.$t('size') },
                    { name: 'downloadCount', label: this.$t('downloads') },
                    { name: 'downloads', label: this.$t('downloads') },
                    { name: 'createdBy', label: this.$t('createdBy') },
                    { name: 'createdDate', label: this.$t('createdDate') },
                    { name: 'lastModifiedBy', label: this.$t('lastModifiedBy') },
                    { name: 'lastModifiedDate', label: this.$t('lastModifiedDate') }
                ].filter(({ name }) => name in this.detail.basic)
                    .map(item => ({ ...item, value: this.detail.basic[item.name] }))
            },
            metadataMap () {
                return (this.detail.metadata || []).reduce((target, meta) => {
                    target[meta.key] = meta.value
                    return target
                }, {})
            },
            showRepoScan () {
                const show = RELEASE_MODE !== 'community' || SHOW_ANALYST_MENU
                return show && this.scannerSupportPackageType.join(',').toLowerCase().includes(this.repoType)
            },
            operationBtns () {
                const basic = this.detail.basic
                const metadataMap = this.metadataMap
                return [
                    ...(!metadataMap.forbidStatus
                        ? [
                            this.permission.edit && { clickEvent: () => this.$emit('tag'), label: this.$t('upgrade'), disabled: (basic.stageTag || '').includes('@release') },
                            this.showRepoScan && { clickEvent: () => this.$emit('scan'), label: this.$t('scanArtifact') }
                        ]
                        : []),
                    { clickEvent: () => this.$emit('forbid'), label: metadataMap.forbidStatus ? this.$t('liftBan') : this.$t('forbiddenUse') },
                    this.permission.delete && { clickEvent: () => this.$emit('delete'), label: this.$t('delete') }
                ]
            }
        },
        watch: {
            version: {
                handler (version) {
                    version && this.getDetail()
                },
                immediate: true
            },
            'detail.basic.readme' (val) {
                if (val) {
                    const promise = window.marked ? Promise.resolve() : window.loadLibScript('/ui/libs/marked.min.js')
                    promise.then(() => {
                        this.readmeContent = window.marked.parse(val)
                    })
                }
            }
        },
        methods: {
            convertFileSize,
            ...mapActions([
                'getVersionDetail'
            ]),
            getDetail () {
                this.isLoading = true
                this.getVersionDetail({
                    projectId: this.projectId,
                    repoType: this.repoType,
                    repoName: this.repoName,
                    packageKey: this.packageKey,
                    version: this.version
                }).then(res => {
                    if (!res) return
                    const basic = res.basic
                    this.detail = {
                        ...res,
                        basic: {
                            ...basic,
                            size: basic.size && convertFileSize(basic.size),
                            createdBy: this.userList[basic.createdBy] ? this.userList[basic.createdBy].name : basic.createdBy,
                            createdDate: basic.createdDate && formatDate(basic.createdDate),
                            lastModifiedBy: this.userList[basic.lastModifiedBy] ? this.userList[basic.lastModifiedBy].name : basic.lastModifiedBy,
                            lastModifiedDate: basic.lastModifiedDate && formatDate(basic.lastModifiedDate)
                        }
                    }
                    if (this.repoType === 'docker') {
                        this.selectedHistory = res.history[0] || {}
                    }
                }).finally(() => {
                    this.isLoading = false
                })
            }
        }
    }
</script>
<style lang="scss" scoped>
.common-version-container {
    height: 100%;
    ::v-deep .bk-tab-section {
        height: calc(100% - 50px);
        overflow-y: auto;
    }
    .version-base-info {
        .grid-item {
            display: flex;
            align-items: center;
            height: 40px;
            overflow: hidden;
            > * {
                padding: 0 10px;
            }
            > label {
                line-height: 40px;
                flex-basis: 130px;
                flex-shrink: 0;
                background-color: var(--bgColor);
            }
        }
        &.base-info {
            display: grid;
            grid-template: auto / repeat(2, 1fr);
            border: solid var(--borderColor);
            border-width: 1px 0 0 1px;
            .grid-item {
                border: solid var(--borderColor);
                border-width: 0 1px 1px 0;
                > label {
                    color: var(--fontSubsidiaryColor);
                    border-right: 1px solid var(--borderColor);
                }
            }
            .package-name,
            .package-description {
                grid-column: 1 / 3;
            }
        }
        &.base-info-guide {
            padding: 20px 20px 0;
            border: 1px dashed var(--borderWeightColor);
            border-radius: 4px;
        }
        &.base-info-checksums {
            padding: 20px 10px;
            display: grid;
            background-color: var(--bgLighterColor);
        }
    }
    .version-metadata {
        .version-metadata-add {
            position: absolute;
            display: flex;
            align-items: center;
            justify-content: center;
            top: 0;
            right: 25px;
            width: 35px;
            height: 40px;
            z-index: 1;
            .version-metadata-add-board {
                position: absolute;
                top: 42px;
                right: -25px;
                width: 300px;
                overflow: hidden;
                background: white;
                border-radius: 2px;
                box-shadow: 0 3px 6px rgba(51, 60, 72, 0.4);
                will-change: height;
                transition: all .3s;
            }
        }
    }
    .version-layers {
        padding: 20px;
        display: grid;
        gap: 20px;
        background-color: var(--bgHoverColor);
        .grid-item {
            display: flex;
            overflow: hidden;
            label {
                padding-left: 10px;
                flex-basis: 600px;
            }
        }
        .block-header {
            border-bottom: 1px solid var(--borderWeightColor);
        }
    }
    .version-history {
        height: 100%;
        display: flex;
        &-left {
            height: 100%;
            width: 30%;
            padding-right: 40px;
            margin-right: 40px;
            border-right: 2px solid var(--borderWeightColor);
            overflow-y: auto;
            counter-reset: row-num;
            .version-history-code {
                height: 42px;
                line-height: 42px;
                overflow: hidden;
                text-overflow: ellipsis;
                white-space: nowrap;
                &:hover {
                    background-color: var(--bgLighterColor);
                }
                &.select {
                    background-color: var(--bgHoverColor);
                }
                &:before {
                    display: inline-block;
                    width: 30px;
                    margin-right: 5px;
                    text-align: center;
                    background-color: var(--bgColor);
                    counter-increment: row-num;
                    content: counter(row-num);
                }
            }
        }
        &-right {
            height: 100%;
            overflow-y: auto;
            width: 70%;
            flex: 2;
        }
    }
    .version-dependencies {
        height: 100%;
        overflow-y: auto;
        &-main {
            display: grid;
            grid-template: auto / repeat(4, 1fr);
            grid-gap: 1px;
            background-color: var(--borderWeightColor);
            border: 1px solid var(--borderWeightColor);
        }
        &-more {
            grid-column: 1 / 5;
            line-height: 40px;
            padding-left: 30px;
            background-color: white;
        }
        &-key, &-value {
            line-height: 40px;
            padding-left: 30px;
            padding-right: 10px;
        }
        &-key {
            background-color: var(--bgHoverColor);
        }
        &-value {
            background-color: white;
        }
        &-empty {
            padding: 20px;
            grid-column: 1 / 5;
            background-color: white;
        }
    }
}
</style>
<style lang="scss">
.version-detail-readme {
    font: initial;
    color: initial;
    line-height: initial;
    ul {
        padding-inline-start: 40px;
    }

    li {
        list-style: inherit;
    }

    a {
        color: var(--primaryColor);
        text-decoration: underline;
        &:hover {
            color: var(--primaryHoverColor)
        }
    }

    pre {
        > * {
            white-space: initial;
        }
    }
    h1, h2, h3, h4, h5, h6 {
        margin-top: 0;
        margin-bottom: 16px;
        font-weight: 600;
        line-height: 1.25;
    }
    blockquote, dl, ol, p, pre, table, ul {
        margin-top: 0;
        margin-bottom: 16px;
    }
    h1 {
        font-size: 2rem;
    }
    h2 {
        font-size: 1.5rem;
    }
    h3 {
        font-size: 1.25rem;
    }

    table {
        overflow: auto;
        word-break: normal;
        word-break: keep-all;
        border-collapse: collapse;
        border-spacing: 0;
        tr {
            background-color: var(--bgColor);
            border-top: 1px solid var(--borderColor);
            th, td {
                padding: 6px 13px;
                text-align: left;
                border: 1px solid var(--borderColor);
            }
        }
    }
}
</style>
