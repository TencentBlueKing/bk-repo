<template>
    <div class="leak-list">
        <div class="flex-align-center">
            <bk-input
                class="w250"
                v-model.trim="sensitiveContent"
                clearable
                placeholder="请输入敏感信息内容, 按Enter键搜索"
                right-icon="bk-icon icon-search"
                @enter="handlerPaginationChange()"
                @clear="handlerPaginationChange()">
            </bk-input>
            <bk-input
                class="ml10 w250"
                v-model.trim="sensitiveType"
                clearable
                placeholder="请输入敏感信息类型, 按Enter键搜索"
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
            :data="sensitiveList"
            :outer-border="false"
            :row-border="false"
            size="small">
            <template #empty>
                <empty-data :is-loading="isLoading" title="未扫描到敏感信息">
                </empty-data>
            </template>
            <bk-table-column type="expand" width="30">
                <template #default="{ row }">
                    <template v-if="row.path">
                        <div class="leak-title">存在敏感信息的文件路径</div>
                        <div class="leak-tip">{{ row.path }}</div>
                    </template>
                    <template v-if="row.content">
                        <div class="leak-title">敏感信息内容</div>
                        <div class="leak-tip">{{ row.content }}</div>
                    </template>
                </template>
            </bk-table-column>
            <bk-table-column label="类型" prop="type" show-overflow-tooltip></bk-table-column>
            <bk-table-column label="路径" prop="path" show-overflow-tooltip></bk-table-column>
            <bk-table-column label="内容" prop="content" show-overflow-tooltip></bk-table-column>
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
        name: 'sensitive',
        props: {
            subtaskOverview: Object,
            projectId: String,
            viewType: String
        },
        data () {
            return {
                isLoading: false,
                sensitiveList: [],
                pagination: {
                    count: 0,
                    current: 1,
                    limit: 20,
                    limitList: [10, 20, 40]
                },
                sensitiveType: null,
                sensitiveContent: null
            }
        },
        watch: {
            subtaskOverview () {
                this.handlerPaginationChange()
            }
        },
        created () {
            if (this.subtaskOverview && this.subtaskOverview.repoName) {
                this.handlerPaginationChange()
            }
        },
        methods: {
            ...mapActions(['getReports']),
            handlerPaginationChange ({ current = 1, limit = this.pagination.limit } = {}) {
                this.pagination.current = current
                this.pagination.limit = limit
                this.getSensitive()
            },
            getSensitive () {
                this.isLoading = true
                return this.getReports({
                    projectId: this.projectId,
                    repoName: this.subtaskOverview.repoName,
                    fullPath: this.subtaskOverview.fullPath,
                    body: {
                        scanner: this.subtaskOverview.scanner,
                        arguments: {
                            type: this.subtaskOverview.scannerType,
                            reportType: 'SENSITIVE',
                            sensitiveType: this.sensitiveType,
                            sensitiveContent: this.sensitiveContent,
                            pageLimit: {
                                pageNumber: this.pagination.current,
                                pageSize: this.pagination.limit
                            }
                        }
                    }
                }).then(({ detail }) => {
                    this.sensitiveList = detail.records
                    this.pagination.count = detail.totalRecords
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
