<template>
    <div class="p20 scan-task-container">
        <div class="mb10 flex-end-center">
            <bk-button :theme="isfiltering ? 'primary' : 'default'" @click="showFilterForm">筛选</bk-button>
        </div>
        <bk-table
            height="calc(100% - 80px)"
            :data="scanList"
            :outer-border="false"
            :row-border="false"
            row-key="recordId"
            size="small">
            <template #empty>
                <empty-data :is-loading="isLoading"></empty-data>
            </template>
            <bk-table-column label="制品名称" show-overflow-tooltip>
                <template #default="{ row }">
                    <span v-if="row.groupId" class="mr5 repo-tag" :data-name="row.groupId"></span>
                    <span class="hover-btn" :class="{ 'disabled': !['UN_QUALITY', 'QUALITY_PASS', 'QUALITY_UNPASS'].includes(row.status) }" @click="showArtiReport(row)">{{ row.name }}</span>
                </template>
            </bk-table-column>
            <bk-table-column label="制品版本/存储路径" show-overflow-tooltip>
                <template #default="{ row }">{{ row.version || row.fullPath }}</template>
            </bk-table-column>
            <bk-table-column label="所属仓库" show-overflow-tooltip>
                <template #default="{ row }">
                    <Icon class="table-svg" size="16" :name="row.repoType.toLowerCase()" />
                    <span class="ml5">{{replaceRepoName(row.repoName)}}</span>
                </template>
            </bk-table-column>
            <!-- <bk-table-column label="质量规则">
                <template #default="{ row }">
                    <span v-if="row.qualityRedLine === true" class="repo-tag SUCCESS">通过</span>
                    <span v-else-if="row.qualityRedLine === false" class="repo-tag FAILED">不通过</span>
                    <span v-else>/</span>
                </template>
            </bk-table-column> -->
            <bk-table-column v-if="!baseInfo.planType.includes('LICENSE')" label="风险等级">
                <template #default="{ row }">
                    <div v-if="row.highestLeakLevel" class="status-sign" :class="row.highestLeakLevel"
                        :data-name="leakLevelEnum[row.highestLeakLevel]">
                    </div>
                    <span v-else>/</span>
                </template>
            </bk-table-column>
            <bk-table-column label="扫描状态">
                <template #default="{ row }">
                    <span class="repo-tag" :class="row.status">{{scanStatusEnum[row.status]}}</span>
                </template>
            </bk-table-column>
            <bk-table-column label="扫描完成时间" width="150">
                <template #default="{ row }">{{formatDate(row.finishTime)}}</template>
            </bk-table-column>
            <bk-table-column label="操作" width="100">
                <template #default="{ row }">
                    <bk-button text title="primary" :disabled="!['UN_QUALITY', 'QUALITY_PASS', 'QUALITY_UNPASS'].includes(row.status)" @click="showArtiReport(row)">查看</bk-button>
                </template>
            </bk-table-column>
        </bk-table>
        <bk-pagination
            class="pt10"
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
        <filter-sideslider ref="filterSideslider" :scan-type="baseInfo.planType" @filter="filterHandler"></filter-sideslider>
    </div>
</template>
<script>
    import filterSideslider from '@repository/views/repoScan/scanReport/filterSideslider'
    import { mapActions } from 'vuex'
    import { formatDate } from '@repository/utils'
    import { scanStatusEnum, leakLevelEnum } from '@repository/store/publicEnum'
    export default {
        name: 'scanTask',
        components: {
            filterSideslider
        },
        data () {
            return {
                scanStatusEnum,
                leakLevelEnum,
                isLoading: false,
                baseInfo: {
                    planType: ''
                },
                filter: {},
                scanList: [],
                pagination: {
                    count: 0,
                    current: 1,
                    limit: 20,
                    limitList: [10, 20, 40]
                }
            }
        },
        computed: {
            projectId () {
                return this.$route.params.projectId
            },
            planId () {
                return this.$route.params.planId
            },
            taskId () {
                return this.$route.params.taskId
            },
            isfiltering () {
                if (this.filter.flag === 'initFlag') {
                    delete this.filter.flag
                }
                return Boolean(Object.values(this.filter).join(''))
            }
        },
        created () {
            this.getScanReportOverview()
            this.handlerPaginationChange()
        },
        methods: {
            formatDate,
            ...mapActions([
                'scanReportOverview',
                'scanTaskReportList'
            ]),
            getScanReportOverview () {
                this.scanReportOverview({
                    projectId: this.projectId,
                    id: this.planId
                }).then(res => {
                    this.baseInfo = res
                })
            },
            handlerPaginationChange ({ current = 1, limit = this.pagination.limit } = {}) {
                this.pagination.current = current
                this.pagination.limit = limit
                this.getReportListHandler()
            },
            getReportListHandler () {
                this.isLoading = true
                return this.scanTaskReportList({
                    id: this.planId,
                    taskId: this.taskId,
                    projectId: this.projectId,
                    query: this.filter,
                    current: this.pagination.current,
                    limit: this.pagination.limit
                }).then(({ records, totalRecords }) => {
                    this.scanList = records
                    this.pagination.count = totalRecords
                }).finally(() => {
                    this.isLoading = false
                })
            },
            showFilterForm () {
                this.$refs.filterSideslider.show()
            },
            filterHandler (filter) {
                this.filter = filter
                this.handlerPaginationChange()
            },
            showArtiReport ({ recordId, name, status }) {
                if (!['UN_QUALITY', 'QUALITY_PASS', 'QUALITY_UNPASS'].includes(status)) return
                this.$router.push({
                    name: 'artiReport',
                    params: {
                        planId: this.planId,
                        recordId
                    },
                    query: {
                        viewType: 'TASKVIEW',
                        taskId: this.taskId,
                        scanType: this.baseInfo.planType,
                        scanName: this.baseInfo.name,
                        artiName: name
                    }
                })
            }
        }
    }
</script>
<style lang="scss" scoped>
.scan-task-container {
    height: 100%;
    background-color: white;
}
</style>
