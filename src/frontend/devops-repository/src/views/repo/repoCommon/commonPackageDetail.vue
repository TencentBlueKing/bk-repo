<template>
    <div class="common-package-detail flex-column">
        <div class="common-package-base-info flex-align-center" v-bkloading="{ isLoading: infoLoading }">
            <icon size="80" name="default-docker" />
            <div class="ml20 common-package-title flex-column">
                <span class="mb10 title" :title="pkg.name">{{ pkg.name }}
                    <span class="ml10 subtitle repo-tag" v-if="pkg.type === 'MAVEN'">{{ pkg.key.replace(/^.*\/\/(.+):.*$/, '$1') }}</span>
                </span>
                <div class="flex-align-center">
                    <div class="mr50">{{ `${$t('downloads')}: ${pkg.downloads}` }}</div>
                    <div class="mr50">{{ `${$t('lastModifiedDate')}: ${formatDate(pkg.lastModifiedDate)}` }}</div>
                    <div>{{ `${$t('lastModifiedBy')}: ${userList[pkg.lastModifiedBy] ? userList[pkg.lastModifiedBy].name : pkg.lastModifiedBy}` }}</div>
                </div>
            </div>
        </div>
        <div class="common-package-tab">
            <bk-tab class="common-package-tab-main" type="unborder-card">
                <bk-tab-panel name="commonVersion" :label="$t('version')" v-bkloading="{ isLoading }">
                    <div class="common-package-version">
                        <div class="mb20 flex-align-center">
                            <bk-input
                                class="common-version-search"
                                v-model="versionInput"
                                clearable
                                :placeholder="$t('versionPlacehodler')"
                                @enter="handlerPaginationChange"
                                @clear="handlerPaginationChange">
                            </bk-input>
                            <i class="common-version-search-btn devops-icon icon-search" @click="handlerPaginationChange"></i>
                        </div>
                        <bk-table
                            class="common-version-table"
                            height="calc(100% - 120px)"
                            :data="versionList"
                            :outer-border="false"
                            :row-border="false"
                            size="small"
                            @row-click="toCommonVersionDetail"
                        >
                            <bk-table-column :label="$t('version')" prop="name"></bk-table-column>
                            <bk-table-column :label="$t('artiStatus')">
                                <template v-if="props.row.stageTag" slot-scope="props">
                                    <span class="mr5 repo-tag" v-for="tag in props.row.stageTag"
                                        :key="props.row.tag + tag">{{ tag }}</span>
                                </template>
                            </bk-table-column>
                            <bk-table-column :label="$t('size')">
                                <template slot-scope="props">
                                    {{ convertFileSize(props.row.size) }}
                                </template>
                            </bk-table-column>
                            <bk-table-column :label="$t('downloads')" prop="downloads"></bk-table-column>
                            <bk-table-column :label="$t('lastModifiedBy')">
                                <template slot-scope="props">
                                    {{ userList[props.row.lastModifiedBy] ? userList[props.row.lastModifiedBy].name : props.row.lastModifiedBy }}
                                </template>
                            </bk-table-column>
                            <bk-table-column :label="$t('lastModifiedDate')">
                                <template slot-scope="props">
                                    {{ formatDate(props.row.lastModifiedDate) }}
                                </template>
                            </bk-table-column>
                            <bk-table-column :label="$t('operation')" width="150">
                                <template slot-scope="props">
                                    <bk-button class="mr20"
                                        :disabled="(props.row.stageTag || '').includes('@release')"
                                        @click.stop="changeStageTagHandler(props.row)" text theme="primary">
                                        <i class="devops-icon icon-arrows-up"></i>
                                    </bk-button>
                                    <bk-button v-if="repoType !== 'docker'" class="mr20" @click.stop="downloadPackageHandler(props.row)" text theme="primary">
                                        <i class="devops-icon icon-download"></i>
                                    </bk-button>
                                    <bk-button @click.stop="deleteVersionHandler(props.row)" text theme="primary">
                                        <i class="devops-icon icon-delete"></i>
                                    </bk-button>
                                </template>
                            </bk-table-column>
                        </bk-table>
                        <bk-pagination
                            class="mt10"
                            size="small"
                            align="right"
                            @change="current => handlerPaginationChange({ current })"
                            @limit-change="limit => handlerPaginationChange({ limit })"
                            :current.sync="pagination.current"
                            :limit="pagination.limit"
                            :count="pagination.count"
                            :limit-list="pagination.limitList">
                        </bk-pagination>
                    </div>
                </bk-tab-panel>
            </bk-tab>
        </div>
        
        <bk-dialog
            v-model="formDialog.show"
            :title="$t('upgrade')"
            :close-icon="false"
            :quick-close="false"
            width="600"
            header-position="left">
            <bk-form :label-width="120" :model="formDialog" :rules="rules" ref="formDialog">
                <bk-form-item :label="$t('upgradeTo')" :required="true" property="tag">
                    <bk-radio-group v-model="formDialog.tag">
                        <bk-radio :disabled="!!formDialog.default.length" value="@prerelease">@prerelease</bk-radio>
                        <bk-radio class="ml20" value="@release">@release</bk-radio>
                    </bk-radio-group>
                </bk-form-item>
            </bk-form>
            <div slot="footer">
                <bk-button ext-cls="mr5" :loading="formDialog.loading" theme="primary" @click.stop.prevent="submitFormDialog">{{$t('submit')}}</bk-button>
                <bk-button ext-cls="mr5" theme="default" @click.stop="cancelFormDialog">{{$t('cancel')}}</bk-button>
            </div>
        </bk-dialog>
    </div>
</template>
<script>
    import { convertFileSize, formatDate } from '@/utils'
    import commonMixin from './commonMixin'
    import { mapState, mapActions } from 'vuex'
    export default {
        name: 'commonPackageDetail',
        mixins: [commonMixin],
        data () {
            return {
                isLoading: false,
                infoLoading: false,
                formDialog: {
                    show: false,
                    loading: false,
                    version: '',
                    default: [],
                    tag: ''
                },
                rules: {
                    tag: [
                        {
                            required: true,
                            message: this.$t('pleaseSelect') + this.$t('tag'),
                            trigger: 'blur'
                        }
                    ]
                },
                pkg: {
                    name: '',
                    key: '',
                    downloads: 0,
                    versions: 0,
                    latest: '1.9',
                    lastModifiedBy: '',
                    lastModifiedDate: new Date()
                },
                versionInput: '',
                versionList: [],
                pagination: {
                    count: 0,
                    current: 1,
                    limit: 10,
                    'limit-list': [10, 20, 40]
                }
            }
        },
        computed: {
            ...mapState(['userList'])
        },
        created () {
            this.getPackageInfoHandler()
            this.handlerPaginationChange()
        },
        methods: {
            formatDate,
            convertFileSize,
            ...mapActions([
                'getPackageInfo',
                'getVersionList',
                'changeStageTag',
                'deleteVersion'
            ]),
            handlerPaginationChange ({ current = 1, limit = this.pagination.limit } = {}) {
                this.pagination.current = current
                this.pagination.limit = limit
                this.getVersionListHandler()
            },
            getVersionListHandler () {
                this.isLoading = true
                this.getVersionList({
                    projectId: this.projectId,
                    repoName: this.repoName,
                    packageKey: this.packageKey,
                    current: this.pagination.current,
                    limit: this.pagination.limit,
                    version: this.versionInput
                }).then(({ records, totalRecords }) => {
                    this.versionList = records
                    this.pagination.count = totalRecords
                }).finally(() => {
                    this.isLoading = false
                })
            },
            getPackageInfoHandler () {
                this.infoLoading = true
                this.getPackageInfo({
                    projectId: this.projectId,
                    repoName: this.repoName,
                    packageKey: this.packageKey
                }).then(info => {
                    this.pkg = info
                }).finally(() => {
                    this.infoLoading = false
                })
            },
            toCommonVersionDetail (row) {
                this.$router.push({
                    name: 'commonVersion',
                    query: {
                        name: this.repoName,
                        package: this.packageKey,
                        version: row.name
                    }
                })
            },
            changeStageTagHandler (row) {
                this.formDialog = {
                    show: true,
                    loading: false,
                    version: row.name,
                    default: row.stageTag,
                    tag: ''
                }
            },
            async submitFormDialog () {
                await this.$refs.formDialog.validate()
                this.formDialog.loading = true
                this.changeStageTag({
                    projectId: this.projectId,
                    repoName: this.repoName,
                    packageKey: this.packageKey,
                    version: this.formDialog.version,
                    tag: this.formDialog.tag
                }).then(() => {
                    this.$bkMessage({
                        theme: 'success',
                        message: this.$t('upgrade') + this.$t('success')
                    })
                    this.cancelFormDialog()
                    this.getVersionListHandler()
                }).finally(() => {
                    this.formDialog.loading = false
                })
            },
            cancelFormDialog () {
                this.$refs.formDialog.clearError()
                this.formDialog.show = false
            },
            downloadPackageHandler (row) {
                const url = `/repository/api/version/download/${this.projectId}/${this.repoName}?packageKey=${this.packageKey}&version=${row.name}`
                this.$ajax.head(url).then(() => {
                    window.open(
                        '/web' + url,
                        '_self'
                    )
                }).catch(() => {
                    this.$bkMessage({
                        theme: 'error',
                        message: this.$t('fileNotExist')
                    })
                })
            },
            deleteVersionHandler (row) {
                this.$bkInfo({
                    type: 'error',
                    title: this.$t('deleteVersionTitle'),
                    subTitle: this.$t('deleteVersionSubTitle'),
                    showFooter: true,
                    confirmFn: () => {
                        this.deleteVersion({
                            projectId: this.projectId,
                            repoType: this.repoType,
                            repoName: this.repoName,
                            packageKey: this.packageKey,
                            version: row.name
                        }).then(data => {
                            this.getVersionListHandler()
                            this.$bkMessage({
                                theme: 'success',
                                message: this.$t('delete') + this.$t('success')
                            })
                        })
                    }
                })
            }
        }
    }
</script>
<style lang="scss" scoped>
@import '@/scss/conf';
.common-package-detail {
    height: 100%;
    .common-package-base-info {
        height: 100px;
        .common-package-title {
            .title {
                max-width: 500px;
                overflow: hidden;
                text-overflow: ellipsis;
                white-space: nowrap;
                font-size: 20px;
                color: $fontBoldColor;
                .subtitle {
                    color: $fontColor;
                    font-weight: normal;
                    font-size: 14px;
                    cursor: pointer;
                }
            }
        }
    }
    .common-package-tab {
        flex: 1;
        .common-package-tab-main {
            height: 100%;
            /deep/ .bk-tab-section {
                height: calc(100% - 40px);
                .bk-tab-content {
                    height: 100%;
                }
            }
            .common-package-version {
                height: calc(100% + 40px);
                margin-bottom: -40px;
                .common-version-search {
                    width: 250px;
                }
                .common-version-search-btn {
                    position: relative;
                    z-index: 1;
                    padding: 9px;
                    color: white;
                    margin-left: -2px;
                    border-radius: 0 2px 2px 0;
                    background-color: #3a84ff;
                    cursor: pointer;
                    &:hover {
                        background-color: #699df4;
                    }
                }
                .common-version-table {
                    .devops-icon {
                        font-size: 16px;
                    }
                    .icon-arrows-up {
                        border-bottom: 1px solid;
                    }
                }
            }
            .docker-description {
                .docker-description-header {
                    margin: 10px 0 20px;
                    font-size: 16px;
                    line-height: 2;
                    border-bottom: 1px solid;
                }
                .docker-description-tip {
                    margin-bottom: 10px;
                    font-size: 12px;
                    padding-left: 20px;
                    line-height: 1.5;
                }
            }
        }
    }
}
</style>
