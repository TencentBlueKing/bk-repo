<template>
    <div class="project-manage-container">
        <div class="ml20 mr20 mt10 flex-between-center">
            <bk-button icon="plus" theme="primary" @click="showProjectDialog()"><span class="mr5">{{ $t('create') }}</span></bk-button>
            <bk-input
                v-model.trim="projectInput"
                class="w250"
                placeholder="请输入项目名称/项目标识"
                clearable
                right-icon="bk-icon icon-search"
                @change="handlerPaginationChange()">
            </bk-input>
        </div>
        <bk-table
            class="mt10"
            :data="filterProjectList"
            height="calc(100% - 104px)"
            :outer-border="false"
            :row-border="false"
            size="small"
            @row-click="showProjectDetailHandler">
            <template #empty>
                <empty-data :search="Boolean(projectInput)">
                    <template v-if="!Boolean(projectInput)">
                        <span class="ml10">暂无项目数据，</span>
                        <bk-button text @click="showProjectDialog()">即刻创建</bk-button>
                    </template>
                </empty-data>
            </template>
            <bk-table-column label="项目标识" prop="id" width="150"></bk-table-column>
            <bk-table-column label="项目名称" prop="name" show-overflow-tooltip></bk-table-column>
            <bk-table-column label="项目描述" prop="description" show-overflow-tooltip></bk-table-column>
            <bk-table-column :label="$t('createdDate')" width="200">
                <template #default="{ row }">
                    {{ formatDate(row.createdDate) }}
                </template>
            </bk-table-column>
            <bk-table-column :label="$t('createdBy')" width="150">
                <template #default="{ row }">
                    {{ userList[row.createdBy] ? userList[row.createdBy].name : row.createdBy }}
                </template>
            </bk-table-column>
        </bk-table>
        <bk-pagination
            class="p10"
            size="small"
            align="right"
            show-total-count
            :current.sync="pagination.current"
            :limit="pagination.limit"
            :count="projectList.length"
            :limit-list="pagination.limitList"
            @change="current => handlerPaginationChange({ current })"
            @limit-change="limit => handlerPaginationChange({ limit })">
        </bk-pagination>
        <canway-dialog
            v-model="editProjectDialog.show"
            :title="editProjectDialog.add ? '新建项目' : '编辑项目'"
            width="500"
            height-num="354"
            @cancel="editProjectDialog.show = false">
            <bk-form class="ml10 mr10" :label-width="75" :model="editProjectDialog" :rules="rules" ref="editProjectDialog">
                <bk-form-item label="项目标识" :required="true" property="id" error-display-type="normal">
                    <bk-input v-model.trim="editProjectDialog.id"
                        :disabled="!editProjectDialog.add" maxlength="32"
                        show-word-limit
                        placeholder="请输入2-32字符的小写字母+数字组合，以字母开头">
                    </bk-input>
                </bk-form-item>
                <bk-form-item label="项目名称" :required="true" property="name" error-display-type="normal">
                    <bk-input v-model.trim="editProjectDialog.name" maxlength="32" show-word-limit></bk-input>
                </bk-form-item>
                <bk-form-item label="项目描述" property="description">
                    <bk-input type="textarea" v-model.trim="editProjectDialog.description" maxlength="200" show-word-limit></bk-input>
                </bk-form-item>
            </bk-form>
            <template #footer>
                <bk-button theme="default" @click.stop="editProjectDialog.show = false">{{$t('cancel')}}</bk-button>
                <bk-button class="ml10" :loading="editProjectDialog.loading" theme="primary" @click.stop.prevent="submitProject()">{{$t('confirm')}}</bk-button>
            </template>
        </canway-dialog>
        <bk-sideslider
            :is-show.sync="showProjectDetail"
            quick-close
            :width="650"
            title="项目配置">
            <template #content>
                <project-config
                    :project="selectedProject"
                    @edit-basic="showProjectDialog">
                </project-config>
            </template>
        </bk-sideslider>
    </div>
</template>
<script>
    import projectConfig from './projectConfig'
    import { mapState, mapActions } from 'vuex'
    import { formatDate } from '@repository/utils'
    export default {
        name: 'projectManage',
        components: { projectConfig },
        data () {
            return {
                projectInput: '',
                pagination: {
                    current: 1,
                    limit: 20,
                    limitList: [10, 20, 40]
                },
                editProjectDialog: {
                    show: false,
                    loading: false,
                    add: true,
                    id: '',
                    name: '',
                    description: ''
                },
                showProjectDetail: false,
                selectedProject: null,
                rules: {
                    id: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + '项目标识',
                            trigger: 'blur'
                        },
                        {
                            regex: /^[a-z][a-z0-9]{1,31}$/,
                            message: '请输入2-32字符的小写字母+数字组合，以字母开头',
                            trigger: 'blur'
                        },
                        {
                            validator: id => this.asynCheck({ id }),
                            message: '项目标识已存在',
                            trigger: 'blur'
                        }
                    ],
                    name: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + '项目名称',
                            trigger: 'blur'
                        },
                        {
                            validator: name => this.asynCheck({ name }),
                            message: '项目名称已存在',
                            trigger: 'blur'
                        }
                    ]
                }
            }
        },
        computed: {
            ...mapState(['projectList', 'userList']),
            filterProjectList () {
                return this.projectList.filter(project => {
                    return Boolean(~project.id.indexOf(this.projectInput) || ~project.name.indexOf(this.projectInput))
                }).slice((this.pagination.current - 1) * this.pagination.limit, this.pagination.current * this.pagination.limit)
            }
        },
        methods: {
            formatDate,
            ...mapActions([
                'getProjectList',
                'createProject',
                'editProject',
                'checkProject'
            ]),
            asynCheck ({ id, name }) {
                if (!this.editProjectDialog.add) {
                    const project = this.projectList.find(v => v.id === this.editProjectDialog.id)
                    if (id || project.name === name) return false
                }
                return this.checkProject({ id, name }).then(res => !res)
            },
            handlerPaginationChange ({ current = 1, limit = this.pagination.limit } = {}) {
                this.pagination.current = current
                this.pagination.limit = limit
            },
            showProjectDialog (project = {}) {
                this.$refs.editProjectDialog.clearError()
                const { id = '', name = '', description = '' } = project
                this.editProjectDialog = {
                    show: true,
                    loading: false,
                    add: !id,
                    id,
                    name,
                    description
                }
            },
            async submitProject () {
                await this.$refs.editProjectDialog.validate()
                this.editProjectDialog.loading = true
                const { id, name, description } = this.editProjectDialog
                const fn = this.editProjectDialog.add ? this.createProject : this.editProject
                fn({
                    id,
                    name,
                    description
                }).then(() => {
                    this.$bkMessage({
                        theme: 'success',
                        message: (this.editProjectDialog.add ? '新建项目' : '编辑项目') + this.$t('success')
                    })
                    this.editProjectDialog.show = false
                    this.getProjectList()
                }).finally(() => {
                    this.editProjectDialog.loading = false
                })
            },
            showProjectDetailHandler (row) {
                this.selectedProject = row
                this.showProjectDetail = true
            }
        }
    }
</script>
<style lang="scss" scoped>
.project-manage-container {
    height: 100%;
    background-color: white;
}
</style>
