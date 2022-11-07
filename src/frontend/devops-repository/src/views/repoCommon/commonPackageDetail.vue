<template>
    <div class="common-package-detail">
        <header class="mb10 pl20 pr20 common-package-header flex-align-center">
            <Icon class="package-img" size="30" :name="repoType" />
            <div class="ml10 common-package-title">
                <div class="repo-title text-overflow" :title="pkg.name">
                    {{ pkg.name }}
                </div>
                <!-- <div class="repo-description text-overflow"
                    :title="pkg.description">
                    {{ pkg.description || '【制品描述】' }}
                </div> -->
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
                            <span class="text-overflow" style="max-width:150px;" :title="$version.name">{{ $version.name }}</span>
                            <operation-list
                                class="version-operation"
                                :list="[
                                    ...(!$version.metadata.forbidStatus ? [
                                        permission.edit && {
                                            label: '晋级', clickEvent: () => changeStageTagHandler($version),
                                            disabled: ($version.stageTag || '').includes('@release')
                                        },
                                        repoType !== 'docker' && { label: '下载', clickEvent: () => downloadPackageHandler($version) },
                                        showRepoScan && { label: '扫描制品', clickEvent: () => scanPackageHandler($version) }
                                    ] : []),
                                    { clickEvent: () => changeForbidStatusHandler($version), label: $version.metadata.forbidStatus ? '解除禁止' : '禁止使用' },
                                    permission.delete && { label: '删除', clickEvent: () => deleteVersionHandler($version) }
                                ]"></operation-list>
                        </div>
                    </infinite-scroll>
                </div>
            </aside>
            <div class="common-version-detail flex-1">
                <version-detail
                    ref="versionDetail"
                    @tag="changeStageTagHandler()"
                    @scan="scanPackageHandler()"
                    @forbid="changeForbidStatusHandler()"
                    @download="downloadPackageHandler()"
                    @delete="deleteVersionHandler()">
                </version-detail>
            </div>
        </div>

        <common-form-dialog ref="commonFormDialog" @refresh="refresh"></common-form-dialog>
    </div>
</template>
<script>
    import OperationList from '@repository/components/OperationList'
    import InfiniteScroll from '@repository/components/InfiniteScroll'
    import VersionDetail from '@repository/views/repoCommon/commonVersionDetail'
    import commonFormDialog from '@repository/views/repoCommon/commonFormDialog'
    import { scanTypeEnum } from '@repository/store/publicEnum'
    import { mapState, mapActions } from 'vuex'
    export default {
        name: 'commonPackageDetail',
        components: { OperationList, InfiniteScroll, VersionDetail, commonFormDialog },
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
                    limitList: [10, 20, 40]
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
                return this.$route.query.packageKey || ''
            },
            version () {
                return this.$route.query.version || ''
            },
            currentVersion () {
                return this.versionList.find(version => version.name === this.version)
            },
            showRepoScan () {
                return RELEASE_MODE !== 'community' && Object.keys(scanTypeEnum).join(',').toLowerCase().includes(this.repoType)
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
                'deleteVersion',
                'forbidPackageMetadata'
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
                    if (!this.versionInput) {
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
            refresh (version) {
                this.getVersionListHandler()
                if (this.version === version) {
                    this.$refs.versionDetail && this.$refs.versionDetail.getDetail()
                }
            },
            changeStageTagHandler (row = this.currentVersion) {
                if ((row.stageTag || '').includes('@release')) return
                this.$refs.commonFormDialog.setData({
                    show: true,
                    loading: false,
                    title: this.$t('upgrade'),
                    type: 'upgrade',
                    version: row.name,
                    default: row.stageTag,
                    tag: ''
                })
            },
            scanPackageHandler (row = this.currentVersion) {
                this.$refs.commonFormDialog.setData({
                    show: true,
                    loading: false,
                    title: '扫描制品',
                    type: 'scan',
                    id: '',
                    name: this.pkg.name,
                    version: row.name
                })
            },
            changeForbidStatusHandler (row = this.currentVersion) {
                this.forbidPackageMetadata({
                    projectId: this.projectId,
                    repoName: this.repoName,
                    body: {
                        packageKey: this.packageKey,
                        version: row.name,
                        versionMetadata: [{ key: 'forbidStatus', value: !row.metadata.forbidStatus }]
                    }
                }).then(() => {
                    this.$bkMessage({
                        theme: 'success',
                        message: (row.metadata.forbidStatus ? '解除禁止' : '禁止使用') + this.$t('success')
                    })
                    this.refresh(row.name)
                })
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
                    const message = e.status === 403 ? this.$t('fileDownloadError') : this.$t('fileError')
                    this.$bkMessage({
                        theme: 'error',
                        message
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
        height: 60px;
        background-color: white;
        .package-img {
            border-radius: 4px;
        }
        .common-package-title {
            .repo-title {
                max-width: 500px;
                font-size: 16px;
                font-weight: 500;
                color: #081E40;
            }
            // .repo-description {
            //     max-width: 70vw;
            //     padding: 5px 15px;
            //     background-color: var(--bgWeightColor);
            //     border-radius: 2px;
            // }
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
                height: calc(100% - 120px);
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
                    background-color: var(--bgLightColor);
                    cursor: pointer;
                    .version-operation {
                        position: absolute;
                        right: 10px;
                    }
                    &:hover {
                        background-color: var(--bgHoverLighterColor);
                    }
                    &.selected {
                        color: white;
                        background-color: var(--primaryColor);
                        .version-operation {
                            ::v-deep .devops-icon.hover-btn {
                                color: white;
                                &:hover {
                                    background-color: transparent;
                                }
                            }
                            &:hover {
                                background-color: var(--primaryColor);
                            }
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
