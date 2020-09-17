<template>
    <div class="repo-generic-container" @click="() => selectRow(selectedTreeNode)">
        <div v-show="!query" class="repo-generic-side" v-bkloading="{ isLoading: treeLoading }">
            <div class="important-search">
                <bk-input
                    v-model="importantSearch"
                    placeholder=""
                    :clearable="true"
                    :right-icon="'bk-icon icon-search'">
                </bk-input>
            </div>
            <repo-tree
                v-if="genericTree.length"
                class="repo-generic-tree"
                ref="repoTree"
                :list="genericTree"
                :important-search="importantSearch"
                :open-list="sideTreeOpenList"
                :selected-node="selectedTreeNode"
                @icon-click="iconClickHandler"
                @item-click="itemClickHandler">
            </repo-tree>
            <div v-else class="mt50 flex-center">
                <bk-button v-if="repoName === 'custom'" @click.stop="addFolder()" theme="primary">
                    <i class="mr5 devops-icon icon-folder-plus"></i>
                    {{ $t('create') + $t('folder') }}
                </bk-button>
                <div v-else>{{ $t('noData') }}</div>
            </div>
        </div>
        <div class="repo-generic-main" v-bkloading="{ isLoading }">
            <div class="repo-generic-table">
                <bk-table
                    :data="artifactoryList"
                    height="100%"
                    :outer-border="false"
                    :row-border="false"
                    size="small"
                    :pagination="pagination"
                    @row-click="selectRow"
                    @row-dblclick="openFolder"
                    @page-change="current => paginationChange({ current })"
                    @page-limit-change="limit => paginationChange({ limit })"
                >
                    <bk-table-column :label="$t('fileName')">
                        <template slot-scope="props">
                            <div class="flex-align-center">
                                <icon size="24" :name="props.row.folder ? 'folder' : getIconName(props.row.name)" />
                                <span class="ml10">{{props.row.name}}</span>
                            </div>
                        </template>
                    </bk-table-column>
                    <bk-table-column :label="$t('artiStatus')">
                        <template v-if="props.row.stageTag" slot-scope="props">
                            <span class="mr5 repo-generic-tag" v-for="tag in props.row.stageTag.split(',')"
                                :key="props.row.fullPath + tag">{{ tag }}</span>
                        </template>
                    </bk-table-column>
                    <bk-table-column :label="$t('lastModifiedDate')" prop="lastModifiedDate">
                        <template slot-scope="props">{{ new Date(props.row.lastModifiedDate).toLocaleString() }}</template>
                    </bk-table-column>
                    <bk-table-column :label="$t('lastModifiedBy')" prop="lastModifiedBy"></bk-table-column>
                    <bk-table-column :label="$t('size')">
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
            </div>
            <aside v-show="!query || selectedRow.fullPath" class="repo-generic-actions">
                <bk-button class="detail-btn" theme="primary" @click.stop="showDetail()">{{ $t('showDetail') }}</bk-button>
                <div class="actions-btn flex-column">
                    <template v-if="selectedRow.fullPath !== selectedTreeNode.fullPath || query">
                        <template v-if="repoName === 'custom'">
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
                            <bk-button :disabled="(selectedRow.stageTag || '').includes('@release')" @click.stop="handlerTag()" text theme="primary">
                                <i class="mr5 devops-icon icon-arrows-up"></i>
                                {{ $t('upgrade') }}
                            </bk-button>
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
                        <template v-if="repoName === 'custom'">
                            <bk-button @click.stop="addFolder()" text theme="primary">
                                <i class="mr5 devops-icon icon-folder-plus"></i>
                                {{$t('create') + $t('repository')}}
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

        <bk-sideslider
            class="artifactory-side-slider"
            :is-show.sync="detailSlider.show"
            :title="detailSlider.data.name"
            @click.native.stop="() => {}"
            :quick-close="true"
            :width="800">
            <bk-tab class="mt10 ml20 mr20" slot="content" type="unborder-card">
                <bk-tab-panel name="detailInfo" :label="$t('baseInfo')">
                    <div class="detail-info info-area" v-bkloading="{ isLoading: detailSlider.loading }">
                        <div class="flex-center" v-for="key in Object.keys(detailInfoMap)" :key="key">
                            <span>{{ key }}:</span>
                            <span>{{ detailSlider.data[detailInfoMap[key]] }}</span>
                        </div>
                    </div>
                    <div class="detail-info checksums-area" v-if="!selectedRow.folder" v-bkloading="{ isLoading: detailSlider.loading }">
                        <div class="flex-center" v-for="key of ['sha256', 'md5']" :key="key">
                            <span>{{ key.toUpperCase() }}:</span>
                            <span>{{ detailSlider.data[key] }}</span>
                        </div>
                    </div>
                </bk-tab-panel>
                <bk-tab-panel v-if="!selectedRow.folder" name="metaDate" :label="$t('metaData')">
                    <bk-table
                        :data="Object.entries(detailSlider.data.metadata || {})"
                        stripe
                        :outer-border="false"
                        :row-border="false"
                        size="small"
                    >
                        <bk-table-column :label="$t('key')" prop="0"></bk-table-column>
                        <bk-table-column :label="$t('value')" prop="1"></bk-table-column>
                    </bk-table>
                </bk-tab-panel>
            </bk-tab>
        </bk-sideslider>
        
        <bk-dialog
            v-model="formDialog.show"
            :title="formDialog.title"
            :close-icon="false"
            :quick-close="false"
            width="600"
            header-position="left">
            <bk-form :label-width="120" :model="formDialog" :rules="rules" ref="formDialog">
                <template v-if="formDialog.type === 'add'">
                    <bk-form-item :label="$t('folder')" :required="true">
                        <span>{{ selectedRow.fullPath + '/' + formDialog.path }}</span>
                    </bk-form-item>
                    <bk-form-item property="path" error-display-type="normal">
                        <bk-input v-model="formDialog.path" :placeholder="$t('folderNamePlacehodler')"></bk-input>
                    </bk-form-item>
                </template>
                <template v-if="formDialog.type === 'rename'">
                    <bk-form-item :label="$t('name')" :required="true" property="name" error-display-type="normal">
                        <bk-input v-model="formDialog.name" :placeholder="$t('repoNamePlacehodler')"></bk-input>
                    </bk-form-item>
                </template>
                <template v-if="formDialog.type === 'share'">
                    <bk-form-item :label="$t('share') + $t('object')" :required="true" property="user" error-display-type="normal">
                        <bk-tag-input
                            v-model="formDialog.user"
                            allow-create
                            has-delete-icon>
                        </bk-tag-input>
                    </bk-form-item>
                    <bk-form-item :label="`${$t('validity')}(${$t('day')})`" :required="true" property="time" error-display-type="normal">
                        <bk-input v-model="formDialog.time" :placeholder="$t('repoNamePlacehodler')"></bk-input>
                    </bk-form-item>
                </template>
                <template v-if="formDialog.type === 'tag'">
                    <bk-form-item :label="$t('upgradeTo')" :required="true" property="tag" error-display-type="normal">
                        <bk-radio-group v-model="formDialog.tag">
                            <bk-radio :disabled="!!selectedRow.stageTag" value="@prerelease">@prerelease</bk-radio>
                            <bk-radio class="ml20" value="@release">@release</bk-radio>
                        </bk-radio-group>
                    </bk-form-item>
                </template>
            </bk-form>
            <div slot="footer">
                <bk-button ext-cls="mr5" :loading="formDialog.loading" theme="primary" @click.stop.prevent="submitFormDialog">{{$t('submit')}}</bk-button>
                <bk-button ext-cls="mr5" theme="default" @click.stop="cancelFormDialog">{{$t('cancel')}}</bk-button>
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

        <bk-dialog
            v-model="uploadDialog.show"
            :title="uploadDialog.title"
            :quick-close="false"
            :mask-close="false"
            :close-icon="false"
            width="600"
            header-position="left">
            <artifactory-upload
                ref="artifactoryUpload"
                @upload-failed="upoadFailed">
            </artifactory-upload>
            <div slot="footer">
                <bk-button :loading="uploadDialog.loading" theme="primary" @click="submitUpload">{{ $t('upload') }}</bk-button>
                <bk-button @click="uploadDialog.show = false">{{ $t('cancel') }}</bk-button>
            </div>
        </bk-dialog>
    </div>
</template>
<script>
    import RepoTree from './repoTree'
    import ArtifactoryUpload from '@/components/ArtifactoryUpload'
    import { convertFileSize } from '@/utils'
    import { fileType } from '@/store/publicEnum'
    import { mapState, mapMutations, mapActions } from 'vuex'
    export default {
        name: 'repoGeneric',
        components: { RepoTree, ArtifactoryUpload },
        data () {
            return {
                convertFileSize,
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
                    count: 1,
                    current: 1,
                    limit: 10,
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
                    time: 1,
                    tag: ''
                },
                // formDialog Rules
                rules: {
                    path: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + this.$t('repoName'),
                            trigger: 'blur'
                        },
                        {
                            regex: /^((\w|-|\.){1,50}\/)*((\w|-|\.){1,50})$/,
                            message: this.$t('pleaseInput') + this.$t('legit') + this.$t('repoType'),
                            trigger: 'blur'
                        }
                    ],
                    name: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + this.$t('repoName'),
                            trigger: 'blur'
                        },
                        {
                            regex: /^(\w|-|\.){1,50}$/,
                            message: this.$t('pleaseInput') + this.$t('legit') + this.$t('repoType'),
                            trigger: 'blur'
                        }
                    ],
                    user: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + this.$t('repoName'),
                            trigger: 'blur'
                        }
                    ],
                    time: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + this.$t('repoName'),
                            trigger: 'blur'
                        },
                        {
                            regex: /^[0-9]*$/,
                            message: this.$t('pleaseInput') + this.$t('legit') + this.$t('repoType'),
                            trigger: 'blur'
                        }
                    ],
                    tag: [
                        {
                            required: true,
                            message: this.$t('pleaseSelect') + this.$t('repoName'),
                            trigger: 'blur'
                        }
                    ]
                },
                // 查看详情
                detailSlider: {
                    show: false,
                    loading: false,
                    data: {}
                },
                // detailInfoMap
                detailInfoMap: {
                    'Name': 'name',
                    'Path': 'fullPath',
                    'Size': 'size',
                    'Created User': 'createdBy',
                    'Created Date': 'createdDate',
                    'Last Modified User': 'lastModifiedBy',
                    'Last Modified Date': 'lastModifiedDate'
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
                    loading: false,
                    title: ''
                },
                query: null
            }
        },
        computed: {
            ...mapState(['genericTree']),
            projectId () {
                return this.$route.params.projectId
            },
            repoName () {
                return this.$route.query.name
            }
        },
        watch: {
            '$route.query.name' () {
                this.initPage()
            },
            'selectedTreeNode.fullPath' () {
                // 重置选中行
                this.selectedRow.element && this.selectedRow.element.classList.remove('selected-row')
                this.selectedRow = this.selectedTreeNode
                this.query && this.getArtifactories()
            }
        },
        created () {
            this.initPage()
        },
        methods: {
            ...mapMutations(['INIT_GENERIC_TREE']),
            ...mapActions([
                'getNodeDetail',
                'getFolderList',
                'getArtifactoryList',
                'createFolder',
                'getArtifactoryListByQuery',
                'uploadArtifactory',
                'downloadArtifactory',
                'deleteArtifactory',
                'renameNode',
                'moveNode',
                'copyNode',
                'changeStageTag',
                'shareArtifactory',
                'getFolderSize'
            ]),
            async initPage () {
                this.importantSearch = ''
                this.INIT_GENERIC_TREE()
                this.sideTreeOpenList = []
                await this.itemClickHandler(this.genericTree[0])
                this.getArtifactories()
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
                    limit: this.pagination.limit
                }).then(({ records, totalRecords }) => {
                    this.pagination.count = totalRecords
                    this.artifactoryList = records
                }).finally(() => {
                    this.isLoading = false
                })
            },
            // 搜索文件函数
            searchHandler (query) {
                this.query = query
                this.selectedTreeNode = this.genericTree[0]
                this.isLoading = true
                this.getArtifactoryListByQuery({
                    projectId: this.projectId,
                    repoName: this.repoName,
                    name: (query.name || []).join(''),
                    stageTag: (query.stageTag || []).join(','),
                    current: this.pagination.current,
                    limit: this.pagination.limit
                }).then(({ records, totalRecords }) => {
                    this.artifactoryList = records
                    this.pagination.count = totalRecords
                }).finally(() => {
                    this.isLoading = false
                })
            },
            resetQueryAndBack () {
                this.query = null
                this.selectedRow.element && this.selectedRow.element.classList.remove('selected-row')
                this.selectedRow = this.selectedTreeNode
                this.initPage()
            },
            paginationChange ({ current = 1, limit = this.pagination.limit }) {
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
                    roadMap: item.roadMap
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
                node.roadMap.split(',').forEach((v, i) => {
                    const roadMap = node.roadMap.slice(0, 2 * i + 1)
                    !this.sideTreeOpenList.includes(roadMap) && this.sideTreeOpenList.push(roadMap)
                })
            },
            // 控制选中的行
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
                    data: {}
                }
                const data = await this.getNodeDetail({
                    projectId: this.projectId,
                    repoName: this.repoName,
                    fullPath: this.selectedRow.fullPath
                })
                this.detailSlider.data = {
                    ...data,
                    createdDate: new Date(data.createdDate).toLocaleString(),
                    lastModifiedDate: new Date(data.lastModifiedDate).toLocaleString()
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
                    time: 1
                }
            },
            handlerTag () {
                this.formDialog = {
                    ...this.formDialog,
                    show: true,
                    loading: false,
                    type: 'tag',
                    title: `${this.$t('upgrade')} (${this.selectedRow.name})`,
                    tag: ''
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
                this.$refs.formDialog.clearError()
                this.formDialog.show = false
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
                    fullPath: this.selectedRow.fullPath,
                    body: {
                        authorizedUserList: this.formDialog.user,
                        expireSeconds: this.formDialog.time * 86400
                    }
                })
            },
            submitTag () {
                return this.changeStageTag({
                    projectId: this.projectId,
                    repoName: this.repoName,
                    fullPath: this.selectedRow.fullPath,
                    tag: this.formDialog.tag
                })
            },
            async deleteRes () {
                this.$bkInfo({
                    title: `${this.$t('confirm') + this.$t('delete')}${this.selectedRow.folder ? this.$t('folder') : this.$t('file')} ${this.selectedRow.name} ？`,
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
                    this.updateGenericTreeNode(this.selectedTreeNode)
                    this.updateGenericTreeNode(this.treeDialog.selectedNode)
                    this.getArtifactories()
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
                    loading: false,
                    title: `${this.$t('upload')} (${this.selectedTreeNode.fullPath || '/'})`
                }
                this.$refs.artifactoryUpload.reset()
            },
            async submitUpload () {
                const { file, progressHandler } = await this.$refs.artifactoryUpload.getFiles()
                this.uploadDialog.loading = true
                this.uploadArtifactory(
                    {
                        projectId: this.projectId,
                        repoName: this.repoName,
                        fullPath: `${this.selectedTreeNode.fullPath}/${file.name}`,
                        body: file.blob,
                        progressHandler,
                        headers: {
                            'Content-Type': 'application/octet-stream',
                            'X-BKREPO-OVERWRITE': file.overwrite,
                            'X-BKREPO-EXPIRES': file.expires
                        }
                    }
                ).then(() => {
                    this.uploadDialog.show = false
                    this.$bkMessage({
                        theme: 'success',
                        message: `${this.$t('upload')} ${file.name} ${this.$t('success')}`
                    })
                    this.getArtifactories()
                }).finally(() => {
                    this.uploadDialog.loading = false
                })
            },
            upoadFailed (file) {
                this.uploadDialog.loading = false
                this.$bkMessage({
                    theme: 'error',
                    message: `${this.$t('upload')} ${file.name} ${this.$t('fail')}`
                })
            },
            handlerDownload () {
                this.downloadArtifactory({
                    projectId: this.projectId,
                    repoName: this.repoName,
                    fullPath: this.selectedRow.fullPath
                }).then(file => {
                    const eleLink = document.createElement('a')
                    const url = window.URL.createObjectURL(file)
                    eleLink.href = url
                    eleLink.download = this.selectedRow.name
                    document.body.appendChild(eleLink)
                    eleLink.click()
                    window.URL.revokeObjectURL(url)
                    document.body.removeChild(eleLink)
                })
            },
            getIconName (name) {
                let type = name.split('.').pop()
                type = {
                    'gif': 'png',
                    'svg': 'png',
                    'jpg': 'png',
                    'psd': 'png',
                    'jpge': 'png',
                    'json': 'txt'
                }[type] || type
                return fileType.includes(type) ? type : 'file'
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
            .repo-generic-tag {
                display: inline-block;
                padding: 5px;
                border-radius: 5px;
                font-size: 14px;
                background-color: $primaryLightColor;
            }
        }
    }
    .repo-generic-actions {
        width: 300px;
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
    .detail-info {
        padding: 15px;
        margin-top: 40px;
        border: 1px solid $borderWeightColor;
        span {
            padding: 10px;
            flex: 3;
            &:first-child {
                flex: 1;
                display: flex;
                justify-content: flex-end;
            }
        }
        &.info-area:before {
            content: 'Info';
            position: absolute;
            padding: 0 10px;
            font-weight: 700;
            margin-top: -25px;
            background-color: white
        }
        &.checksums-area:before {
            content: 'Checksums';
            position: absolute;
            padding: 0 10px;
            font-weight: 700;
            margin-top: -25px;
            background-color: white
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
