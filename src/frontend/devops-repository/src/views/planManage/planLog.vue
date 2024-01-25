<template>
    <div>
        <bk-sideslider :is-show.sync="showSideslider" :quick-close="true" :width="600" :title="$t('planLogTitle', { name: planData.name })">
            <template #content>
                <div class="plan-detail-container" v-bkloading="{ isLoading }">
                    <bk-radio-group
                        v-if="planData.replicaType !== 'REAL_TIME'"
                        class="mt10 pr20"
                        style="text-align: right;"
                        v-model="status"
                        @change="handlerPaginationChange()">
                        <bk-radio class="ml50" value="">{{ $t('total') }}</bk-radio>
                        <bk-radio class="ml50" value="SUCCESS">{{ $t('asyncPlanStatusEnum.SUCCESS') }}</bk-radio>
                        <bk-radio class="ml50" value="FAILED">{{ $t('asyncPlanStatusEnum.FAILED') }}</bk-radio>
                    </bk-radio-group>
                    <bk-table
                        class="mt10"
                        :height="planData.replicaType !== 'REAL_TIME' ? 'calc(100% - 90px)' : 'calc(100% - 60px)'"
                        :data="logList"
                        :outer-border="false"
                        :row-border="false"
                        :row-style="{ cursor: 'pointer' }"
                        size="small"
                        :empty-text="$t('planLogEmptyTitle')"
                        @row-click="showLogDetailHandler">
                        <bk-table-column type="index" :label="$t('NO')" width="70"></bk-table-column>
                        <bk-table-column :label="$t('runningStatus')" width="110">
                            <template #default="{ row }">
                                <span class="repo-tag" :class="row.status">{{$t(`asyncPlanStatusEnum.${row.status}`) || $t('notExecuted')}}</span>
                            </template>
                        </bk-table-column>
                        <bk-table-column :label="$t('startExecutionTime')" width="150">
                            <template #default="{ row }">{{formatDate(row.startTime)}}</template>
                        </bk-table-column>
                        <bk-table-column v-if="planData.replicaType !== 'REAL_TIME'" :label="$t('endExecutionTime')" width="150">
                            <template #default="{ row }">{{formatDate(row.endTime)}}</template>
                        </bk-table-column>
                        <bk-table-column :label="$t('planLogErrorReason')" width="120">
                            <template #default="{ row }">
                                <bk-button v-if="row.errorReason" style="width:80%;" text theme="primary" @click.stop="openErrorDetail(row)">{{$t('view')}}</bk-button>
                                <span v-else>/</span>
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
                </div>
            </template>
        </bk-sideslider>
        <canway-dialog
            v-model="errorDialog.show"
            width="450"
            height-num="380"
            :title="$t('planLogErrorReason')"
            :show-footer="false"
            @cancel="closeErrorDialog">
            <div class="error-dialog-info">{{errorDialog.reason}}</div>
            <template #footer>
                <bk-button @click="closeErrorDialog">{{$t('close')}}</bk-button>
            </template>
        </canway-dialog>
    </div>
</template>
<script>
    import { formatDate } from '@repository/utils'
    import { mapActions } from 'vuex'
    import { asyncPlanStatusEnum } from '@repository/store/publicEnum'
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
                asyncPlanStatusEnum,
                isLoading: false,
                status: '',
                logList: [],
                pagination: {
                    count: 0,
                    current: 1,
                    limit: 20,
                    limitList: [10, 20, 40]
                },
                errorDialog: {
                    show: false,
                    reason: ''
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
                // 历史的分发计划，后端接口返回的record字段为null，后端开发原话：record为true与null表示同一个意思，都是记录所有的分发日志
                if (this.planData?.record || this.planData.record === null) {
                    this.$router.push({
                        name: 'logDetail',
                        params: {
                            ...this.$route.params,
                            logId: id
                        },
                        query: {
                            planName: this.planData.name
                        }
                    })
                } else {
                    // 不记录日志详情，此时直接弹窗警告
                    const name = this.planData.name
                    this.$bkMessage({
                        message: this.$t('planTaskLogInfo', { name }),
                        theme: 'warning',
                        limit: 1,
                        offsetY: 50
                    })
                }
            },
            openErrorDetail (row) {
                // 因为后端接口涉及改动较大，且不好实现，后端在分发异常的时候错误信息会始终携带指定字符串 “部分数据同步失败”，所以产品同学说前端针对这几个指定字符串的国际化做特殊处理
                const errorReason = (this.currentLanguage === 'zh-cn' ? '部分数据同步失败' : 'Partial data synchronization failed') + row.errorReason.split('部分数据同步失败')?.[1] || ''
                this.errorDialog = {
                    show: true,
                    reason: errorReason
                }
            },
            closeErrorDialog () {
                this.errorDialog = {
                    show: false,
                    reason: ''
                }
            }
        }
    }
</script>
<style lang="scss" scoped>
.plan-detail-container {
    height: 100%;
}
.error-dialog-info{
    max-height: 250px;
    overflow-y: auto;
    word-break: break-all;
}
</style>
