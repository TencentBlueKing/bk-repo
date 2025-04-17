<template>
    <bk-dialog v-model="previewDialog.show"
        :width="dialogWidth"
        :show-footer="false"
        :title="($t('preview') + '-' + previewDialog.title)">
        <span
            v-for="(item, index) in breadcrumbList"
            :key="item.name"
            class="breadcrumb-list"
        >
            <span
                :class="{ 'breadcrumb-item': true, 'hover-cursor': index !== breadcrumbList.length - 1 }"
                @click="handleBreadcrumbItemClick(item, index)">{{ item.name }} </span>
            <span v-if="index !== breadcrumbList.length - 1" class="mr10">/</span>
        </span>
        <bk-table
            v-bkloading="{ isLoading: previewDialog.isLoading }"
            :data="curData"
            @row-dblclick="openFolder"
            style="margin-top: 10px;"
        >
            <bk-table-column :label="$t('fileName')" prop="name" show-overflow-tooltip>
                <template #default="{ row }">
                    <Icon class="table-svg" size="16" :name="row.folder ? 'folder' : getIconName(row.name)" />
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
            <bk-table-column :label="$t('operation')" width="100">
                <template #default="{ row, $index }">
                    <div v-if="!row.folder" v-bk-tooltips="{ disabled: getBtnDisabled(row.name), content: $t('supportPreview') }">
                        <bk-button class="mr10" :ext-cls="!getBtnDisabled(row.name) ? 'preview-btn' : ''" text :disabled="!getBtnDisabled(row.name)" @click="handlerPreview(row)">{{ $t('preview') }}</bk-button>
                    </div>
                    <bk-button v-else text @click="openFolder(row, null, null, $index)">{{ $t('openBtn')}}</bk-button>
                </template>
            </bk-table-column>
        </bk-table>
        <bk-pagination
            class="p10"
            size="small"
            :show-limit="false"
            @change="current => handlePageChange(current)"
            :current.sync="pagination.current"
            :count="pagination.count">
        </bk-pagination>
    </bk-dialog>
</template>

<script>
    import { convertFileSize } from '@repository/utils'
    import { mapActions } from 'vuex'
    import { getIconName } from '@repository/store/publicEnum'

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
                breadcrumbList: [],
                cacheData: [],
                curData: [],
                pagination: {
                    current: 1,
                    count: 0,
                    limit: 10
                },
                previewDialog: {
                    show: false,
                    title: '',
                    isLoading: false,
                    path: ''
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
                    this.pagination.current = 1
                    this.pagination.count = val.length
                    this.curData = this.getDataByPage(this.data, this.pagination.current)
                    this.breadcrumbList = [{
                        name: this.previewDialog.title,
                        index: -1,
                        data: this.curData
                    }]
                },
                deep: true
            }
        },
        methods: {
            ...mapActions([
                'previewBasicFile'
            ]),
            getIconName,
            convertFileSize,
            setData (data) {
                this.previewDialog = {
                    ...data
                }
                this.breadcrumbList = []
                this.curData = []
                this.pagination.count = 0
            },

            getDataByPage (list, page) {
                this.pagination.count = list.length
                let startIndex = (page - 1) * this.pagination.limit
                let endIndex = page * this.pagination.limit
                if (startIndex < 0) {
                    startIndex = 0
                }
                if (endIndex > list.length) {
                    endIndex = list.length
                }
                this.curData = []
                return list.slice(startIndex, endIndex)
            },

            handlePageChange (page) {
                this.pagination.current = page
                const list = this.breadcrumbList.length === 1 ? this.data : this.cacheData
                this.curData = this.getDataByPage(list, page)
            },

            handlerPreview (row) {
                this.$emit('show-preview', {
                    projectId: this.projectId,
                    repoName: this.repoName,
                    path: this.previewDialog.path,
                    filePath: row.filePath
                })
            },
            openFolder (row, event, column, rowIndex) {
                if (!row.folder) return
                this.cacheData = this.curData[rowIndex].children
                this.curData = this.getDataByPage(this.cacheData, this.pagination.current)
                this.breadcrumbList.push({
                    name: row.name,
                    index: rowIndex,
                    data: this.curData
                })
            },
            handleBreadcrumbItemClick (row, index) {
                this.pagination.current = 1
                if (row.index === -1) {
                    this.curData = this.getDataByPage(this.data, 1)
                    this.breadcrumbList = [{
                        name: this.previewDialog.title,
                        index: -1
                    }]
                } else {
                    this.curData = row.data
                    this.breadcrumbList = this.breadcrumbList.slice(0, index + 1)
                }
            },
            getBtnDisabled (name) {
                return name.endsWith('txt')
                    || name.endsWith('sh')
                    || name.endsWith('bat')
                    || name.endsWith('json')
                    || name.endsWith('yaml')
                    || name.endsWith('xml')
                    || name.endsWith('log')
                    || name.endsWith('ini')
                    || name.endsWith('log')
                    || name.endsWith('properties')
                    || name.endsWith('toml')
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
    .breadcrumb-list {
        .hover-cursor {
            cursor: pointer;
            &:hover {
                color: #699df4;
            }
        }
    }
</style>
