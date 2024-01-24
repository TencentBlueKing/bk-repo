<template>
    <div class="project-manage-container">
        <div class="ml20 mr20 mt10 flex-between-center">
            <bk-button icon="plus" theme="primary" @click="showProjectDialog()">{{ $t('create') }}</bk-button>
            <bk-input
                v-model.trim="projectInput"
                class="w250"
                :placeholder="$t('projectNameOrIdPlaceHolder')"
                clearable
                right-icon="bk-icon icon-search"
                style="margin-left: auto;margin-right: 10px"
                @change="handlerPaginationChange()">
            </bk-input>
            <div class="sort-tool flex-align-center">
                <bk-select
                    style="width:170px;"
                    v-model="property"
                    :clearable="true"
                    @change="queryProjects">
                    <bk-option id="name" :name="$t('projectNameSorting')"></bk-option>
                    <bk-option id="createdDate" :name="$t('creatTimeSorting')"></bk-option>
                </bk-select>
                <bk-popover :content="focusContent + ' ' + `${direction === 'ASC' ? $t('desc') : $t('asc')}`" placement="top">
                    <div class="ml10 sort-order flex-center" @click="changeDirection">
                        <Icon :name="`order-${direction.toLowerCase()}`" size="16"></Icon>
                    </div>
                </bk-popover>
            </div>
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
            <bk-table-column :label="$t('projectName')" show-overflow-tooltip>
                <template #default="{ row }">
                    <span class="hover-btn" @click="showProjectDetailHandler(row)">{{row.name}}</span>
                </template>
            </bk-table-column>
            <bk-table-column :label="$t('projectId')" prop="id" show-overflow-tooltip></bk-table-column>
            <bk-table-column :label="$t('projectDescription')" show-overflow-tooltip>
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
            <bk-table-column :label="$t('bkPermissionGeneration')" show-overflow-tooltip v-if="iamStatus">
                <template #default="{ row }">
                    <bk-button theme="primary" @click="createPermission(row.name)" v-if="row.rbacFlag === false">{{ $t('generate') }}</bk-button>
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
            :count="pagination.total"
            :limit-list="pagination.limitList"
            @change="current => handlerPaginationChange({ current })"
            @limit-change="limit => handlerPaginationChange({ limit })">
        </bk-pagination>
        <project-info-dialog ref="projectInfoDialog"></project-info-dialog>
    </div>
</template>
<script>
    import projectInfoDialog from './projectInfoDialog'
    import { mapState, mapActions } from 'vuex'
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
                    limitList: [10, 20, 40],
                    total: 0
                },
                focusContent: this.$t('toggle'),
                direction: this.$route.query.direction || 'DESC',
                filterProjectList: [],
                property: 'createdDate',
                iamStatus: false
            }
        },
        computed: {
            ...mapState(['projectList', 'userList'])
        },
        created () {
            this.queryProjects()
            this.getIamPermissionStatus().then(res => {
                this.iamStatus = res
            })
        },
        methods: {
            ...mapActions(['refreshIamPermission', 'getIamPermissionStatus', 'queryProjectList', 'getProjectList']),
            formatDate,
            handlerPaginationChange ({ current = 1, limit = this.pagination.limit } = {}) {
                this.pagination.current = current
                this.pagination.limit = limit
                this.queryProjects()
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
            },
            changeDirection () {
                this.direction = this.direction === 'ASC' ? 'DESC' : 'ASC'
                this.queryProjects()
            },
            queryProjects () {
                this.queryProjectList({
                    sortProperty: this.property,
                    direction: this.direction
                }).then(
                    res => {
                        res.forEach(project => {
                            project.id = project.name
                            project.name = project.displayName
                        })
                        this.setPageData(res)
                    }
                )
            },
            setPageData (res) {
                this.pagination.total = res.filter(project => {
                    return Boolean(~project.id.indexOf(this.projectInput) || ~project.name.indexOf(this.projectInput))
                }).length
                this.filterProjectList = res.filter(project => {
                    return Boolean(~project.id.indexOf(this.projectInput) || ~project.name.indexOf(this.projectInput))
                }).slice((this.pagination.current - 1) * this.pagination.limit, this.pagination.current * this.pagination.limit)
            },
            createPermission (projectId) {
                this.refreshIamPermission({ projectId: projectId }).then(res => {
                    if (res === true) {
                        this.$bkMessage({
                            theme: 'success',
                            message: this.$t('permissionsGeneratedSuccessTip')
                        })
                    } else {
                        this.$bkMessage({
                            theme: 'error',
                            message: this.$t('permissionsGeneratedFailTip')
                        })
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
