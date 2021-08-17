<template>
    <div class="repo-generic-container" @click="() => selectRow(selectedTreeNode)">
        <div v-show="!query" class="mr20 repo-generic-side" v-bkloading="{ isLoading: treeLoading }">
            <div class="important-search">
                <bk-input
                    v-model.trim="importantSearch"
                    placeholder=""
                    :clearable="true"
                    :right-icon="'bk-icon icon-search'">
                </bk-input>
            </div>
            <repo-tree
                class="repo-generic-tree"
                ref="repoTree"
                :sortable="true"
                :list="genericTree"
                :important-search="importantSearch"
                :open-list="sideTreeOpenList"
                :selected-node="selectedTreeNode"
                @icon-click="iconClickHandler"
                @item-click="itemClickHandler">
            </repo-tree>
        </div>
        <div class="repo-generic-main" v-bkloading="{ isLoading }">
            <div class="repo-generic-table">
                <bk-table
                    :data="artifactoryList"
                    height="calc(100% - 42px)"
                    :outer-border="false"
                    :row-border="false"
                    size="small"
                    @row-click="selectRow"
                    @row-dblclick="openFolder">
                    <bk-table-column :label="$t('fileName')" prop="name" :render-header="renderHeader">
                        <template #default="{ row }">
                            <div class="flex-align-center fine-name">
                                <icon size="24" :name="row.folder ? 'folder' : getIconName(row.name)" />
                                <div class="ml10" :title="row.name">{{row.name}}</div>
                            </div>
                        </template>
                    </bk-table-column>
                    <bk-table-column :label="$t('lastModifiedDate')" prop="lastModifiedDate" width="200" :render-header="renderHeader">
                        <template #default="{ row }">{{ formatDate(row.lastModifiedDate) }}</template>
                    </bk-table-column>
                    <bk-table-column :label="$t('lastModifiedBy')" width="120">
                        <template #default="{ row }">
                            {{ userList[row.lastModifiedBy] ? userList[row.lastModifiedBy].name : row.lastModifiedBy }}
                        </template>
                    </bk-table-column>
                    <bk-table-column :label="$t('size')" width="100">
                        <template #default="{ row }">
                            <bk-button text
                                v-show="row.folder && !row.hasOwnProperty('folderSize')"
                                :disabled="row.sizeLoading"
                                @click="calculateFolderSize(row)">{{ $t('calculate') }}</bk-button>
                            <span v-show="!row.folder || row.hasOwnProperty('folderSize')">
                                {{ convertFileSize(row.size || row.folderSize || 0) }}
                            </span>
                        </template>
                    </bk-table-column>
                </bk-table>
                <bk-pagination
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
            <aside v-show="!query || selectedRow.fullPath" class="repo-generic-actions">
                <bk-button class="detail-btn" theme="primary" @click.stop="showDetail()">{{ $t('showDetail') }}</bk-button>
                <div class="actions-btn flex-column">
                    <bk-button v-for="btn in operationBtns" :key="btn.label" @click.stop="btn.clickEvent()" text theme="primary">
                        <i :class="`mr5 devops-icon icon-${btn.icon}`"></i>
                        {{ btn.label }}
                    </bk-button>
                </div>
            </aside>
        </div>
        <genericDetail :detail-slider="detailSlider"></genericDetail>
        <generic-form-dialog ref="genericFormDialog" @submit="submitGenericForm"></generic-form-dialog>
        <generic-tree-dialog ref="genericTreeDialog" @update="updateGenericTreeNode" @submit="submitGenericTree"></generic-tree-dialog>
        <generic-upload-dialog v-bind="uploadDialog" @update="getArtifactories" @cancel="uploadDialog.show = false"></generic-upload-dialog>
    </div>
</template>
<script>
    import RepoTree from '@/components/RepoTree'
    import genericDetail from './genericDetail'
    import genericUploadDialog from './genericUploadDialog'
    import genericFormDialog from './genericFormDialog'
    import genericTreeDialog from './genericTreeDialog'
    import { convertFileSize, formatDate } from '@/utils'
    import { getIconName } from '@/store/publicEnum'
    import { mapState, mapMutations, mapActions } from 'vuex'
    export default {
        name: 'repoGeneric',
        components: { RepoTree, genericDetail, genericUploadDialog, genericFormDialog, genericTreeDialog },
        data () {
            return {
                isLoading: false,
                treeLoading: false,
                importantSearch: '',
                // 左侧树处于打开状态的目录
                sideTreeOpenList: [],
                sortType: 'lastModifiedDate',
                // 中间展示的table数据
                artifactoryList: [],
                // 左侧树选中的节点
                selectedTreeNode: {},
                // 分页信息
                pagination: {
                    count: 0,
                    current: 1,
                    limit: 20,
                    'limit-list': [10, 20, 40]
                },
                // table单击事件，debounce
                rowClickCallback: null,
                // table选中的行
                selectedRow: {},
                // 查看详情
                detailSlider: {
                    show: false,
                    loading: false,
                    folder: false,
                    data: {}
                },
                // 上传制品
                uploadDialog: {
                    show: false,
                    title: '',
                    fullPath: ''
                },
                query: null
            }
        },
        computed: {
            ...mapState(['userList', 'genericTree']),
            projectId () {
                return this.$route.params.projectId
            },
            repoName () {
                return this.$route.query.name
            },
            operationBtns () {
                // 是否选中了行
                const isSelectedRow = this.selectedRow.fullPath !== this.selectedTreeNode.fullPath
                // 是否是限制操作仓库，report/log已被过滤
                const isLimit = this.repoName === 'pipeline'
                // 是否选中的是文件夹
                const isFolder = this.selectedRow.folder
                return [
                    isSelectedRow && !isLimit && { clickEvent: this.renameRes, icon: 'edit', label: this.$t('rename') },
                    isSelectedRow && !isLimit && { clickEvent: this.moveRes, icon: 'move', label: this.$t('move') },
                    isSelectedRow && !isLimit && { clickEvent: this.copyRes, icon: 'save', label: this.$t('copy') },
                    isSelectedRow && !isLimit && { clickEvent: this.deleteRes, icon: 'delete', label: this.$t('delete') },
                    isSelectedRow && !isFolder && { clickEvent: this.handlerShare, icon: 'none', label: this.$t('share') },
                    isSelectedRow && { clickEvent: this.handlerDownload, icon: 'download', label: this.$t('download') },
                    !isSelectedRow && !isLimit && { clickEvent: this.addFolder, icon: 'folder-plus', label: this.$t('create') + this.$t('folder') },
                    !isSelectedRow && !isLimit && { clickEvent: this.handlerUpload, icon: 'upload', label: this.$t('upload') },
                    !isSelectedRow && { clickEvent: this.getArtifactories, icon: 'refresh', label: this.$t('refresh') }
                ].filter(Boolean)
            }
        },
        beforeRouteEnter (to, from, next) {
            // 前端隐藏report仓库/log仓库
            if (to.query.name === 'report' || to.query.name === 'log') {
                next({
                    name: 'repoList',
                    params: {
                        projectId: to.params.projectId
                    }
                })
            } else next()
        },
        watch: {
            '$route.query.name' () {
                this.initPage()
            }
        },
        created () {
            this.initPage()
        },
        beforeDestroy () {
            this.SET_BREADCRUMB([])
        },
        methods: {
            convertFileSize,
            getIconName,
            formatDate,
            ...mapMutations(['INIT_TREE', 'SET_BREADCRUMB']),
            ...mapActions([
                'getNodeDetail',
                'getFolderList',
                'getArtifactoryList',
                'createFolder',
                'getArtifactoryListByQuery',
                'deleteArtifactory',
                'renameNode',
                'moveNode',
                'copyNode',
                'shareArtifactory',
                'getFolderSize',
                'getFileNumOfFolder'
            ]),
            initPage () {
                this.importantSearch = ''
                this.INIT_TREE()
                this.sideTreeOpenList = []
                this.itemClickHandler(this.genericTree[0])
            },
            renderHeader (h, { column }) {
                return h('div', {
                    class: 'flex-align-center hover-btn',
                    on: {
                        click: () => {
                            this.sortType = column.property
                            this.handlerPaginationChange()
                        }
                    }
                }, [
                    h('span', column.label),
                    h('i', {
                        class: {
                            'ml5 devops-icon icon-down-shape': true,
                            'selected': this.sortType === column.property
                        }
                    })
                ])
            },
            // 获取中间列表数据
            getArtifactories () {
                // 自定义查询
                if (this.query) {
                    this.searchHandler(this.query)
                    return
                }
                this.isLoading = true
                this.getArtifactoryList({
                    projectId: this.projectId,
                    repoName: this.repoName,
                    fullPath: this.selectedTreeNode.fullPath,
                    current: this.pagination.current,
                    limit: this.pagination.limit,
                    sortType: this.sortType,
                    isPipeline: this.repoName === 'pipeline'
                }).then(({ records, totalRecords }) => {
                    this.pagination.count = totalRecords
                    this.artifactoryList = records.map(v => {
                        return {
                            ...v,
                            name: (v.metadata && v.metadata.displayName) || v.name
                        }
                    })
                }).finally(() => {
                    this.isLoading = false
                })
            },
            // 搜索文件函数
            searchHandler (query) {
                this.query = query
                this.isLoading = true
                this.getArtifactoryListByQuery({
                    projectId: this.projectId,
                    repoName: this.repoName,
                    name: query.name,
                    current: this.pagination.current,
                    limit: this.pagination.limit
                }).then(({ records, totalRecords }) => {
                    this.pagination.count = totalRecords
                    this.artifactoryList = records.map(v => {
                        return {
                            ...v,
                            name: (v.metadata && v.metadata.displayName) || v.name
                        }
                    })
                }).finally(() => {
                    this.isLoading = false
                })
            },
            resetQueryAndBack () {
                this.query = null
                this.selectedRow.element && this.selectedRow.element.classList.remove('selected-row')
                this.selectedRow = this.selectedTreeNode
                this.handlerPaginationChange()
            },
            handlerPaginationChange ({ current = 1, limit = this.pagination.limit } = {}) {
                this.pagination.current = current
                this.pagination.limit = limit
                this.getArtifactories()
            },
            // 树组件选中文件夹
            itemClickHandler (node) {
                this.selectedTreeNode = node
                // 取消table行选中样式
                this.selectedRow.element && this.selectedRow.element.classList.remove('selected-row')
                // 初始化table选中行、数据
                this.selectedRow = node
                this.handlerPaginationChange()
                // 更新面包屑
                this.setBreadcrumb()
                // 更新已展开文件夹数据
                const reg = new RegExp(`^${node.roadMap}`)
                const openList = this.sideTreeOpenList
                openList.splice(0, openList.length, ...openList.filter(v => !reg.test(v)))
                // 打开选中节点的左侧树的所有祖先节点
                node.roadMap.split(',').forEach((v, i, arr) => {
                    const roadMap = arr.slice(0, i + 1).join(',')
                    !openList.includes(roadMap) && openList.push(roadMap)
                })
                // 更新子文件夹
                if (node.loading) return
                this.updateGenericTreeNode(node)
            },
            iconClickHandler (node) {
                // 更新已展开文件夹数据
                const reg = new RegExp(`^${node.roadMap}`)
                const openList = this.sideTreeOpenList
                if (openList.includes(node.roadMap)) {
                    openList.splice(0, openList.length, ...openList.filter(v => !reg.test(v)))
                } else {
                    openList.push(node.roadMap)
                    // 更新子文件夹
                    if (node.loading) return
                    // 当前选中文件夹为当前操作文件夹的后代文件夹，则锁定文件夹保证选中文件夹路径完整
                    if (node.roadMap !== openList.roadMap && reg.test(openList.roadMap)) return
                    this.updateGenericTreeNode(node)
                }
            },
            updateGenericTreeNode (item) {
                if (this.query) return
                this.$set(item, 'loading', true)
                this.getFolderList({
                    projectId: this.projectId,
                    repoName: this.repoName,
                    fullPath: item.fullPath,
                    roadMap: item.roadMap,
                    isPipeline: this.repoName === 'pipeline'
                }).finally(() => {
                    this.$set(item, 'loading', false)
                })
            },
            // 双击table打开文件夹
            openFolder (row, $event) {
                if (!row.folder) return
                $event.stopPropagation()
                this.rowClickCallback && clearTimeout(this.rowClickCallback)
                const node = this.selectedTreeNode.children.find(v => v.fullPath === row.fullPath)
                this.itemClickHandler(node)
            },
            // 控制选中的行
            selectRow (row, $event) {
                $event && $event.stopPropagation()
                const element = $event ? $event.currentTarget : null
                this.rowClickCallback && clearTimeout(this.rowClickCallback)
                this.rowClickCallback = window.setTimeout(() => {
                    this.selectedRow.element && this.selectedRow.element.classList.remove('selected-row')
                    element && element.classList.add('selected-row')
                    this.selectedRow = {
                        ...row,
                        element
                    }
                }, 300)
            },
            showDetail () {
                this.detailSlider = {
                    show: true,
                    loading: true,
                    folder: this.selectedRow.folder,
                    data: {}
                }
                this.getNodeDetail({
                    projectId: this.projectId,
                    repoName: this.repoName,
                    fullPath: this.selectedRow.fullPath
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
            renameRes () {
                this.$refs.genericFormDialog.setFormData({
                    show: true,
                    loading: false,
                    type: 'rename',
                    name: this.selectedRow.name,
                    title: `${this.$t('rename')} (${this.selectedRow.name})`
                })
            },
            addFolder () {
                this.$refs.genericFormDialog.setFormData({
                    show: true,
                    loading: false,
                    type: 'add',
                    folderPath: this.selectedRow.fullPath,
                    path: '',
                    title: `${this.$t('create') + this.$t('folder')} (${this.selectedTreeNode.fullPath || '/'})`
                })
            },
            handlerShare () {
                this.$refs.genericFormDialog.setFormData({
                    show: true,
                    loading: false,
                    type: 'share',
                    title: `${this.$t('share')} (${this.selectedRow.name})`,
                    user: [],
                    ip: [],
                    permits: 1,
                    time: 1
                })
            },
            submitGenericForm (data) {
                this.$refs.genericFormDialog.setFormData({ loading: true })
                let message = ''
                let fn = null
                switch (data.type) {
                    case 'add':
                        fn = this.submitAddFolder(data).then(() => {
                            this.updateGenericTreeNode(this.selectedTreeNode)
                        })
                        message = this.$t('create') + this.$t('folder')
                        break
                    case 'rename':
                        fn = this.submitRenameNode(data).then(() => {
                            this.updateGenericTreeNode(this.selectedTreeNode)
                        })
                        message = this.$t('rename')
                        break
                    case 'share':
                        fn = this.submitShareArtifactory(data)
                        message = this.$t('share')
                        break
                }
                fn.then(() => {
                    this.selectRow(this.selectedTreeNode)
                    this.getArtifactories()
                    this.$bkMessage({
                        theme: 'success',
                        message: message + this.$t('success')
                    })
                    this.$refs.genericFormDialog.setFormData({ show: false })
                }).finally(() => {
                    this.$refs.genericFormDialog.setFormData({ loading: false })
                })
            },
            submitAddFolder (data) {
                return this.createFolder({
                    projectId: this.projectId,
                    repoName: this.repoName,
                    fullPath: `${this.selectedTreeNode.fullPath}/${data.path}`
                })
            },
            submitRenameNode (data) {
                return this.renameNode({
                    projectId: this.projectId,
                    repoName: this.repoName,
                    fullPath: this.selectedRow.fullPath,
                    newFullPath: this.selectedRow.fullPath.replace(/[^/]*$/, data.name)
                })
            },
            submitShareArtifactory (data) {
                return this.shareArtifactory({
                    projectId: this.projectId,
                    repoName: this.repoName,
                    fullPathSet: [this.selectedRow.fullPath],
                    type: 'DOWNLOAD',
                    host: `${location.origin}/web/generic`,
                    needsNotify: true,
                    ...(data.ip.length ? { authorizedIpSet: data.ip } : {}),
                    ...(data.user.length ? { authorizedUserSet: data.user } : {}),
                    ...(Number(data.time) > 0 ? { expireSeconds: Number(data.time) * 86400 } : {}),
                    ...(Number(data.permits) > 0 ? { permits: Number(data.permits) } : {})
                })
            },
            async deleteRes () {
                if (!this.selectedRow.fullPath) return
                let totalRecords
                if (this.selectedRow.folder) {
                    totalRecords = await this.getFileNumOfFolder({
                        projectId: this.projectId,
                        repoName: this.repoName,
                        fullPath: this.selectedRow.fullPath
                    })
                }
                this.$bkInfo({
                    title: `${this.$t('confirm') + this.$t('delete')}${this.selectedRow.folder ? this.$t('folder') : this.$t('file')} ${this.selectedRow.name} ？`,
                    subTitle: `${this.selectedRow.folder && totalRecords ? `当前文件夹下存在${totalRecords}个文件` : ''}`,
                    closeIcon: false,
                    theme: 'danger',
                    confirmLoading: true,
                    confirmFn: () => {
                        return this.deleteArtifactory({
                            projectId: this.projectId,
                            repoName: this.repoName,
                            fullPath: this.selectedRow.fullPath
                        }).then(() => {
                            this.selectRow(this.selectedTreeNode)
                            this.updateGenericTreeNode(this.selectedTreeNode)
                            this.getArtifactories()
                            this.$bkMessage({
                                theme: 'success',
                                message: this.$t('delete') + this.$t('success')
                            })
                        })
                    }
                })
            },
            moveRes () {
                this.$refs.genericTreeDialog.setTreeData({
                    show: true,
                    type: 'move',
                    title: `${this.$t('move')} (${this.selectedRow.name})`,
                    openList: [],
                    selectedNode: this.genericTree[0]
                })
            },
            copyRes () {
                this.$refs.genericTreeDialog.setTreeData({
                    show: true,
                    type: 'copy',
                    title: `${this.$t('copy')} (${this.selectedRow.name})`,
                    openList: [],
                    selectedNode: this.genericTree[0]
                })
            },
            submitGenericTree (data) {
                this.$refs.genericTreeDialog.setTreeData({ loading: true })
                this[data.type + 'Node']({
                    body: {
                        srcProjectId: this.projectId,
                        srcRepoName: this.repoName,
                        srcFullPath: this.selectedRow.fullPath,
                        destProjectId: this.projectId,
                        destRepoName: this.repoName,
                        destFullPath: `${data.selectedNode.fullPath || '/'}`,
                        overwrite: false
                    }
                }).then(() => {
                    this.$refs.genericTreeDialog.setTreeData({ show: false })
                    this.selectRow(this.selectedTreeNode)
                    // 更新源和目的的节点信息
                    this.updateGenericTreeNode(this.selectedTreeNode)
                    this.updateGenericTreeNode(data.selectedNode)
                    this.getArtifactories()
                    this.$bkMessage({
                        theme: 'success',
                        message: data.type + this.$t('success')
                    })
                }).finally(() => {
                    this.$refs.genericTreeDialog.setTreeData({ loading: false })
                })
            },
            handlerUpload () {
                this.uploadDialog = {
                    show: true,
                    title: `${this.$t('upload')} (${this.selectedTreeNode.fullPath || '/'})`,
                    fullPath: this.selectedTreeNode.fullPath
                }
            },
            handlerDownload () {
                const url = `/generic/${this.projectId}/${this.repoName}/${this.selectedRow.fullPath}`
                this.$ajax.head(url).then(() => {
                    window.open(
                        '/web' + url + '?download=true',
                        '_self'
                    )
                }).catch(e => {
                    this.$bkMessage({
                        theme: 'error',
                        message: e.status !== 404 ? e.message : this.$t('fileNotExist')
                    })
                })
            },
            calculateFolderSize (row) {
                this.$set(row, 'sizeLoading', true)
                this.getFolderSize({
                    projectId: this.projectId,
                    repoName: this.repoName,
                    fullPath: row.fullPath
                }).then(({ size }) => {
                    this.$set(row, 'folderSize', size)
                }).finally(() => {
                    this.$set(row, 'sizeLoading', false)
                })
            },
            setBreadcrumb () {
                const breadcrumb = []
                let node = this.genericTree[0].children
                const road = this.selectedTreeNode.roadMap.split(',').slice(1)
                road.forEach(index => {
                    breadcrumb.push({
                        name: node[index].name,
                        value: node[index],
                        cilckHandler: item => {
                            this.itemClickHandler(item.value)
                        }
                    })
                    node = node[index].children
                })
                this.SET_BREADCRUMB(breadcrumb)
            }
        }
    }
</script>
<style lang="scss" scoped>
@import '@/scss/conf';
.repo-generic-container {
    display: flex;
    .repo-generic-side {
        width: 300px;
        border: 1px solid $borderWeightColor;
        .important-search {
            padding: 10px;
            background-color: #f2f2f2;
            border-bottom: 1px solid $borderWeightColor;
        }
        .repo-generic-tree {
            max-height: calc(100% - 54px);
            overflow: auto;
        }
    }
    .repo-generic-main {
        flex: 1;
        display: flex;
        .repo-generic-table {
            flex: 1;
            font-size: 0;
            .bk-table {
                margin-bottom: 10px;
                border-bottom: 1px solid $borderWeightColor;
            }
            .fine-name {
                padding: 7px 0;
                svg {
                    flex: none;
                }
            }
            ::v-deep .devops-icon {
                font-size: 16px;
                &.disabled {
                    color: $disabledColor;
                    cursor: not-allowed;
                }
                &.icon-down-shape {
                    color: $fontLigtherColor;
                    &.selected {
                        color: $fontWeightColor;
                    }
                }
            }
        }
    }
    .repo-generic-actions {
        width: 200px;
        .detail-btn {
            margin: 40px 20px 20px;
            width: calc(100% - 40px);
        }
        .actions-btn {
            align-items: flex-start;
            padding-left: 60px;
            button {
                height: 32px;
                line-height: 32px;
            }
        }
    }
}

::v-deep .bk-table-row.selected-row {
    background-color: $primaryLightColor;
}
</style>
