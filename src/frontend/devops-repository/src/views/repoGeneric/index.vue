<template>
    <div class="repo-generic-container">
        <header class="mb10 pl20 pr20 generic-header flex-align-center">
            <Icon class="generic-img" size="30" name="generic" />
            <div class="ml10 generic-title">
                <div class="repo-title text-overflow" :title="replaceRepoName(repoName)">
                    {{ replaceRepoName(repoName) }}
                </div>
            </div>
        </header>
        <div class="repo-generic-main flex-align-center"
            :style="{ 'margin-left': `${searchFileName ? -(sideBarWidth + moveBarWidth) : 0}px` }">
            <div class="repo-generic-side"
                :style="{ 'flex-basis': `${sideBarWidth}px` }"
                v-bkloading="{ isLoading: treeLoading }">
                <div class="repo-generic-side-info">
                    <span>{{$t('folderDirectory')}}</span>
                </div>
                <repo-tree
                    class="repo-generic-tree"
                    ref="repoTree"
                    :tree="genericTree"
                    :open-list="sideTreeOpenList"
                    :selected-node="selectedTreeNode"
                    @icon-click="iconClickHandler"
                    @item-click="itemClickHandler">
                    <template #operation="{ item }">
                        <operation-list
                            v-if="item.roadMap === selectedTreeNode.roadMap"
                            :list="[
                                permission.write && repoName !== 'pipeline' && { clickEvent: () => addFolder(item), label: $t('createFolder') },
                                permission.write && repoName !== 'pipeline' && { clickEvent: () => handlerUpload(item), label: $t('uploadFile') },
                                permission.write && repoName !== 'pipeline' && { clickEvent: () => handlerUpload(item, true), label: $t('uploadFolder') }
                            ]">
                        </operation-list>
                    </template>
                </repo-tree>
            </div>
            <move-split-bar
                :left="sideBarWidth"
                :width="moveBarWidth"
                @change="changeSideBarWidth"
            />
            <div class="repo-generic-table" v-bkloading="{ isLoading }">
                <div class="multi-operation flex-between-center">
                    <breadcrumb v-if="!searchFileName" :list="breadcrumb" omit-middle></breadcrumb>
                    <span v-else> {{repoName + (searchFullPath || (selectedTreeNode && selectedTreeNode.fullPath) || '') }}</span>
                    <div class="repo-generic-actions bk-button-group">
                        <bk-button
                            v-if="multiSelect.length && repoName !== 'pipeline'"
                            @click="handlerMultiDownload">
                            {{$t('batchDownload')}}
                        </bk-button>
                        <bk-button
                            v-if="multiSelect.length && ((repoName === 'pipeline' && (userInfo.admin || userInfo.manage)) || repoName !== 'pipeline')"
                            class="ml10"
                            @click="handlerMultiDelete()">
                            {{ $t('batchDeletion') }}
                        </bk-button>
                        <bk-button class="ml10" v-if="(userInfo.admin || userInfo.manage) && multiSelect.length && multiSelect.some(key => (
                            key.folder === true
                        ))" @click="clean">
                            {{ $t('clean') }}
                        </bk-button>
                        <bk-input
                            class="w250 ml10"
                            v-model.trim="inFolderSearchName"
                            :placeholder="$t('inFolderSearchPlaceholder')"
                            clearable
                            right-icon="bk-icon icon-search"
                            @enter="inFolderSearchFile"
                            @clear="inFolderSearchFile">
                        </bk-input>
                        <bk-button class="ml10"
                            @click="getArtifactories">
                            {{ $t('refresh') }}
                        </bk-button>
                    </div>
                </div>
                <bk-table
                    :data="artifactoryList"
                    height="calc(100% - 100px)"
                    :outer-border="false"
                    :row-border="false"
                    size="small"
                    ref="artifactoryTable"
                    @sort-change="orderList"
                    @row-dblclick="openFolder"
                    @selection-change="selectMultiRow">
                    <template #empty>
                        <empty-data :is-loading="isLoading" :search="Boolean(searchFileName)"></empty-data>
                    </template>
                    <bk-table-column :selectable="selectable" type="selection" width="60"></bk-table-column>
                    <bk-table-column :label="$t('fileName')" prop="name" show-overflow-tooltip :render-header="renderHeader">
                        <template #default="{ row }">
                            <Icon class="table-svg mr5" size="16" :name="row.folder ? 'folder' : getIconName(row.name)" />
                            <span
                                class="hover-btn disabled"
                                v-if="!row.folder && row.metadata.forbidStatus"
                                v-bk-tooltips="{ content: tooltipContent(row.metadata), placements: ['top'] }"
                            >{{row.name}}</span>
                            <span v-else>{{ row.name }}</span>
                            <scan-tag class="mr5 table-svg"
                                v-if="showRepoScan(row)"
                                :status="row.metadata.scanStatus"
                                repo-type="generic"
                                :full-path="row.fullPath">
                            </scan-tag>
                        </template>
                    </bk-table-column>
                    <bk-table-column :label="$t('size')" prop="size" width="90" :sortable="sortableRepo" show-overflow-tooltip>
                        <template #default="{ row }">
                            {{ convertFileSize(row.size > 0 ? row.size : 0 ) }}
                        </template>
                    </bk-table-column>
                    <bk-table-column :label="$t('fileNum')" prop="nodeNum" :sortable="sortableRepo" show-overflow-tooltip>
                        <template #default="{ row }">
                            {{ row.nodeNum ? row.nodeNum : row.folder ? 0 : '--'}}
                        </template>
                    </bk-table-column>

                    <bk-table-column :label="$t('metadata')">
                        <template #default="{ row }">
                            <metadata-tag :metadata="row.nodeMetadata" :metadata-label-list="metadataLabelList" />
                        </template>
                    </bk-table-column>

                    <bk-table-column v-if="searchFileName" :label="$t('path')" prop="fullPath" show-overflow-tooltip></bk-table-column>

                    <bk-table-column :label="$t('clusterNames')" prop="clusterNames" width="150">
                        <template #default="{ row }">
                            {{ row.clusterNames ? row.clusterNames.join() : row.clusterNames }}
                        </template>
                    </bk-table-column>
                    <bk-table-column :label="$t('lastModifiedDate')" prop="lastModifiedDate" width="150" :render-header="renderHeader">
                        <template #default="{ row }">{{ formatDate(row.lastModifiedDate) }}</template>
                    </bk-table-column>
                    <bk-table-column :label="$t('lastModifiedBy')" width="150" show-overflow-tooltip>
                        <template #default="{ row }">
                            {{ userList[row.lastModifiedBy] ? userList[row.lastModifiedBy].name : row.lastModifiedBy }}
                        </template>
                    </bk-table-column>
                    <bk-table-column :label="$t('operation')" width="100">
                        <template #default="{ row }">
                            <operation-list
                                :list="[
                                    { clickEvent: () => showDetail(row), label: $t('detail') },
                                    row.folder && row.category !== 'REMOTE' && { clickEvent: () => calculateFolderSize(row), label: $t('realSize') },
                                    !row.folder && getBtnDisabled(row.name) && { clickEvent: () => handlerPreviewBasicsFile(row), label: $t('preview') }, //基本类型文件 eg: txt
                                    !row.folder && row.category !== 'REMOTE' && baseCompressedType.includes(row.name.slice(-3)) && { clickEvent: () => handlerPreviewCompressedFile(row), label: $t('preview') }, //压缩文件 eg: rar|zip|gz|tgz|tar|jar
                                    ...(!row.metadata.forbidStatus ? [
                                        (row.category !== 'REMOTE' || !row.folder) && { clickEvent: () => handlerDownload(row), label: $t('download') },
                                        ...(repoName !== 'pipeline' && row.category !== 'REMOTE' ? [
                                            permission.edit && { clickEvent: () => renameRes(row), label: $t('rename') },
                                            permission.write && { clickEvent: () => moveRes(row), label: $t('move') },
                                            permission.write && { clickEvent: () => copyRes(row), label: $t('copy') }
                                        ] : []),
                                        ...(!row.folder && row.category !== 'REMOTE' ? [
                                            !community && { clickEvent: () => handlerShare(row), label: $t('share') },
                                            showRepoScan(row) && { clickEvent: () => handlerScan(row), label: $t('scanArtifact') }
                                        ] : [])
                                    ] : []),
                                    !row.folder && row.category !== 'REMOTE' && { clickEvent: () => handlerForbid(row), label: row.metadata.forbidStatus ? $t('liftBan') : $t('forbiddenUse') },
                                    permission.delete && row.category !== 'REMOTE' && ((repoName === 'pipeline' && (userInfo.admin || userInfo.manage)) || repoName !== 'pipeline') && { clickEvent: () => deleteRes(row), label: $t('delete') }
                                ]">
                            </operation-list>
                        </template>
                    </bk-table-column>
                </bk-table>
                <bk-button v-if="!localRepo"
                    :disabled="artifactoryList.length === 0 || artifactoryList.length < pagination.limit"
                    size="small"
                    icon="icon-angle-right"
                    @click="changePage(1)"
                    class="mt10 mr10 fr">
                </bk-button>
                <bk-button v-if="!localRepo"
                    :disabled="pagination.current === 1"
                    size="small"
                    icon="icon-angle-left"
                    @click="changePage(-1)"
                    class="mt10 mr5 fr">
                </bk-button>
                <bk-pagination
                    v-if="localRepo"
                    class="p10"
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
        </div>

        <generic-clean-dialog ref="genericCleanDialog" @refresh="refreshNodeChange"></generic-clean-dialog>
        <generic-detail ref="genericDetail"></generic-detail>
        <generic-form-dialog ref="genericFormDialog" @refresh="refreshNodeChange"></generic-form-dialog>
        <generic-share-dialog ref="genericShareDialog"></generic-share-dialog>
        <generic-tree-dialog ref="genericTreeDialog" @update="updateGenericTreeNode" @refresh="refreshNodeChange"></generic-tree-dialog>
        <preview-basic-file-dialog ref="previewBasicFileDialog"></preview-basic-file-dialog>
        <compressed-file-table ref="compressedFileTable" :data="compressedData" @show-preview="handleShowPreview"></compressed-file-table>
        <loading ref="loading" @closeLoading="closeLoading"></loading>
        <iam-deny-dialog :visible.sync="showIamDenyDialog" :show-data="showData"></iam-deny-dialog>
    </div>
</template>
<script>
    import OperationList from '@repository/components/OperationList'
    import Breadcrumb from '@repository/components/Breadcrumb'
    import MoveSplitBar from '@repository/components/MoveSplitBar'
    import RepoTree from '@repository/components/RepoTree'
    import ScanTag from '@repository/views/repoScan/scanTag'
    import metadataTag from '@repository/views/repoCommon/metadataTag'
    import genericDetail from '@repository/views/repoGeneric/genericDetail'
    import genericFormDialog from '@repository/views/repoGeneric/genericFormDialog'
    import genericShareDialog from '@repository/views/repoGeneric/genericShareDialog'
    import genericTreeDialog from '@repository/views/repoGeneric/genericTreeDialog'
    import previewBasicFileDialog from './previewBasicFileDialog'
    import iamDenyDialog from '@repository/components/IamDenyDialog/IamDenyDialog'
    import compressedFileTable from './compressedFileTable'
    import { convertFileSize, formatDate, debounce } from '@repository/utils'
    import { beforeMonths, beforeYears } from '@repository/utils/date'
    import { customizeDownloadFile } from '@repository/utils/downloadFile'
    import { getIconName } from '@repository/store/publicEnum'
    import { mapState, mapMutations, mapActions } from 'vuex'
    import Loading from '@repository/components/Loading/loading'
    import genericCleanDialog from '@repository/views/repoGeneric/genericCleanDialog'

    export default {
        name: 'repoGeneric',
        components: {
            Loading,
            OperationList,
            Breadcrumb,
            MoveSplitBar,
            RepoTree,
            ScanTag,
            metadataTag,
            genericDetail,
            genericFormDialog,
            genericShareDialog,
            genericTreeDialog,
            previewBasicFileDialog,
            compressedFileTable,
            iamDenyDialog,
            genericCleanDialog
        },
        data () {
            return {
                MODE_CONFIG,
                sideBarWidth: 300,
                moveBarWidth: 10,
                isLoading: false,
                treeLoading: false,
                // 左侧树处于打开状态的目录
                sideTreeOpenList: [],
                sortType: 'lastModifiedDate',
                // 中间展示的table数据
                artifactoryList: [],
                multiSelect: [],
                // 左侧树选中的节点
                selectedTreeNode: {},
                // 分页信息
                pagination: {
                    count: 0,
                    current: 1,
                    limit: 20,
                    limitList: [10, 20, 40]
                },
                baseCompressedType: ['rar', 'zip', 'gz', 'tgz', 'tar', 'jar'],
                compressedData: [],
                metadataLabelList: [],
                debounceClickTreeNode: null,
                inFolderSearchName: this.$route.query.fileName,
                searchFullPath: '',
                showIamDenyDialog: false,
                showData: {},
                sortParams: [],
                timer: null
            }
        },
        computed: {
            ...mapState(['repoListAll', 'userList', 'permission', 'genericTree', 'scannerSupportFileNameExt', 'userInfo']),
            projectId () {
                return this.$route.params.projectId
            },
            repoName () {
                return this.$route.query.repoName
            },
            currentRepo () {
                return this.repoListAll.find(repo => repo.name === this.repoName) || {}
            },
            breadcrumb () {
                if (!this.selectedTreeNode.roadMap) return
                const breadcrumb = []
                let node = this.genericTree
                const road = this.selectedTreeNode.roadMap.split(',')
                road.forEach(index => {
                    if (node[index]) {
                        breadcrumb.push({
                            name: node[index].displayName,
                            value: node[index],
                            cilckHandler: item => {
                                this.itemClickHandler(item.value)
                            }
                        })
                        node = node[index].children
                    }
                })
                return breadcrumb
            },
            community () {
                return RELEASE_MODE === 'community'
            },
            searchFileName () {
                return this.$route.query.fileName
            },
            localRepo () {
                const configuration = this.currentRepo.configuration
                if (this.currentRepo.category === 'COMPOSITE') {
                    const proxy = configuration.proxy
                    return !proxy || !proxy.channelList || proxy.channelList.length === 0
                } else {
                    return this.currentRepo.category === 'LOCAL'
                }
            },
            sortableRepo () {
                // 仅允许LOCAL仓库排序
                return this.localRepo ? 'custom' : false
            }
        },
        watch: {
            projectId () {
                this.getRepoListAll({ projectId: this.projectId })
            },
            repoName () {
                this.initTree()
            },
            '$route.query.path' () {
                this.pathChange()
            }
        },
        beforeRouteEnter (to, from, next) {
            // 前端隐藏report仓库/log仓库
            if (MODE_CONFIG === 'ci' && (to.query.repoName === 'report' || to.query.repoName === 'log')) {
                next({
                    name: 'repoList',
                    params: {
                        projectId: to.params.projectId
                    }
                })
            } else next()
        },
        created () {
            this.getRepoListAll({ projectId: this.projectId }).then(_ => {
                this.pathChange()
            })
            this.initTree()
            this.debounceClickTreeNode = debounce(this.clickTreeNodeHandler, 100)
            window.repositoryVue.$on('upload-refresh', debounce((path) => {
                if (path.replace(/\/[^/]+$/, '').includes(this.selectedTreeNode.fullPath)) {
                    this.itemClickHandler(this.selectedTreeNode)
                }
            }))
            if (!this.community || SHOW_ANALYST_MENU) {
                this.refreshSupportFileNameExtList()
            }

            this.sortType = this.localRepo ? 'lastModifiedDate' : ''
        },
        methods: {
            convertFileSize,
            getIconName,
            formatDate,
            ...mapMutations(['INIT_TREE']),
            ...mapActions([
                'getRepoListAll',
                'getFolderList',
                'getArtifactoryList',
                'getMetadataLabelList',
                'deleteArtifactory',
                'deleteMultiArtifactory',
                'getFolderSize',
                'getFileNumOfFolder',
                'getMultiFileNumOfFolder',
                'previewBasicFile',
                'previewCompressedBasicFile',
                'previewCompressedFileList',
                'forbidMetadata',
                'refreshSupportFileNameExtList',
                'getMultiFolderNumOfFolder',
                'getPermissionUrl'
            ]),
            showRepoScan (node) {
                const indexOfLastDot = node.name.lastIndexOf('.')
                let supportFileNameExt = false
                if (indexOfLastDot === -1) {
                    supportFileNameExt = this.scannerSupportFileNameExt.includes('')
                } else {
                    supportFileNameExt = this.scannerSupportFileNameExt.includes(node.name.substring(indexOfLastDot + 1))
                }
                const show = !this.community || SHOW_ANALYST_MENU
                return !node.folder && show && supportFileNameExt
            },
            tooltipContent ({ forbidType, forbidUser }) {
                switch (forbidType) {
                    case 'SCANNING':
                        return this.$t('forbidTip1')
                    case 'QUALITY_UNPASS':
                        return this.$t('forbidTip2')
                    case 'MANUAL':
                        return `${this.userList[forbidUser]?.name || forbidUser}` + this.$t('manualBan')
                    default:
                        return ''
                }
            },
            changeSideBarWidth (sideBarWidth) {
                if (sideBarWidth > 260) {
                    this.sideBarWidth = sideBarWidth
                }
            },
            renderHeader (h, { column }) {
                const elements = [h('span', column.label)]
                if (this.localRepo) {
                    elements.push(h('i', { class: 'ml5 devops-icon icon-down-shape' }))
                }
                return h('div', {
                    class: {
                        'flex-align-center': true,
                        'hover-btn': this.localRepo,
                        'selected-header': this.sortType === column.property
                    },
                    on: {
                        click: () => {
                            if (this.localRepo) {
                                this.sortType = column.property
                                this.$refs.artifactoryTable.clearSort()
                                this.sortParams = []
                                const sortParam = {
                                    properties: column.property,
                                    direction: 'DESC'
                                }
                                this.sortParams.push(sortParam)
                                this.handlerPaginationChange()
                            }
                        }
                    }
                }, elements)
            },
            initTree () {
                this.INIT_TREE([{
                    name: this.replaceRepoName(this.repoName),
                    displayName: this.replaceRepoName(this.repoName),
                    fullPath: '',
                    folder: true,
                    children: [],
                    roadMap: '0'
                }])
            },
            pathChange () {
                const paths = (this.$route.query.path || '').split('/').filter(Boolean)
                paths.pop() // 定位到文件/文件夹的上级目录
                const tempPaths = paths
                const num = paths.length
                let tempTree = this.genericTree[0]
                if (tempTree.children.length === 0 && num > 0) {
                    paths.reduce(async (chain, path) => {
                        const node = await chain
                        if (!node) return
                        await this.updateGenericTreeNode(node)
                        const child = node.children.find(child => child.name === path)
                        if (!child) return
                        this.sideTreeOpenList.push(child.roadMap)
                        return child
                    }, Promise.resolve(this.genericTree[0])).then(node => {
                        this.itemClickHandler(node || this.genericTree[0])
                    })
                } else {
                    let destNode
                    while (tempPaths.length !== 0) {
                        tempTree = tempTree.children.find(node => node.name === tempPaths[0])
                        if (tempPaths.length === 1) {
                            destNode = tempTree
                        }
                        tempPaths.shift()
                    }
                    if (num !== 0) {
                        this.itemClickHandler(destNode)
                    } else {
                        this.itemClickHandler(this.genericTree[0])
                    }
                }
            },
            // 获取中间列表数据
            async getArtifactories () {
                this.isLoading = true

                const metadataLabelList = await this.getMetadataLabelList({
                    projectId: this.projectId
                })
                let sortTypes
                this.metadataLabelList = metadataLabelList
                if (this.sortParams.some(param => {
                    return param.direction === 'ASC'
                })) {
                    sortTypes = {
                        properties: [],
                        direction: 'ASC'
                    }
                    sortTypes.properties.push(this.sortParams[0].properties)
                } else {
                    sortTypes = {
                        properties: ['folder', 'lastModifiedDate'],
                        direction: 'DESC'
                    }
                    if (this.sortParams.length > 0) {
                        sortTypes.properties = []
                        if (this.sortParams[0].properties === 'name' || this.sortParams[0].properties === 'lastModifiedDate') {
                            sortTypes.properties.push('folder')
                        }
                        sortTypes.properties.push(this.sortParams[0].properties)
                    }
                }
                this.getArtifactoryList({
                    projectId: this.projectId,
                    repoName: this.repoName,
                    fullPath: this.searchFullPath || this.selectedTreeNode?.fullPath,
                    ...(this.inFolderSearchName
                        ? {
                            name: this.searchFileName
                        }
                        : {}
                    ),
                    current: this.pagination.current,
                    limit: this.pagination.limit,
                    sortType: sortTypes,
                    isPipeline: this.repoName === 'pipeline',
                    searchFlag: this.searchFileName,
                    localRepo: this.localRepo
                }).then(({ records, totalRecords }) => {
                    this.pagination.count = totalRecords
                    this.artifactoryList = records.map(v => {
                        v.nodeMetadata.forEach(item => {
                            metadataLabelList.forEach(ele => {
                                if (ele.labelKey === item.key) {
                                    item.display = ele.display
                                }
                            })
                        })

                        return {
                            metadata: {},
                            clusterNames: v.clusterNames || [],
                            ...v,
                            // 流水线文件夹名称替换
                            name: v.metadata?.displayName || v.name
                        }
                    })
                    if (this.repoName === 'pipeline' && !this.inFolderSearchName) {
                        const originData = this.artifactoryList
                        const direction = this.sortParams.some(param => {
                            return param.direction === 'ASC'
                        })
                        if (!direction) {
                            const hasFolder = sortTypes.properties.some(param => {
                                return param === 'folder'
                            })
                            if (hasFolder) {
                                const hasName = sortTypes.properties.some(param => {
                                    return param === 'name'
                                })
                                originData.sort(function (a) {
                                    return a.folder
                                }).sort(function (a, b) {
                                    if (hasName) {
                                        return b.name - a.name
                                    } else {
                                        return a.lastModifiedDate - b.lastModifiedDate
                                    }
                                })
                            } else {
                                const hasSize = sortTypes.properties.some(param => {
                                    return param === 'size'
                                })
                                originData.sort(function (a, b) {
                                    if (hasSize) {
                                        return b.size - a.size
                                    } else {
                                        return b.nodeNum - a.nodeNum
                                    }
                                })
                            }
                        } else {
                            const hasSize = sortTypes.properties.some(param => {
                                return param === 'size'
                            })
                            originData.sort(function (a, b) {
                                if (hasSize) {
                                    return a.size - b.size
                                } else {
                                    return a.nodeNum - b.nodeNum
                                }
                            })
                        }
                        this.artifactoryList = originData
                    }
                }).finally(() => {
                    this.isLoading = false
                })
            },
            changePage (inc) {
                if (this.isLoading) {
                    return
                }
                const oldPage = this.pagination.current

                if (this.pagination.current + inc <= 0) {
                    this.pagination.current = 1
                } else if (this.artifactoryList.length !== 0 || inc < 0) {
                    this.pagination.current += inc
                }

                if (oldPage !== this.pagination.current) {
                    this.handlerPaginationChange({ current: this.pagination.current })
                }
            },
            handlerPaginationChange ({ current = 1, limit = this.pagination.limit } = {}) {
                this.pagination.current = current
                this.pagination.limit = limit
                this.getArtifactories()
            },
            // 树组件选中文件夹
            itemClickHandler (node) {
                // 添加防抖操作，防止用户在目录树之前一直快速切换，导致前端在某些情况下获取不到相应参数进而导致报错
                this.debounceClickTreeNode(node)
            },
            clickTreeNodeHandler (node) {
                if (node.fullPath + '/default' === this.$route.query.path) {
                    this.selectedTreeNode = node

                    this.handlerPaginationChange()
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
                } else {
                    // 更新url参数
                    this.$router.replace({
                        query: {
                            ...this.$route.query,
                            path: `${node.fullPath}/default`
                        }
                    })
                }
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
                    if (node.roadMap !== this.selectedTreeNode.roadMap && reg.test(this.selectedTreeNode.roadMap)) return
                    this.updateGenericTreeNode(node)
                }
            },
            updateGenericTreeNode (item) {
                this.$set(item, 'loading', true)
                return this.getFolderList({
                    projectId: this.projectId,
                    repoName: this.repoName,
                    fullPath: item.fullPath,
                    roadMap: item.roadMap,
                    isPipeline: this.repoName === 'pipeline',
                    localRepo: this.localRepo
                }).catch(err => {
                    if (err.status === 403) {
                        this.getPermissionUrl({
                            body: {
                                projectId: this.projectId,
                                action: 'READ',
                                resourceType: 'REPO',
                                uid: this.userInfo.name,
                                repoName: this.repoName
                            }
                        }).then(res => {
                            if (res !== '') {
                                this.showIamDenyDialog = true
                                this.showData = {
                                    projectId: this.projectId,
                                    repoName: this.repoName,
                                    action: 'READ',
                                    url: res
                                }
                            }
                        })
                    }
                }).finally(() => {
                    this.$set(item, 'loading', false)
                })
            },
            // 双击table打开文件夹
            openFolder (row) {
                if (!row.folder) return
                if (this.searchFileName) {
                    // 搜索中打开文件夹
                    this.inFolderSearchName = ''
                    this.searchFullPath = row.fullPath
                    this.handlerPaginationChange()
                } else {
                    const node = this.selectedTreeNode.children.find(v => v.fullPath === row.fullPath)
                    this.itemClickHandler(node)
                }
            },
            showDetail ({ folder, fullPath, category }) {
                this.$refs.genericDetail.setData({
                    show: true,
                    loading: false,
                    projectId: this.projectId,
                    repoName: this.repoName,
                    folder,
                    path: fullPath,
                    data: {},
                    metadataLabelList: this.metadataLabelList,
                    localNode: category !== 'REMOTE'
                })
            },
            renameRes ({ name, fullPath }) {
                this.$refs.genericFormDialog.setData({
                    show: true,
                    loading: false,
                    type: 'rename',
                    name,
                    path: fullPath,
                    title: `${this.$t('rename') + this.$t('space')} (${name})`
                })
            },
            addFolder ({ fullPath }) {
                this.$refs.genericFormDialog.setData({
                    show: true,
                    loading: false,
                    type: 'add',
                    path: fullPath + '/',
                    title: `${this.$t('create') + this.$t('space') + this.$t('folder')}`
                })
            },
            handlerScan ({ name, fullPath }) {
                this.$refs.genericFormDialog.setData({
                    show: true,
                    loading: false,
                    title: this.$t('scanArtifact'),
                    type: 'scan',
                    id: '',
                    name,
                    path: fullPath
                })
            },
            refreshNodeChange (destTreeData) {
                // 在当前仓库中复制或移动文件夹后需要更新选中目录
                if (destTreeData?.repoName && destTreeData?.folder && destTreeData?.repoName === this.repoName) {
                    // 复制或移动之后需要默认选中目标文件夹
                    this.itemClickHandler(destTreeData)
                }
                this.updateGenericTreeNode(this.selectedTreeNode)
                this.getArtifactories()
            },
            handlerShare ({ name, fullPath }) {
                this.$refs.genericShareDialog.setData({
                    projectId: this.projectId,
                    repoName: this.repoName,
                    show: true,
                    loading: false,
                    title: `${this.$t('share') + this.$t('space')} (${name})`,
                    path: fullPath,
                    user: [],
                    ip: [],
                    permits: '',
                    time: 7
                })
            },
            async deleteRes ({ name, folder, fullPath }) {
                if (!fullPath) return
                let totalRecords
                let totalFolderNum
                if (folder) {
                    totalRecords = await this.getMultiFileNumOfFolder({
                        projectId: this.projectId,
                        repoName: this.repoName,
                        paths: [fullPath]
                    })
                    totalFolderNum = await this.getMultiFolderNumOfFolder({
                        projectId: this.projectId,
                        repoName: this.repoName,
                        paths: [fullPath],
                        isFolder: true
                    })
                }
                this.$confirm({
                    theme: 'danger',
                    message: `${this.$t('confirm') + this.$t('space') + this.$t('delete') + this.$t('space')}${folder ? this.$t('folder') : this.$t('file')} ${name} ？`,
                    subMessage: `${folder && totalRecords ? this.$t('totalFilesMsg', [totalRecords]) : ''}`,
                    confirmFn: () => {
                        return this.deleteArtifactory({
                            projectId: this.projectId,
                            repoName: this.repoName,
                            fullPath
                        }).then(res => {
                            this.refreshNodeChange()
                            if (folder && totalRecords === res.deletedNumber - totalFolderNum) {
                                this.$bkMessage({
                                    theme: 'success',
                                    message: this.$t('delete') + this.$t('space') + this.$t('success')
                                })
                            } else if (!folder && res.deletedNumber === 1) {
                                this.$bkMessage({
                                    theme: 'success',
                                    message: this.$t('delete') + this.$t('space') + this.$t('success')
                                })
                            } else {
                                const failNum = folder ? totalRecords + totalFolderNum - res.deletedNumber : 1
                                this.$bkMessage({
                                    theme: 'error',
                                    message: this.$t('delete') + this.$t('space') + res.deletedNumber + this.$t('per') + this.$t('file') + this.$t('space') + this.$t('success') + ',' + this.$t('delete') + this.$t('space') + failNum + this.$t('per') + this.$t('file') + this.$t('space') + this.$t('fail')
                                })
                            }
                            this.$bkMessage({
                                theme: 'success',
                                message: this.$t('delete') + this.$t('success')
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
                                        path: fullPath
                                    }
                                }).then(res => {
                                    if (res !== '') {
                                        this.showIamDenyDialog = true
                                        this.showData = {
                                            projectId: this.projectId,
                                            repoName: this.repoName,
                                            action: 'DELETE',
                                            path: fullPath,
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
                        })
                    }
                })
            },
            moveRes ({ name, fullPath, folder }) {
                this.$refs.genericTreeDialog.setTreeData({
                    show: true,
                    type: 'move',
                    title: `${this.$t('move')} (${name})`,
                    path: fullPath,
                    folder: folder
                })
            },
            copyRes ({ name, fullPath, folder }) {
                this.$refs.genericTreeDialog.setTreeData({
                    show: true,
                    type: 'copy',
                    title: `${this.$t('copy')} (${name})`,
                    path: fullPath,
                    folder: folder
                })
            },
            handlerUpload ({ fullPath }, folder = false) {
                this.$globalUploadFiles({
                    projectId: this.projectId,
                    repoName: this.repoName,
                    folder,
                    fullPath
                })
            },
            handlerDownload (row) {
                const transPath = encodeURIComponent(row.fullPath)
                const url = `/generic/${this.projectId}/${this.repoName}/${transPath}?download=true`
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
                        this.$refs.loading.message = this.$t('backUpMessage', { 0: row.name })
                        this.timerDownload(url, row.fullPath, row.name)
                    } else if (e.status === 403) {
                        this.getPermissionUrl({
                            body: {
                                projectId: this.projectId,
                                action: 'READ',
                                resourceType: 'NODE',
                                uid: this.userInfo.name,
                                repoName: this.repoName,
                                path: row.fullPath
                            }
                        }).then(res => {
                            if (res !== '') {
                                this.showIamDenyDialog = true
                                this.showData = {
                                    projectId: this.projectId,
                                    repoName: this.repoName,
                                    path: row.fullPath,
                                    action: 'READ',
                                    url: res
                                }
                            } else {
                                const message = this.$t('fileDownloadError', [this.$route.params.projectId])
                                this.$bkMessage({
                                    theme: 'error',
                                    message
                                })
                            }
                        })
                    } else {
                        const message = this.$t('fileError')
                        this.$bkMessage({
                            theme: 'error',
                            message
                        })
                    }
                })
            },
            timerDownload (url, fullPath, name) {
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
                            this.$refs.loading.message = this.$t('backUpMessage', { 0: name })
                        } else {
                            clearInterval(this.timer)
                            this.timer = null
                            this.$refs.loading.isShow = false
                            const message = e.status === 403 ? this.$t('fileDownloadError', [this.$route.params.projectId]) : this.$t('fileError')
                            this.$bkMessage({
                                theme: 'error',
                                message
                            })
                        }
                    })
                }, 5000)
            },
            handlerMultiDownload () {
                const fullPaths = this.multiSelect.map(r => r.fullPath)
                customizeDownloadFile(this.projectId, this.repoName, fullPaths)
            },
            handlerForbid ({ fullPath, metadata: { forbidStatus } }) {
                this.forbidMetadata({
                    projectId: this.projectId,
                    repoName: this.repoName,
                    fullPath,
                    body: {
                        nodeMetadata: [{ key: 'forbidStatus', value: !forbidStatus }]
                    }
                }).then(() => {
                    this.$bkMessage({
                        theme: 'success',
                        message: (forbidStatus ? this.$t('liftBan') : this.$t('forbiddenUse')) + this.$t('space') + this.$t('success')
                    })
                    this.getArtifactories()
                }).catch(e => {
                    if (e.status === 403) {
                        this.getPermissionUrl({
                            body: {
                                projectId: this.projectId,
                                action: 'UPDATE',
                                resourceType: 'NODE',
                                path: fullPath,
                                uid: this.userInfo.name,
                                repoName: this.repoName
                            }
                        }).then(res => {
                            if (res !== '') {
                                this.showIamDenyDialog = true
                                this.showData = {
                                    projectId: this.projectId,
                                    repoName: this.repoName,
                                    action: 'UPDATE',
                                    path: fullPath,
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
                })
            },
            calculateFolderSize (row) {
                this.$set(row, 'sizeLoading', true)
                this.$refs.loading.isShow = true
                this.$refs.loading.complete = false
                this.$refs.loading.backUp = false
                this.$refs.loading.title = this.$t('calculateTitle')
                this.$refs.loading.message = this.$t('calculateMsg', { 0: row.fullPath })
                this.$refs.loading.subMessage = ''
                this.$refs.loading.cancelMessage = this.$t('cancel')
                this.getFolderSize({
                    projectId: this.projectId,
                    repoName: this.repoName,
                    fullPath: row.fullPath
                }).then(({ size, subNodeWithoutFolderCount }) => {
                    this.$set(row, 'size', size)
                    this.$set(row, 'nodeNum', subNodeWithoutFolderCount)
                    this.$refs.loading.message = this.$t('calculateCompleteMsg', { 0: row.fullPath, 1: convertFileSize(size) })
                    this.$refs.loading.complete = true
                }).finally(() => {
                    this.$set(row, 'sizeLoading', false)
                })
            },
            selectable (row, index) {
                return row.category !== 'REMOTE'
            },
            selectMultiRow (selects) {
                this.multiSelect = selects
            },
            async handlerMultiDelete () {
                const paths = this.multiSelect.map(r => r.fullPath)
                const totalRecords = await this.getMultiFileNumOfFolder({
                    projectId: this.projectId,
                    repoName: this.repoName,
                    paths: paths
                })
                const folderNum = await this.getMultiFolderNumOfFolder({
                    projectId: this.projectId,
                    repoName: this.repoName,
                    paths: paths,
                    isFolder: true
                }).catch(e => {
                    if (e.status === 403) {
                        this.getPermissionUrl({
                            body: {
                                projectId: this.projectId,
                                action: 'DELETE',
                                resourceType: 'REPO',
                                uid: this.userInfo.name,
                                repoName: this.repoName
                            }
                        }).then(res => {
                            if (res !== '') {
                                this.showIamDenyDialog = true
                                this.showData = {
                                    projectId: this.projectId,
                                    repoName: this.repoName,
                                    action: 'DELETE',
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
                })
                this.$confirm({
                    theme: 'danger',
                    message: this.$t('batchDeleteMsg', [this.multiSelect.length]),
                    subMessage: this.$t('batchDeleteSubMsg', [totalRecords]),
                    confirmFn: () => {
                        return this.deleteMultiArtifactory({
                            projectId: this.projectId,
                            repoName: this.repoName,
                            paths
                        }).then(res => {
                            this.refreshNodeChange()
                            if (res.deletedNumber === totalRecords + folderNum) {
                                this.$bkMessage({
                                    theme: 'success',
                                    message: this.$t('delete') + this.$t('space') + this.$t('success')
                                })
                            } else {
                                const failNum = totalRecords + folderNum - res.deletedNumber
                                this.$bkMessage({
                                    theme: 'error',
                                    message: this.$t('delete') + this.$t('space') + res.deletedNumber + this.$t('per') + this.$t('file') + this.$t('space') + this.$t('success') + ',' + this.$t('delete') + this.$t('space') + failNum + this.$t('per') + this.$t('file') + this.$t('space') + this.$t('fail')
                                })
                            }
                        })
                    }
                })
            },
            async handlerPreviewBasicsFile (row) {
                this.$refs.previewBasicFileDialog.setDialogData({
                    show: true,
                    title: row.name,
                    isLoading: true
                })
                const res = await this.previewBasicFile({
                    projectId: this.projectId,
                    repoName: this.repoName,
                    path: row.fullPath
                }).catch(e => {
                    if (e.status === 403) {
                        this.getPermissionUrl({
                            body: {
                                projectId: this.projectId,
                                action: 'READ',
                                resourceType: 'NODE',
                                uid: this.userInfo.name,
                                repoName: this.repoName,
                                path: row.fullPath
                            }
                        }).then(res => {
                            if (res !== '') {
                                this.showIamDenyDialog = true
                                this.showData = {
                                    projectId: this.projectId,
                                    repoName: this.repoName,
                                    action: 'READ',
                                    path: row.fullPath,
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
                })
                this.$refs.previewBasicFileDialog.setData(typeof (res) === 'string' ? res : JSON.stringify(res))
            },
            async handlerPreviewCompressedFile (row) {
                if (row.size > 1073741824) {
                    this.$bkMessage({
                        theme: 'error',
                        message: this.$t('previewCompressedLimitTips')
                    })
                    return
                }
                this.$refs.compressedFileTable.setData({
                    show: true,
                    title: row.name,
                    isLoading: true,
                    path: row.fullPath
                })

                const res = await this.previewCompressedFileList({
                    projectId: row.projectId,
                    repoName: row.repoName,
                    path: row.fullPath
                }).catch(e => {
                    if (e.status === 403) {
                        this.getPermissionUrl({
                            body: {
                                projectId: this.projectId,
                                action: 'READ',
                                resourceType: 'NODE',
                                uid: this.userInfo.name,
                                repoName: this.repoName,
                                path: row.fullPath
                            }
                        }).then(res => {
                            if (res !== '') {
                                this.showIamDenyDialog = true
                                this.showData = {
                                    projectId: this.projectId,
                                    repoName: this.repoName,
                                    action: 'READ',
                                    path: row.fullPath,
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
                })

                this.compressedData = res.reduce((acc, item) => {
                    const names = item.name.split('/')
                    names.reduce((target, name) => {
                        let temp = target.find(o => o.name === name)
                        if (!temp) {
                            target.push(temp = { name, children: [], filePath: item.name, folder: !name.includes('.'), size: item.size })
                        }
                        return temp.children
                    }, acc)
                    return acc
                }, [])
            },

            async handleShowPreview (row) {
                const { projectId, repoName, path, filePath } = row
                this.$refs.previewBasicFileDialog.setDialogData({
                    show: true,
                    title: filePath,
                    isLoading: true
                })
                const res = await this.previewCompressedBasicFile({
                    projectId,
                    repoName,
                    path,
                    filePath
                })
                this.$refs.previewBasicFileDialog.setData(typeof (res) === 'string' ? res : JSON.stringify(res))
            },

            getBtnDisabled (name) {
                return name.endsWith('txt')
                    || name.endsWith('sh')
                    || name.endsWith('bat')
                    || name.endsWith('json')
                    || name.endsWith('yaml')
                    || name.endsWith('xml')
                    || name.endsWith('log')
                    || name.endsWith('ini')
                    || name.endsWith('log')
                    || name.endsWith('properties')
                    || name.endsWith('toml')
            },
            // 文件夹内部的搜索，根据文件名或文件夹名搜索
            inFolderSearchFile () {
                this.$router.replace({
                    query: {
                        ...this.$route.query,
                        fileName: this.inFolderSearchName
                    }
                })
                if (!this.inFolderSearchName) {
                    this.searchFullPath = ''
                }
                this.handlerPaginationChange()
            },
            orderList (sort) {
                this.sortType = ''
                this.sortParams = []
                if (sort.prop) {
                    const sortParam = {
                        properties: sort.prop,
                        direction: sort.order === 'ascending' ? 'ASC' : 'DESC'
                    }
                    this.sortParams.push(sortParam)
                }
                this.getArtifactories()
            },
            closeLoading () {
                clearInterval(this.timer)
                this.timer = null
            },
            clean () {
                const fullPaths = []
                const displayPaths = []
                this.multiSelect.forEach(value => {
                    let tempTree = this.genericTree[0]
                    let tempDisplayName = '/'
                    if (value.folder === true) {
                        const pas = value.fullPath.split('/').filter(Boolean)
                        while (pas.length !== 0) {
                            tempTree = tempTree.children.find(node => node.name === pas[0])
                            tempDisplayName = tempDisplayName + tempTree.displayName + '/'
                            pas.shift()
                        }
                        displayPaths.push({
                            path: tempDisplayName,
                            isComplete: false
                        })
                        fullPaths.push({
                            path: value.fullPath,
                            isComplete: false
                        })
                    }
                })
                this.$refs.genericCleanDialog.show = true
                this.$refs.genericCleanDialog.repoName = this.repoName
                this.$refs.genericCleanDialog.projectId = this.projectId
                this.$refs.genericCleanDialog.paths = fullPaths
                this.$refs.genericCleanDialog.displayPaths = displayPaths
                this.$refs.genericCleanDialog.loading = false
                this.$refs.genericCleanDialog.isComplete = false
                if (this.repoName === 'pipeline') {
                    this.$refs.genericCleanDialog.date = beforeMonths(2)
                } else {
                    this.$refs.genericCleanDialog.date = beforeYears(1)
                }
            }
        }
    }
</script>
<style lang="scss" scoped>
.repo-generic-container {
    height: 100%;
    overflow: hidden;
    .generic-header{
        height: 60px;
        background-color: white;
        .generic-img {
            border-radius: 4px;
        }
        .generic-title {
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
    .repo-generic-main {
        height: calc(100% - 100px);
        .repo-generic-side {
            height: 100%;
            overflow: hidden;
            background-color: white;
            &-info{
                height: 50px;
                display: flex;
                align-items: center;
                padding-left: 20px;
            }
            .repo-generic-tree {
                border-top: 1px solid var(--borderColor);
                height: calc(100% - 50px);
            }
        }
        .repo-generic-table {
            flex: 1;
            height: 100%;
            width:0;
            background-color: white;
            .multi-operation {
                height: 50px;
                padding: 10px 20px;
            }
            ::v-deep .selected-header {
                color: var(--fontPrimaryColor);
                .icon-down-shape {
                    color: var(--primaryColor);
                }
            }
        }
    }
}

::v-deep .bk-table-row.selected-row {
    background-color: var(--bgHoverColor);
}
</style>
