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
                <header class="pl30 version-header flex-align-center">{{ $t('artifactVersion')}}</header>
                <div class="version-search">
                    <bk-input
                        v-model.trim="versionInput"
                        :placeholder="$t('versionPlaceHolder')"
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
                        <div class="mb10 list-count">{{ $t('totalVersionCount', [pagination.count])}}</div>
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
                                            label: $t('upgrade'), clickEvent: () => changeStageTagHandler($version),
                                            disabled: ($version.stageTag || '').includes('@release')
                                        },
                                        repoType !== 'docker' && { label: $t('download'), clickEvent: () => downloadPackageHandler($version) },
                                        showRepoScan && { label: $t('scanArtifact'), clickEvent: () => scanPackageHandler($version) }
                                    ] : []),
                                    { clickEvent: () => changeForbidStatusHandler($version), label: $version.metadata.forbidStatus ? $t('liftBan') : $t('forbiddenUse') },
                                    permission.delete && { label: $t('delete'), clickEvent: () => deleteVersionHandler($version) }
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
        <loading ref="loading" @closeLoading="closeLoading"></loading>
        <iam-deny-dialog :visible.sync="showIamDenyDialog" :show-data="showData"></iam-deny-dialog>
    </div>
</template>
<script>
    import OperationList from '@repository/components/OperationList'
    import InfiniteScroll from '@repository/components/InfiniteScroll'
    import VersionDetail from '@repository/views/repoCommon/commonVersionDetail'
    import commonFormDialog from '@repository/views/repoCommon/commonFormDialog'
    import iamDenyDialog from '@repository/components/IamDenyDialog/IamDenyDialog'
    import { mapState, mapActions } from 'vuex'
    import Loading from '@repository/components/Loading/loading'
    export default {
        name: 'commonPackageDetail',
        components: { Loading, OperationList, InfiniteScroll, VersionDetail, commonFormDialog, iamDenyDialog },
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
                            message: this.$t('pleaseSelect') + this.$t('space') + this.$t('tag'),
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
                },
                showIamDenyDialog: false,
                showData: {},
                timer: null
            }
        },
        computed: {
            ...mapState(['permission', 'scannerSupportPackageType', 'userInfo']),
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
                const show = RELEASE_MODE !== 'community' || SHOW_ANALYST_MENU
                return show && this.scannerSupportPackageType.join(',').toLowerCase().includes(this.repoType)
            }
        },
        created () {
            this.getPackageInfoHandler()
            this.handlerPaginationChange()
            if (RELEASE_MODE !== 'community' || SHOW_ANALYST_MENU) {
                this.refreshSupportPackageTypeList()
            }
        },
        methods: {
            ...mapActions([
                'getPackageInfo',
                'getVersionList',
                'changeStageTag',
                'deleteVersion',
                'forbidPackageMetadata',
                'refreshSupportPackageTypeList',
                'getPermissionUrl'
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
                    tag: '',
                    path: this.currentVersion.contentPath
                })
            },
            scanPackageHandler (row = this.currentVersion) {
                this.$refs.commonFormDialog.setData({
                    show: true,
                    loading: false,
                    title: this.$t('scanArtifact'),
                    type: 'scan',
                    id: '',
                    name: this.pkg.name,
                    version: row.name,
                    path: this.currentVersion.contentPath
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
                        message: (row.metadata.forbidStatus ? this.$t('liftBan') : this.$t('forbiddenUse')) + this.$t('space') + this.$t('success')
                    })
                    this.refresh(row.name)
                }).catch(e => {
                    if (e.status === 403) {
                        this.getPermissionUrl({
                            body: {
                                projectId: this.projectId,
                                action: 'UPDATE',
                                resourceType: 'NODE',
                                uid: this.userInfo.name,
                                repoName: this.repoName,
                                path: this.currentVersion.contentPath
                            }
                        }).then(res => {
                            if (res !== '') {
                                this.showIamDenyDialog = true
                                this.showData = {
                                    projectId: this.projectId,
                                    repoName: this.repoName,
                                    action: 'UPDATE',
                                    url: res,
                                    path: this.currentVersion.contentPath
                                }
                            }
                        })
                    }
                })
            },
            downloadPackageHandler (row = this.currentVersion) {
                if (this.repoType === 'docker') return
                const url = `/repository/api/version/download/${this.projectId}/${this.repoName}?packageKey=${this.packageKey}&version=${encodeURIComponent(row.name)}&download=true`
                this.$ajax.head(url).then(() => {
                    window.open(
                        '/web' + url,
                        '_self'
                    )
                }).catch(e => {
                    if (e.status === 451) {
                        this.$refs.loading.isShow = true
                        this.$refs.loading.complete = false
                        this.$refs.loading.title = ''
                        this.$refs.loading.backUp = true
                        this.$refs.loading.cancelMessage = this.$t('downloadLater')
                        this.$refs.loading.subMessage = this.$t('backUpSubMessage')
                        this.$refs.loading.message = this.$t('backUpMessage', { 0: this.currentVersion.contentPath })
                        this.timerDownload(url, this.currentVersion.contentPath)
                    } else {
                        const message = e.status === 403 ? this.$t('fileDownloadError') : this.$t('fileError')
                        this.$bkMessage({
                            theme: 'error',
                            message
                        })
                    }
                })
            },
            timerDownload (url, fullPath) {
                this.timer = setInterval(() => {
                    this.$ajax.head(url).then(() => {
                        clearInterval(this.timer)
                        this.timer = null
                        this.$refs.loading.isShow = false
                        window.open(
                            '/web' + url,
                            '_self'
                        )
                    }).catch(e => {
                        if (e.status === 451) {
                            this.$refs.loading.isShow = true
                            this.$refs.loading.complete = false
                            this.$refs.loading.title = ''
                            this.$refs.loading.backUp = true
                            this.$refs.loading.cancelMessage = this.$t('downloadLater')
                            this.$refs.loading.subMessage = this.$t('backUpSubMessage')
                            this.$refs.loading.message = this.$t('backUpMessage', { 0: fullPath })
                        } else {
                            clearInterval(this.timer)
                            this.timer = null
                            this.$refs.loading.isShow = false
                            const message = e.status === 403 ? this.$t('fileDownloadError') : this.$t('fileError')
                            this.$bkMessage({
                                theme: 'error',
                                message
                            })
                        }
                    })
                }, 5000)
            },
            deleteVersionHandler ({ name: version } = this.currentVersion) {
                this.$confirm({
                    theme: 'danger',
                    message: this.$t('deleteVersionTitle', { name: '' }),
                    subMessage: version,
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
                                message: this.$t('delete') + this.$t('space') + this.$t('success')
                            })
                        }).catch(e => {
                            if (e.status === 403) {
                                this.getPermissionUrl({
                                    body: {
                                        projectId: this.projectId,
                                        action: 'DELETE',
                                        resourceType: 'NODE',
                                        uid: this.userInfo.name,
                                        repoName: this.repoName,
                                        path: this.currentVersion.contentPath
                                    }
                                }).then(res => {
                                    if (res !== '') {
                                        this.showIamDenyDialog = true
                                        this.showData = {
                                            projectId: this.projectId,
                                            repoName: this.repoName,
                                            action: 'DELETE',
                                            url: res,
                                            packageName: this.currentVersion.metadata.name,
                                            packageVersion: this.currentVersion.metadata.version
                                        }
                                    }
                                })
                            }
                        })
                    }
                })
            },
            closeLoading () {
                clearInterval(this.timer)
                this.timer = null
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
