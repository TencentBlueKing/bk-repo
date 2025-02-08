<template>
    <div class="repo-list-container" v-bkloading="{ isLoading }">
        <div class="ml20 mr20 mt10 flex-align-center">
            <bk-input
                v-model.trim="query.name"
                class="w250"
                :placeholder="$t('repoEnterTip')"
                clearable
                @enter="handlerPaginationChange"
                @clear="handlerPaginationChange"
                right-icon="bk-icon icon-search">
            </bk-input>
            <bk-select
                v-model="query.type"
                class="ml10 w250"
                @change="handlerPaginationChange"
                :placeholder="$t('allTypes')">
                <bk-option v-for="type in repoEnum.filter(r => r.value !== 'generic')" :key="type.value" :id="type.value" :name="type.label">
                    <div class="flex-align-center">
                        <Icon size="20" :name="type.value" />
                        <span class="ml10 flex-1 text-overflow">{{type.label}}</span>
                    </div>
                </bk-option>
            </bk-select>
            <bk-select
                v-model="query.projectId"
                class="ml10 w250"
                searchable
                :placeholder="$t('inputProject')"
                @change="handlerPaginationChange"
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
            height="calc(100% - 100px)"
            :outer-border="false"
            :row-border="false"
            size="small">
            <template #empty>
                <empty-data :is-loading="isLoading" :search="Boolean(query.name || query.type)"></empty-data>
            </template>
            <bk-table-column :label="$t('belongProject')" show-overflow-tooltip width="200">
                <template #default="{ row }">
                    {{ (projectList.find(p => p.id === row.projectId) || {}).name || '/' }}
                </template>
            </bk-table-column>
            <bk-table-column :label="$t('repoName')" show-overflow-tooltip>
                <template #default="{ row }">
                    <Icon class="mr5 table-svg" size="16" :name="row.repoType" />
                    <span class="hover-btn" @click="toPackageList(row)">{{replaceRepoName(row.name)}}</span>
                    <span v-if="row.public" class="mr5 repo-tag WARNING" :data-name="$t('public')"></span>
                </template>
            </bk-table-column>
            <bk-table-column :label="$t('createdDate')" width="250">
                <template #default="{ row }">{{ formatDate(row.createdDate) }}</template>
            </bk-table-column>
            <bk-table-column :label="$t('createdBy')" width="150">
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
    import { formatDate, debounce } from '@repository/utils'
    import { cloneDeep } from 'lodash'
    const paginationParams = {
        count: 0,
        current: 1,
        limit: 20,
        limitList: [10, 20, 40]
    }
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
                    type: this.$route.query.type,
                    c: this.$route.query.c || 1,
                    l: this.$route.query.l || 20
                },
                pagination: cloneDeep(paginationParams),
                debounceGetListData: null
            }
        },
        computed: {
            ...mapState(['projectList', 'userList'])
        },
        watch: {
            '$route.query' () {
                if (Object.values(this.$route.query).filter(Boolean)?.length === 0) {
                    // 此时需要将筛选条件清空，否则会导致点击菜单的时候筛选条件还在，不符合产品要求(点击菜单清空筛选条件，重新请求最新数据)
                    this.query = {
                        c: 1,
                        l: 20
                    }
                    // 此时需要将页码相关参数重置，否则会导致点击制品列表菜单后不能返回首页(页码为1，每页大小为20)
                    this.pagination = cloneDeep(paginationParams)
                    this.handlerPaginationChange()
                }
            }
        },

        created () {
            // 此处的两个顺序不能更换，否则会导致请求数据时报错，防抖这个方法不是function
            this.debounceGetListData = debounce(this.getListData, 100)
            // 当从制品仓库列表页进入依赖源仓库的详情页后点击上方面包屑返回会导致页码相关参数变为string类型，
            // 而bk-pagination的页码相关参数要求为number类型，导致页码不对应，出现一系列问题
            const dependentCurrent = parseInt(this.$route.query.c || 1)
            const dependentLimit = parseInt(this.$route.query.l || 20)
            this.handlerPaginationChange({ current: dependentCurrent, limit: dependentLimit })
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
                this.debounceGetListData ? this.debounceGetListData() : this.getListData()
            },
            toPackageList ({ projectId, repoType, name }) {
                this.$router.push({
                    name: repoType === 'generic' ? 'repoGeneric' : 'commonList',
                    params: {
                        projectId,
                        repoType
                    },
                    query: {
                        repoName: name,
                        ...this.$route.query,
                        c: this.pagination.current,
                        l: this.pagination.limit
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
