<template>
    <div class="leak-list">
        <div class="flex-align-center">
            <bk-input
                class="w250"
                v-model.trim="filter.vulId"
                clearable
                placeholder="请输入漏洞ID, 按Enter键搜索"
                right-icon="bk-icon icon-search"
                @enter="handlerPaginationChange()"
                @clear="handlerPaginationChange()">
            </bk-input>
            <bk-select
                class="ml10 w250"
                v-model="filter.severity"
                placeholder="漏洞等级"
                @change="handlerPaginationChange()">
                <bk-option v-for="[id, name] in Object.entries(leakLevelEnum)" :key="id" :id="id" :name="name"></bk-option>
            </bk-select>
            <div class="flex-1 flex-end-center">
                <bk-button theme="default" @click="$emit('rescan')">重新扫描</bk-button>
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
                    title="未扫描到漏洞">
                </empty-data>
            </template>
            <bk-table-column type="expand" width="30">
                <template #default="{ row }">
                    <template v-if="row.path">
                        <div class="leak-title">存在漏洞的文件路径</div>
                        <div class="leak-tip">{{ row.path }}</div>
                    </template>
                    <div class="leak-title">{{ row.title }}</div>
                    <div class="leak-tip">{{ row.description || '/' }}</div>
                    <div class="leak-title">修复建议</div>
                    <div class="leak-tip">{{ row.officialSolution || '/' }}</div>
                    <template v-if="row.reference && row.reference.length">
                        <div class="leak-title">相关资料</div>
                        <div class="leak-tip" v-for="url in row.reference" :key="url">
                            <a :href="url" target="_blank">{{ url }}</a>
                        </div>
                    </template>
                </template>
            </bk-table-column>
            <bk-table-column label="漏洞ID" prop="vulId" show-overflow-tooltip></bk-table-column>
            <bk-table-column label="漏洞等级">
                <template #default="{ row }">
                    <div class="status-sign" :class="row.severity" :data-name="leakLevelEnum[row.severity]"></div>
                </template>
            </bk-table-column>
            <bk-table-column label="所属依赖" prop="pkgName" show-overflow-tooltip></bk-table-column>
            <bk-table-column label="引入版本" prop="installedVersion" show-overflow-tooltip></bk-table-column>
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
