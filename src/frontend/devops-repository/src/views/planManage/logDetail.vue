<template>
    <main class="log-detail-container" v-bkloading="{ isLoading }">
        <div class="mr20 mt10 log-package-seach flex-align-center">
            <div v-if="showOverviewCount" class="flex-align-center ml20" style="margin-right:auto;">
                <div v-for="(value,key) in countList" :key="key" class="mr30 ">
                    <span>
                        {{$t(`planLogEnum.${key}`)}}
                    </span>
                    <span>: </span>
                    <span class="log-count"
                        v-bk-tooltips="{
                            content: $t(`planLogEnum.${key}`) + ': ' + value,
                            placements: ['top'],
                            disabled: ((value + '').length || 0) < 6
                        }">
                        {{value}}
                    </span>
                </div>
            </div>
            <bk-search-select
                class="search-group"
                clearable
                v-model="searchGroup"
                :placeholder="$t('enterSearch')"
                :show-condition="false"
                :data="searchGroupList"
                @change="handlerSearchSelectChange()"
                @clear="handlerSearchSelectChange()"
                @search="handlerSearchSelectChange()">
            </bk-search-select>
            <bk-select
                class="ml10 w250"
                v-model="status"
                :placeholder="$t('syncStatus')"
                @change="handlerSearchSelectChange()">
                <bk-option id="SUCCESS" :name="$t('asyncPlanStatusEnum.SUCCESS')"></bk-option>
                <bk-option id="FAILED" :name="$t('asyncPlanStatusEnum.FAILED')"></bk-option>
            </bk-select>
        </div>
        <bk-table
            class="mt10"
            height="calc(100% - 100px)"
            :data="pkgList"
            :outer-border="false"
            :row-border="false"
            size="small">
            <template #empty>
                <empty-data :is-loading="isLoading" :search="Boolean(searchGroup.length || status)"></empty-data>
            </template>
            <bk-table-column :label="$t('syncNode')" show-overflow-tooltip>
                <template #default="{ row }">{{ `${masterNode.name} - ${row.remoteCluster}` }}</template>
            </bk-table-column>
            <bk-table-column :label="$t('synchronizeRepository')" show-overflow-tooltip>
                <template #default="{ row }">
                    <Icon class="table-svg" size="16" :name="row.repoType.toLowerCase()" />
                    <span class="ml5">{{ row.localRepoName }}</span>
                </template>
            </bk-table-column>
            <bk-table-column :label="$t('artifactName')" show-overflow-tooltip>
                <template #default="{ row }">{{ row.artifactName || row.packageKey || row.path || '/' }}</template>
            </bk-table-column>
            <bk-table-column :label="$t('version')" show-overflow-tooltip>
                <template #default="{ row }">{{ row.version || (row.versions || ['/']).join('、') }}</template>
            </bk-table-column>
            <bk-table-column :label="$t('syncStatus')" width="90">
                <template #default="{ row }">
                    <div class="status-sign" :class="row.status" :data-name="$t(`asyncPlanStatusEnum.${row.status}`) || $t('notExecuted')"></div>
                </template>
            </bk-table-column>
            <bk-table-column :label="$t('conflictStrategy')" width="150">
                <template #default="{ row }">
                    <span>{{row.conflictStrategy ? $t(`conflictStrategyEnum.${row.conflictStrategy}`) : '/'}}</span>
                </template>
            </bk-table-column>
            <bk-table-column :label="$t('startTime')" width="150">
                <template #default="{ row }">{{formatDate(row.startTime)}}</template>
            </bk-table-column>
            <bk-table-column :label="$t('endTime')" width="150">
                <template #default="{ row }">{{formatDate(row.endTime)}}</template>
            </bk-table-column>
            <!-- 历史数据因为统计数量不存在，需要显示之前的成功数量等字段 -->
            <template v-if="!showOverviewCount">
                <bk-table-column :label="$t('successNum')" prop="success"></bk-table-column>
                <bk-table-column :label="$t('skipNum')" prop="skip"></bk-table-column>
                <bk-table-column :label="$t('failNum')" prop="failed"></bk-table-column>
            </template>
            <bk-table-column :label="$t('note')" show-overflow-tooltip>
                <template #default="{ row }">{{row.errorReason || '/'}}</template>
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
    </main>
</template>
<script>
    import { mapGetters, mapActions } from 'vuex'
    import { formatDate } from '@repository/utils'
    import { asyncPlanStatusEnum, planLogEnum } from '@repository/store/publicEnum'
    export default {
        name: 'logDetail',
        data () {
            return {
                asyncPlanStatusEnum,
                planLogEnum,
                isLoading: false,
                logDetail: {},
                pkgList: [],
                status: '',
                searchGroup: [],
                pagination: {
                    count: 0,
                    current: 1,
                    limit: 20,
                    limitList: [10, 20, 40]
                },
                // 分发计划日志详情的同步数量
                countList: {
                    total: 0,
                    success: 0,
                    failed: 0,
                    conflict: 0
                },
                // 是否需要显示日志详情的同步数量相关字段
                showOverviewCount: false
            }
        },
        computed: {
            ...mapGetters(['masterNode']),
            logId () {
                return this.$route.params.logId
            },
            searchGroupList () {
                return [
                    { name: this.$t('nodeName'), id: 'clusterName' },
                    { name: this.$t('repoName'), id: 'repoName' },
                    { name: this.$t('artifactName'), id: 'artifactName' }
                ]
            }
        },
        created () {
            this.handlerPaginationChange()
            this.getPlanLogDetail({
                id: this.logId
            }).then(res => {
                if (res) {
                    this.logDetail = {
                        ...res,
                        ...res.record
                    }
                } else {
                    this.$router.replace({
                        name: 'planManage'
                    })
                }
            })
            this.getPlanLogCountList()
        },
        methods: {
            formatDate,
            ...mapActions(['getPlanLogDetail', 'getPlanLogPackageList', 'getPlanLogDetailOverview']),
            handlerPaginationChange ({ current = 1, limit = this.pagination.limit } = {}) {
                this.pagination.current = current
                this.pagination.limit = limit
                this.getPlanLogPackageListHandler()
            },
            getPlanLogPackageListHandler () {
                this.isLoading = true
                this.getPlanLogPackageList({
                    current: this.pagination.current,
                    limit: this.pagination.limit,
                    id: this.logId,
                    status: this.status,
                    ...this.getSearchQuery()
                }).then(({ records, totalRecords }) => {
                    this.pkgList = records.map(v => {
                        return {
                            ...v,
                            ...v.progress,
                            ...(v.packageConstraint || {}),
                            ...(v.pathConstraint || {})
                        }
                    })
                    this.pagination.count = totalRecords
                }).finally(() => {
                    this.isLoading = false
                })
            },
            handlerSearchSelectChange () {
                this.searchGroup = this.searchGroup.reduce((target, item) => {
                    if (!item.values) return target
                    const index = target.findIndex(search => search.id === item.id)
                    if (index === -1) {
                        target.push(item)
                        return target
                    } else {
                        target.splice(index, 1, { ...target[index], values: item.values })
                        return target
                    }
                }, [])
                this.handlerPaginationChange()
            },
            getSearchQuery () {
                return this.searchGroup.reduce((target, item) => {
                    target[item.id] = item.values[0].id
                    return target
                }, {})
            },
            // 获取当前分发计划执行记录中同步数量相关参数展示数据
            getPlanLogCountList () {
                this.getPlanLogDetailOverview({ id: this.logId }).then((res) => {
                    // 只有在后端接口返回的数据有值时才需要展示，后端接口没有返回时则表明可能是历史数据，历史数据不展示
                    if (res) {
                        this.showOverviewCount = true
                        this.countList = {
                            total: (res?.success || 0) + (res?.failed || 0),
                            ...res
                        }
                    }
                })
            }
        }
    }
</script>
<style lang="scss" scoped>
.log-detail-container {
    height: 100%;
    background-color: white;
    .log-package-seach {
        justify-content: flex-end;
        .search-group {
            min-width: 250px;
        }
    }
    .log-count {
        font-size: 14px;
        font-weight: bold;
        max-width: 50px;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        display: inline-block;
        vertical-align: middle;
    }
}
</style>
