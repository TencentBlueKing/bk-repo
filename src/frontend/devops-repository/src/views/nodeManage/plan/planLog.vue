<template>
    <bk-sideslider :is-show.sync="showSideslider" :quick-close="true" :width="850" :title="`${planData.name} 执行日志`">
        <template #content>
            <div class="plan-detail-container" v-bkloading="{ isLoading }">
                <bk-select
                    class="mr20 w250"
                    v-model="status"
                    placeholder="运行状态"
                    @change="handlerPaginationChange()">
                    <bk-option v-for="(label, key) in statusMap" :key="key" :id="key" :name="label"></bk-option>
                </bk-select>
                <bk-table
                    class="mt10"
                    height="calc(100% - 84px)"
                    :data="logList"
                    :outer-border="false"
                    :row-border="false"
                    :row-style="{ cursor: 'pointer' }"
                    size="small"
                    @row-click="showLogDetailHandler">
                    <bk-table-column type="index" label="编号" width="60"></bk-table-column>
                    <bk-table-column label="运行状态" width="100">
                        <template #default="{ row }">
                            <span class="repo-tag" :class="row.status">{{statusMap[row.status] || '未执行'}}</span>
                        </template>
                    </bk-table-column>
                    <bk-table-column label="开始执行时间" width="150">
                        <template #default="{ row }">
                            {{formatDate(row.startTime)}}
                        </template>
                    </bk-table-column>
                    <bk-table-column v-if="planData.replicaType !== 'REAL_TIME'" label="结束执行时间" width="150">
                        <template #default="{ row }">
                            {{formatDate(row.endTime)}}
                        </template>
                    </bk-table-column>
                    <bk-table-column label="备注">
                        <template #default="{ row }">
                            <span :title="row.errorReason">{{row.errorReason || '--'}}</span>
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
            </div>
        </template>
    </bk-sideslider>
</template>
<script>
    import { formatDate } from '@/utils'
    import { mapActions } from 'vuex'
    const statusMap = {
        'RUNNING': '执行中',
        'SUCCESS': '成功',
        'FAILED': '失败'
    }
    export default {
        name: 'planLog',
        model: {
            prop: 'show',
            event: 'show'
        },
        props: {
            show: Boolean,
            planData: {
                type: Object,
                default: () => {}
            }
        },
        data () {
            return {
                isLoading: false,
                statusMap,
                status: '',
                logList: [],
                pagination: {
                    count: 0,
                    current: 1,
                    limit: 20,
                    limitList: [10, 20, 40]
                }
            }
        },
        computed: {
            showSideslider: {
                get () {
                    return this.show
                },
                set (show) {
                    this.$emit('show', show)
                }
            }
        },
        watch: {
            show (val) {
                val && this.handlerPaginationChange()
            }
        },
        methods: {
            formatDate,
            ...mapActions(['getPlanLogList']),
            handlerPaginationChange ({ current = 1, limit = this.pagination.limit } = {}) {
                this.pagination.current = current
                this.pagination.limit = limit
                this.getPlanLogListHandler()
            },
            getPlanLogListHandler () {
                this.isLoading = true
                this.getPlanLogList({
                    key: this.planData.key,
                    status: this.status || undefined,
                    current: this.pagination.current,
                    limit: this.pagination.limit
                }).then(({ records, totalRecords }) => {
                    this.logList = records
                    this.pagination.count = totalRecords
                }).finally(() => {
                    this.isLoading = false
                })
            },
            showLogDetailHandler ({ id }) {
                this.$router.push({
                    name: 'logDetail',
                    params: {
                        ...this.$route.params,
                        logId: id
                    },
                    query: {
                        plan: this.planData.name
                    }
                })
            }
        }
    }
</script>
<style lang="scss" scoped>
@import '@/scss/conf';
.plan-detail-container {
    height: 100%;
    padding: 20px;
    .SUCCESS {
        color: #2DCB56;
        background-color: #DCFFE2;
    }
    .FAILED {
        color: #EA3636;
        background-color: #FFDDDD;
    }
    .RUNNING {
        color: #FF9C01;
        background-color: #FFE8C3;
    }
}
</style>
