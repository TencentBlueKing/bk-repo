<template>
    <bk-tab class="docker-tag-container" type="unborder-card">
        <bk-tab-panel name="tagBaseInfo" :label="$t('baseInfo')">
            <div class="tag-base-info">
                <div class="base-info-left">
                    <div class="base-info-guide">
                        <header class="base-info-header">{{ $t('useTips') }}</header>
                        <div class="mt20 base-info-guide-main flex-column">
                            <span class="mb10">{{ $t('useSubTips') }}</span>
                            <code-area :code-list="[`docker pull ${$route.query.docker}:${$route.query.tag}`]"></code-area>
                        </div>
                    </div>
                    <div class="base-info-checksums">
                        <header class="base-info-header">Checksums</header>
                        <div class="mt20 flex-column tag-checksums">
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
                            <span class="display-key">tag</span>
                            <span class="display-value">{{ detail.basic.tag }}</span>
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
        <bk-tab-panel name="tagMetaData" :label="$t('metaData')">
            <div class="mt20 flex-column">
                <div class="pl10 pb10 flex-align-center tag-metadata">
                    <span class="display-key">{{ $t('key') }}</span>
                    <span class="display-value">{{ $t('value') }}</span>
                </div>
                <div class="pl10 pb10 pt10 flex-align-center tag-metadata" v-for="([key, value]) in Object.entries(detail.metadata)" :key="key">
                    <span class="display-key">{{ key }}</span>
                    <span class="display-value">{{ value }}</span>
                </div>
            </div>
        </bk-tab-panel>
        <bk-tab-panel name="tagLayers" label="Layers">
            <div class="mt20 flex-column">
                <div class="pl10 pb10 flex-align-center tag-layers">
                    <span class="display-key">ID</span>
                    <span class="display-value">{{ $t('size') }}</span>
                </div>
                <div class="pl10 pb10 pt10 flex-align-center tag-layers" v-for="layer in detail.layers" :key="layer.digest">
                    <span class="display-key">{{ layer.digest }}</span>
                    <span class="display-value">{{ convertFileSize(layer.size) }}</span>
                </div>
            </div>
        </bk-tab-panel>
        <bk-tab-panel name="tagImageHistory" label="IMAGE HISTORY">
            <div class="tag-history">
                <div class="tag-history-left">
                    <header class="tag-history-header"></header>
                    <div class="tag-history-code hover-btn"
                        v-for="(code, index) in detail.history"
                        :key="code.created_by"
                        :class="{ select: selectedHistory.created_by === code.created_by }"
                        @click="selectedHistory = code">
                        <span class="tag-history-index">{{index + 1}}</span>
                        {{code.created_by}}
                    </div>
                </div>
                <div class="tag-history-right">
                    <header class="tag-history-header">Command</header>
                    <code-area class="mt20 tag-history-code"
                        :line-number="false"
                        :code-list="[selectedHistory.created_by]">
                    </code-area>
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
        name: 'dockerDetail',
        components: { CodeArea },
        props: {
            tag: Object
        },
        data () {
            return {
                convertFileSize,
                detail: {
                    basic: {},
                    history: [],
                    metadata: {},
                    layers: []
                },
                pagination: {
                    count: 1,
                    current: 1,
                    limit: 10,
                    'limit-list': [10, 20, 40]
                },
                selectedHistory: {}
            }
        },
        watch: {
            tag () {
                this.initDetail()
            }
        },
        created () {
            this.initDetail()
        },
        methods: {
            ...mapActions([
                'getDockerTagDetail'
            ]),
            initDetail () {
                this.getDetail()
            },
            getDetail () {
                this.getDockerTagDetail({
                    projectId: this.$route.params.projectId,
                    repoName: this.$route.query.name,
                    dockerName: this.$route.query.docker,
                    tagName: this.$route.query.tag
                }).then(res => {
                    this.detail = res
                })
            }
        }
    }
</script>
<style lang="scss" scoped>
@import '@/scss/conf';
.docker-tag-container {
    height: 100%;
    /deep/ .bk-tab-section {
        height: calc(100% - 40px);
        .bk-tab-content {
            height: 100%;
        }
    }
    .tag-base-info {
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
    .tag-checksums {
        .display-value {
            flex: 12;
        }
    }
    .tag-metadata {
        border-bottom: 1px solid $borderWeightColor;
        line-height: 2;
        .display-key {
            text-align: left;
        }
    }
    .tag-layers {
        border-bottom: 1px solid $borderWeightColor;
        line-height: 2;
        .display-key {
            text-align: left;
            flex: 6;
        }
    }
    .tag-history {
        height: 100%;
        display: flex;
        &-left {
            height: 100%;
            width: 30%;
            padding-right: 40px;
            margin-right: 40px;
            border-right: 2px solid $borderWeightColor;
            .tag-history-code {
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
                .tag-history-index {
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
