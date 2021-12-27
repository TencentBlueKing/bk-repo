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
            size="small">
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
            <bk-table-column :label="$t('operation')" width="70">
                <template #default="{ row }">
                    <operation-list
                        :list="[
                            { label: '详情', clickEvent: () => showProjectDetailHandler(row) }
                        ]"></operation-list>
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
    import OperationList from '@repository/components/OperationList'
    import projectInfoDialog from './projectInfoDialog'
    import { mapState } from 'vuex'
    import { formatDate } from '@repository/utils'
    export default {
        name: 'projectManage',
        components: { OperationList, projectInfoDialog },
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
