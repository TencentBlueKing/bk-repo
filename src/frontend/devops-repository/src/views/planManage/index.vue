<template>
    <div class="plan-container" v-bkloading="{ isLoading }">
        <div class="ml20 mr20 mt10 flex-between-center">
            <bk-button icon="plus" theme="primary" @click="$router.push({ name: 'createPlan' })"><span class="mr5">{{ $t('create') }}</span></bk-button>
            <div class="flex-align-center">
                <bk-input
                    class="w250"
                    v-model.trim="planInput"
                    clearable
                    placeholder="请输入计划名称, 按Enter键搜索"
                    right-icon="bk-icon icon-search"
                    @enter="handlerPaginationChange()"
                    @clear="handlerPaginationChange()">
                </bk-input>
                <bk-select
                    class="ml10 w250"
                    v-model="lastExecutionStatus"
                    placeholder="上次执行状态"
                    @change="handlerPaginationChange()">
                    <bk-option v-for="(label, key) in statusMap" :key="key" :id="key" :name="label"></bk-option>
                </bk-select>
                <bk-select
                    class="ml10 w250"
                    v-model="showEnabled"
                    placeholder="计划状态"
                    @change="handlerPaginationChange()">
                    <bk-option id="true" name="启用的计划"></bk-option>
                    <bk-option id="false" name="停用的计划"></bk-option>
                </bk-select>
            </div>
        </div>
        <bk-table
            class="mt10 plan-table"
            height="calc(100% - 104px)"
            :data="planList"
            :outer-border="false"
            :row-border="false"
            row-key="userId"
            size="small"
            @row-click="showPlanDetailHandler">
            <template #empty>
                <empty-data :is-loading="isLoading" :search="Boolean(planInput || lastExecutionStatus || showEnabled)">
                    <template v-if="!Boolean(planInput || lastExecutionStatus || showEnabled)">
                        <span class="ml10">暂无计划数据，</span>
                        <bk-button text @click="$router.push({ name: 'createPlan' })">即刻创建</bk-button>
                    </template>
                </empty-data>
            </template>
            <bk-table-column label="计划名称" prop="name"></bk-table-column>
            <bk-table-column label="同步类型" width="100">
                <template #default="{ row }">
                    {{ { 'REPOSITORY': '同步仓库', 'PACKAGE': '同步制品', 'PATH': '同步文件' }[row.replicaObjectType] }}
                </template>
            </bk-table-column>
            <bk-table-column label="目标节点">
                <template #default="{ row }">
                    {{ row.remoteClusters.map(v => v.name).join('、') }}
                </template>
            </bk-table-column>
            <bk-table-column label="同步策略" width="100">
                <template #default="{ row }">
                    {{ getExecutionStrategy(row) }}
                </template>
            </bk-table-column>
            <bk-table-column label="上次执行时间" prop="LAST_EXECUTION_TIME" width="150" :render-header="renderHeader">
                <template #default="{ row }">
                    {{formatDate(row.lastExecutionTime)}}
                </template>
            </bk-table-column>
            <bk-table-column label="上次执行状态" width="100">
                <template #default="{ row }">
                    <span class="repo-tag" :class="row.lastExecutionStatus">{{statusMap[row.lastExecutionStatus] || '未执行'}}</span>
                </template>
            </bk-table-column>
            <bk-table-column label="下次执行时间" prop="NEXT_EXECUTION_TIME" width="150" :render-header="renderHeader">
                <template #default="{ row }">
                    {{formatDate(row.nextExecutionTime)}}
                </template>
            </bk-table-column>
            <bk-table-column label="创建者" width="100">
                <template #default="{ row }">
                    {{userList[row.createdBy] ? userList[row.createdBy].name : row.createdBy}}
                </template>
            </bk-table-column>
            <bk-table-column :label="$t('createdDate')" prop="CREATED_TIME" width="150" :render-header="renderHeader">
                <template #default="{ row }">
                    {{formatDate(row.createdDate)}}
                </template>
            </bk-table-column>
            <bk-table-column label="计划状态" width="120">
                <template #default="{ row }">
                    <div class="flex-align-center" @click.stop="() => {}">
                        <bk-switcher class="mr10" v-model="row.enabled" @change="changeEnabledHandler(row)"></bk-switcher>
                        <div>{{row.enabled ? '启用' : '停用'}}</div>
                    </div>
                </template>
            </bk-table-column>
            <bk-table-column label="执行" width="70">
                <template #default="{ row }">
                    <i class="p5 devops-icon icon-play3 hover-btn"
                        :class="{ 'disabled': row.lastExecutionStatus === 'RUNNING' || row.replicaType === 'REAL_TIME' }"
                        @click.stop="executePlanHandler(row)">
                    </i>
                </template>
            </bk-table-column>
            <bk-table-column :label="$t('operation')" width="90">
                <template #default="{ row }">
                    <bk-popover placement="bottom-end" theme="light" ext-cls="operation-container">
                        <i class="p5 devops-icon icon-ellipsis hover-btn"></i>
                        <template #content><ul class="operation-list">
                            <li class="operation-item hover-btn"
                                :class="{ 'disabled': Boolean(row.lastExecutionStatus) || row.replicaType === 'REAL_TIME' }"
                                @click.stop="editPlanHandler(row)">编辑</li>
                            <li class="operation-item hover-btn" @click.stop="copyPlanHandler(row)">复制</li>
                            <li class="operation-item hover-btn" @click.stop="deletePlanHandler(row)">删除</li>
                            <li class="operation-item hover-btn" @click.stop="showPlanLogHandler(row)">日志</li>
                        </ul></template>
                    </bk-popover>
                </template>
            </bk-table-column>
        </bk-table>
        <bk-pagination
            class="p10"
            size="small"
            align="right"
            show-total-count
            @change="current => handlerPaginationChange({ current })"
            @limit-change="limit => handlerPaginationChange({ limit })"
            :current.sync="pagination.current"
            :limit="pagination.limit"
            :count="pagination.count"
            :limit-list="pagination.limitList">
        </bk-pagination>
        <plan-log v-model="planLog.show" :plan-data="planLog.planData"></plan-log>
        <plan-copy-dialog v-bind="planCopy" @cancel="planCopy.show = false" @refresh="handlerPaginationChange()"></plan-copy-dialog>
    </div>
</template>
<script>
    import { mapState, mapActions } from 'vuex'
    import { formatDate } from '@repository/utils'
    import planLog from './planLog'
    import planCopyDialog from './planCopyDialog'
    const statusMap = {
        RUNNING: '执行中',
        SUCCESS: '成功',
        FAILED: '失败'
    }
    export default {
        name: 'plan',
        components: { planLog, planCopyDialog },
        data () {
            return {
                statusMap,
                isLoading: false,
                showEnabled: undefined,
                lastExecutionStatus: '',
                planInput: '',
                sortType: 'CREATED_TIME',
                planList: [],
                pagination: {
                    count: 0,
                    current: 1,
                    limit: 20,
                    limitList: [10, 20, 40]
                },
                planLog: {
                    show: false,
                    planData: {}
                },
                planCopy: {
                    show: false,
                    name: '',
                    planKey: '',
                    description: ''
                }
            }
        },
        computed: {
            ...mapState(['userList'])
        },
        created () {
            this.handlerPaginationChange()
        },
        methods: {
            formatDate,
            ...mapActions([
                'getPlanList',
                'changeEnabled',
                'executePlan',
                'deletePlan'
            ]),
            getExecutionStrategy ({ replicaType, setting: { executionStrategy } }) {
                return replicaType === 'REAL_TIME'
                    ? '实时同步'
                    : {
                        IMMEDIATELY: '立即执行',
                        SPECIFIED_TIME: '指定时间',
                        CRON_EXPRESSION: '定时执行'
                    }[executionStrategy]
            },
            renderHeader (h, { column }) {
                return h('div', {
                    class: {
                        'flex-align-center hover-btn': true,
                        'selected-header': this.sortType === column.property
                    },
                    on: {
                        click: () => {
                            this.sortType = column.property
                            this.handlerPaginationChange()
                        }
                    }
                }, [
                    h('span', column.label),
                    h('i', {
                        class: 'ml5 devops-icon icon-down-shape'
                    })
                ])
            },
            handlerPaginationChange ({ current = 1, limit = this.pagination.limit } = {}) {
                this.pagination.current = current
                this.pagination.limit = limit
                this.getPlanListHandler()
            },
            getPlanListHandler () {
                this.isLoading = true
                return this.getPlanList({
                    projectId: this.$route.params.projectId,
                    name: this.planInput || undefined,
                    enabled: this.showEnabled || undefined,
                    lastExecutionStatus: this.lastExecutionStatus || undefined,
                    sortType: this.sortType,
                    current: this.pagination.current,
                    limit: this.pagination.limit
                }).then(({ records, totalRecords }) => {
                    this.planList = records
                    this.pagination.count = totalRecords
                }).finally(() => {
                    this.isLoading = false
                })
            },
            executePlanHandler ({ key, name, lastExecutionStatus, replicaType }) {
                if (lastExecutionStatus === 'RUNNING' || replicaType === 'REAL_TIME') return
                this.$confirm({
                    theme: 'warning',
                    message: `确认执行计划 ${name} ?`,
                    confirmFn: () => {
                        return this.executePlan({
                            key
                        }).then(() => {
                            this.getPlanListHandler()
                            this.$bkMessage({
                                theme: 'success',
                                message: '执行计划' + this.$t('success')
                            })
                        })
                    }
                })
            },
            editPlanHandler ({ name, key, lastExecutionStatus, replicaType }) {
                if (lastExecutionStatus || replicaType === 'REAL_TIME') return
                this.$router.push({
                    name: 'editPlan',
                    params: {
                        ...this.$route.params,
                        planId: key
                    },
                    query: {
                        planName: name
                    }
                })
            },
            copyPlanHandler ({ name, key, description }) {
                this.planCopy = {
                    show: true,
                    name,
                    planKey: key,
                    description
                }
            },
            deletePlanHandler ({ key, name }) {
                this.$confirm({
                    theme: 'danger',
                    message: `确认删除计划 ${name} ?`,
                    confirmFn: () => {
                        return this.deletePlan({
                            key
                        }).then(() => {
                            this.handlerPaginationChange()
                            this.$bkMessage({
                                theme: 'success',
                                message: '删除计划' + this.$t('success')
                            })
                        })
                    }
                })
            },
            changeEnabledHandler ({ key, enabled }) {
                this.changeEnabled({
                    key
                }).then(res => {
                    this.$bkMessage({
                        theme: 'success',
                        message: `${enabled ? '启用' : '停用'}计划成功`
                    })
                }).finally(() => {
                    this.getPlanListHandler()
                })
            },
            showPlanDetailHandler ({ key }) {
                this.$router.push({
                    name: 'planDetail',
                    params: {
                        ...this.$route.params,
                        planId: key
                    }
                })
            },
            showPlanLogHandler (row) {
                this.planLog.show = true
                this.planLog.planData = row
            }
        }
    }
</script>
<style lang="scss" scoped>
.plan-container {
    height: 100%;
    overflow: hidden;
    background-color: white;
    .plan-table {
        .SUCCESS {
            color: var(--successColor);
            background-color: #DCFFE2;
        }
        .FAILED {
            color: var(--dangerColor);
            background-color: #FFDDDD;
        }
        .RUNNING {
            color: var(--warningColor);
            background-color: #FFE8C3;
        }
        ::v-deep .selected-header {
            color: var(--primaryColor);
        }
        ::v-deep .devops-icon {
            &.disabled {
                color: var(--fontDisableColor);
                cursor: not-allowed;
            }
        }
    }
}
</style>
