<template>
    <div class="leak-list">
        <div class="flex-align-center">
            <bk-input
                class="w250"
                v-model.trim="filter.vulId"
                clearable
                :placeholder="$t('bugSearchHolder')"
                right-icon="bk-icon icon-search"
                @enter="handlerPaginationChange()"
                @clear="handlerPaginationChange()">
            </bk-input>
            <bk-select
                class="ml10 w250"
                v-model="filter.severity"
                :placeholder="$t('vulnerabilityLevel')"
                @change="handlerPaginationChange()">
                <bk-option v-for="[id] in Object.entries(leakLevelEnum)" :key="id" :id="id" :name="$t(`leakLevelEnum.${id}`)"></bk-option>
            </bk-select>
            <div class="flex-1 flex-end-center">
                <bk-button theme="default" @click="$emit('rescan')">{{$t('rescan')}}</bk-button>
            </div>
        </div>
        <bk-table
            class="mt10 leak-table"
            height="calc(100% - 100px)"
            :data="leakList"
            :outer-border="false"
            :row-border="false"
            row-key="leakKey"
            size="small">
            <template #empty>
                <empty-data
                    :is-loading="isLoading"
                    :search="Boolean(filter.vulId || filter.severity)"
                    :title="$t('noVulnerabilityTitle')">
                </empty-data>
            </template>
            <bk-table-column type="expand" width="30">
                <template #default="{ row }">
                    <template v-if="row.path">
                        <div class="leak-title">{{ $t('vulnerabilityPathTitle') }}</div>
                        <div class="leak-tip">{{ row.path }}</div>
                    </template>
                    <div class="leak-title">{{ row.title }}</div>
                    <div class="leak-tip">{{ row.description || '/' }}</div>
                    <div class="leak-title">{{$t('fixSuggestion')}}</div>
                    <div class="leak-tip">{{ row.officialSolution || '/' }}</div>
                    <template v-if="row.reference && row.reference.length">
                        <div class="leak-title">{{ $t('relatedInfo') }}</div>
                        <div class="leak-tip" v-for="url in row.reference" :key="url">
                            <a :href="url" target="_blank">{{ url }}</a>
                        </div>
                    </template>
                </template>
            </bk-table-column>
            <bk-table-column :label="$t('vulnerability') + 'ID'" prop="vulId" show-overflow-tooltip></bk-table-column>
            <bk-table-column :label="$t('vulnerabilityLevel')">
                <template #default="{ row }">
                    <div class="status-sign" :class="row.severity" :data-name="$t(`leakLevelEnum.${row.severity}`)"></div>
                </template>
            </bk-table-column>
            <bk-table-column :label="$t('dependPackage')" prop="pkgName" show-overflow-tooltip></bk-table-column>
            <bk-table-column :label="$t('installedVersion')" prop="installedVersion" show-overflow-tooltip></bk-table-column>
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
    import { leakLevelEnum } from '@repository/store/publicEnum'
    export default {
        name: 'leak',
        props: {
            subtaskOverview: Object,
            projectId: String,
            viewType: String
        },
        data () {
            return {
                leakLevelEnum,
                isLoading: false,
                leakList: [],
                pagination: {
                    count: 0,
                    current: 1,
                    limit: 20,
                    limitList: [10, 20, 40]
                },
                filter: {
                    vulId: '',
                    severity: ''
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
            ...mapActions(['getLeakList']),
            handlerPaginationChange ({ current = 1, limit = this.pagination.limit } = {}) {
                this.pagination.current = current
                this.pagination.limit = limit
                this.getLeakListHandler()
            },
            getLeakListHandler () {
                this.isLoading = true
                return this.getLeakList({
                    projectId: this.projectId,
                    recordId: this.subtaskOverview.recordId,
                    viewType: this.viewType,
                    vulId: this.filter.vulId,
                    severity: this.filter.severity,
                    current: this.pagination.current,
                    limit: this.pagination.limit
                }).then(({ records, totalRecords }) => {
                    this.leakList = records.map(v => ({
                        ...v,
                        leakKey: `${v.vulId}${v.pkgName}${v.installedVersion}`
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
