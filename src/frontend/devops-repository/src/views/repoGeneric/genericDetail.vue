<template>
    <div>
        <bk-sideslider
            :is-show.sync="detailSlider.show"
            :title="detailSlider.data.name"
            @click.native.stop="() => {}"
            :quick-close="true"
            :width="720">
            <template #content>
                <bk-tab class="detail-container" type="unborder-card" :active.sync="tabName">
                    <bk-tab-panel name="detailInfo" :label="$t('baseInfo')">
                        <div class="version-base-info base-info display-block" :data-title="$t('baseInfo')" v-bkloading="{ isLoading: detailSlider.loading }">
                            <div class="grid-item"
                                v-for="{ name, label, value } in detailInfoMap"
                                :key="name">
                                <label>{{ label }}：</label>
                                <span class="flex-1 text-overflow" :title="value">{{ value }}</span>
                            </div>
                        </div>
                        <div v-if="!detailSlider.folder" class="version-base-info base-info-checksums display-block" data-title="Checksums" v-bkloading="{ isLoading: detailSlider.loading }">
                            <div v-if="detailSlider.data.sha256" class="grid-item">
                                <label>SHA256：</label>
                                <span class="flex-1 text-overflow" :title="detailSlider.data.sha256">{{ detailSlider.data.sha256 }}</span>
                            </div>
                            <div v-if="detailSlider.data.md5" class="grid-item">
                                <label>MD5：</label>
                                <span class="flex-1 text-overflow" :title="detailSlider.data.md5">{{ detailSlider.data.md5 }}</span>
                            </div>
                        </div>
                        <div v-if="!detailSlider.folder && !hasErr" class="display-block" :data-title="$t('commandDownload')">
                            <div class="pl30">
                                <bk-button text theme="primary" @click="createToken">{{ $t('createToken') }}</bk-button>
                                {{ $t('tokenSubTitle') }}
                                <router-link :to="{ name: 'repoToken' }">{{ $t('token') }}</router-link>
                            </div>
                            <code-area class="mt10" :code-list="codeList"></code-area>
                            <create-token-dialog ref="createToken"></create-token-dialog>
                        </div>
                    </bk-tab-panel>
                    <bk-tab-panel v-if="!detailSlider.folder && !hasErr && detailSlider.localNode" name="metaDate" :label="$t('metaData')">
                        <div class="version-metadata display-block" :data-title="$t('metaData')">
                            <div class="version-metadata-add" v-bk-clickoutside="hiddenAddMetadata">
                                <i @click="metadata.show ? hiddenAddMetadata() : showAddMetadata()" class="devops-icon icon-plus flex-center hover-btn"></i>
                                <div class="version-metadata-add-board"
                                    :style="{ height: metadata.show ? '230px' : '0' }">
                                    <bk-form class="p20" :label-width="80" :model="metadata" :rules="rules" ref="metadatForm">
                                        <bk-form-item :label="$t('key')" :required="true" property="key">
                                            <bk-input size="small" v-model="metadata.key" :placeholder="$t('key')"></bk-input>
                                        </bk-form-item>
                                        <bk-form-item :label="$t('value')" :required="true" property="value">
                                            <bk-input size="small" v-model="metadata.value" :placeholder="$t('value')"></bk-input>
                                        </bk-form-item>
                                        <bk-form-item :label="$t('description')">
                                            <bk-input size="small" v-model="metadata.description" :placeholder="$t('description')"></bk-input>
                                        </bk-form-item>
                                        <bk-form-item>
                                            <bk-button size="small" theme="default" @click.stop="hiddenAddMetadata">{{$t('cancel')}}</bk-button>
                                            <bk-button class="ml5" size="small" :loading="metadata.loading" theme="primary" @click="addMetadataHandler">{{$t('confirm')}}</bk-button>
                                        </bk-form-item>
                                    </bk-form>
                                </div>
                            </div>
                            <bk-table
                                :data="(detailSlider.data.nodeMetadata || []).filter(m => !m.system)"
                                :outer-border="false"
                                :row-border="false"
                                size="small">
                                <template #empty>
                                    <empty-data :is-loading="detailSlider.loading"></empty-data>
                                </template>
                                <!-- <bk-table-column :label="$t('key')" prop="key" show-overflow-tooltip></bk-table-column>
                            <bk-table-column :label="$t('value')" prop="value" show-overflow-tooltip></bk-table-column> -->
                                <bk-table-column :label="$t('metadata')">
                                    <template #default="{ row }">
                                        <metadata-tag :metadata="row" :metadata-label-list="detailSlider.metadataLabelList" />
                                    </template>
                                </bk-table-column>

                        <bk-table-column :label="$t('description')" prop="description" show-overflow-tooltip></bk-table-column>
                        <bk-table-column width="60">
                            <template #default="{ row }">
                                <Icon class="hover-btn" size="24" name="icon-delete" v-if="!row.system"
                                    @click.native.stop="deleteMetadataHandler(row)" />
                            </template>
                        </bk-table-column>
                    </bk-table>
                </div>
            </bk-tab-panel>
        </bk-tab>
        </template>
    </bk-sideslider>
    <iam-deny-dialog :visible.sync="showIamDenyDialog" :show-data="showData"></iam-deny-dialog>
    </div>
</template>
<script>
    import metadataTag from '@repository/views/repoCommon/metadataTag'
    import CodeArea from '@repository/components/CodeArea'
    import createTokenDialog from '@repository/views/repoToken/createTokenDialog'
    import iamDenyDialog from '@repository/components/IamDenyDialog/IamDenyDialog'
    import { mapState, mapActions } from 'vuex'
    import { convertFileSize, formatDate } from '@repository/utils'
    export default {
        name: 'genericDetail',
        components: { CodeArea, createTokenDialog, metadataTag, iamDenyDialog },
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
                    data: {},
                    metadataLabelList: []
                },
                metadata: {
                    show: false,
                    loading: false,
                    key: '',
                    value: '',
                    description: ''
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
                },
                showIamDenyDialog: false,
                showData: {},
                hasErr: false
            }
        },
        computed: {
            ...mapState(['userInfo', 'userList']),
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
            },
            codeList () {
                const { projectId, repoName, path } = this.detailSlider
                return [
                    `wget --user=${this.userInfo.username} --password=<PERSONAL_ACCESS_TOKEN> "${location.origin}/generic/${projectId}/${repoName}${path}"`
                ]
            }
        },
        methods: {
            ...mapActions(['getNodeDetail', 'addMetadata', 'deleteMetadata', 'getPermissionUrl']),
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
                    fullPath: this.detailSlider.path,
                    localNode: this.detailSlider.localNode
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
                }).catch(e => {
                    this.hasErr = true
                    if (e.status === 403) {
                        this.getPermissionUrl({
                            body: {
                                projectId: this.detailSlider.projectId,
                                action: 'READ',
                                resourceType: 'NODE',
                                uid: this.userInfo.name,
                                repoName: this.detailSlider.repoName,
                                path: this.detailSlider.path
                            }
                        }).then(res => {
                            if (res !== '') {
                                this.showIamDenyDialog = true
                                this.showData = {
                                    projectId: this.projectId,
                                    repoName: this.repoName,
                                    action: 'READ',
                                    path: this.detailSlider.path,
                                    url: res
                                }
                            } else {
                                this.$bkMessage({
                                    theme: 'error',
                                    message: e.message
                                })
                            }
                        })
                    } else {
                        this.$bkMessage({
                            theme: 'error',
                            message: e.message
                        })
                    }
                }).finally(() => {
                    this.detailSlider.loading = false
                })
            },
            createToken () {
                this.$refs.createToken.showDialogHandler()
            },
            showAddMetadata () {
                this.metadata = {
                    show: true,
                    loading: false,
                    key: '',
                    value: '',
                    description: ''
                }
            },
            hiddenAddMetadata () {
                this.metadata.show = false
                this.$refs.metadatForm.clearError()
            },
            async addMetadataHandler () {
                await this.$refs.metadatForm.validate()
                this.metadata.loading = true
                const { key, value, description } = this.metadata
                this.addMetadata({
                    projectId: this.detailSlider.projectId,
                    repoName: this.detailSlider.repoName,
                    fullPath: this.detailSlider.data.fullPath,
                    body: {
                        nodeMetadata: [{ key, value, description }]
                    }
                }).then(() => {
                    this.$bkMessage({
                        theme: 'success',
                        message: this.$t('add') + this.$t('space') + this.$t('success')
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
                        keyList: [row.key]
                    }
                }).then(() => {
                    this.$bkMessage({
                        theme: 'success',
                        message: this.$t('delete') + this.$t('space') + this.$t('metadata') + this.$t('space') + this.$t('success')
                    })
                    this.getDetail()
                })
            }
        }
    }
</script>
<style lang="scss" scoped>
.detail-container {
    height: 100%;
    ::v-deep .bk-tab-section {
        height: calc(100% - 50px);
        overflow-y: auto;
    }
    .version-base-info {
        &.base-info {
            padding: 20px;
            display: grid;
            gap: 20px;
            background-color: var(--bgHoverColor);
        }
        &.base-info-checksums {
            padding: 20px;
            display: grid;
            gap: 20px;
            background-color: var(--bgHoverColor);
        }
        .grid-item {
            display: flex;
            overflow: hidden;
            label {
                flex-basis: 80px;
                text-align: right;
            }
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
}
</style>
