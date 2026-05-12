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
                <header class="pl30 version-header flex-align-center">
                    <span>{{ $t('artifactVersion')}}</span>
                </header>
                <div v-if="showTagList" class="version-tabs">
                    <div
                        class="version-tab-item"
                        :class="{ 'active': viewType === 'version' }"
                        @click="viewType = 'version'">
                        {{ $t('versionList') }}
                    </div>
                    <div
                        class="version-tab-item"
                        :class="{ 'active': viewType === 'tag' }"
                        @click="viewType = 'tag'">
                        {{ $t('tagList') }}
                    </div>
                </div>
                <div class="version-search">
                    <bk-input
                        v-if="viewType === 'version'"
                        v-model.trim="versionInput"
                        :placeholder="$t('versionPlaceHolder')"
                        clearable
                        @enter="handlerPaginationChange()"
                        @clear="handlerPaginationChange()"
                        right-icon="bk-icon icon-search">
                    </bk-input>
                    <bk-input
                        v-else
                        v-model.trim="tagInput"
                        :placeholder="$t('tagPlaceHolder')"
                        clearable
                        @clear="tagInput = ''"
                        right-icon="bk-icon icon-search">
                    </bk-input>
                </div>
                <div class="version-list" :class="{ 'with-tabs': showTagList }">
                    <!-- 版本列表视图 -->
                    <template v-if="viewType === 'version'">
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
                    </template>
                    <!-- Tag 列表视图 -->
                    <template v-else>
                        <div class="mb10 list-count">
                            {{ tagInput ? $t('searchResultCount', [tagList.length]) : $t('totalTagCount', [tagList.length]) }}
                        </div>
                        <div class="tag-list-container">
                            <div
                                class="mb10 tag-item"
                                v-for="tagItem in tagList"
                                :key="tagItem.tag">
                                <div
                                    class="tag-version-item flex-center"
                                    :class="{ 'selected': tagItem.version.name === version }"
                                    @click="changeVersion(tagItem.version)">
                                    <span class="tag-name text-overflow" style="max-width:100%;" :title="tagItem.tag">{{ tagItem.tag }}</span>
                                </div>
                            </div>
                            <div v-if="tagList.length === 0" class="empty-tag-list">
                                <div class="empty-text">{{ $t('noTags') }}</div>
                            </div>
                        </div>
                    </template>
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
                viewType: 'version', // 'version' 或 'tag'
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
                tagInput: '',
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
            },
            // 只有 huggingface 仓库显示 tag 列表
            showTagList () {
                return this.repoType === 'huggingface'
            },
            // Tag 列表，每个 tag 唯一对应一个 version，支持搜索过滤
            tagList () {
                const tagMap = new Map()
                // 遍历所有版本，收集 tags（tag 是唯一的，不会重复）
                this.versionList.forEach(version => {
                    const tags = version.tags || []
                    tags.forEach(tag => {
                        // tag 是唯一的，如果已存在则跳过
                        if (!tagMap.has(tag)) {
                            tagMap.set(tag, version)
                        }
                    })
                })
                // 转换为数组并按 tag 字母顺序排序
                let result = Array.from(tagMap.entries())
                    .map(([tag, version]) => ({
                        tag,
                        version
                    }))
                    .sort((a, b) => {
                        // Tag 按字母顺序降序排序（大小写不敏感）
                        const tagA = (a.tag || '').toLowerCase()
                        const tagB = (b.tag || '').toLowerCase()
                        return tagB.localeCompare(tagA)
                    })
                // 如果有关键词，进行搜索过滤
                if (this.tagInput && this.tagInput.trim()) {
                    const keyword = this.tagInput.trim().toLowerCase()
                    result = result.filter(item => {
                        const tag = (item.tag || '').toLowerCase()
                        const versionName = (item.version.name || '').toLowerCase()
                        return tag.includes(keyword) || versionName.includes(keyword)
                    })
                }
                return result
            }
        },
        watch: {
            // 监听仓库类型变化，非 huggingface 仓库强制使用版本列表
            showTagList (show) {
                if (!show && this.viewType === 'tag') {
                    this.viewType = 'version'
                }
            },
            // 监听视图类型切换
            viewType (newType) {
                // 非 huggingface 仓库不允许切换到 tag 视图
                if (newType === 'tag' && !this.showTagList) {
                    this.viewType = 'version'
                    return
                }
                if (newType === 'tag' && this.tagList.length > 0) {
                    // 切换到标签列表时，检查当前版本是否在 tagList 中
                    const currentVersionInTagList = this.tagList.find(item => item.version.name === this.version)
                    if (!currentVersionInTagList) {
                        // 如果当前版本不在 tagList 中，选中第一个 tag 对应的版本
                        const firstTag = this.tagList[0]
                        if (firstTag && firstTag.version) {
                            this.changeVersion(firstTag.version)
                        }
                    }
                }
            },
            // 监听 tagList 变化，当 tagList 有数据且当前在 tag 视图时，检查是否需要选中第一个
            tagList: {
                handler (newList) {
                    if (this.viewType === 'tag' && newList.length > 0) {
                        const currentVersionInTagList = newList.find(item => item.version.name === this.version)
                        if (!currentVersionInTagList) {
                            // 如果当前版本不在 tagList 中，选中第一个 tag 对应的版本
                            const firstTag = newList[0]
                            if (firstTag && firstTag.version) {
                                this.changeVersion(firstTag.version)
                            }
                        }
                    }
                },
                immediate: false
            }
        },
        created () {
            // 非 huggingface 仓库默认使用版本列表
            if (!this.showTagList) {
                this.viewType = 'version'
            }
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
                        window.BK_SUBPATH + 'web' + url + `&x-bkrepo-project-id=${this.projectId}`,
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
                            window.BK_SUBPATH + 'web' + url + `&x-bkrepo-project-id=${this.projectId}`,
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
        height: calc(100% - 70px);
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
            .version-tabs {
                display: flex;
                height: 40px;
                border-bottom: 1px solid var(--borderWeightColor);
                background-color: var(--bgColor);
                .version-tab-item {
                    flex: 1;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    font-size: 13px;
                    color: var(--fontSubsidiaryColor);
                    cursor: pointer;
                    border-bottom: 2px solid transparent;
                    transition: all 0.2s;
                    &:hover {
                        color: var(--fontPrimaryColor);
                        background-color: var(--bgHoverLighterColor);
                    }
                    &.active {
                        color: var(--primaryColor);
                        border-bottom-color: var(--primaryColor);
                        background-color: white;
                        font-weight: 500;
                    }
                }
            }
            .version-search {
                padding: 20px 20px 10px;
            }
            .version-list {
                height: calc(100% - 120px);
                padding: 0 20px 10px;
                background-color: white;
                // 当显示 tab 时，需要减去 tab 的高度
                &.with-tabs {
                    height: calc(100% - 160px);
                }
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
            .tag-list-container {
                height: 100%;
                overflow-y: auto;
                .tag-item {
                    margin-bottom: 8px;
                    .tag-version-item {
                        position: relative;
                        height: 42px;
                        padding: 0 12px;
                        border-radius: 2px;
                        background-color: var(--bgLightColor);
                        cursor: pointer;
                        font-size: 13px;
                        .tag-name {
                            font-weight: 500;
                            color: var(--fontPrimaryColor);
                            width: 100%;
                            text-align: center;
                        }
                        &:hover {
                            background-color: var(--bgHoverLighterColor);
                        }
                        &.selected {
                            color: white;
                            background-color: var(--primaryColor);
                            .tag-name {
                                color: white;
                            }
                        }
                    }
                }
                .empty-tag-list {
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    height: 200px;
                    .empty-text {
                        color: var(--fontSubsidiaryColor);
                        font-size: 14px;
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
