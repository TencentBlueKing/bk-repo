<template>
    <div class="common-package-detail">
        <header class="mb10 pl20 pr20 common-package-header flex-align-center">
            <Icon class="p10 package-img" size="80" :name="repoType" />
            <div class="ml20 common-package-title flex-column">
                <span class="mb5 repo-title text-overflow" :title="pkg.name">
                    {{ pkg.name }}
                </span>
                <span class="repo-description text-overflow"
                    :title="pkg.description">
                    {{ pkg.description || '【制品描述】' }}
                </span>
            </div>
        </header>
        <div class="common-version-main flex-align-center">
            <aside class="common-version" v-bkloading="{ isLoading }">
                <header class="pl30 version-header flex-align-center">制品版本</header>
                <div class="version-search">
                    <bk-input
                        v-model.trim="versionInput"
                        placeholder="请输入版本, 按Enter键搜索"
                        clearable
                        @enter="handlerPaginationChange()"
                        @clear="handlerPaginationChange()"
                        right-icon="bk-icon icon-search">
                    </bk-input>
                </div>
                <div class="version-list">
                    <infinite-scroll
                        ref="infiniteScroll"
                        :is-loading="isLoading"
                        :has-next="versionList.length < pagination.count"
                        @load="handlerPaginationChange({ current: pagination.current + 1 }, true)">
                        <div class="mb10 list-count">共计{{ pagination.count }}个版本</div>
                        <div
                            class="mb10 version-item flex-center"
                            :class="{ 'selected': $version.name === version }"
                            v-for="$version in versionList"
                            :key="$version.name"
                            @click="changeVersion($version)">
                            <span>{{ $version.name }}</span>
                            <operation-list
                                class="version-operation"
                                :list="[
                                    permission.edit && { label: '晋级', clickEvent: () => changeStageTagHandler($version), disabled: ($version.stageTag || '').includes('@release') },
                                    repoType !== 'docker' && { label: '下载', clickEvent: () => downloadPackageHandler($version) },
                                    permission.delete && { label: '删除', clickEvent: () => deleteVersionHandler($version) }
                                ].filter(Boolean)"></operation-list>
                        </div>
                    </infinite-scroll>
                </div>
            </aside>
            <div class="common-version-detail flex-1">
                <version-detail
                    ref="versionDetail"
                    @tag="changeStageTagHandler()"
                    @download="downloadPackageHandler()"
                    @delete="deleteVersionHandler()">
                </version-detail>
            </div>
        </div>
        
        <canway-dialog
            v-model="formDialog.show"
            width="400"
            height-num="193"
            :title="$t('upgrade')"
            @cancel="cancelFormDialog">
            <bk-form :label-width="100" :model="formDialog" :rules="rules" ref="formDialog">
                <bk-form-item :label="$t('upgradeTo')" :required="true" property="tag" error-display-type="normal">
                    <bk-radio-group v-model="formDialog.tag">
                        <bk-radio :disabled="!!formDialog.default.length" value="@prerelease">@prerelease</bk-radio>
                        <bk-radio class="ml20" value="@release">@release</bk-radio>
                    </bk-radio-group>
                </bk-form-item>
            </bk-form>
            <template #footer>
                <bk-button theme="default" @click.stop="cancelFormDialog">{{$t('cancel')}}</bk-button>
                <bk-button class="ml5" :loading="formDialog.loading" theme="primary" @click="submitFormDialog">{{$t('confirm')}}</bk-button>
            </template>
        </canway-dialog>
    </div>
</template>
<script>
    import OperationList from '@repository/components/OperationList'
    import InfiniteScroll from '@repository/components/InfiniteScroll'
    import VersionDetail from '@repository/views/repoCommon/commonVersionDetail'
    import { mapState, mapActions } from 'vuex'
    export default {
        name: 'commonPackageDetail',
        components: { OperationList, InfiniteScroll, VersionDetail },
        data () {
            return {
                tabName: 'commonVersion',
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
                    limit: 20,
                    'limit-list': [10, 20, 40]
                }
            }
        },
        computed: {
            ...mapState(['permission']),
            projectId () {
                return this.$route.params.projectId || ''
            },
            repoType () {
                return this.$route.params.repoType || ''
            },
            repoName () {
                return this.$route.query.repoName || ''
            },
            packageKey () {
                return this.$route.query.package || ''
            },
            version () {
                return this.$route.query.version || ''
            },
            currentVersion () {
                return this.versionList.find(version => version.name === this.version)
            }
        },
        created () {
            this.getPackageInfoHandler()
            this.handlerPaginationChange()
        },
        methods: {
            ...mapActions([
                'getPackageInfo',
                'getVersionList',
                'changeStageTag',
                'deleteVersion'
            ]),
            handlerPaginationChange ({ current = 1, limit = this.pagination.limit } = {}, load) {
                this.pagination.current = current
                this.pagination.limit = limit
                this.getVersionListHandler(load)
                if (!load) {
                    this.$refs.infiniteScroll && this.$refs.infiniteScroll.scrollToTop()
                    this.$router.replace({
                        query: {
                            ...this.$route.query,
                            versionName: this.versionInput
                        }
                    })
                }
            },
            getVersionListHandler (load) {
                if (this.isLoading) return
                this.isLoading = !load
                this.getVersionList({
                    projectId: this.projectId,
                    repoName: this.repoName,
                    packageKey: this.packageKey,
                    current: this.pagination.current,
                    limit: this.pagination.limit,
                    version: this.versionInput
                }).then(({ records, totalRecords }) => {
                    load ? this.versionList.push(...records) : (this.versionList = records)
                    this.pagination.count = totalRecords
                    if (!this.versionList.length) {
                        this.$router.back()
                    }
                    if (!this.version || !this.versionList.find(v => v.name === this.version)) {
                        this.$router.replace({
                            query: {
                                ...this.$route.query,
                                version: records[0].name
                            }
                        })
                    }
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
            changeVersion ({ name: version }) {
                this.$router.replace({
                    query: {
                        ...this.$route.query,
                        version
                    }
                })
            },
            changeStageTagHandler (row = this.currentVersion) {
                if ((row.stageTag || '').includes('@release')) return
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
                    // 当前版本晋级，更新详情
                    if (this.version === this.formDialog.version) {
                        this.$refs.versionDetail && this.$refs.versionDetail.getDetail()
                    }
                }).finally(() => {
                    this.formDialog.loading = false
                })
            },
            cancelFormDialog () {
                this.$refs.formDialog.clearError()
                this.formDialog.show = false
            },
            downloadPackageHandler (row = this.currentVersion) {
                if (this.repoType === 'docker') return
                const url = `/repository/api/version/download/${this.projectId}/${this.repoName}?packageKey=${this.packageKey}&version=${row.name}&download=true`
                this.$ajax.head(url).then(() => {
                    window.open(
                        '/web' + url,
                        '_self'
                    )
                }).catch(e => {
                    this.$bkMessage({
                        theme: 'error',
                        message: e.status !== 404 ? e.message : this.$t('fileNotExist')
                    })
                })
            },
            deleteVersionHandler ({ name: version } = this.currentVersion) {
                this.$confirm({
                    theme: 'danger',
                    message: this.$t('deleteVersionTitle', { version }),
                    confirmFn: () => {
                        return this.deleteVersion({
                            projectId: this.projectId,
                            repoType: this.repoType,
                            repoName: this.repoName,
                            packageKey: this.packageKey,
                            version
                        }).then(() => {
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
.common-package-detail {
    height: 100%;
    .common-package-header{
        height: 90px;
        color: var(--fontPrimaryColor);
        background-color: white;
        .package-img {
            width: 78px;
            height: 68px;
            border-radius: 4px;
            box-shadow: 0px 3px 5px 0px rgba(217, 217, 217, 0.5);
        }
        .common-package-title {
            .repo-title {
                margin-top: -5px;
                max-width: 500px;
                font-size: 20px;
                font-weight: bold;
            }
            .repo-description {
                max-width: 70vw;
                padding: 6px 10px;
                background-color: var(--bgWeightColor);
                border-radius: 2px;
            }
        }
    }
    .common-version-main {
        height: calc(100% - 100px);
        .common-version {
            width: 250px;
            height: 100%;
            margin-right: 10px;
            background-color: white;
            .version-header {
                height: 50px;
                font-size: 14px;
                color: var(--fontPrimaryColor);
                border-bottom: 1px solid var(--borderWeightColor);
            }
            .version-search {
                padding: 20px 20px 10px;
            }
            .version-list {
                height: calc(100% - 122px);
                padding: 0 20px 10px;
                background-color: white;
                .list-count {
                    font-size: 12px;
                    color: var(--fontSubsidiaryColor);
                }
                .version-item {
                    position: relative;
                    height: 42px;
                    border-radius: 2px;
                    background-color: var(--bgLighterColor);
                    cursor: pointer;
                    .version-operation {
                        position: absolute;
                        right: 10px;
                    }
                    &.selected {
                        color: white;
                        background-color: var(--primaryColor);
                        .version-operation:hover {
                            background-color: var(--primaryHoverColor);
                        }
                    }
                }
            }
        }
        .common-version-detail {
            height: 100%;
            background-color: white;
        }
    }
}
</style>
