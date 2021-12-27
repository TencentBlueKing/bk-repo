<template>
    <bk-sideslider
        :is-show.sync="detailSlider.show"
        :title="detailSlider.data.name"
        @click.native.stop="() => {}"
        :quick-close="true"
        :width="720">
        <template #content><bk-tab class="detail-container" type="unborder-card" :active.sync="tabName">
            <bk-tab-panel name="detailInfo" :label="$t('baseInfo')">
                <div class="version-base-info base-info" :data-title="$t('baseInfo')" v-bkloading="{ isLoading: detailSlider.loading }">
                    <div class="grid-item"
                        v-for="{ name, label, value } in detailInfoMap"
                        :key="name">
                        <label>{{ label }}：</label>
                        <span class="flex-1 text-overflow" :title="value">{{ value }}</span>
                    </div>
                </div>
                <div v-if="!detailSlider.folder" class="version-base-info base-info-checksums" data-title="Checksums" v-bkloading="{ isLoading: detailSlider.loading }">
                    <div v-if="detailSlider.data.sha256" class="grid-item">
                        <label>SHA256：</label>
                        <span class="flex-1 text-overflow" :title="detailSlider.data.sha256">{{ detailSlider.data.sha256 }}</span>
                    </div>
                    <div v-if="detailSlider.data.md5" class="grid-item">
                        <label>MD5：</label>
                        <span class="flex-1 text-overflow" :title="detailSlider.data.md5">{{ detailSlider.data.md5 }}</span>
                    </div>
                </div>
            </bk-tab-panel>
            <bk-tab-panel v-if="!detailSlider.folder" name="metaDate" :label="$t('metaData')">
                <div class="version-metadata" data-title="元数据">
                    <div class="version-metadata-add" v-bk-clickoutside="hiddenAddMetadata">
                        <i @click="metadata.show ? hiddenAddMetadata() : showAddMetadata()" class="devops-icon icon-plus flex-center hover-btn"></i>
                        <div class="version-metadata-add-board"
                            :style="{ height: metadata.show ? '180px' : '0' }">
                            <bk-form class="p20" :label-width="80" :model="metadata" :rules="rules" ref="metadatForm">
                                <bk-form-item :label="$t('key')" :required="true" property="key">
                                    <bk-input size="small" v-model="metadata.key" :placeholder="$t('key')"></bk-input>
                                </bk-form-item>
                                <bk-form-item :label="$t('value')" :required="true" property="value">
                                    <bk-input size="small" v-model="metadata.value" :placeholder="$t('value')"></bk-input>
                                </bk-form-item>
                                <bk-form-item>
                                    <bk-button size="small" theme="default" @click.stop="hiddenAddMetadata">{{$t('cancel')}}</bk-button>
                                    <bk-button class="ml5" size="small" :loading="metadata.loading" theme="primary" @click="addMetadataHandler">{{$t('confirm')}}</bk-button>
                                </bk-form-item>
                            </bk-form>
                        </div>
                    </div>
                    <bk-table
                        :data="Object.entries(detailSlider.data.metadata || {})"
                        :outer-border="false"
                        :row-border="false"
                        size="small">
                        <template #empty>
                            <empty-data :is-loading="detailSlider.loading">
                                <span class="ml10">暂无元数据，</span>
                                <bk-button text @click="showAddMetadata">即刻添加</bk-button>
                            </empty-data>
                        </template>
                        <bk-table-column :label="$t('key')" prop="0" width="250"></bk-table-column>
                        <bk-table-column :label="$t('value')" prop="1"></bk-table-column>
                        <bk-table-column width="60">
                            <template #default="{ row }">
                                <i class="devops-icon icon-delete hover-btn hover-danger" @click="deleteMetadataHandler(row)"></i>
                            </template>
                        </bk-table-column>
                    </bk-table>
                </div>
            </bk-tab-panel>
        </bk-tab></template>
    </bk-sideslider>
</template>
<script>
    import { mapState, mapActions } from 'vuex'
    import { convertFileSize, formatDate } from '@repository/utils'
    export default {
        name: 'genericDetail',
        data () {
            return {
                tabName: 'detailInfo',
                detailSlider: {
                    show: false,
                    loading: false,
                    projectId: '',
                    repoName: '',
                    folder: false,
                    path: '',
                    data: {}
                },
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
            ...mapState(['userList']),
            detailInfoMap () {
                return [
                    { name: 'fullPath', label: this.$t('path') },
                    { name: 'size', label: this.$t('size') },
                    { name: 'createdBy', label: this.$t('createdBy') },
                    { name: 'createdDate', label: this.$t('createdDate') },
                    { name: 'lastModifiedBy', label: this.$t('lastModifiedBy') },
                    { name: 'lastModifiedDate', label: this.$t('lastModifiedDate') }
                ].filter(({ name }) => name in this.detailSlider.data && (name !== 'size' || !this.detailSlider.data.folder))
                    .map(item => ({ ...item, value: this.detailSlider.data[item.name] }))
            }
        },
        methods: {
            ...mapActions(['getNodeDetail', 'addMetadata', 'deleteMetadata']),
            setData (data) {
                this.detailSlider = {
                    ...this.detailSlider,
                    ...data
                }
                this.getDetail()
            },
            getDetail () {
                this.detailSlider.loading = true
                this.getNodeDetail({
                    projectId: this.detailSlider.projectId,
                    repoName: this.detailSlider.repoName,
                    fullPath: this.detailSlider.path
                }).then(data => {
                    this.detailSlider.data = {
                        ...data,
                        name: data.name || this.repoName,
                        size: convertFileSize(data.size),
                        createdBy: this.userList[data.createdBy] ? this.userList[data.createdBy].name : data.createdBy,
                        createdDate: formatDate(data.createdDate),
                        lastModifiedBy: this.userList[data.lastModifiedBy] ? this.userList[data.lastModifiedBy].name : data.lastModifiedBy,
                        lastModifiedDate: formatDate(data.lastModifiedDate)
                    }
                }).finally(() => {
                    this.detailSlider.loading = false
                })
            },
            showAddMetadata () {
                this.metadata = {
                    show: true,
                    loading: false,
                    key: '',
                    value: ''
                }
            },
            hiddenAddMetadata () {
                this.metadata.show = false
                this.$refs.metadatForm.clearError()
            },
            async addMetadataHandler () {
                await this.$refs.metadatForm.validate()
                this.metadata.loading = true
                this.addMetadata({
                    projectId: this.detailSlider.projectId,
                    repoName: this.detailSlider.repoName,
                    fullPath: this.detailSlider.data.fullPath,
                    body: {
                        metadata: {
                            [this.metadata.key]: this.metadata.value
                        }
                    }
                }).then(() => {
                    this.$bkMessage({
                        theme: 'success',
                        message: this.$t('add') + this.$t('success')
                    })
                    this.hiddenAddMetadata()
                    this.getDetail()
                }).finally(() => {
                    this.metadata.loading = false
                })
            },
            deleteMetadataHandler (row) {
                this.deleteMetadata({
                    projectId: this.detailSlider.projectId,
                    repoName: this.detailSlider.repoName,
                    fullPath: this.detailSlider.data.fullPath,
                    body: {
                        keyList: [row[0]]
                    }
                }).finally(() => {
                    this.getDetail()
                })
            }
        }
    }
</script>
<style lang="scss" scoped>
@mixin display-block {
    position: relative;
    margin-top: 55px;
    &:first-child {
        margin-top: 35px;
    }
    &:before {
        position: absolute;
        top: -30px;
        left: 20px;
        content: '';
        width: 3px;
        height: 16px;
        background-color: var(--primaryColor);
    }
    &:after {
        position: absolute;
        top: -35px;
        left: 30px;
        content: attr(data-title);
        font-size: 16px;
        font-weight: bold;
    }
}
.detail-container {
    height: 100%;
    ::v-deep .bk-tab-section {
        height: calc(100% - 50px);
        overflow-y: auto;
    }
    .version-base-info {
        &.base-info,
        &.base-info-checksums {
            @include display-block;
        }
        &.base-info {
            padding: 20px;
            display: grid;
            grid-gap: 20px;
            background-color: var(--bgHoverColor);
        }
        &.base-info-checksums {
            padding: 20px;
            display: grid;
            grid-gap: 20px;
            background-color: var(--bgHoverColor);
        }
        .grid-item {
            display: flex;
            overflow: hidden;
            label {
                flex-basis: 100px;
                text-align: right;
            }
        }
    }
    .version-metadata {
        @include display-block;
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
            .icon-plus {
                width: 100%;
                height: 100%;
                &:hover {
                    background-color: var(--bgHoverColor);
                }
            }
        }
    }
}
</style>
