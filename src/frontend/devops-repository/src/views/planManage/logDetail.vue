<template>
    <main class="log-detail-container" v-bkloading="{ isLoading }">
        <div class="mr20 mt10 log-package-seach flex-align-center">
            <bk-search-select
                class="search-group"
                clearable
                v-model="searchGroup"
                placeholder="按下Enter键搜索"
                :show-condition="false"
                :data="searchGroupList"
                @change="handlerSearchSelectChange()"
                @clear="handlerSearchSelectChange()"
                @search="handlerSearchSelectChange()">
            </bk-search-select>
            <bk-select
                class="ml10 w250"
                v-model="status"
                placeholder="同步状态"
                @change="handlerSearchSelectChange()">
                <bk-option id="SUCCESS" name="成功"></bk-option>
                <bk-option id="FAILED" name="失败"></bk-option>
            </bk-select>
        </div>
        <bk-table
            class="mt10"
            height="calc(100% - 104px)"
            :data="pkgList"
            :outer-border="false"
            :row-border="false"
            size="small">
            <template #empty>
                <empty-data :is-loading="isLoading" :search="Boolean(searchGroup.length || status)">
                    <template v-if="!Boolean(searchGroup.length || status)">
                        <span class="ml10">暂无同步记录</span>
                    </template>
                </empty-data>
            </template>
            <bk-table-column label="同步节点" prop="remoteCluster" width="150"
                :formatter="(row, column, cellValue) => `${masterNode.name} - ${cellValue}`">
            </bk-table-column>
            <bk-table-column label="同步仓库" width="150">
                <template #default="{ row }">
                    <div class="flex-align-center" :title="row.localRepoName">
                        <Icon size="16" :name="row.repoType.toLowerCase()" />
                        <span class="ml5 text-overflow" style="max-width: 95px;">{{ row.localRepoName }}</span>
                    </div>
                </template>
            </bk-table-column>
            <bk-table-column label="同步状态" align="center" width="80">
                <template #default="{ row }">
                    <div class="flex-align-center">
                        <i class="status-icon" :class="row.status"></i>
                        <span class="ml5" :class="row.status">{{ statusMap[row.status] || '未执行' }}</span>
                    </div>
                </template>
            </bk-table-column>
            <template v-if="logDetail.replicaType === 'REAL_TIME' || logDetail.replicaObjectType !== 'REPOSITORY'">
                <bk-table-column label="制品名称 / 文件路径" show-overflow-tooltip width="200">
                    <template #default="{ row }">
                        {{ row.packageKey || row.path || '--' }}
                    </template>
                </bk-table-column>
                <bk-table-column label="版本" prop="versions" show-overflow-tooltip width="120"
                    :formatter="(row, column, cellValue) => (cellValue || ['--']).join('、')">
                </bk-table-column>
            </template>
            <bk-table-column label="开始时间" width="150">
                <template #default="{ row }">
                    {{formatDate(row.startTime)}}
                </template>
            </bk-table-column>
            <bk-table-column label="结束时间" width="150">
                <template #default="{ row }">
                    {{formatDate(row.endTime)}}
                </template>
            </bk-table-column>
            <bk-table-column label="成功数量" prop="success" width="80"></bk-table-column>
            <bk-table-column label="跳过数量" prop="skip" width="80"></bk-table-column>
            <bk-table-column label="失败数量" prop="failed" width="80"></bk-table-column>
            <bk-table-column label="备注">
                <template #default="{ row }">
                    <span :title="row.errorReason">{{row.errorReason || '--'}}</span>
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
    </main>
</template>
<script>
    import { mapGetters, mapActions } from 'vuex'
    import { formatDate } from '@repository/utils'
    const statusMap = {
        RUNNING: '执行中',
        SUCCESS: '成功',
        FAILED: '失败'
    }
    export default {
        name: 'logDetail',
        data () {
            return {
                statusMap,
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
                }
            }
        },
        computed: {
            ...mapGetters(['masterNode']),
            logId () {
                return this.$route.params.logId
            },
            searchGroupList () {
                return [
                    { name: '节点名称', id: 'clusterName' },
                    { name: '仓库名称', id: 'repoName' },
                    ...(this.logDetail.replicaObjectType === 'PACKAGE' ? [{ name: '制品名称', id: 'packageName' }] : []),
                    ...(this.logDetail.replicaObjectType === 'PATH' ? [{ name: '文件路径', id: 'path' }] : [])
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
        },
        methods: {
            formatDate,
            ...mapActions(['getPlanLogDetail', 'getPlanLogPackageList']),
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
    .SUCCESS {
        color: var(--successColor);
    }
    .FAILED {
        color: var(--dangerColor);
    }
    .RUNNING {
        color: var(--primaryColor);
    }
    .status-icon {
        width: 10px;
        height: 10px;
        border-radius: 50%;
        &.SUCCESS {
            background-color: var(--successColor);
        }
        &.FAILED {
            background-color: var(--dangerColor);
        }
        &.RUNNING {
            background-color: var(--primaryColor);
        }
    }
}
</style>
