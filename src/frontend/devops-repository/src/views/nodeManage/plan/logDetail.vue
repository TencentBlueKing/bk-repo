<template>
    <div class="log-detail-container">
        <header class="log-detail-header">
            <span>{{ title }}</span>
            <bk-button class="ml20" theme="default" @click="$router.back()">
                {{$t('returnBack')}}
            </bk-button>
        </header>
        <main class="log-detail-main" v-bkloading="{ isLoading }">
            <div class="mb10 log-detail-meta flex-align-center">
                <span class="mr50">执行状态：<span class="repo-tag" :class="logDetail.status">{{statusMap[logDetail.status] || '未执行'}}</span></span>
                <span class="mr50">开始时间：{{formatDate(logDetail.startTime)}}</span>
                <span class="mr50">结束时间：{{formatDate(logDetail.endTime)}}</span>
            </div>
            <div class="mb10 log-package-seach flex-align-center">
                <bk-select
                    class="mr20 w250"
                    v-model="status"
                    placeholder="同步状态"
                    @change="handlerSearchSelectChange()">
                    <bk-option v-for="(label, key) in statusMap" :key="key" :id="key" :name="label"></bk-option>
                </bk-select>
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
            </div>
            <bk-table
                height="calc(100% - 144px)"
                :data="pkgList"
                :outer-border="false"
                :row-border="false"
                size="small">
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
                        <span class="repo-tag" :class="row.status">{{statusMap[row.status] || '未执行'}}</span>
                    </template>
                </bk-table-column>
                <template v-if="logDetail.replicaObjectType === 'PACKAGE'">
                    <bk-table-column label="制品名称" prop="packageKey" show-overflow-tooltip width="150"
                        :formatter="(row, column, cellValue) => cellValue || '--'">
                    </bk-table-column>
                    <bk-table-column label="版本" prop="versions" show-overflow-tooltip width="120"
                        :formatter="(row, column, cellValue) => (cellValue || ['--']).join('、')">
                    </bk-table-column>
                </template>
                <template v-if="logDetail.replicaObjectType === 'PATH'">
                    <bk-table-column label="文件路径" prop="path" show-overflow-tooltip width="200"
                        :formatter="(row, column, cellValue) => cellValue || '--'">
                    </bk-table-column>
                </template>
                <bk-table-column label="成功数量" prop="success" width="80"></bk-table-column>
                <bk-table-column label="跳过数量" prop="skip" width="80"></bk-table-column>
                <bk-table-column label="失败数量" prop="failed" width="80"></bk-table-column>
                <bk-table-column label="备注" show-overflow-tooltip>
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
        </main>
    </div>
</template>
<script>
    import { mapGetters, mapActions } from 'vuex'
    import { formatDate } from '@/utils'
    const statusMap = {
        'RUNNING': '执行中',
        'SUCCESS': '成功',
        'FAILED': '失败'
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
            planName () {
                return this.$route.query.plan
            },
            title () {
                return `${this.planName || '分发计划'} > 执行历史 > 分发详情`
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
                        ...res.record,
                        replicaObjectType: res.replicaObjectType
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
@import '@/scss/conf';
.log-detail-container {
    height: 100%;
    .log-detail-header {
        height: 50px;
        padding: 0 20px;
        display: flex;
        align-items: center;
        justify-content: space-between;
        font-size: 14px;
        background-color: white;
    }
    .log-detail-main {
        height: calc(100% - 70px);
        margin-top: 20px;
        padding: 20px;
        background-color: white;
        .log-detail-meta {
            height: 50px;
            border-bottom: 1px solid $borderLightColor;
        }
        .log-package-seach {
            .search-group {
                min-width: 250px;
            }
        }
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
}
</style>
