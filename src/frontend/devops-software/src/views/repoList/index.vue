<template>
    <div class="repo-list-container" v-bkloading="{ isLoading }">
        <div class="ml20 mr20 mt10 flex-align-center">
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
            <bk-select
                v-model="query.projectId"
                class="ml10 w250"
                searchable
                placeholder="请选择项目"
                @change="handlerPaginationChange()"
                :enable-virtual-scroll="projectList && projectList.length > 3000"
                :list="projectList">
                <bk-option v-for="option in projectList"
                    :key="option.id"
                    :id="option.id"
                    :name="option.name">
                </bk-option>
            </bk-select>
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
            <bk-table-column label="所属项目" show-overflow-tooltip>
                <template #default="{ row }">
                    {{ (projectList.find(p => p.id === row.projectId) || {}).name || '--' }}
                </template>
            </bk-table-column>
            <bk-table-column :label="$t('repoName')" show-overflow-tooltip>
                <template #default="{ row }">
                    <span v-if="row.public"
                        class="mr5 repo-tag WARNING" data-name="公开"></span>
                    <Icon class="mr5 table-svg" size="16" :name="row.repoType" />
                    <span class="hover-btn">{{replaceRepoName(row.name)}}</span>
                </template>
            </bk-table-column>
            <bk-table-column :label="$t('createdDate')" width="150">
                <template #default="{ row }">{{ formatDate(row.createdDate) }}</template>
            </bk-table-column>
            <bk-table-column :label="$t('createdBy')" width="90">
                <template #default="{ row }">
                    {{ userList[row.createdBy] ? userList[row.createdBy].name : row.createdBy }}
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
    </div>
</template>
<script>
    import { mapState, mapActions } from 'vuex'
    import { repoEnum } from '@repository/store/publicEnum'
    import { formatDate } from '@repository/utils'
    export default {
        name: 'repoList',
        data () {
            return {
                repoEnum,
                isLoading: false,
                repoList: [],
                query: {
                    projectId: this.$route.query.projectId,
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
            ...mapState(['projectList', 'userList'])
        },
        created () {
            this.handlerPaginationChange()
        },
        methods: {
            formatDate,
            ...mapActions([
                'getRepoList'
            ]),
            getListData () {
                this.isLoading = true
                this.getRepoList({
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
