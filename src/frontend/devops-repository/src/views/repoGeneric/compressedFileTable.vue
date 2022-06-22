<template>
    <div>
        <bk-dialog v-model="previewDialog.show"
            :width="dialogWidth"
            :show-footer="false"
            :title="($t('preview') + '-' + previewDialog.title)">
            <bk-table
                v-bkloading="{ isLoading: previewDialog.isLoading }"
                :data="curData"
            >
                <bk-table-column :label="$t('fileName')" prop="name" show-overflow-tooltip>
                    <template #default="{ row }">
                        <span class="ml10">{{row.name}}</span>
                    </template>
                </bk-table-column>
                <bk-table-column :label="$t('size')" width="90" show-overflow-tooltip>
                    <template #default="{ row }">
                        <span v-if="row.size > -1">
                            {{ convertFileSize(row.size || 0) }}
                        </span>
                        <span v-else>{{ $t('unknownSize') }}</span>
                    </template>
                </bk-table-column>
                <bk-table-column :label="$t('operation')" width="70">
                    <template #default="{ row }">
                        <div v-bk-tooltips="{ disabled: row.name.endsWith('txt'), content: $t('supportPreview') }">
                            <bk-button ext-cls="preview-btn" text :disabled="!row.name.endsWith('txt')" @click="handlerPreview(row)">{{ $t('preview') }}</bk-button>
                        </div>
                    </template>
                </bk-table-column>
            </bk-table>
            <bk-pagination
                class="p10"
                size="small"
                @change="current => handlePageChange(current)"
                @limit-change="limit => handlePageLimitChange(limit)"
                :current.sync="pagination.current"
                :limit="pagination.limit"
                :count="pagination.count">
            </bk-pagination>
        </bk-dialog>
    </div>
</template>

<script>
    import { convertFileSize } from '@repository/utils'
    import { mapActions } from 'vuex'

    export default {
        name: 'previewBasicFileDialog',
        props: {
            data: {
                type: Array,
                default: () => []
            }
        },
        data () {
            return {
                curData: [],
                pagination: {
                    current: 1,
                    count: 0,
                    limit: 10
                },
                previewDialog: {
                    show: false,
                    title: '',
                    isLoading: false
                },
                dialogWidth: window.innerWidth - 400
            }
        },
        computed: {
            projectId () {
                return this.$route.params.projectId
            },
            repoName () {
                return this.$route.query.repoName
            }
        },
        watch: {
            data: {
                handler (val) {
                    this.previewDialog.isLoading = false
                    this.pagination.count = val.length
                    this.curData = this.getDataByPage(this.pagination.current)
                },
                deep: true
            }
        },
        methods: {
            ...mapActions([
                'previewBasicFile'
            ]),
            convertFileSize,
            setData (data) {
                this.previewDialog = {
                    ...data
                }
                this.curData = []
                this.pagination.count = 0
            },

            getDataByPage (page) {
                let startIndex = (page - 1) * this.pagination.limit
                let endIndex = page * this.pagination.limit
                if (startIndex < 0) {
                    startIndex = 0
                }
                if (endIndex > this.data.length) {
                    endIndex = this.data.length
                }
                this.curData = []
                return this.data.slice(startIndex, endIndex)
            },

            handlePageChange (page) {
                this.pagination.current = page
                this.curData = this.getDataByPage(page)
            },

            handlePageLimitChange (limit) {
                this.pagination.limit = limit
                this.pagination.current = 1
                this.handlePageChange(this.pagination.current)
            },
            handlerPreview (row) {
                this.$emit('show-preview', {
                    projectId: this.projectId,
                    repoName: this.repoName,
                    path: '/' + this.previewDialog.title,
                    filePath: row.name
                })
            }
        }
    }
</script>

<style lang="scss">
    .preview-btn {
        &:hover {
            color: #dcdee5 !important;
        }
    }
</style>
