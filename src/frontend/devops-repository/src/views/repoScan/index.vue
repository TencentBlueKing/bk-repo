<template>
    <div class="scan-container" v-bkloading="{ isLoading }">
        <div class="ml20 mr20 mt10 flex-between-center">
            <bk-button icon="plus" theme="primary" @click="showCreateDialog">{{ $t('create') }}</bk-button>
            <div class="flex-align-center">
                <bk-input
                    class="w250"
                    v-model.trim="scanName"
                    clearable
                    placeholder="请输入方案名称, 按Enter键搜索"
                    right-icon="bk-icon icon-search"
                    @enter="handlerPaginationChange()"
                    @clear="handlerPaginationChange()">
                </bk-input>
                <bk-select
                    class="ml10 w250"
                    v-model="scanType"
                    placeholder="方案类型"
                    @change="handlerPaginationChange()">
                    <bk-option v-for="[id, name] in Object.entries(scanTypeEnum)" :key="id" :id="id" :name="name"></bk-option>
                </bk-select>
            </div>
        </div>
        <bk-table
            class="mt10 scan-table"
            height="calc(100% - 102px)"
            :data="scanList"
            :outer-border="false"
            :row-border="false"
            row-key="id"
            size="small">
            <template #empty>
                <empty-data :is-loading="isLoading" :search="Boolean(scanName)">
                    <template v-if="!scanName">
                        <span class="ml10">暂无扫描方案，</span>
                        <bk-button text @click="showCreateDialog">即刻创建</bk-button>
                    </template>
                </empty-data>
            </template>
            <bk-table-column label="方案名称" prop="name" show-overflow-tooltip></bk-table-column>
            <bk-table-column label="扫描类型">
                <template #default="{ row }">{{ scanTypeEnum[row.planType] }}</template>
            </bk-table-column>
            <bk-table-column
                v-for="column in [
                    { label: '累计扫描制品', prop: 'artifactCount' },
                    { label: '危急漏洞', prop: 'critical' },
                    { label: '高风险漏洞', prop: 'high' },
                    { label: '中风险漏洞', prop: 'medium' },
                    { label: '低风险漏洞', prop: 'low' }
                ]"
                :key="column.prop"
                :label="column.label"
                :prop="column.prop"
                align="right">
                <template #default="{ row }">{{ segmentNumberThree(row[column.prop]) }}</template>
            </bk-table-column>
            <bk-table-column label="扫描状态">
                <template #default="{ row }">
                    <span class="repo-tag" :class="row.status">{{scanStatusEnum[row.status]}}</span>
                </template>
            </bk-table-column>
            <bk-table-column label="最后扫描时间">
                <template #default="{ row }">{{formatDate(row.lastScanDate)}}</template>
            </bk-table-column>
            <bk-table-column :label="$t('operation')" width="70">
                <template #default="{ row }">
                    <operation-list
                        :list="[
                            { label: '报告', clickEvent: () => showScanReport(row) },
                            { label: '设置', clickEvent: () => showScanConfig(row) },
                            { label: '扫描', clickEvent: () => startScanHandler(row) },
                            { label: '删除', clickEvent: () => deleteScanHandler(row) }
                        ]"></operation-list>
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
        <create-scan-dialog ref="createScanDialog" @refresh="handlerPaginationChange()"></create-scan-dialog>
    </div>
</template>
<script>
    import OperationList from '@repository/components/OperationList'
    import createScanDialog from './createScanDialog'
    import { mapState, mapActions } from 'vuex'
    import { formatDate, segmentNumberThree } from '@repository/utils'
    import { scanTypeEnum, scanStatusEnum } from '@repository/store/publicEnum'
    export default {
        name: 'plan',
        components: { OperationList, createScanDialog },
        data () {
            return {
                scanTypeEnum,
                scanStatusEnum,
                isLoading: false,
                scanName: '',
                scanType: '',
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
            ...mapState(['userList']),
            projectId () {
                return this.$route.params.projectId
            }
        },
        created () {
            this.handlerPaginationChange()
        },
        methods: {
            formatDate,
            segmentNumberThree,
            ...mapActions([
                'getScanList',
                'deleteScan'
            ]),
            showCreateDialog () {
                this.$refs.createScanDialog.setData({
                    show: true,
                    loading: false,
                    type: '',
                    name: '',
                    description: ''
                })
            },
            handlerPaginationChange ({ current = 1, limit = this.pagination.limit } = {}) {
                this.pagination.current = current
                this.pagination.limit = limit
                this.getScanListHandler()
            },
            getScanListHandler () {
                this.isLoading = true
                return this.getScanList({
                    projectId: this.projectId,
                    name: this.scanName,
                    type: this.scanType,
                    current: this.pagination.current,
                    limit: this.pagination.limit
                }).then(({ records, totalRecords }) => {
                    this.scanList = records
                    this.pagination.count = totalRecords
                }).finally(() => {
                    this.isLoading = false
                })
            },
            deleteScanHandler ({ id, name }) {
                this.$confirm({
                    theme: 'danger',
                    message: `确认删除扫描方案 ${name} ?`,
                    confirmFn: () => {
                        return this.deleteScan({
                            projectId: this.projectId,
                            id
                        }).then(() => {
                            this.handlerPaginationChange()
                            this.$bkMessage({
                                theme: 'success',
                                message: '删除扫描方案' + this.$t('success')
                            })
                        })
                    }
                })
            },
            showScanReport ({ id, name }) {
                this.$router.push({
                    name: 'scanReport',
                    params: {
                        ...this.$route.params,
                        planId: id
                    },
                    query: {
                        scanName: name
                    }
                })
            },
            showScanConfig ({ id, name }) {
                this.$router.push({
                    name: 'scanConfig',
                    params: {
                        ...this.$route.params,
                        planId: id
                    },
                    query: {
                        scanName: name
                    }
                })
            },
            startScanHandler ({ id, name }) {
                this.$router.push({
                    name: 'startScan',
                    params: {
                        ...this.$route.params,
                        planId: id
                    },
                    query: {
                        scanName: name
                    }
                })
            }
        }
    }
</script>
<style lang="scss" scoped>
.scan-container {
    height: 100%;
    overflow: hidden;
    background-color: white;
}
</style>
