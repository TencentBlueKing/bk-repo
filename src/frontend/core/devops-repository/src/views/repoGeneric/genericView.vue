<template>
    <div class="generic-view-container">
        <!-- 单文件详情视图 -->
        <template v-if="singleFile">
            <div class="top-section">
                <div class="top-left">
                    <bk-button class="back-btn" text @click="goBackToList">
                        <i class="bk-icon icon-angle-left"></i>
                    </bk-button>
                    <div class="file-info">
                        <Icon class="file-icon" size="28" :name="getIconName(singleFile.name)" />
                        <div class="file-details">
                            <div class="file-name">{{ singleFile.name }}</div>
                            <div class="file-meta">
                                <span>{{ $t('size') }}: {{ convertFileSize(singleFile.size > 0 ? singleFile.size : 0) }}</span>
                            </div>
                        </div>
                    </div>
                </div>
                <bk-button class="download-btn white-bg" @click="handleDownload(singleFile)">{{ $t('download') }}</bk-button>
            </div>
            <div class="bottom-section">
                <div class="file-preview">
                    <Icon class="file-icon" size="120" :name="getIconName(singleFile.name)" />
                    <div class="file-details">
                        <div class="file-name">{{ singleFile.name }}</div>
                        <div class="file-size">{{ convertFileSize(singleFile.size > 0 ? singleFile.size : 0) }}</div>
                        <bk-button class="download-btn" @click="handleDownload(singleFile)">{{ $t('download') }}</bk-button>
                    </div>
                </div>
            </div>
        </template>
        <!-- 文件列表视图 -->
        <template v-else>
            <header class="generic-view-header">
                <div class="header-left">
                    <Icon class="header-icon" size="32" name="folder" />
                    <div class="header-info">
                        <span class="header-title">{{ rootName }}</span>
                    </div>
                </div>
            </header>
            <div v-if="invalidPath" class="generic-view-invalid">
                <i class="bk-icon icon-close-circle-shape"></i>
                <span>{{ $t('illegalPath') || '非法路径' }}</span>
            </div>
            <template v-else>
            <div class="generic-view-body">
                <div class="generic-view-card" v-bkloading="{ isLoading }">
                    <div class="card-header">
                        <div class="card-breadcrumb">
                            <span
                                class="breadcrumb-item"
                                :class="{ 'hover-btn': relativeBreadcrumbs.length > 0 }"
                                @click="relativeBreadcrumbs.length > 0 && navigateTo(rootPath)">
                                {{ rootName }}
                            </span>
                            <template v-for="(seg, index) in relativeBreadcrumbs">
                                <span :key="'sep-' + index" class="breadcrumb-sep">/</span>
                                <span
                                    :key="'seg-' + index"
                                    class="breadcrumb-item"
                                    :class="{ 'hover-btn': index < relativeBreadcrumbs.length - 1 }"
                                    @click="index < relativeBreadcrumbs.length - 1 && navigateTo(rootPath + '/' + relativeBreadcrumbs.slice(0, index + 1).join('/'))">
                                    {{ seg }}
                                </span>
                            </template>
                        </div>
                    </div>
                    <bk-table
                        :data="fileList"
                        :outer-border="false"
                        :row-border="false"
                        size="small"
                        @sort-change="handleSortChange"
                        @row-dblclick="handleRowDblclick">
                        <template #empty>
                            <empty-data :is-loading="isLoading"></empty-data>
                        </template>
                        <bk-table-column :label="$t('fileName')" prop="name" show-overflow-tooltip>
                            <template #default="{ row }">
                                <div class="file-name-cell">
                                    <Icon class="table-svg mr5" size="16" :name="row.folder ? 'folder' : getIconName(row.name)" />
                                    <span :class="{ 'hover-btn': row.folder }">{{ row.name }}</span>
                                </div>
                            </template>
                        </bk-table-column>
                        <bk-table-column :label="$t('lastModifiedDate')" prop="lastModifiedDate" width="200" sortable="custom">
                            <template #default="{ row }">{{ formatDate(row.lastModifiedDate) }}</template>
                        </bk-table-column>
                        <bk-table-column :label="$t('size')" prop="size" width="120" sortable="custom" show-overflow-tooltip>
                            <template #default="{ row }">
                                {{ row.folder ? '-' : convertFileSize(row.size > 0 ? row.size : 0) }}
                            </template>
                        </bk-table-column>
                        <bk-table-column :label="$t('operation')" width="80">
                            <template #default="{ row }">
                                <bk-button
                                    v-if="!row.folder"
                                    text
                                    theme="primary"
                                    @click="handleDownload(row)">
                                    {{ $t('download') }}
                                </bk-button>
                            </template>
                        </bk-table-column>
                    </bk-table>
                    <div class="pagination-bar">
                        <bk-button
                            :disabled="pagination.current === 1"
                            size="small"
                            icon="icon-angle-left"
                            @click="changePage(-1)">
                        </bk-button>
                        <span class="page-info">{{ pagination.current }}</span>
                        <bk-button
                            :disabled="fileList.length === 0 || fileList.length < pagination.limit"
                            size="small"
                            icon="icon-angle-right"
                            @click="changePage(1)">
                        </bk-button>
                    </div>
                </div>
            </div>
            </template>
        </template>
    </div>
</template>
<script>
    import { getIconName } from '@repository/store/publicEnum'
    import { convertFileSize, formatDate } from '@repository/utils'
    import { mapActions } from 'vuex'

    export default {
        name: 'GenericView',
        data () {
            return {
                isLoading: false,
                invalidPath: false,
                fileList: [],
                rootPath: this.$route.query.path || '',
                currentPath: '',
                singleFile: null,
                sortParams: [],
                pagination: {
                    current: 1,
                    limit: 20
                }
            }
        },
        computed: {
            projectId () {
                return this.$route.params.projectId
            },
            repoName () {
                return this.$route.query.repoName
            },
            rootName () {
                const segments = this.rootPath.split('/').filter(Boolean)
                return segments.length > 0 ? segments[segments.length - 1] : this.replaceRepoName(this.repoName)
            },
            relativeBreadcrumbs () {
                if (!this.currentPath || this.currentPath === this.rootPath) return []
                const relative = this.currentPath.slice(this.rootPath.length).replace(/^\//, '')
                return relative.split('/').filter(Boolean)
            }
        },
        watch: {
            '$route.query.path' (newVal) {
                const newPath = newVal || this.rootPath
                if (!newPath.startsWith(this.rootPath)) return
                this.currentPath = newPath
                this.singleFile = null
                this.pagination.current = 1
                this.loadFiles()
            }
        },
        created () {
            if (this.repoName === 'lsync' && !/^\/Personal\/[^/]+/.test(this.rootPath)) {
                this.invalidPath = true
                this.$bkMessage({
                    theme: 'error',
                    message: this.$t('illegalPath') || '非法路径'
                })
                return
            }
            this.currentPath = this.rootPath
            this.loadFiles()
        },
        methods: {
            convertFileSize,
            getIconName,
            formatDate,
            ...mapActions([
                'getArtifactoryList'
            ]),
            async loadFiles () {
                this.isLoading = true
                try {
                    let sortType = {
                        properties: ['folder', 'lastModifiedDate'],
                        direction: 'DESC'
                    }
                    if (this.sortParams.length > 0) {
                        sortType = {
                            properties: ['folder', this.sortParams[0].properties],
                            direction: this.sortParams[0].direction
                        }
                    }
                    const { records, totalRecords } = await this.getArtifactoryList({
                        projectId: this.projectId,
                        repoName: this.repoName,
                        fullPath: this.currentPath || '',
                        current: this.pagination.current,
                        limit: this.pagination.limit,
                        sortType,
                        isPipeline: false,
                        searchFlag: false,
                        localRepo: true
                    })
                    this.fileList = records.map(v => ({
                        metadata: {},
                        ...v,
                        name: v.metadata?.displayName || v.name
                    }))
                    // 如果只有一个文件（非文件夹），自动进入单文件视图
                    if (this.fileList.length === 1 && !this.fileList[0].folder) {
                        this.singleFile = this.fileList[0]
                    }
                } catch (e) {
                    this.$bkMessage({
                        theme: 'error',
                        message: e.message || this.$t('fileError')
                    })
                } finally {
                    this.isLoading = false
                }
            },
            handleRowDblclick (row) {
                if (row.folder) {
                    this.$router.replace({
                        query: {
                            ...this.$route.query,
                            path: row.fullPath
                        }
                    })
                } else {
                    this.singleFile = row
                }
            },
            goBackToList () {
                this.singleFile = null
            },
            navigateTo (path) {
                if (path === this.currentPath) return
                this.$router.replace({
                    query: {
                        ...this.$route.query,
                        path: path || this.rootPath
                    }
                })
            },
            handleDownload (row) {
                const transPath = encodeURIComponent(row.fullPath)
                const url = `/generic/${this.projectId}/${this.repoName}/${transPath}?download=true`
                window.open(
                    window.BK_SUBPATH + 'web' + url + `&x-bkrepo-project-id=${this.projectId}`,
                    '_self'
                )
            },
            handleSortChange (sort) {
                this.sortParams = []
                if (sort.prop) {
                    this.sortParams.push({
                        properties: sort.prop,
                        direction: sort.order === 'ascending' ? 'ASC' : 'DESC'
                    })
                }
                this.loadFiles()
            },
            changePage (inc) {
                if (this.isLoading) return
                const newPage = this.pagination.current + inc
                if (newPage < 1) return
                if (inc > 0 && (this.fileList.length === 0 || this.fileList.length < this.pagination.limit)) return
                this.pagination.current = newPage
                this.loadFiles()
            }
        }
    }
</script>
<style lang="scss" scoped>
.generic-view-container {
    height: 100%;
    display: flex;
    flex-direction: column;
    background-color: #f0f1f5;

    // 单文件详情视图样式（与 share 页面一致）
    .top-section {
        display: flex;
        justify-content: space-between;
        align-items: center;
        height: 60px;
        padding: 0 20px;
        background-color: #f5f5f5;
        border-bottom: 1px solid #ddd;
        .top-left {
            display: flex;
            align-items: center;
            .back-btn {
                font-size: 24px;
                color: #63656e;
                margin-right: 8px;
                cursor: pointer;
                &:hover {
                    color: #3a84ff;
                }
            }
        }
        .file-info {
            display: flex;
            align-items: center;
            .file-icon {
                margin-right: 10px;
            }
            .file-details {
                display: flex;
                flex-direction: column;
                .file-name {
                    font-weight: bold;
                }
                .file-meta span {
                    margin-right: 10px;
                }
            }
        }
        .download-btn.white-bg {
            background-color: white;
            color: #007bff;
            border: 1px solid #007bff;
        }
    }
    .bottom-section {
        flex: 1;
        display: flex;
        justify-content: center;
        align-items: center;
        background-color: #fff;
        .file-preview {
            display: flex;
            align-items: center;
            .file-icon {
                margin-right: 20px;
            }
            .file-details {
                display: flex;
                flex-direction: column;
                align-items: flex-start;
                .file-name {
                    font-size: 1.2em;
                    font-weight: bold;
                }
                .file-size {
                    margin-bottom: 10px;
                }
                .download-btn {
                    background-color: #007bff;
                    color: white;
                    border: none;
                    padding: 5px 10px;
                    cursor: pointer;
                    border-radius: 4px;
                }
            }
        }
    }

    // 文件列表视图样式
    .generic-view-header {
        height: 56px;
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 0 24px;
        background-color: #fff;
        border-bottom: 1px solid #dcdee5;
        .header-left {
            display: flex;
            align-items: center;
            .header-icon {
                color: #3a84ff;
            }
            .header-info {
                margin-left: 10px;
                .header-title {
                    font-size: 16px;
                    font-weight: 600;
                    color: #313238;
                }
            }
        }
    }
    .generic-view-invalid {
        flex: 1;
        display: flex;
        align-items: center;
        justify-content: center;
        flex-direction: column;
        color: #63656e;
        font-size: 14px;
        .icon-close-circle-shape {
            font-size: 42px;
            color: #ea3636;
            margin-bottom: 12px;
        }
    }
    .generic-view-body {
        flex: 1;
        display: flex;
        justify-content: center;
        padding: 24px;
        overflow: auto;
        .generic-view-card {
            width: 100%;
            max-width: 800px;
            background-color: #fff;
            border-radius: 4px;
            box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
            display: flex;
            flex-direction: column;
            .card-header {
                display: flex;
                align-items: center;
                justify-content: space-between;
                padding: 16px 20px;
                border-bottom: 1px solid #eaebf0;
                .card-breadcrumb {
                    font-size: 16px;
                    font-weight: 600;
                    color: #313238;
                    .breadcrumb-item {
                        &.hover-btn {
                            color: #3a84ff;
                            cursor: pointer;
                            font-weight: 400;
                            &:hover {
                                text-decoration: underline;
                            }
                        }
                    }
                    .breadcrumb-sep {
                        margin: 0 4px;
                        color: #c4c6cc;
                        font-weight: 400;
                    }
                }
            }
            .file-name-cell {
                display: flex;
                align-items: center;
            }
            .pagination-bar {
                display: flex;
                align-items: center;
                justify-content: flex-end;
                padding: 10px 20px;
                .page-info {
                    margin: 0 10px;
                    font-size: 12px;
                    color: #63656e;
                }
            }
        }
    }
}
</style>
