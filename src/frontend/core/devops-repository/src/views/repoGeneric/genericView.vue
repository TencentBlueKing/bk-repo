<template>
    <div class="generic-view-container">
        <header class="generic-view-header">
            <div class="header-left">
                <Icon class="header-icon" size="24" name="generic" />
                <span class="header-title">{{ replaceRepoName(repoName) }}</span>
                <span class="header-path">{{ currentPath || '/' }}</span>
            </div>
        </header>
        <div class="generic-view-breadcrumb" v-if="pathSegments.length">
            <span class="breadcrumb-item hover-btn" @click="navigateTo('')">{{ replaceRepoName(repoName) }}</span>
            <template v-for="(seg, index) in pathSegments">
                <span :key="'sep-' + index" class="breadcrumb-sep">/</span>
                <span
                    :key="'seg-' + index"
                    class="breadcrumb-item"
                    :class="{ 'hover-btn': index < pathSegments.length - 1 }"
                    @click="index < pathSegments.length - 1 && navigateTo(pathSegments.slice(0, index + 1).join('/'))">
                    {{ seg }}
                </span>
            </template>
        </div>
        <div class="generic-view-toolbar">
            <bk-input
                class="search-input"
                v-model.trim="searchName"
                :placeholder="$t('inFolderSearchPlaceholder')"
                clearable
                right-icon="bk-icon icon-search"
                @enter="handleSearch"
                @clear="handleSearch">
            </bk-input>
        </div>
        <div class="generic-view-table" v-bkloading="{ isLoading }">
            <bk-table
                :data="fileList"
                height="calc(100% - 50px)"
                :outer-border="false"
                :row-border="false"
                size="small"
                @sort-change="handleSortChange"
                @row-dblclick="openFolder">
                <template #empty>
                    <empty-data :is-loading="isLoading" :search="Boolean(searchName)"></empty-data>
                </template>
                <bk-table-column :label="$t('fileName')" prop="name" show-overflow-tooltip>
                    <template #default="{ row }">
                        <div class="file-name-cell">
                            <Icon class="table-svg mr5" size="16" :name="row.folder ? 'folder' : getIconName(row.name)" />
                            <span :class="{ 'hover-btn': row.folder }">{{ row.name }}</span>
                        </div>
                    </template>
                </bk-table-column>
                <bk-table-column :label="$t('size')" prop="size" width="120" sortable="custom" show-overflow-tooltip>
                    <template #default="{ row }">
                        {{ row.folder ? '--' : convertFileSize(row.size > 0 ? row.size : 0) }}
                    </template>
                </bk-table-column>
                <bk-table-column :label="$t('lastModifiedDate')" prop="lastModifiedDate" width="200" sortable="custom">
                    <template #default="{ row }">{{ formatDate(row.lastModifiedDate) }}</template>
                </bk-table-column>
                <bk-table-column :label="$t('operation')" width="100" fixed="right">
                    <template #default="{ row }">
                        <bk-button
                            v-if="!row.folder"
                            text
                            theme="primary"
                            @click="handleDownload(row)">
                            {{ $t('download') }}
                        </bk-button>
                        <bk-button
                            v-if="row.folder"
                            text
                            theme="primary"
                            @click="openFolder(row)">
                            {{ $t('open') }}
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
<script>
    import { getIconName } from '@repository/store/publicEnum'
    import { convertFileSize, formatDate } from '@repository/utils'
    import { mapActions } from 'vuex'

    export default {
        name: 'GenericView',
        data () {
            return {
                isLoading: false,
                fileList: [],
                searchName: '',
                currentPath: '',
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
            path () {
                return this.$route.query.path || ''
            },
            pathSegments () {
                return this.currentPath.split('/').filter(Boolean)
            }
        },
        watch: {
            '$route.query' () {
                this.currentPath = this.path
                this.pagination.current = 1
                this.searchName = ''
                this.loadFiles()
            }
        },
        created () {
            this.currentPath = this.path
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
                        ...(this.searchName ? { name: this.searchName } : {}),
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
                } catch (e) {
                    this.$bkMessage({
                        theme: 'error',
                        message: e.message || this.$t('fileError')
                    })
                } finally {
                    this.isLoading = false
                }
            },
            openFolder (row) {
                if (!row.folder) return
                this.$router.replace({
                    query: {
                        ...this.$route.query,
                        path: row.fullPath
                    }
                })
            },
            navigateTo (path) {
                if (path === this.currentPath) return
                this.$router.replace({
                    query: {
                        ...this.$route.query,
                        path: path ? '/' + path : ''
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
            handleSearch () {
                this.pagination.current = 1
                this.loadFiles()
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
    background-color: #f5f7fa;
    .generic-view-header {
        height: 56px;
        display: flex;
        align-items: center;
        padding: 0 24px;
        background-color: #fff;
        border-bottom: 1px solid #dcdee5;
        .header-left {
            display: flex;
            align-items: center;
            .header-icon {
                border-radius: 4px;
            }
            .header-title {
                margin-left: 10px;
                font-size: 16px;
                font-weight: 600;
                color: #313238;
            }
            .header-path {
                margin-left: 12px;
                font-size: 12px;
                color: #979ba5;
            }
        }
    }
    .generic-view-breadcrumb {
        padding: 12px 24px;
        background-color: #fff;
        font-size: 13px;
        color: #63656e;
        border-bottom: 1px solid #eaebf0;
        .breadcrumb-item {
            &.hover-btn {
                color: #3a84ff;
                cursor: pointer;
                &:hover {
                    text-decoration: underline;
                }
            }
        }
        .breadcrumb-sep {
            margin: 0 4px;
            color: #c4c6cc;
        }
    }
    .generic-view-toolbar {
        padding: 12px 24px;
        background-color: #fff;
        .search-input {
            width: 320px;
        }
    }
    .generic-view-table {
        flex: 1;
        margin: 0 24px 24px;
        padding: 16px;
        background-color: #fff;
        border-radius: 2px;
        overflow: hidden;
        .file-name-cell {
            display: flex;
            align-items: center;
        }
        .pagination-bar {
            display: flex;
            align-items: center;
            justify-content: flex-end;
            padding: 10px 0;
            .page-info {
                margin: 0 10px;
                font-size: 12px;
                color: #63656e;
            }
        }
    }
}
</style>
