<template>
    <div class="repo-npm-detail">
        <div class="mb20 flex-align-center">
            <bk-input
                class="npm-version-search"
                v-model="versionNameInput"
                :placeholder="$t('pleaseInput')"
                clearable>
            </bk-input>
        </div>
        <bk-table
            class="npm-version-table"
            height="calc(100% - 34px)"
            :data="filterVersionList"
            :outer-border="false"
            :row-border="false"
            size="small"
            @row-click="toNpmPkgVersionDetail"
            :pagination="pagination"
            @page-change="current => handlerPaginationChange({ current })"
            @page-limit-change="limit => handlerPaginationChange({ limit })"
        >
            <bk-table-column :label="$t('name')" prop="version"></bk-table-column>
            <bk-table-column :label="$t('artiStatus')">
                <template v-if="props.row.status" slot-scope="props">
                    <span class="mr5 repo-generic-version" v-for="version in props.row.status.split(',')"
                        :key="props.row.version + version">{{ version }}</span>
                </template>
            </bk-table-column>
            <bk-table-column :label="$t('size')">
                <template slot-scope="props">
                    {{ convertFileSize(props.row.size) }}
                </template>
            </bk-table-column>
            <bk-table-column :label="$t('downloadCount')" prop="downloadCount"></bk-table-column>
            <bk-table-column :label="$t('lastModifiedBy')" prop="lastModifiedBy"></bk-table-column>
            <bk-table-column :label="$t('lastModifiedDate')">
                <template slot-scope="props">
                    {{ new Date(props.row.lastModifiedDate).toLocaleString() }}
                </template>
            </bk-table-column>
            <bk-table-column :label="$t('operation')" width="150">
                <template slot-scope="props">
                    <!-- <i class="devops-icon icon-arrows-up mr20" @click="showRepoConfig(props.row)"></i> -->
                    <i class="devops-icon icon-delete hover-btn" @click.stop="deleteVersion(props.row)"></i>
                </template>
            </bk-table-column>
        </bk-table>
    </div>
</template>
<script>
    import { convertFileSize } from '@/utils'
    export default {
        name: 'npmDetail',
        props: {
            versionList: {
                type: Array,
                default: []
            }
        },
        data () {
            return {
                convertFileSize,
                versionNameInput: '',
                pagination: {
                    count: this.versionList.length,
                    current: 1,
                    limit: 10,
                    'limit-list': [10, 20, 40]
                }
            }
        },
        computed: {
            filterVersionList () {
                const start = (this.pagination.current - 1) * this.pagination.limit
                this.pagination.count = this.versionList.length
                return this.versionList
                    .filter(v => v.version.includes(this.versionNameInput))
                    .slice(start, this.pagination.limit)
            }
        },
        watch: {
            versionList (list) {
                if (list.length < (this.pagination.current - 1) * this.pagination.limit) {
                    this.pagination.current--
                }
            }
        },
        methods: {
            handlerPaginationChange ({ current = 1, limit = this.pagination.limit } = {}) {
                this.pagination.current = current
                this.pagination.limit = limit
            },
            toNpmPkgVersionDetail (row) {
                this.$emit('show-version-detail', row.version)
            },
            deleteTag (row) {
                this.$emit('delete-version', row.version)
            }
        }
    }
</script>
<style lang="scss" scoped>
.repo-npm-detail {
    height: 100%;
    .npm-version-search {
        width: 250px;
    }
}
</style>
