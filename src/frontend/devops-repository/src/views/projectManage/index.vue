<template>
    <div class="project-manage-container">
        <div class="ml20 mr20 mt10 flex-between-center">
            <bk-button icon="plus" theme="primary" @click="showProjectDialog()">{{ $t('create') }}</bk-button>
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
            height="calc(100% - 100px)"
            :outer-border="false"
            :row-border="false"
            size="small">
            <template #empty>
                <empty-data :search="Boolean(projectInput)"></empty-data>
            </template>
            <bk-table-column label="项目名称" show-overflow-tooltip>
                <template #default="{ row }">
                    <span class="hover-btn" @click="showProjectDetailHandler(row)">{{row.name}}</span>
                </template>
            </bk-table-column>
            <bk-table-column label="项目标识" prop="id" show-overflow-tooltip></bk-table-column>
            <bk-table-column label="项目描述" show-overflow-tooltip>
                <template #default="{ row }">{{row.description || '/'}}</template>
            </bk-table-column>
            <bk-table-column :label="$t('createdDate')">
                <template #default="{ row }">{{ formatDate(row.createdDate) }}</template>
            </bk-table-column>
            <bk-table-column :label="$t('createdBy')">
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
        <project-info-dialog ref="projectInfoDialog"></project-info-dialog>
    </div>
</template>
<script>
    import projectInfoDialog from './projectInfoDialog'
    import { mapState } from 'vuex'
    import { formatDate } from '@repository/utils'
    export default {
        name: 'projectManage',
        components: {
            projectInfoDialog
        },
        data () {
            return {
                projectInput: '',
                pagination: {
                    current: 1,
                    limit: 20,
                    limitList: [10, 20, 40]
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
            handlerPaginationChange ({ current = 1, limit = this.pagination.limit } = {}) {
                this.pagination.current = current
                this.pagination.limit = limit
            },
            showProjectDialog (project = {}) {
                const { id = '', name = '', description = '' } = project
                this.$refs.projectInfoDialog.setData({
                    show: true,
                    loading: false,
                    add: !id,
                    id,
                    name,
                    description
                })
            },
            showProjectDetailHandler ({ id }) {
                this.$router.push({
                    name: 'projectConfig',
                    query: {
                        projectId: id
                    }
                })
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
