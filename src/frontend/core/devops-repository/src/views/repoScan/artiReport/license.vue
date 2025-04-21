<template>
    <div class="leak-list">
        <div class="flex-align-center">
            <bk-input
                class="w250"
                v-model.trim="filter.licenseId"
                clearable
                :placeholder="$t('licencePlaceHolder')"
                right-icon="bk-icon icon-search"
                @enter="handlerPaginationChange()"
                @clear="handlerPaginationChange()">
            </bk-input>
            <div class="flex-1 flex-end-center">
                <bk-button theme="default" @click="$emit('rescan')">{{$t('rescan')}}</bk-button>
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
                    :title="$t('noCrtTitle')">
                </empty-data>
            </template>
            <bk-table-column type="expand" width="40">
                <template #default="{ row }">
                    <div class="leak-title">{{$t('licenceInfo')}}</div>
                    <div class="leak-tip">
                        <a :href="row.description" target="_blank">{{ row.description || '/' }}</a>
                    </div>
                </template>
            </bk-table-column>
            <bk-table-column :label="$t('name')" width="200px">
                <template #default="{ row }">
                    <span v-bk-tooltips="{ content: row.fullName, placements: ['top'] }">{{ row.licenseId }}</span>
                </template>
            </bk-table-column>
            <bk-table-column :label="$t('dependPath')" prop="dependentPath"></bk-table-column>
            <!--            <bk-table-column :label="`OSI` + $t('authenticated')" width="120">-->
            <!--                <template #default="{ row }">{{ row.description ? `${row.isOsiApproved ? $t('authenticated') : $t('notAuthenticated')}` : '/' }}</template>-->
            <!--            </bk-table-column>-->
            <!--            <bk-table-column :label="`FSF` + $t('openSource')" width="120">-->
            <!--                <template #default="{ row }">{{ row.description ? `${row.isFsfLibre ? $t('openSource') : $t('notOpenSource')}` : '/' }}</template>-->
            <!--            </bk-table-column>-->
            <!--            <bk-table-column :label="$t('recommendUse')" width="120">-->
            <!--                <template #default="{ row }">{{ row.description ? `${row.recommended ? $t('recommended') : $t('notRecommended')}` : '/' }}</template>-->
            <!--            </bk-table-column>-->
            <!--            <bk-table-column :label="$t('compliance')" width="120">-->
            <!--                <template #default="{ row }">-->
            <!--                    <span v-if="row.description" class="repo-tag" :class="row.compliance ? 'SUCCESS' : 'FAILED'">{{ `${row.compliance ? $t('compliance') : $t('notCompliance')}` }}</span>-->
            <!--                    <span v-else>/</span>-->
            <!--                </template>-->
            <!--            </bk-table-column>-->
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
