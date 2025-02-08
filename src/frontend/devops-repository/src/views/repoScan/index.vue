<template>
    <div class="scan-container" v-bkloading="{ isLoading }">
        <div class="ml20 mr20 mt10 flex-between-center">
            <div class="flex-align-center">
                <bk-button icon="plus" theme="primary" @click="showCreateDialog">{{ $t('newAnalysis') }}</bk-button>
            </div>
            <div class="flex-align-center">
                <bk-input
                    class="w250"
                    v-model.trim="scanName"
                    clearable
                    :placeholder="$t('planNamePlaceHolder')"
                    right-icon="bk-icon icon-search"
                    @enter="handlerPaginationChange()"
                    @clear="handlerPaginationChange()">
                </bk-input>
                <bk-select
                    class="ml10 w250"
                    v-model="scanType"
                    :placeholder="$t('schemeType')"
                    @change="handlerPaginationChange()">
                    <bk-option v-for="[id] in Object.entries(scanTypeEnum)" :key="id" :id="id" :name="$t(`scanTypeEnum.${id}`)"></bk-option>
                </bk-select>
            </div>
        </div>
        <bk-table
            class="mt10 scan-table"
            height="calc(100% - 100px)"
            :data="scanList"
            :outer-border="false"
            :row-border="false"
            row-key="id"
            size="small">
            <template #empty>
                <empty-data :is-loading="isLoading" :search="Boolean(scanName)"></empty-data>
            </template>
            <bk-table-column :label="$t('schemeName')" show-overflow-tooltip>
                <template #default="{ row }">
                    <span class="hover-btn" @click="showScanReport(row)">{{row.name}}</span>
                </template>
            </bk-table-column>
            <bk-table-column :label="$t('schemeType')">
                <template #default="{ row }">{{ $t(`scanTypeEnum.${row.planType}`) }}</template>
            </bk-table-column>
            <!-- <bk-table-column label="扫描器" prop="scanner" show-overflow-tooltip></bk-table-column> -->
            <bk-table-column :label="$t('scanStatus')">
                <template #default="{ row }">
                    <span class="repo-tag" :class="row.status">{{ $t(`scanStatusEnum.${row.status}`)}}</span>
                </template>
            </bk-table-column>
            <bk-table-column :label="$t('lastScanTime')">
                <template #default="{ row }">{{formatDate(row.lastScanDate)}}</template>
            </bk-table-column>
            <bk-table-column :label="$t('operation')" width="100">
                <template #default="{ row }">
                    <operation-list
                        :list="[
                            { label: $t('setting'), clickEvent: () => showScanConfig(row) },
                            { label: $t('suspend'), clickEvent: () => stopScanHandler(row) },
                            !row.readOnly && { label: $t('scanImmediately'), clickEvent: () => startScanHandler(row) },
                            !row.readOnly && { label: $t('delete'), clickEvent: () => deleteScanHandler(row) }
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
    import { formatDate, debounce } from '@repository/utils'
    import { scanTypeEnum, scanStatusEnum } from '@repository/store/publicEnum'
    import { cloneDeep } from 'lodash'
    const paginationParams = {
        count: 0,
        current: 1,
        limit: 20,
        limitList: [10, 20, 40]
    }
    export default {
        name: 'plan',
        components: { OperationList, createScanDialog },
        data () {
            return {
                scanTypeEnum,
                scanStatusEnum,
                isLoading: false,
                scanName: this.$route.query.sn || '',
                scanType: this.$route.query.st || '',
                scanList: [],
                pagination: cloneDeep(paginationParams),
                debounceGetScanListHandler: null
            }
        },
        computed: {
            ...mapState(['userList']),
            projectId () {
                return this.$route.params.projectId
            }
        },
        watch: {
            '$route.query' () {
                if (Object.values(this.$route.query).filter(Boolean)?.length === 0) {
                    // 此时需要将筛选条件清空和页码参数重置为初始值，否则会导致点击菜单的时候筛选条件还在，不符合产品要求(点击菜单清空筛选条件，重新请求最新数据)
                    this.pagination = cloneDeep(paginationParams)
                    this.scanName = ''
                    this.scanType = ''
                    this.handlerPaginationChange()
                }
            }
        },
        created () {
            // 此处的两个顺序不能更换，否则会导致请求数据时报错，防抖这个方法不是function
            this.debounceGetScanListHandler = debounce(this.getScanListHandler, 100)
            // vue-router 切换路由时会导致页码相关参数变为string类型，而bk-pagination的页码相关参数要求为number类型，导致页码类型不对应，出现一系列问题
            const dependentCurrent = parseInt(this.$route.query.sc || 1)
            const dependentLimit = parseInt(this.$route.query.sl || 20)
            this.handlerPaginationChange({ current: dependentCurrent, limit: dependentLimit })
        },
        methods: {
            formatDate,
            ...mapActions([
                'getScanList',
                'deleteScan',
                'stopScan'
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
                this.$router.replace({
                    query: {
                        sn: this.scanName,
                        st: this.scanType,
                        sc: this.pagination.current,
                        sl: this.pagination.limit
                    }
                })
                this.debounceGetScanListHandler ? this.debounceGetScanListHandler() : this.getScanListHandler()
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
                    message: this.$t('confirmDeleteScanMsg', [name]),
                    confirmFn: () => {
                        return this.deleteScan({
                            projectId: this.projectId,
                            id
                        }).then(() => {
                            this.handlerPaginationChange()
                            this.$bkMessage({
                                theme: 'success',
                                message: this.$t('deleteScan') + this.$t('space') + this.$t('success')
                            })
                        })
                    }
                })
            },
            showScanReport ({ id, planType, name }) {
                this.$router.push({
                    name: 'scanReport',
                    params: {
                        ...this.$route.params,
                        planId: id
                    },
                    query: {
                        scanName: name,
                        sn: this.scanName,
                        st: this.scanType,
                        sc: this.pagination.current,
                        sl: this.pagination.limit
                    }
                })
            },
            showScanConfig ({ id, planType, name }) {
                this.$router.push({
                    name: 'scanConfig',
                    params: {
                        ...this.$route.params,
                        planId: id
                    },
                    query: {
                        ...this.$route.query,
                        scanType: planType,
                        scanName: name
                    }
                })
            },
            startScanHandler ({ id, planType, name }) {
                this.$router.push({
                    name: 'startScan',
                    params: {
                        ...this.$route.params,
                        planId: id
                    },
                    query: {
                        scanType: planType,
                        scanName: name
                    }
                })
            },
            stopScanHandler ({ id, name }) {
                this.$confirm({
                    theme: 'danger',
                    message: this.$t('stopScanMsg', [name]),
                    confirmFn: () => {
                        return this.stopScan({
                            projectId: this.projectId,
                            id
                        }).then(() => {
                            this.handlerPaginationChange()
                            this.$bkMessage({
                                theme: 'success',
                                message: this.$t('discontinuedProgram') + this.$t('space') + this.$t('success')
                            })
                        })
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
