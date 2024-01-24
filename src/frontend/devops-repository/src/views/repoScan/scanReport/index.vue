<template>
    <div class="scan-report-container">
        <report-overview
            v-if="scanPlan.id"
            :scan-plan="scanPlan"
            class="mb10"
            @refreshData="refreshData"
            @refresh="refreshList">
        </report-overview>
        <div class="report-list-header flex-between-center">
            <span class="report-title flex-align-center">{{ $t('scanHistory') }}</span>
            <div class="flex-align-center">
                <operation-list class="mr10"
                    :list="Object.keys(viewEnum).map(type => ({ clickEvent: () => viewType = type, label: viewEnum[type] }))">
                    <bk-button theme="default">{{ viewEnum[viewType] }}</bk-button>
                </operation-list>
                <bk-button class="ml10" :theme="isfiltering ? 'primary' : 'default'" @click="showFilterForm">{{ $t('filter') }}</bk-button>
            </div>
            <filter-sideslider v-if="scanPlan.id" ref="filterSideslider" :scan-type="scanPlan.type" @filter="filterHandler"></filter-sideslider>
        </div>
        <div class="report-list flex-align-center" v-bkloading="{ isLoading }">
            <div class="mr20 view-task" v-show="viewType === 'TASKVIEW'">
                <div class="task-header flex-align-center">{{ $t('taskList') }}</div>
                <div class="p20">
                    <bk-input
                        v-model.trim="taskNameSearch"
                        :placeholder="$t('keySearchPlaceHolder')"
                        clearable
                        right-icon="bk-icon icon-search"
                        @enter="handlerTaskPaginationChange()"
                        @clear="handlerTaskPaginationChange()">
                    </bk-input>
                </div>
                <div class="task-list">
                    <infinite-scroll
                        ref="infiniteScroll"
                        :is-loading="taskLoading"
                        :has-next="taskList.length < taskPagination.count"
                        @load="handlerTaskPaginationChange({ current: taskPagination.current + 1 }, true)">
                        <div class="mb10 p10 task-item"
                            :class="{ 'selected': task.taskId === taskSelected.taskId }"
                            v-for="task in taskList"
                            :key="task.taskId"
                            @click="changeSelectedTask(task)">
                            <div class="task-name text-overflow" style="max-width:180px;"
                                v-bk-tooltips="{ content: task.name, placements: ['top'] }">{{ task.name || '/' }}</div>
                            <div class="mt10 flex-between-center">
                                <div class="task-time">{{ formatDate(task.triggerDateTime) }}</div>
                                <span v-if="task.status !== 'STOPPED' && task.status !== 'FINISHED'"
                                    class="stop-task flex-align-center"
                                    @click.stop="stopTask(task)">
                                    <Icon class="mr5" name="icon-plus-stop" size="12" />
                                    <span>{{ $t('suspend') }}</span>
                                </span>
                            </div>
                        </div>
                    </infinite-scroll>
                </div>
            </div>
            <div class="flex-1">
                <div v-show="viewType === 'TASKVIEW'" class="mb20 task-overview flex-center">
                    <div class="overview-key">{{ $t('startTime') }}</div>
                    <div class="overview-value">{{ formatDate(taskSelected.startDateTime) }}</div>
                    <div class="overview-key">{{ $t('endTime') }}</div>
                    <div class="overview-value">{{ formatDate(taskSelected.finishedDateTime) }}</div>
                    <div class="overview-key">{{ $t('scanArtifactNum') }}</div>
                    <div class="overview-value">{{ taskSelected.total }}</div>
                </div>
                <bk-table
                    :height="`calc(100% - ${viewType === 'TASKVIEW' ? 100 : 40}px)`"
                    :data="scanList"
                    :outer-border="false"
                    :row-border="false"
                    row-key="recordId"
                    size="small">
                    <template #empty>
                        <empty-data :is-loading="isLoading"></empty-data>
                    </template>
                    <bk-table-column :label="$t('artifactName')" show-overflow-tooltip>
                        <template #default="{ row }">
                            <span v-if="row.groupId" class="mr5 repo-tag" :data-name="row.groupId"></span>
                            <span class="hover-btn" :class="{ 'disabled': !['UN_QUALITY', 'QUALITY_PASS', 'QUALITY_UNPASS'].includes(row.status) }" @click="showArtiReport(row)">{{ row.name }}</span>
                        </template>
                    </bk-table-column>
                    <bk-table-column :label="$t('artifactVersion') + '/' + $t('storagePath')" show-overflow-tooltip>
                        <template #default="{ row }">{{ row.version || row.fullPath }}</template>
                    </bk-table-column>
                    <bk-table-column :label="$t('repo')" show-overflow-tooltip>
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
                    <bk-table-column v-if="scanPlan.scanTypes.includes(SCAN_TYPE_SECURITY)" :label="$t('riskLevel')">
                        <template #default="{ row }">
                            <div v-if="row.highestLeakLevel" class="status-sign" :class="row.highestLeakLevel"
                                :data-name="$t(`leakLevelEnum.${row.highestLeakLevel}`)">
                            </div>
                            <span v-else>/</span>
                        </template>
                    </bk-table-column>
                    <bk-table-column :label="$t('scanStatus')">
                        <template #default="{ row }">
                            <span class="repo-tag" :class="row.status">{{$t(`scanStatusEnum.${row.status}`)}}</span>
                        </template>
                    </bk-table-column>
                    <bk-table-column :label="$t('scanCompletionTime')" width="150">
                        <template #default="{ row }">{{formatDate(row.finishTime)}}</template>
                    </bk-table-column>
                    <bk-table-column :label="$t('operation')" width="100">
                        <template #default="{ row }">
                            <operation-list
                                :list="[
                                    viewType === 'OVERVIEW' && { label: $t('suspend'), clickEvent: () => stopScanHandler(row), disabled: row.status !== 'INIT' && row.status !== 'RUNNING' },
                                    viewType === 'OVERVIEW' && !scanPlan.readOnly && {
                                        label: $t('rescan'),
                                        clickEvent: () => startScanSingleHandler(row),
                                        disabled: !['UN_QUALITY', 'QUALITY_PASS', 'QUALITY_UNPASS', 'STOP', 'FAILED'].includes(row.status)
                                    }
                                ]"></operation-list>
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
            </div>
        </div>
    </div>
</template>
<script>
    import OperationList from '@repository/components/OperationList'
    import InfiniteScroll from '@repository/components/InfiniteScroll'
    import reportOverview from './overview'
    import filterSideslider from './filterSideslider'
    import { mapActions } from 'vuex'
    import { formatDate, debounce } from '@repository/utils'
    import { scanStatusEnum, leakLevelEnum } from '@repository/store/publicEnum'
    import { SCAN_TYPE_SECURITY } from '../../../store/publicEnum'
    const filterParams = {
        name: '',
        repoName: '',
        highestLeakLevel: '',
        status: ''
    }
    export default {
        name: 'scanReport',
        components: {
            OperationList,
            InfiniteScroll,
            reportOverview,
            filterSideslider
        },
        data () {
            return {
                SCAN_TYPE_SECURITY: SCAN_TYPE_SECURITY,
                scanStatusEnum,
                leakLevelEnum,
                scanPlan: {
                    scanTypes: []
                },
                formatISO: {},
                filter: {},
                viewEnum: {
                    OVERVIEW: this.$t('overview'),
                    TASKVIEW: this.$t('taskView')
                },
                viewType: this.$route.query.viewType || 'OVERVIEW',
                taskLoading: false,
                taskNameSearch: '',
                taskSelected: {},
                taskList: [],
                taskPagination: {
                    count: 0,
                    current: 1,
                    limit: 20,
                    limitList: [10, 20, 40]
                },
                isLoading: false,
                scanList: [],
                pagination: {
                    count: 0,
                    current: 1,
                    limit: 20,
                    limitList: [10, 20, 40]
                },
                debounceGetReportListHandler: null,
                dependentCurrent: parseInt(this.$route.query.rc || 1),
                dependentLimit: parseInt(this.$route.query.rl || 20)
            }
        },
        computed: {
            projectId () {
                return this.$route.params.projectId
            },
            planId () {
                return this.$route.params.planId
            },
            isfiltering () {
                return Boolean(Object.values(this.filter).join(''))
            }
        },
        watch: {
            viewType (val) {
                this.$route.query.viewType = val
                this.handlerViewPagination(val)
            }
        },
        created () {
            // 添加防抖，否则会导致请求次数过多
            this.debounceGetReportListHandler = debounce(this.getReportListHandler, 100)
            this.refreshScanPlan(this.projectId, this.planId)
            this.handlerViewPagination(this.viewType)
        },
        methods: {
            formatDate,
            ...mapActions([
                'getScanTaskList',
                'scanReportList',
                'scanTaskReportList',
                'startScanSingle',
                'stopScanTask',
                'stopScan',
                'getScanConfig'
            ]),
            handlerViewPagination (val) {
                if (val === 'OVERVIEW') {
                    this.handlerPaginationChange({ current: this.dependentCurrent, limit: this.dependentLimit })
                } else {
                    // 因为任务视图下的任务列表是滚动加载的，所以没必要精确定位到具体是哪个任务，自然也就不用保留扫描记录列表页的页码及每页大小了
                    this.handlerTaskPaginationChange()
                }
            },
            refreshData (key, value) {
                this[key] = value
                // 当设置时间之后，需要将设置的时间更新到VueRouter的query中，即浏览器的url中
                this.$router.replace({
                    query: {
                        ...this.$route.query,
                        startTime: this.formatISO?.startTime || '',
                        endTime: this.formatISO?.endTime || ''
                    }
                })
            },
            refreshList (force) {
                if (force.forceFlag) {
                    const initFlag = force.initFlag
                    delete force.initFlag
                    // 此时表明是点击面包屑返回或者当前url中携带了相关参数，此时需要根据url中的页码参数筛选
                    if (initFlag === 'initFlag') {
                        this.handlerPaginationChange({ current: this.dependentCurrent, limit: this.dependentLimit })
                    } else {
                        this.handlerPaginationChange()
                    }
                } else {
                    this.getReportListHandler()
                }
            },
            refreshScanPlan (projectId, planId) {
                this.getScanConfig({ projectId: projectId, id: planId }).then(res => {
                    this.scanPlan = res
                })
            },
            handlerPaginationChange ({ current = 1, limit = this.pagination.limit } = {}) {
                this.pagination.current = current
                this.pagination.limit = limit
                this.$router.replace({
                    query: {
                        ...this.$route.query,
                        rc: this.pagination.current,
                        rl: this.pagination.limit
                    }
                })
                this.debounceGetReportListHandler ? this.debounceGetReportListHandler() : this.getReportListHandler()
            },
            getReportListHandler () {
                this.isLoading = true
                const fn = this.viewType === 'TASKVIEW' ? this.scanTaskReportList : this.scanReportList
                return fn({
                    id: this.planId,
                    taskId: this.taskSelected.taskId,
                    projectId: this.projectId,
                    query: {
                        ...(this.viewType === 'TASKVIEW' ? {} : this.formatISO),
                        ...this.filter
                    },
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
                const flag = filter.flag
                // 此时不需要将这个子组件告知父组件是够是第一次筛选的状态同步到浏览器的url
                delete filter.flag
                this.$router.replace({
                    query: {
                        ...this.$route.query,
                        ...filterParams,
                        ...this.filter
                    }
                })
                if (flag === 'initFlag') {
                    // 如果是子组件告知的是第一次筛选，需要保留列表的页码等参数
                    this.handlerPaginationChange({ current: this.dependentCurrent, limit: this.dependentLimit })
                } else {
                    this.handlerPaginationChange()
                }
            },
            stopScanHandler ({ recordId }) {
                this.stopScan({
                    projectId: this.projectId,
                    recordId
                }).then(() => {
                    this.$bkMessage({
                        theme: 'success',
                        message: this.$t('stopScanSuccessMsg')
                    })
                    this.getReportListHandler()
                })
            },
            showArtiReport ({ recordId, name, status }) {
                if (!['UN_QUALITY', 'QUALITY_PASS', 'QUALITY_UNPASS'].includes(status)) return
                this.$router.push({
                    name: 'artiReport',
                    params: {
                        ...this.$route.params,
                        recordId
                    },
                    query: {
                        ...this.$route.query,
                        viewType: this.viewType,
                        taskId: this.taskSelected.taskId,
                        scanType: this.scanPlan.type,
                        artiName: name
                    }
                })
            },
            startScanSingleHandler ({ repoType, repoName, fullPath, packageKey, version }) {
                this.startScanSingle({
                    projectId: this.projectId,
                    id: this.planId,
                    repoType,
                    repoName,
                    fullPath,
                    packageKey,
                    version
                }).then(() => {
                    this.$bkMessage({
                        theme: 'success',
                        message: this.$t('scanArtMsg')
                    })
                    this.handlerPaginationChange()
                })
            },
            handlerTaskPaginationChange ({ current = 1, limit = this.taskPagination.limit } = {}, load) {
                this.taskPagination.current = current
                this.taskPagination.limit = limit
                this.getTaskListHandler(load)
                !load && this.$refs.infiniteScroll && this.$refs.infiniteScroll.scrollToTop()
            },
            getTaskListHandler (load) {
                if (this.taskLoading) return
                this.taskLoading = !load
                this.getScanTaskList({
                    planId: this.planId,
                    projectId: this.projectId,
                    triggerType: this.scanPlan.readOnly ? 'PIPELINE' : 'MANUAL',
                    namePrefix: this.taskNameSearch || undefined,
                    current: this.taskPagination.current,
                    limit: this.taskPagination.limit
                }).then(({ records, totalRecords }) => {
                    load ? this.taskList.push(...records) : (this.taskList = records)
                    this.taskPagination.count = totalRecords
                    if (!load && records.length) {
                        this.changeSelectedTask(records[0])
                    }
                }).finally(() => {
                    this.taskLoading = false
                })
            },
            changeSelectedTask (task) {
                if (this.taskSelected.taskId === task.taskId) return
                this.taskSelected = task
                this.handlerPaginationChange()
            },
            stopTask (task) {
                this.$confirm({
                    theme: 'danger',
                    message: this.$t('confirmStopScanMsg') + `${task.name} ?`,
                    confirmFn: () => {
                        return this.stopScanTask({
                            projectId: this.projectId,
                            taskId: task.taskId
                        }).then(() => {
                            this.$bkMessage({
                                theme: 'success',
                                message: this.$t('abortTask') + this.$t('success')
                            })
                            this.$set(task, 'status', 'STOPPED')
                            this.handlerPaginationChange()
                        })
                    }
                })
            }
        }
    }
</script>
<style lang="scss" scoped>
.scan-report-container {
    position: relative;
    height: 100%;
    overflow: hidden;
    .report-list-header {
        padding: 10px 20px;
        background-color: white;
        .report-title {
            font-size: 14px;
            font-weight: 600;
            &:before {
                content: '';
                width: 3px;
                height: 12px;
                margin-right: 7px;
                border-radius: 1px;
                background-color: var(--primaryColor);
            }
        }
    }
    .report-list {
        height: calc(100% - 200px);
        padding: 0 20px 10px;
        background-color: white;
        > div {
            height: 100%;
        }
        .view-task {
            width: 240px;
            border: 1px solid var(--borderColor);
            .task-header {
                height: 40px;
                padding: 0 20px;
                color: var(--fontSubsidiaryColor);
                border-bottom: 1px solid var(--borderColor);
                background-color: var(--bgColor);
            }
            .task-list {
                padding: 0 20px 10px;
                height:calc(100% - 110px);
                .task-item {
                    border-radius: 2px;
                    background-color: var(--bgLightColor);
                    cursor: pointer;
                    .task-name {
                        font-weight: 600;
                    }
                    .task-time {
                        color: var(--fontSubsidiaryColor);
                    }
                    .stop-task {
                        color: var(--primaryColor);
                    }
                    &:hover {
                        background-color: var(--bgHoverLighterColor);
                    }
                    &.selected {
                        color: white;
                        background-color: var(--primaryColor);
                        .task-time,
                        .stop-task {
                            color: white;
                        }
                    }
                }
            }
        }
        .task-overview {
            height: 40px;
            border-right: 1px solid var(--borderColor);
            .overview-key,
            .overview-value {
                display: flex;
                align-items: center;
                height: 100%;
                padding-left: 10px;
                border: 1px solid var(--borderColor);
                border-right: 0 none;
            }
            .overview-key {
                width: auto;
                color: var(--fontSubsidiaryColor);
                background-color: var(--bgColor);
            }
            .overview-value {
                flex: 1;
            }
        }
    }
}
</style>
