<template>
    <div class="license-manage-container" v-bkloading="{ isLoading }">
        <div class="mt10 flex-between-center">
            <bk-button class="ml20" icon="plus" theme="primary" @click="showUploadLicense">{{ $t('upload') }}</bk-button>
            <div class="mr20 flex-align-center">
                <bk-input
                    v-model.trim="name"
                    class="w250"
                    placeholder="请输入名称, 按Enter键搜索"
                    clearable
                    @enter="handlerPaginationChange()"
                    @clear="handlerPaginationChange()"
                    right-icon="bk-icon icon-search">
                </bk-input>
                <bk-select
                    class="ml10 w250"
                    v-model="isTrust"
                    placeholder="请选择合规性"
                    @change="handlerPaginationChange()">
                    <bk-option id="true" name="合规"></bk-option>
                    <bk-option id="false" name="不合规"></bk-option>
                </bk-select>
            </div>
        </div>
        <bk-table
            class="mt10"
            height="calc(100% - 100px)"
            :data="licenseList"
            :outer-border="false"
            :row-border="false"
            size="small">
            <template #empty>
                <empty-data :is-loading="isLoading" :search="Boolean(name || isTrust)"></empty-data>
            </template>
            <bk-table-column label="名称">
                <template #default="{ row }">
                    <span v-bk-tooltips="{ content: row.name, placements: ['top'] }">{{ row.licenseId }}</span>
                </template>
            </bk-table-column>
            <bk-table-column label="OSI认证" width="120">
                <template #default="{ row }">{{ `${row.isOsiApproved ? '已' : '未'}认证` }}</template>
            </bk-table-column>
            <bk-table-column label="FSF开源" width="120">
                <template #default="{ row }">{{ `${row.isFsfLibre ? '已' : '未'}开源` }}</template>
            </bk-table-column>
            <bk-table-column label="推荐使用" width="120">
                <template #default="{ row }">{{ `${row.isDeprecatedLicenseId ? '不' : ''}推荐` }}</template>
            </bk-table-column>
            <bk-table-column label="合规性" width="120">
                <template #default="{ row }">
                    <span class="repo-tag" :class="row.isTrust ? 'SUCCESS' : 'FAILED'">{{ `${row.isTrust ? '' : '不'}合规` }}</span>
                </template>
            </bk-table-column>
            <bk-table-column :label="$t('operation')" width="70">
                <template #default="{ row }">
                    <operation-list
                        :list="[
                            row.isTrust && { label: '设置不合规', clickEvent: () => changeTrust(row) },
                            !row.isTrust && { label: '设置合规', clickEvent: () => changeTrust(row) },
                            { label: '详细信息', clickEvent: () => showLicenseUrl(row) }
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
        <generic-upload-dialog ref="genericUploadDialog" @update="getLicenseListHandler"></generic-upload-dialog>
        <canway-dialog
            v-model="licenseInfo.show"
            title="证书详细信息"
            :height-num="400"
            @cancel="licenseInfo.show = false">
            <bk-form class="mr10" :label-width="90">
                <bk-form-item label="证书信息">
                    <a style="word-break:break-all;" :href="licenseInfo.reference" target="_blank">{{ licenseInfo.reference }}</a>
                </bk-form-item>
                <bk-form-item label="参考文档">
                    <div v-for="url in licenseInfo.seeAlso" :key="url">
                        <a style="word-break:break-all;" :href="url" target="_blank">{{ url }}</a>
                    </div>
                </bk-form-item>
            </bk-form>
            <template #footer>
                <bk-button theme="default" @click="licenseInfo.show = false">{{$t('close')}}</bk-button>
            </template>
        </canway-dialog>
    </div>
</template>
<script>
    import OperationList from '@repository/components/OperationList'
    import genericUploadDialog from '@repository/views/repoGeneric/genericUploadDialog'
    import { mapActions } from 'vuex'
    export default {
        name: 'user',
        components: { OperationList, genericUploadDialog },
        data () {
            return {
                isLoading: false,
                name: '',
                isTrust: '',
                licenseList: [],
                licenseInfo: {
                    show: false,
                    reference: '',
                    seeAlso: []
                },
                pagination: {
                    count: 0,
                    current: 1,
                    limit: 20,
                    limitList: [10, 20, 40]
                }
            }
        },
        created () {
            this.handlerPaginationChange()
        },
        methods: {
            ...mapActions([
                'getLicenseList',
                'editLicense'
            ]),
            handlerPaginationChange ({ current = 1, limit = this.pagination.limit } = {}) {
                this.pagination.current = current
                this.pagination.limit = limit
                this.getLicenseListHandler()
            },
            getLicenseListHandler () {
                this.isLoading = true
                return this.getLicenseList({
                    name: this.name || undefined,
                    isTrust: this.isTrust || undefined,
                    current: this.pagination.current,
                    limit: this.pagination.limit
                }).then(({ records, totalRecords }) => {
                    this.licenseList = records
                    this.pagination.count = totalRecords
                }).finally(() => {
                    this.isLoading = false
                })
            },
            showUploadLicense () {
                this.$refs.genericUploadDialog.setData({
                    projectId: 'public-global',
                    repoName: 'vuldb-repo',
                    show: true,
                    title: '上传许可证',
                    fullPath: '/spdx-license'
                })
            },
            changeTrust ({ licenseId, isTrust }) {
                this.editLicense({
                    licenseId,
                    isTrust: !isTrust
                }).then(() => {
                    this.$bkMessage({
                        theme: 'success',
                        message: `设置证书${!isTrust ? '合规' : '不合规'}`
                    })
                }).finally(() => {
                    this.getLicenseListHandler()
                })
            },
            showLicenseUrl (row) {
                this.licenseInfo = {
                    show: true,
                    ...row
                }
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
