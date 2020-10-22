<template>
    <bk-tab class="common-version-container" type="unborder-card" v-bkloading="{ isLoading }">
        <bk-tab-panel v-if="detail.basic" name="versionBaseInfo" :label="$t('baseInfo')">
            <div class="version-base-info">
                <div class="base-info-left">
                    <div class="base-info-guide">
                        <header class="base-info-header">{{ $t('useTips') }}</header>
                        <div class="section-main">
                            <div class="sub-section flex-column" v-for="block in articleInstall[0].main" :key="block.subTitle">
                                <span class="mb10">{{ block.subTitle }}</span>
                                <code-area v-if="block.codeList && block.codeList.length" :code-list="block.codeList"></code-area>
                            </div>
                        </div>
                    </div>
                    <div class="base-info-checksums">
                        <header class="base-info-header">Checksums</header>
                        <div class="mt20 flex-column version-checksums">
                            <div v-if="detail.basic.sha256" class="mt20 flex-align-center">
                                <span class="display-key">SHA256</span>
                                <span class="display-value">{{ detail.basic.sha256 }}</span>
                            </div>
                            <div v-if="detail.basic.md5" class="mt20 flex-align-center">
                                <span class="display-key">MD5</span>
                                <span class="display-value">{{ detail.basic.md5 }}</span>
                            </div>
                        </div>
                    </div>
                </div>
                <div class="base-info">
                    <header class="base-info-header">{{ $t('baseInfo') }}</header>
                    <div class="mt20 flex-column">
                        <div v-for="key in Object.keys(detailInfoMap)" :key="key">
                            <div class="mt20 flex-align-center" v-if="detail.basic.hasOwnProperty(key)">
                                <span class="display-key">{{ detailInfoMap[key] }}</span>
                                <span class="display-value">
                                    {{ detail.basic[key] }}
                                    <template v-if="key === 'version'">
                                        <span class="mr5 repo-tag"
                                            v-for="tag in detail.basic.stageTag"
                                            :key="tag">
                                            {{ tag }}
                                        </span>
                                    </template>
                                </span>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </bk-tab-panel>
        <bk-tab-panel v-if="detail.metadata" name="versionMetaData" :label="$t('metaData')">
            <div class="flex-column version-metadata">
                <div class="pl20 pb10 flex-align-center metadata-thead">
                    <span class="display-key">{{ $t('key') }}</span>
                    <span class="display-value">{{ $t('value') }}</span>
                </div>
                <div class="pl20 pb10 pt10 flex-align-center metadata-tr" v-for="([key, value]) in Object.entries(detail.metadata)" :key="key">
                    <span class="display-key">{{ key }}</span>
                    <span class="display-value">{{ value }}</span>
                </div>
                <empty-data v-if="!Object.keys(detail.metadata).length"></empty-data>
            </div>
        </bk-tab-panel>
        <bk-tab-panel v-if="detail.layers" name="versionLayers" label="Layers">
            <div class="mt20 flex-column">
                <div class="pl10 pb10 flex-align-center version-layers">
                    <span class="display-key">ID</span>
                    <span class="display-value">{{ $t('size') }}</span>
                </div>
                <div class="pl10 pb10 pt10 flex-align-center version-layers" v-for="layer in detail.layers" :key="layer.digest">
                    <span class="display-key">{{ layer.digest }}</span>
                    <span class="display-value">{{ convertFileSize(layer.size) }}</span>
                </div>
            </div>
        </bk-tab-panel>
        <bk-tab-panel v-if="detail.history" name="versionImageHistory" label="IMAGE HISTORY">
            <div class="version-history">
                <div class="version-history-left">
                    <div class="version-history-code hover-btn"
                        v-for="(code, index) in detail.history"
                        :key="code.created_by"
                        :class="{ select: selectedHistory.created_by === code.created_by }"
                        @click="selectedHistory = code">
                        <span class="version-history-index">{{index + 1}}</span>
                        {{code.created_by}}
                    </div>
                </div>
                <div class="version-history-right">
                    <header class="version-history-header">Command</header>
                    <code-area class="mt20 version-history-code"
                        :line-number="false"
                        :code-list="[selectedHistory.created_by]">
                    </code-area>
                </div>
            </div>
        </bk-tab-panel>
        <bk-tab-panel v-if="detail.dependencyInfo" name="versionDependencies" :label="$t('dependencies')">
            <article class="version-dependencies">
                <section v-for="type in ['dependencies', 'devDependencies']" :key="type">
                    <header class="version-dependencies-header">{{ type }}</header>
                    <div class="version-dependencies-main">
                        <template v-if="detail.dependencyInfo[type].length">
                            <div class="flex-align-center version-dependencies-item"
                                v-for="{ name, version } in detail.dependencyInfo[type]"
                                :key="name + Math.random()">
                                <div class="version-dependencies-key">{{ name }}</div>
                                <div class="version-dependencies-value">{{ version }}</div>
                            </div>
                            <div class="flex-align-center hover-btn version-dependencies-more"
                                v-if="type === 'dependents' && dependentsPage"
                                @click="loadMore">
                                {{ $t('loadMore') }}
                            </div>
                        </template>
                        <div v-else>{{$t('noData')}}</div>
                    </div>
                </section>
                <section>
                    <header class="version-dependencies-header">dependents</header>
                    <div class="version-dependencies-main version-dependencies-dependents">
                        <template v-if="detail.dependencyInfo.dependents.length">
                            <div class="flex-align-center version-dependencies-item"
                                v-for="({ name }, index) in detail.dependencyInfo.dependents"
                                :key="name + Math.random()">
                                <div :class="`version-dependencies-${index % 2 ? 'value' : 'key'}`">{{ name }}</div>
                            </div>
                            <div class="flex-align-center hover-btn version-dependencies-more"
                                v-if="dependentsPage"
                                @click="loadMore">
                                {{ $t('loadMore') }}
                            </div>
                        </template>
                        <div v-else>{{$t('noData')}}</div>
                    </div>
                </section>
            </article>
        </bk-tab-panel>
    </bk-tab>
</template>
<script>
    import emptyData from '@/components/emptyData'
    import CodeArea from '@/components/CodeArea'
    import { mapState, mapActions } from 'vuex'
    import { convertFileSize, formatDate } from '@/utils'
    import repoGuideMixin from '../repoGuideMixin'
    import commonMixin from './commonMixin'
    export default {
        name: 'commonVersionDetail',
        components: { CodeArea, emptyData },
        mixins: [repoGuideMixin, commonMixin],
        data () {
            return {
                isLoading: false,
                detail: {
                },
                // 当前已请求页数，0代表没有更多
                dependentsPage: 1,
                pagination: {
                    count: 1,
                    current: 1,
                    limit: 10,
                    'limit-list': [10, 20, 40]
                },
                selectedHistory: {}
            }
        },
        computed: {
            ...mapState(['userList']),
            detailInfoMap () {
                return {
                    'version': this.$t('version'),
                    'os': 'OS/ARCH',
                    'fullPath': this.$t('path'),
                    'size': this.$t('size'),
                    'downloadCount': this.$t('downloads'),
                    'downloads': this.$t('downloads'),
                    'createdBy': this.$t('createdBy'),
                    'createdDate': this.$t('createdDate'),
                    'lastModifiedBy': this.$t('lastModifiedBy'),
                    'lastModifiedDate': this.$t('lastModifiedDate')
                }
            }
        },
        created () {
            this.initDetail()
        },
        methods: {
            convertFileSize,
            ...mapActions([
                'getVersionDetail',
                'getNpmDependents'
            ]),
            initDetail () {
                this.getDetail()
            },
            getDetail () {
                this.isLoading = true
                this.getVersionDetail({
                    projectId: this.projectId,
                    repoType: this.repoType,
                    repoName: this.repoName,
                    packageKey: this.packageKey,
                    version: this.version
                }).then(res => {
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
                    if (this.repoType === 'npm') {
                        const dependents = res.dependencyInfo.dependents
                        this.detail.dependencyInfo.dependents = dependents.records
                        if (dependents.totalRecords < 20) {
                            this.dependentsPage = 0
                        }
                    }
                    if (this.repoType === 'docker') {
                        this.selectedHistory = res.history[0] || {}
                    }
                }).finally(() => {
                    this.isLoading = false
                })
            },
            loadMore () {
                if (this.isLoading) return
                this.isLoading = true
                this.getNpmDependents({
                    projectId: this.projectId,
                    repoName: this.repoName,
                    packageKey: this.packageKey,
                    current: this.dependentsPage + 1
                }).then(({ records }) => {
                    this.detail.dependencyInfo.dependents.push(...records)
                    this.dependentsPage++
                    if (records.length < 20) this.dependentsPage = 0
                }).finally(() => {
                    this.isLoading = false
                })
            }
        }
    }
</script>
<style lang="scss" scoped>
@import '@/scss/conf';
.common-version-container {
    height: 100%;
    /deep/ .bk-tab-section {
        height: calc(100% - 40px);
        .bk-tab-content {
            height: 100%;
        }
    }
    .version-base-info {
        height: calc(100% + 20px);
        margin-bottom: -20px;
        display: flex;
        .base-info-left {
            flex: 3;
            padding-top: 20px;
            padding-right: 20px;
            border-right: 1px solid $borderWeightColor;
            .base-info-guide {
                border-top: 1px solid $borderWeightColor;
                .section-main {
                    margin-top: 20px;
                    padding: 20px;
                    border: 2px dashed $borderWeightColor;
                    border-radius: 5px;
                    .sub-section {
                        & + .sub-section {
                            margin-top: 20px;
                        }
                    }
                }
            }
            .base-info-checksums {
                margin-top: 20px;
                border-top: 1px solid $borderWeightColor;
            }
        }
        .base-info {
            flex: 2;
            margin-top: 20px;
            margin-left: 20px;
            border-top: 1px solid $borderWeightColor;
        }
        .base-info-header {
            position: absolute;
            padding-right: 20px;
            margin-top: -10px;
            color: $fontBoldColor;
            background-color: white;
            font-weight: bolder;
        }
    }
    .version-checksums {
        .display-value {
            flex: 12;
        }
    }
    .version-metadata {
        height: 100%;
        overflow: auto;
        .metadata-thead, .metadata-tr {
            border-bottom: 1px solid $borderWeightColor;
            line-height: 2;
        }
        .display-key {
            text-align: left;
        }
    }
    .version-layers {
        border-bottom: 1px solid $borderWeightColor;
        line-height: 2;
        .display-key {
            text-align: left;
            flex: 6;
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
            border-right: 2px solid $borderWeightColor;
            overflow-y: auto;
            .version-history-code {
                height: 42px;
                line-height: 42px;
                overflow: hidden;
                text-overflow: ellipsis;
                white-space: nowrap;
                &:hover {
                    background-color: $bgHoverColor;
                }
                &.select {
                    background-color: #ebedf0;
                }
                .version-history-index {
                    display: inline-block;
                    width: 30px;
                    margin-right: 5px;
                    text-align: center;
                    background-color: #f9f9f9;
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
        height: calc(100% + 20px);
        margin-bottom: -20px;
        overflow-y: auto;
        &-header {
            font-size: 16px;
            font-weight: bold;
            line-height: 2;
        }
        &-main {
            display: grid;
            grid-template: auto / 1fr 1fr;
            margin: 5px 0 20px;
        }
        &-dependents {
            grid-template: auto / 1fr 1fr 1fr 1fr;
        }
        &-item {
            border-bottom: 1px solid $borderWeightColor;
            &:first-child, &:nth-child(2) {
                border-top: 1px solid $borderWeightColor;
            }
        }
        &-more {
            line-height: 40px;
            padding-left: 30px;
        }
        &-key, &-value {
            flex: 1;
            line-height: 40px;
            padding-left: 30px;
        }
        &-key {
            background-color: $bgLightColor;
        }
    }
    .display-key {
        flex: 1;
        text-align: right;
        margin-right: 40px;
    }
    .display-value {
        flex: 3;
    }
}
</style>
