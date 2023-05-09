<template>
    <div class="license-manage-container" v-bkloading="{ isLoading }">
        <div class="mt10 flex-between-center">
            <bk-button class="ml20" icon="plus" theme="primary" @click="upload">{{ $t('upload') }}</bk-button>
            <bk-select
                class="mr20 w250"
                v-model="scannerFilter"
                @change="handlerPaginationChange()">
                <bk-option v-for="scanner in scannerList" :key="scanner.typr" :id="scanner.type" :name="scanner.name"></bk-option>
            </bk-select>
        </div>
        <bk-table
            class="mt10"
            height="calc(100% - 100px)"
            :data="dataList"
            :outer-border="false"
            :row-border="false"
            size="small">
            <template #empty>
                <empty-data :is-loading="isLoading"></empty-data>
            </template>
            <bk-table-column :label="$t('bugPackageName')" prop="name"></bk-table-column>
            <bk-table-column :label="$t('associatedScanner')">
                <template #default="{ row }">{{ getScannerName(row) }}</template>
            </bk-table-column>
            <bk-table-column :label="$t('lastModifiedDate')" prop="lastModifiedDate" width="200">
                <template #default="{ row }">{{ formatDate(row.lastModifiedDate) }}</template>
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
        <generic-upload-dialog ref="genericUploadDialog" height-num="375" @update="getDataListHandler">
            <bk-form class="mb20" :label-width="90">
                <bk-form-item :label="$t('associatedScanner')">
                    <bk-select
                        class="w250"
                        v-model="scannerType"
                        :placeholder="$t('selectScannerPlaceHolder')"
                        :clearable="false">
                        <bk-option v-for="scanner in scannerList" :key="scanner.type" :id="scanner.type" :name="scanner.name"></bk-option>
                    </bk-select>
                </bk-form-item>
            </bk-form>
        </generic-upload-dialog>
    </div>
</template>
<script>
    import genericUploadDialog from '@repository/views/repoGeneric/genericUploadDialog'
    import { mapActions } from 'vuex'
    import { formatDate } from '@repository/utils'
    export default {
        name: 'user',
        components: { genericUploadDialog },
        data () {
            return {
                isLoading: false,
                scannerFilter: '',
                scannerType: '',
                scannerList: [],
                dataList: [],
                pagination: {
                    count: 0,
                    current: 1,
                    limit: 20,
                    limitList: [10, 20, 40]
                }
            }
        },
        watch: {
            scannerType (val) {
                this.$refs.genericUploadDialog.setData({
                    fullPath: `/${val}`
                }, true)
            }
        },
        created () {
            this.getScannerList().then(res => {
                this.scannerList = res.filter(v => v.type !== 'scancodeToolkit')
                this.scannerType = this.scannerList[0]?.type || ''
            })
            this.handlerPaginationChange()
        },
        methods: {
            formatDate,
            ...mapActions([
                'getScannerList',
                'searchPackageList'
            ]),
            handlerPaginationChange ({ current = 1, limit = this.pagination.limit } = {}) {
                this.pagination.current = current
                this.pagination.limit = limit
                this.getDataListHandler()
            },
            getDataListHandler () {
                this.isLoading = true
                this.searchPackageList({
                    projectId: 'public-global',
                    repoType: 'generic',
                    repoName: 'vuldb-repo',
                    extRules: this.scannerFilter
                        ? [
                            {
                                field: 'path',
                                value: `/${this.scannerFilter}/`,
                                operation: 'EQ'
                            }
                        ]
                        : [
                            {
                                field: 'path',
                                value: ['/spdx-license/'],
                                operation: 'NIN'
                            }
                        ],
                    current: this.pagination.current,
                    limit: this.pagination.limit
                }).then(({ records, totalRecords }) => {
                    this.dataList = records
                    this.pagination.count = totalRecords
                }).finally(() => {
                    this.isLoading = false
                })
            },
            upload () {
                this.$refs.genericUploadDialog.setData({
                    projectId: 'public-global',
                    repoName: 'vuldb-repo',
                    show: true,
                    title: this.$t('upload'),
                    fullPath: `/${this.scannerType}`
                })
            },
            getScannerName ({ fullPath }) {
                const scannerType = fullPath.replace(/^\/([^/]+)\/[^/]+$/, '$1')
                const scanner = this.scannerList.find(s => s.type === scannerType)
                return scanner?.name
            }
        }
    }
</script>
<style lang="scss" scoped>
.license-manage-container {
    height: 100%;
    overflow: hidden;
}
</style>
