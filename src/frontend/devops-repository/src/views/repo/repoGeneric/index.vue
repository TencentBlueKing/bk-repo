<template>
    <div class="repo-generic-container" @click="() => selectRow(selectedTreeNode)">
        <div v-show="!query" class="repo-generic-side" v-bkloading="{ isLoading: treeLoading }">
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
                    <bk-table-column :label="$t('fileName')">
                        <template slot-scope="props">
                            <div class="flex-align-center fine-name">
                                <icon size="24" :name="props.row.folder ? 'folder' : getIconName(props.row.name)" />
                                <div class="ml10" :title="props.row.name">{{props.row.name}}</div>
                            </div>
                        </template>
                    </bk-table-column>
                    <bk-table-column :label="$t('lastModifiedDate')" prop="lastModifiedDate" width="200">
                        <template slot-scope="props">{{ formatDate(props.row.lastModifiedDate) }}</template>
                    </bk-table-column>
                    <bk-table-column :label="$t('lastModifiedBy')" width="120">
                        <template slot-scope="props">
                            {{ userList[props.row.lastModifiedBy] ? userList[props.row.lastModifiedBy].name : props.row.lastModifiedBy }}
                        </template>
                    </bk-table-column>
                    <bk-table-column :label="$t('size')" width="100">
                        <template slot-scope="props">
                            <bk-button text
                                v-show="props.row.folder && !props.row.hasOwnProperty('folderSize')"
                                :disabled="props.row.sizeLoading"
                                @click="calculateFolderSize(props.row)">{{ $t('calculate') }}</bk-button>
                            <span v-show="!props.row.folder || props.row.hasOwnProperty('folderSize')">
                                {{ convertFileSize(props.row.size || props.row.folderSize || 0) }}
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
                    <template v-if="selectedRow.fullPath !== selectedTreeNode.fullPath || query">
                        <template v-if="repoName !== 'pipeline'">
                            <bk-button @click.stop="renameRes()" text theme="primary">
                                <i class="mr5 devops-icon icon-edit"></i>
                                {{ $t('rename') }}
                            </bk-button>
                            <bk-button @click.stop="moveRes()" text theme="primary">
                                <i class="mr5 devops-icon icon-move"></i>
                                {{ $t('move') }}
                            </bk-button>
                            <bk-button @click.stop="copyRes()" text theme="primary">
                                <i class="mr5 devops-icon icon-save"></i>
                                {{ $t('copy') }}
                            </bk-button>
                            <bk-button @click.stop="deleteRes()" text theme="primary">
                                <i class="mr5 devops-icon icon-delete"></i>
                                {{ $t('delete') }}
                            </bk-button>
                        </template>
                        <template v-if="!selectedRow.folder">
                            <bk-button @click.stop="handlerShare()" text theme="primary">
                                <i class="mr5 devops-icon icon-none"></i>
                                {{ $t('share') }}
                            </bk-button>
                            <bk-button @click.stop="handlerDownload()" text theme="primary">
                                <i class="mr5 devops-icon icon-download"></i>
                                {{ $t('download') }}
                            </bk-button>
                        </template>
                    </template>
                    <template v-else>
                        <template v-if="repoName !== 'pipeline'">
                            <bk-button @click.stop="addFolder()" text theme="primary">
                                <i class="mr5 devops-icon icon-folder-plus"></i>
                                {{$t('create') + $t('folder')}}
                            </bk-button>
                            <bk-button @click.stop="handlerUpload()" text theme="primary">
                                <i class="mr5 devops-icon icon-upload"></i>
                                {{ $t('upload') }}
                            </bk-button>
                        </template>
                        <bk-button
                            @click.stop="getArtifactories()" text theme="primary">
                            <i class="mr5 devops-icon icon-refresh"></i>
                            {{ $t('refresh') }}
                        </bk-button>
                    </template>
                </div>
            </aside>
        </div>

        <genericDetail :detail-slider="detailSlider"></genericDetail>
        <bk-dialog
            v-model="formDialog.show"
            :title="formDialog.title"
            :close-icon="false"
            :quick-close="false"
            width="600"
            header-position="left">
            <bk-form class="repo-generic-form" :label-width="120" :model="formDialog" :rules="rules" ref="formDialog">
                <template v-if="formDialog.type === 'add'">
                    <bk-form-item :label="$t('folder') + $t('path')">
                        <span class="break-all">{{ selectedRow.fullPath + '/' + formDialog.path }}</span>
                    </bk-form-item>
                    <bk-form-item :label="$t('createFolderLabel')" :required="true" property="path">
                        <bk-input v-model.trim="formDialog.path" :placeholder="$t('folderNamePlacehodler')"></bk-input>
                    </bk-form-item>
                </template>
                <template v-else-if="formDialog.type === 'rename'">
                    <bk-form-item :label="$t('name')" :required="true" property="name">
                        <bk-input v-model.trim="formDialog.name" :placeholder="$t('folderNamePlacehodler')"></bk-input>
                    </bk-form-item>
                </template>
                <template v-else-if="formDialog.type === 'share'">
                    <bk-form-item label="授权用户" property="user">
                        <bk-tag-input
                            v-model="formDialog.user"
                            :list="Object.values(userList)"
                            placeholder="授权访问用户，为空则任意用户可访问，按Enter键确认"
                            trigger="focus"
                            allow-create
                            has-delete-icon>
                        </bk-tag-input>
                    </bk-form-item>
                    <bk-form-item label="授权IP" property="ip">
                        <bk-tag-input
                            v-model="formDialog.ip"
                            placeholder="授权访问IP，为空则任意IP可访问，按Enter键确认"
                            trigger="focus"
                            allow-create
                            has-delete-icon>
                        </bk-tag-input>
                    </bk-form-item>
                    <bk-form-item label="访问次数" property="permits">
                        <bk-input v-model.trim="formDialog.permits" placeholder="请输入数字，小于等于0则永久有效"></bk-input>
                    </bk-form-item>
                    <bk-form-item :label="`${$t('validity')}(${$t('day')})`" property="time">
                        <bk-input v-model.trim="formDialog.time" placeholder="请输入数字，小于等于0则永久有效"></bk-input>
                    </bk-form-item>
                </template>
            </bk-form>
            <div slot="footer">
                <bk-button ext-cls="mr5" :loading="formDialog.loading" theme="primary" @click="submitFormDialog">{{$t('submit')}}</bk-button>
                <bk-button ext-cls="mr5" theme="default" @click="cancelFormDialog">{{$t('cancel')}}</bk-button>
            </div>
        </bk-dialog>

        <bk-dialog
            v-model="treeDialog.show"
            :title="treeDialog.title"
            :close-icon="false"
            :quick-close="false"
            width="600"
            height="600"
            header-position="left">
            <div class="dialog-tree-container">
                <repo-tree
                    ref="dialogTree"
                    :list="genericTree"
                    :open-list="treeDialog.openList"
                    :selected-node="treeDialog.selectedNode"
                    @icon-click="item => iconClickHandler(item, false, treeDialog.openList)"
                    @item-click="changeDialogTreeNode">
                </repo-tree>
            </div>
            <div slot="footer">
                <bk-button :loading="treeDialog.loading" theme="primary" @click="treeConfirmHandler">{{ $t('confirm') }}</bk-button>
                <bk-button @click="treeDialog.show = false">{{ $t('cancel') }}</bk-button>
            </div>
        </bk-dialog>

        <generic-upload v-bind="uploadDialog" @cancel="cancelUpload"></generic-upload>
    </div>
</template>
<script>
    import RepoTree from '@/components/RepoTree'
    import genericDetail from './genericDetail'
    import genericUpload from './genericUpload'
    import { convertFileSize, formatDate } from '@/utils'
    import { getIconName } from '@/store/publicEnum'
    import { mapState, mapMutations, mapActions } from 'vuex'
    export default {
        name: 'repoGeneric',
        components: { RepoTree, genericDetail, genericUpload },
        data () {
            return {
                isLoading: false,
                treeLoading: false,
                importantSearch: '',
                // 左侧树处于打开状态的目录
                sideTreeOpenList: [],
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
                // 新建文件夹、重命名、分享、制品晋级
                formDialog: {
                    show: false,
                    loading: false,
                    type: '',
                    name: '',
                    title: '',
                    user: [],
                    ip: [],
                    permits: 1,
                    time: 1
                },
                // formDialog Rules
                rules: {
                    path: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + this.$t('folder') + this.$t('path'),
                            trigger: 'blur'
                        },
                        {
                            regex: /^((\w|-|\.){1,50}\/)*((\w|-|\.){1,50})$/,
                            message: this.$t('folder') + this.$t('path') + this.$t('include') + this.$t('folderNamePlacehodler'),
                            trigger: 'blur'
                        }
                    ],
                    name: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + this.$t('fileName'),
                            trigger: 'blur'
                        },
                        {
                            regex: /^(\w|-|\.){1,50}$/,
                            message: this.$t('fileName') + this.$t('include') + this.$t('folderNamePlacehodler'),
                            trigger: 'blur'
                        }
                    ],
                    permits: [
                        {
                            regex: /^[0-9]*$/,
                            message: '请输入数字',
                            trigger: 'blur'
                        }
                    ],
                    time: [
                        {
                            regex: /^[0-9]*$/,
                            message: '请输入数字',
                            trigger: 'blur'
                        }
                    ]
                },
                // 查看详情
                detailSlider: {
                    show: false,
                    loading: false,
                    folder: false,
                    data: {}
                },
                // 移动，复制
                treeDialog: {
                    show: false,
                    loading: false,
                    type: 'move',
                    title: '',
                    openList: [],
                    selectedNode: {}
                },
                // 上传制品
                uploadDialog: {
                    show: false,
                    title: '',
                    fullPath: ''
                },
                query: null,
                uploadXHR: null
            }
        },
        computed: {
            ...mapState(['userList', 'genericTree']),
            projectId () {
                return this.$route.params.projectId
            },
            repoName () {
                return this.$route.query.name
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
                this.handlerPaginationChange()
            },
            'selectedTreeNode.fullPath' () {
                // 重置选中行
                this.selectedRow.element && this.selectedRow.element.classList.remove('selected-row')
                this.selectedRow = this.selectedTreeNode
                this.handlerPaginationChange()
                this.setBreadcrumb()
            }
        },
        created () {
            this.initPage()
            this.setBreadcrumb()
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
                'uploadArtifactory',
                'deleteArtifactory',
                'renameNode',
                'moveNode',
                'copyNode',
                'shareArtifactory',
                'getFolderSize'
            ]),
            async initPage () {
                this.importantSearch = ''
                this.INIT_TREE()
                this.sideTreeOpenList = []
                await this.itemClickHandler(this.genericTree[0])
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
            // 选中文件夹
            async itemClickHandler (item) {
                // 初始化选中行
                this.selectedTreeNode = item
                await this.iconClickHandler(item, true)
            },
            async iconClickHandler (item, justOpen = false, target = this.sideTreeOpenList) {
                const reg = new RegExp(`^${item.roadMap}`)
                if (!justOpen && target.includes(item.roadMap)) {
                    target.splice(0, target.length, ...target.filter(v => !reg.test(v)))
                } else {
                    target.push(item.roadMap)
                    if (item.loading) return
                    if (item.children && item.children.length && reg.test(this.selectedTreeNode.roadMap)) return
                    await this.updateGenericTreeNode(item)
                }
            },
            async updateGenericTreeNode (item) {
                if (this.query) return
                this.$set(item, 'loading', true)
                await this.getFolderList({
                    projectId: this.projectId,
                    repoName: this.repoName,
                    fullPath: item.fullPath,
                    roadMap: item.roadMap,
                    isPipeline: this.repoName === 'pipeline'
                })
                this.$set(item, 'loading', false)
            },
            // 双击table打开文件夹
            async openFolder (row, $event) {
                if (!row.folder) return
                $event.stopPropagation()
                this.rowClickCallback && clearTimeout(this.rowClickCallback)
                const node = this.selectedTreeNode.children.find(v => v.fullPath === row.fullPath)
                this.itemClickHandler(node)
                // 打开选中节点的左侧树的父节点
                node.roadMap.split(',').forEach((v, i, arr) => {
                    const roadMap = arr.slice(0, i + 1).join(',')
                    this.sideTreeOpenList.push(roadMap)
                })
            },
            // 控制选中的行，手动添加样式
            getParentElement (element) {
                let parent = element.parentElement
                while (!parent.className.includes('bk-table-row') && !parent.className.includes('repo-generic-container')) {
                    parent = parent.parentElement
                }
                parent.classList.add('selected-row')
                return parent
            },
            // 控制选中的行
            selectRow (row, $event) {
                $event && $event.stopPropagation()
                this.rowClickCallback && clearTimeout(this.rowClickCallback)
                this.rowClickCallback = window.setTimeout(() => {
                    this.selectedRow.element && this.selectedRow.element.classList.remove('selected-row')
                    this.selectedRow = {
                        ...row,
                        element: $event ? this.getParentElement($event.target) : null
                    }
                }, 300)
            },
            async showDetail () {
                this.detailSlider = {
                    show: true,
                    loading: true,
                    folder: this.selectedRow.folder,
                    data: {}
                }
                const data = await this.getNodeDetail({
                    projectId: this.projectId,
                    repoName: this.repoName,
                    fullPath: this.selectedRow.fullPath
                })
                this.detailSlider.data = {
                    ...data,
                    name: data.name || this.repoName,
                    size: convertFileSize(data.size),
                    createdBy: this.userList[data.createdBy] ? this.userList[data.createdBy].name : data.createdBy,
                    createdDate: formatDate(data.createdDate),
                    lastModifiedBy: this.userList[data.lastModifiedBy] ? this.userList[data.lastModifiedBy].name : data.lastModifiedBy,
                    lastModifiedDate: formatDate(data.lastModifiedDate)
                }
                this.detailSlider.loading = false
            },
            renameRes () {
                this.formDialog = {
                    ...this.formDialog,
                    show: true,
                    loading: false,
                    type: 'rename',
                    name: this.selectedRow.name,
                    title: `${this.$t('rename')} (${this.selectedRow.name})`
                }
            },
            addFolder () {
                this.formDialog = {
                    ...this.formDialog,
                    show: true,
                    loading: false,
                    type: 'add',
                    path: '',
                    title: `${this.$t('create') + this.$t('folder')} (${this.selectedTreeNode.fullPath || '/'})`
                }
            },
            handlerShare () {
                this.formDialog = {
                    ...this.formDialog,
                    show: true,
                    loading: false,
                    type: 'share',
                    title: `${this.$t('share')} (${this.selectedRow.name})`,
                    user: [],
                    ip: [],
                    permits: 1,
                    time: 1
                }
            },
            async submitFormDialog () {
                await this.$refs.formDialog.validate()
                this.formDialog.loading = true
                let message = ''
                let fn = null
                switch (this.formDialog.type) {
                    case 'add':
                        fn = this.submitAddFolder().then(() => {
                            this.updateGenericTreeNode(this.selectedTreeNode)
                        })
                        message = this.$t('create') + this.$t('folder')
                        break
                    case 'rename':
                        fn = this.submitRenameNode().then(() => {
                            this.updateGenericTreeNode(this.selectedTreeNode)
                        })
                        message = this.$t('rename')
                        break
                    case 'share':
                        fn = this.submitShareArtifactory()
                        message = this.$t('share')
                        break
                    case 'tag':
                        fn = this.submitTag()
                        message = this.$t('upgrade')
                        break
                }
                await fn.finally(() => {
                    this.formDialog.loading = false
                })
                this.selectRow(this.selectedTreeNode)
                this.getArtifactories()
                this.$bkMessage({
                    theme: 'success',
                    message: message + this.$t('success')
                })
                this.cancelFormDialog()
            },
            cancelFormDialog () {
                this.formDialog.show = false
                this.$refs.formDialog.clearError()
            },
            submitAddFolder () {
                return this.createFolder({
                    projectId: this.projectId,
                    repoName: this.repoName,
                    fullPath: `${this.selectedTreeNode.fullPath}/${this.formDialog.path}`
                })
            },
            submitRenameNode () {
                return this.renameNode({
                    projectId: this.projectId,
                    repoName: this.repoName,
                    fullPath: this.selectedRow.fullPath,
                    newFullPath: this.selectedRow.fullPath.replace(/[^/]*$/, this.formDialog.name)
                })
            },
            submitShareArtifactory () {
                return this.shareArtifactory({
                    projectId: this.projectId,
                    repoName: this.repoName,
                    fullPathSet: [this.selectedRow.fullPath],
                    type: 'DOWNLOAD',
                    host: `${location.origin}/web/generic`,
                    needsNotify: true,
                    ...(this.formDialog.ip.length ? { authorizedIpSet: this.formDialog.ip } : {}),
                    ...(this.formDialog.user.length ? { authorizedUserSet: this.formDialog.user } : {}),
                    ...(Number(this.formDialog.time) > 0 ? { expireSeconds: Number(this.formDialog.time) * 86400 } : {}),
                    ...(Number(this.formDialog.permits) > 0 ? { permits: Number(this.formDialog.permits) } : {})
                })
            },
            async deleteRes () {
                this.$bkInfo({
                    title: `${this.$t('confirm') + this.$t('delete')}${this.selectedRow.folder ? this.$t('folder') : this.$t('file')}？`,
                    closeIcon: false,
                    theme: 'danger',
                    confirmFn: () => {
                        this.deleteArtifactory({
                            projectId: this.projectId,
                            repoName: this.repoName,
                            fullPath: this.selectedRow.fullPath
                        }).then(res => {
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
                this.treeDialog = {
                    ...this.treeDialog,
                    show: true,
                    type: 'move',
                    title: `${this.$t('move')} (${this.selectedRow.name})`,
                    openList: [],
                    selectedNode: {}
                }
            },
            copyRes () {
                this.treeDialog = {
                    ...this.treeDialog,
                    show: true,
                    type: 'copy',
                    title: `${this.$t('copy')} (${this.selectedRow.name})`,
                    openList: [],
                    selectedNode: {}
                }
            },
            changeDialogTreeNode (item) {
                this.treeDialog.selectedNode = item
                this.iconClickHandler(item, true, this.treeDialog.openList)
            },
            treeConfirmHandler () {
                this.treeDialog.loading = true
                this[this.treeDialog.type + 'Node']({
                    body: {
                        srcProjectId: this.projectId,
                        srcRepoName: this.repoName,
                        srcFullPath: this.selectedRow.fullPath,
                        destProjectId: this.projectId,
                        destRepoName: this.repoName,
                        destFullPath: `${this.treeDialog.selectedNode.fullPath}`,
                        overwrite: false
                    }
                }).then(res => {
                    this.treeDialog.show = false
                    this.selectRow(this.selectedTreeNode)
                    // 更新源和目的的节点信息
                    this.updateGenericTreeNode(this.selectedTreeNode)
                    this.updateGenericTreeNode(this.treeDialog.selectedNode)
                    this.handlerPaginationChange()
                    this.$bkMessage({
                        theme: 'success',
                        message: this.treeDialog.type + this.$t('success')
                    })
                }).finally(() => {
                    this.treeDialog.loading = false
                })
            },
            handlerUpload () {
                this.uploadDialog = {
                    show: true,
                    title: `${this.$t('upload')} (${this.selectedTreeNode.fullPath || '/'})`,
                    fullPath: this.selectedTreeNode.fullPath
                }
            },
            cancelUpload () {
                this.uploadDialog.show = false
                this.getArtifactories()
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
                const road = this.selectedTreeNode.roadMap.split(',')
                road.shift()
                road.forEach(index => {
                    breadcrumb.push({
                        name: node[index].name,
                        value: node[index],
                        cilckHandler: item => {
                            this.selectedTreeNode = item.value
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
        padding-left: 20px;
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

/deep/ .bk-table-row.selected-row {
    background-color: $primaryLightColor;
}
.dialog-tree-container {
    max-height: 500px;
    overflow: auto;
}
</style>
