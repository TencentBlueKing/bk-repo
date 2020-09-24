<template>
    <bk-tab class="npm-version-container" type="unborder-card">
        <bk-tab-panel name="versionBaseInfo" :label="$t('baseInfo')">
            <div class="version-base-info">
                <div class="base-info-left">
                    <div class="base-info-guide">
                        <header class="base-info-header">{{ $t('useTips') }}</header>
                        <div class="mt20 base-info-guide-main flex-column">
                            <span class="mb10">{{ $t('useSubTips') }}</span>
                            <code-area :code-list="[`npm install ${npmName}@${$route.query.version}`]"></code-area>
                        </div>
                        <div class="mt20 base-info-guide-main flex-column">
                            <span class="mb10">{{ $t('useSubTips') }}</span>
                            <code-area :code-list="[`npm install ${npmName}@${$route.query.version} --registry http://bkrepo.canway.soft/xxx/npm-local/`]"></code-area>
                        </div>
                    </div>
                    <div class="base-info-checksums">
                        <header class="base-info-header">Checksums</header>
                        <div class="mt20 flex-column version-checksums">
                            <div class="mt20 flex-align-center">
                                <span class="display-key">SHA256</span>
                                <span class="display-value">{{ detail.basic.sha256 }}</span>
                            </div>
                        </div>
                    </div>
                </div>
                <div class="base-info">
                    <header class="base-info-header">{{ $t('baseInfo') }}</header>
                    <div class="mt20 flex-column">
                        <div class="mt20 flex-align-center">
                            <span class="display-key">version</span>
                            <span class="display-value">{{ detail.basic.version }}</span>
                        </div>
                        <div class="mt20 flex-align-center">
                            <span class="display-key">Image ID</span>
                            <span class="display-value">-</span>
                        </div>
                        <div class="mt20 flex-align-center">
                            <span class="display-key">OS/ARCH</span>
                            <span class="display-value">{{ detail.basic.os }}</span>
                        </div>
                        <div class="mt20 flex-align-center">
                            <span class="display-key">{{ $t('size') }}</span>
                            <span class="display-value">{{ convertFileSize(detail.basic.size) }}</span>
                        </div>
                        <div class="mt20 flex-align-center">
                            <span class="display-key">{{ $t('downloadCount') }}</span>
                            <span class="display-value">{{ detail.basic.downloadCount }}</span>
                        </div>
                        <div class="mt20 flex-align-center">
                            <span class="display-key">{{ $t('lastModifiedDate') }}</span>
                            <span class="display-value">{{ new Date(detail.basic.lastModifiedDate).toLocaleString() }}</span>
                        </div>
                        <div class="mt20 flex-align-center">
                            <span class="display-key">{{ $t('lastModifiedBy') }}</span>
                            <span class="display-value">{{ detail.basic.lastModifiedBy }}</span>
                        </div>
                    </div>
                </div>
            </div>
        </bk-tab-panel>
        <bk-tab-panel name="versionMetaData" :label="$t('metaData')">
            <div class="mt20 flex-column">
                <div class="pl10 pb10 flex-align-center version-metadata">
                    <span class="display-key">{{ $t('key') }}</span>
                    <span class="display-value">{{ $t('value') }}</span>
                </div>
                <div class="pl10 pb10 pt10 flex-align-center version-metadata" v-for="([key, value]) in Object.entries(detail.metadata)" :key="key">
                    <span class="display-key">{{ key }}</span>
                    <span class="display-value">{{ value }}</span>
                </div>
            </div>
        </bk-tab-panel>
    </bk-tab>
</template>
<script>
    import CodeArea from '@/components/CodeArea'
    import { mapActions } from 'vuex'
    import { convertFileSize } from '@/utils'
    export default {
        name: 'npmDetail',
        components: { CodeArea },
        props: {
            version: Object
        },
        data () {
            return {
                convertFileSize,
                detail: {
                    basic: {},
                    history: [],
                    metadata: {},
                    layers: []
                }
            }
        },
        computed: {
            projectId () {
                return this.$route.params.projectId
            },
            repoName () {
                return this.$route.query.name
            },
            npmName () {
                return this.$route.query.npm
            },
            npmVersion () {
                return this.$route.query.version
            }
        },
        watch: {
            version () {
                this.initDetail()
            }
        },
        created () {
            this.initDetail()
        },
        methods: {
            ...mapActions([
                'getNpmPkgVersionDetail'
            ]),
            initDetail () {
                this.getDetail()
            },
            getDetail () {
                this.getNpmPkgVersionDetail({
                    projectId: this.$route.params.projectId,
                    repoName: this.$route.query.name,
                    npmName: this.$route.query.npm,
                    versionName: this.$route.query.version
                }).then(res => {
                    this.detail = res
                })
            }
        }
    }
</script>
<style lang="scss" scoped>
@import '@/scss/conf';
.npm-version-container {
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
            padding-top: 40px;
            padding-right: 20px;
            border-right: 1px solid $borderWeightColor;
            .base-info-guide {
                border-top: 1px solid $borderWeightColor;
                .base-info-guide-main {
                    padding: 10px;
                    border: 1px dashed $borderWeightColor;
                    border-radius: 5px;
                }
            }
            .base-info-checksums {
                margin-top: 20px;
                border-top: 1px solid $borderWeightColor;
            }
        }
        .base-info {
            flex: 2;
            margin-top: 40px;
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
        border-bottom: 1px solid $borderWeightColor;
        line-height: 2;
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
            width: 70%;
            flex: 2;
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
