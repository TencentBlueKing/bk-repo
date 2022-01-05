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
            height="calc(100% - 104px)"
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
                        <bk-tag v-if="MODE_CONFIG === 'ci' && (row.name === 'custom' || row.name === 'pipeline')"
                            class="ml10" type="filled" style="background-color: var(--successColor);">内置</bk-tag>
                        <bk-tag v-if="row.configuration.settings.system"
                            class="ml10" type="filled" style="background-color: var(--primaryHoverColor);">系统</bk-tag>
                        <bk-tag v-if="row.public"
                            class="ml10" type="filled" style="background-color: var(--warningColor);">公开</bk-tag>
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
.repo-list-container {
    height: 100%;
    background-color: white;
    ::v-deep .bk-table td,
    ::v-deep .bk-table th {
        height: 44px;
    }
}
</style>
