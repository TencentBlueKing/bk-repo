<template>
    <div class="plan-container" v-bkloading="{ isLoading }">
        <div class="mb20 flex-align-center">
            <bk-select
                class="mr20 w250"
                v-model.trim="sortType"
                :clearable="false"
                @change="handlerPaginationChange()">
                <bk-option id="CREATED_TIME" name="按创建时间排序"></bk-option>
                <bk-option id="LAST_EXECUTION_TIME" name="按上次执行时间排序"></bk-option>
                <bk-option id="NEXT_EXECUTION_TIME" name="按下次执行时间排序"></bk-option>
            </bk-select>
            <bk-input
                class="w250"
                v-model.trim="planInput"
                clearable
                :placeholder="'请输入计划名称'"
                @enter="handlerPaginationChange()"
                @clear="handlerPaginationChange()">
            </bk-input>
            <i class="plan-search-btn devops-icon icon-search" @click="handlerPaginationChange()"></i>
            <div class="create-user flex-align-center">
                <bk-checkbox
                    class="mr20"
                    v-model="showEnabled"
                    :true-value="true"
                    :false-value="undefined"
                    @change="handlerPaginationChange()">
                    仅查看启用计划
                </bk-checkbox>
                <bk-button theme="primary" @click.stop="$router.push({ name: 'createPlan' })">{{ $t('create') + '计划' }}</bk-button>
            </div>
        </div>
        <bk-table
            class="plan-table"
            height="calc(100% - 120px)"
            :data="planList"
            :outer-border="false"
            :row-border="false"
            :row-style="{ cursor: 'pointer' }"
            row-key="userId"
            size="small"
            @row-click="showPlanDetailHandler">
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
                    {{ { 'IMMEDIATELY': '立即执行', 'SPECIFIED_TIME': '指定时间', 'CRON_EXPRESSION': '定时执行' }[row.setting.executionStrategy] }}
                </template>
            </bk-table-column>
            <bk-table-column label="上次执行时间" width="150">
                <template #default="{ row }">
                    {{formatDate(row.lastExecutionTime)}}
                </template>
            </bk-table-column>
            <bk-table-column label="运行状态" width="100">
                <template #default="{ row }">
                    <span class="repo-tag" :class="row.lastExecutionStatus">{{statusMap[row.lastExecutionStatus] || '未执行'}}</span>
                </template>
            </bk-table-column>
            <bk-table-column label="下次执行时间" width="150">
                <template #default="{ row }">
                    {{formatDate(row.nextExecutionTime)}}
                </template>
            </bk-table-column>
            <bk-table-column label="创建者" width="100">
                <template #default="{ row }">
                    {{userList[row.createdBy] ? userList[row.createdBy].name : row.createdBy}}
                </template>
            </bk-table-column>
            <bk-table-column :label="$t('createdDate')" width="150">
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
            <bk-table-column :label="$t('operation')" width="170">
                <template #default="{ row }">
                    <div class="flex-align-center">
                        <!-- <i title="执行" class="mr10 devops-icon icon-play3 hover-btn" :class="{ 'disabled': row.lastExecutionStatus === 'REPLICATING' }" @click.stop="executePlanHandler(row)"></i> -->
                        <!-- <i title="编辑" class="mr10 devops-icon icon-edit hover-btn" @click.stop="editPlanHandler(row)"></i> -->
                        <i title="复制" class="mr10 devops-icon icon-clipboard hover-btn" @click.stop="copyPlanHandler(row)"></i>
                        <i title="删除" class="mr10 devops-icon icon-delete hover-btn" @click.stop="deletePlanHandler(row)"></i>
                        <i title="详情" class="mr10 devops-icon icon-calendar hover-btn" @click.stop="showPlanLogHandler(row)"></i>
                    </div>
                </template>
            </bk-table-column>
        </bk-table>
        <bk-pagination
            class="mt10"
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
        <plan-log v-model="planLog.show" :plan-key="planLog.key" :title="planLog.name"></plan-log>
        <plan-copy-dialog v-bind="planCopy" @cancel="planCopy.show = false"></plan-copy-dialog>
    </div>
</template>
<script>
    import { mapState, mapActions } from 'vuex'
    import { formatDate } from '@/utils'
    import planLog from './planLog'
    import planCopyDialog from './planCopyDialog'
    const statusMap = {
        'WAITING': '等待',
        'PAUSED': '暂停',
        'REPLICATING': '执行中',
        'SUCCESS': '成功',
        'INTERRUPTED': '中断',
        'FAILED': '失败'
    }
    export default {
        name: 'plan',
        components: { planLog, planCopyDialog },
        data () {
            return {
                statusMap,
                isLoading: false,
                showEnabled: undefined,
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
                    name: '',
                    key: ''
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
                'checkUpdatePlan',
                'deletePlan'
            ]),
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
            executePlanHandler ({ key, name, lastExecutionStatus }) {
                if (lastExecutionStatus === 'REPLICATING') return
                this.$bkInfo({
                    type: 'error',
                    title: `确认执行计划 ${name} ?`,
                    showFooter: true,
                    confirmFn: () => {
                        this.executePlan({
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
            editPlanHandler ({ key }) {
                this.checkUpdatePlan({ key }).then(res => {
                    if (res) {
                        this.$router.push({
                            name: 'editPlan',
                            params: {
                                ...this.$route.params,
                                planId: key
                            }
                        })
                    } else {
                        this.$bkMessage({
                            theme: 'error',
                            message: '当前计划已执行或正在执行，无法编辑'
                        })
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
                this.$bkInfo({
                    type: 'error',
                    title: `确认删除计划 ${name} ?`,
                    showFooter: true,
                    confirmFn: () => {
                        this.deletePlan({
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
            showPlanLogHandler ({ key, name }) {
                this.planLog.show = true
                this.planLog.name = name
                this.planLog.key = key
            }
        }
    }
</script>
<style lang="scss" scoped>
@import '@/scss/conf';
.plan-container {
    height: calc(100% + 40px);
    margin-bottom: -40px;
    .plan-search-btn {
        position: relative;
        z-index: 1;
        padding: 9px;
        color: white;
        margin-left: -2px;
        border-radius: 0 2px 2px 0;
        background-color: #3a84ff;
        cursor: pointer;
        &:hover {
            background-color: #699df4;
        }
    }
    .create-user {
        flex: 1;
        justify-content: flex-end;
    }
    .plan-table {
        .SUCCESS {
            color: #2DCB56;
            background-color: #DCFFE2;
        }
        .FAILED, .INTERRUPTED {
            color: #EA3636;
            background-color: #FFDDDD;
        }
        .WAITING, .PAUSED, .REPLICATING {
            color: #FF9C01;
            background-color: #FFE8C3;
        }
        .devops-icon {
            font-size: 16px;
            &.disabled {
                color: $disabledColor;
                cursor: not-allowed;
            }
        }
    }
    /deep/ .bk-sideslider-content {
        height: calc(100% - 60px);
    }
}
</style>
