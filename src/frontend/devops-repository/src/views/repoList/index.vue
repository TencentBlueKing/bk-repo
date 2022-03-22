<template>
    <div class="repo-list-container" v-bkloading="{ isLoading }">
        <div class="ml20 mr20 mt10 flex-between-center">
            <bk-button icon="plus" theme="primary" @click="createRepo"><span class="mr5">{{ $t('create') }}</span></bk-button>
            <div class="flex-align-center">
                <bk-input
                    v-model.trim="query.name"
                    class="w250"
                    placeholder="请输入仓库名称, 按Enter键搜索"
                    clearable
                    @enter="handlerPaginationChange()"
                    @clear="handlerPaginationChange()"
                    right-icon="bk-icon icon-search">
                </bk-input>
                <bk-select
                    v-model="query.type"
                    class="ml10 w250"
                    @change="handlerPaginationChange()"
                    :placeholder="$t('allTypes')">
                    <bk-option v-for="type in repoEnum" :key="type" :id="type" :name="type">
                        <div class="flex-align-center">
                            <Icon size="20" :name="type" />
                            <span class="ml10 flex-1 text-overflow">{{type}}</span>
                        </div>
                    </bk-option>
                </bk-select>
            </div>
        </div>
        <bk-table
            class="mt10"
            :data="repoList"
            height="calc(100% - 102px)"
            :outer-border="false"
            :row-border="false"
            size="small"
            @row-click="toPackageList">
            <template #empty>
                <empty-data
                    :is-loading="isLoading"
                    :search="Boolean(query.name || query.type)"
                    :config="{
                        imgSrc: '/ui/no-repo.png',
                        title: '暂无仓库数据'
                    }">
                </empty-data>
            </template>
            <bk-table-column :label="$t('repoName')">
                <template #default="{ row }">
                    <div class="flex-align-center" :title="replaceRepoName(row.name)">
                        <Icon size="20" :name="row.repoType" />
                        <span class="ml10 text-overflow hover-btn" style="max-width:400px">{{replaceRepoName(row.name)}}</span>
                        <span v-if="MODE_CONFIG === 'ci' && ['custom', 'pipeline'].includes(row.name)"
                            class="ml10 repo-tag SUCCESS">内置</span>
                        <span v-if="row.configuration.settings.system"
                            class="ml10 repo-tag">系统</span>
                        <span v-if="row.public"
                            class="ml10 repo-tag WARNING">公开</span>
                    </div>
                </template>
            </bk-table-column>
            <bk-table-column :label="$t('repoQuota')" width="250">
                <template #default="{ row }">
                    <div class="repoQuota" v-if="row.quota">
                        <bk-popover placement="top">
                            <bk-progress size="large" :percent="((row.used || 0) / (row.quota || 1))" :show-text="false"></bk-progress>
                            <div slot="content">
                                <div>{{ $t('totalQuota') }}: {{ convertBytesToGb(row.quota) }}GB</div>
                                <div>{{ $t('usedQuotaCapacity') }}: {{ convertBytesToGb(row.used) }}GB</div>
                            </div>
                        </bk-popover>
                    </div>
                    <div v-else>
                        --
                    </div>
                </template>
            </bk-table-column>
            <bk-table-column :label="$t('createdDate')" width="250">
                <template #default="{ row }">
                    {{ formatDate(row.createdDate) }}
                </template>
            </bk-table-column>
            <bk-table-column :label="$t('createdBy')" width="200">
                <template #default="{ row }">
                    {{ userList[row.createdBy] ? userList[row.createdBy].name : row.createdBy }}
                </template>
            </bk-table-column>
            <bk-table-column :label="$t('operation')" width="70">
                <template #default="{ row }">
                    <operation-list
                        :list="[
                            { label: '设置', clickEvent: () => toRepoConfig(row) },
                            row.repoType !== 'generic' && { label: $t('delete'), clickEvent: () => deleteRepo(row) }
                        ].filter(Boolean)">
                    </operation-list>
                </template>
            </bk-table-column>
        </bk-table>
        <bk-pagination
            class="p10"
            size="small"
            align="right"
            show-total-count
            :current.sync="pagination.current"
            :limit="pagination.limit"
            :count="pagination.count"
            :limit-list="pagination.limitList"
            @change="current => handlerPaginationChange({ current })"
            @limit-change="limit => handlerPaginationChange({ limit })">
        </bk-pagination>
        <create-repo-dialog ref="createRepo" @refresh="handlerPaginationChange()"></create-repo-dialog>
    </div>
</template>
<script>
    import OperationList from '@repository/components/OperationList'
    import createRepoDialog from '@repository/views/repoList/createRepoDialog'
    import { mapState, mapActions } from 'vuex'
    import { repoEnum } from '@repository/store/publicEnum'
    import { formatDate } from '@repository/utils'
    export default {
        name: 'repoList',
        components: { OperationList, createRepoDialog },
        data () {
            return {
                MODE_CONFIG,
                repoEnum,
                isLoading: false,
                repoList: [],
                query: {
                    name: this.$route.query.name,
                    type: this.$route.query.type
                },
                value: 20,
                pagination: {
                    count: 0,
                    current: 1,
                    limit: 20,
                    limitList: [10, 20, 40]
                }
            }
        },
        computed: {
            ...mapState(['userList']),
            projectId () {
                return this.$route.params.projectId
            }
        },
        watch: {
            projectId () {
                this.handlerPaginationChange()
            }
        },
        created () {
            this.handlerPaginationChange()
        },
        methods: {
            formatDate,
            ...mapActions([
                'getRepoList',
                'deleteRepoList'
            ]),
            convertBytesToGb (bytes, decimals = 2) {
                if (bytes === 0) return 0
                return parseFloat((bytes / Math.pow(1024, 3)).toFixed(decimals))
            },
            getListData () {
                this.isLoading = true
                this.getRepoList({
                    projectId: this.projectId,
                    ...this.pagination,
                    ...this.query
                }).then(({ records, totalRecords }) => {
                    this.repoList = records.map(v => ({ ...v, repoType: v.type.toLowerCase() }))
                    this.pagination.count = totalRecords
                }).finally(() => {
                    this.isLoading = false
                })
            },
            handlerPaginationChange ({ current = 1, limit = this.pagination.limit } = {}) {
                this.pagination.current = current
                this.pagination.limit = limit
                this.$router.replace({
                    query: this.query
                })
                this.getListData()
            },
            createRepo () {
                this.$refs.createRepo.showDialogHandler()
            },
            toPackageList ({ projectId, repoType, name }) {
                this.$router.push({
                    name: repoType === 'generic' ? 'repoGeneric' : 'commonList',
                    params: {
                        projectId,
                        repoType
                    },
                    query: {
                        repoName: name
                    }
                })
            },
            toRepoConfig ({ repoType, name }) {
                this.$router.push({
                    name: 'repoConfig',
                    params: {
                        ...this.$route.params,
                        repoType
                    },
                    query: {
                        repoName: name
                    }
                })
            },
            deleteRepo ({ name }) {
                this.$confirm({
                    theme: 'danger',
                    message: this.$t('deleteRepoTitle', { name }),
                    confirmFn: () => {
                        return this.deleteRepoList({
                            projectId: this.projectId,
                            name
                        }).then(() => {
                            this.getListData()
                            this.$bkMessage({
                                theme: 'success',
                                message: this.$t('delete') + this.$t('success')
                            })
                        })
                    }
                })
            }
        }
    }
</script>
<style lang="scss" scoped>
.repoQuota {
    width: 80%;
    ::v-deep .bk-tooltip,
    ::v-deep .bk-tooltip-ref {
        display: block;
    }
}
.quota-slider {
    ::v-deep .bk-slider-bar {
        background-color: #3a84ff;
    }
}
.repo-list-container {
    height: 100%;
    background-color: white;
    ::v-deep .bk-table td,
    ::v-deep .bk-table th {
        height: 44px;
    }
}
</style>
