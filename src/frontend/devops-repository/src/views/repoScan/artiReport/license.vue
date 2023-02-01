<template>
    <div class="leak-list">
        <div class="flex-align-center">
            <bk-input
                class="w250"
                v-model.trim="filter.licenseId"
                clearable
                placeholder="请输入许可证名称, 按Enter键搜索"
                right-icon="bk-icon icon-search"
                @enter="handlerPaginationChange()"
                @clear="handlerPaginationChange()">
            </bk-input>
            <div class="flex-1 flex-end-center">
                <bk-button theme="default" @click="$emit('rescan')">重新扫描</bk-button>
            </div>
        </div>
        <bk-table
            class="mt10 leak-table"
            height="calc(100% - 100px)"
            :data="licenseList"
            :outer-border="false"
            :row-border="false"
            row-key="licenseKey"
            size="small">
            <template #empty>
                <empty-data
                    :is-loading="isLoading"
                    :search="Boolean(filter.licenseId)"
                    title="未扫描到证书信息">
                </empty-data>
            </template>
            <bk-table-column type="expand" width="30">
                <template #default="{ row }">
                    <div class="leak-title">证书信息</div>
                    <div class="leak-tip">
                        <a :href="row.description" target="_blank">{{ row.description || '/' }}</a>
                    </div>
                </template>
            </bk-table-column>
            <bk-table-column label="名称">
                <template #default="{ row }">
                    <span v-bk-tooltips="{ content: row.fullName, placements: ['top'] }">{{ row.licenseId }}</span>
                </template>
            </bk-table-column>
            <bk-table-column label="依赖路径" prop="dependentPath"></bk-table-column>
            <bk-table-column label="OSI认证" width="120">
                <template #default="{ row }">{{ row.description ? `${row.isOsiApproved ? '已' : '未'}认证` : '/' }}</template>
            </bk-table-column>
            <bk-table-column label="FSF开源" width="120">
                <template #default="{ row }">{{ row.description ? `${row.isFsfLibre ? '已' : '未'}开源` : '/' }}</template>
            </bk-table-column>
            <bk-table-column label="推荐使用" width="120">
                <template #default="{ row }">{{ row.description ? `${row.recommended ? '' : '不'}推荐` : '/' }}</template>
            </bk-table-column>
            <bk-table-column label="合规性" width="120">
                <template #default="{ row }">
                    <span v-if="row.description" class="repo-tag" :class="row.compliance ? 'SUCCESS' : 'FAILED'">{{ `${row.compliance ? '' : '不'}合规` }}</span>
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
<script>
    import { mapActions } from 'vuex'
    export default {
        name: 'license',
        props: {
            subtaskOverview: Object,
            projectId: String,
            viewType: String
        },
        data () {
            return {
                isLoading: false,
                licenseList: [],
                pagination: {
                    count: 0,
                    current: 1,
                    limit: 20,
                    limitList: [10, 20, 40]
                },
                filter: {
                    licenseId: ''
                }
            }
        },
        watch: {
            subtaskOverview () {
                this.handlerPaginationChange()
            }
        },
        created () {
            if (this.subtaskOverview && this.subtaskOverview.recordId) {
                this.handlerPaginationChange()
            }
        },
        methods: {
            ...mapActions(['getLicenseLeakList']),
            handlerPaginationChange ({ current = 1, limit = this.pagination.limit } = {}) {
                this.pagination.current = current
                this.pagination.limit = limit
                this.getLicenseList()
            },
            getLicenseList () {
                this.isLoading = true
                return this.getLicenseLeakList({
                    projectId: this.projectId,
                    recordId: this.subtaskOverview.recordId,
                    viewType: this.viewType,
                    licenseId: this.filter.licenseId,
                    current: this.pagination.current,
                    limit: this.pagination.limit
                }).then(({ records, totalRecords }) => {
                    this.licenseList = records.map(v => ({
                        ...v,
                        licenseKey: `${v.licenseId}-${v.dependentPath}-${v.pkgName}`
                    }))
                    this.pagination.count = totalRecords
                }).finally(() => {
                    this.isLoading = false
                })
            }
        }
    }
</script>
<style lang="scss" scoped>
.leak-list {
    flex: 1;
    height: 100%;
    .leak-title {
        padding: 5px 20px 0;
        font-weight: 800;
    }
    .leak-tip {
        padding: 0 20px 5px;
        color: var(--fontDisableColor);
    }
}
</style>
