<template>
    <div class="repo-list-container" v-bkloading="{ isLoading }">
        <div class="ml20 mr20 mt10 flex-between-center">
            <bk-button icon="plus" theme="primary" @click="createRepo">{{ $t('createRepository') }}</bk-button>
            <div class="flex-align-center">
                <bk-input
                    v-model.trim="query.name"
                    class="w250"
                    :placeholder="repoEnterTip"
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
                    <bk-option v-for="type in repoEnum" :key="type.value" :id="type.value" :name="type.label">
                        <div class="flex-align-center">
                            <Icon size="20" :name="type.value" />
                            <span class="ml10 flex-1 text-overflow">{{type.label}}</span>
                        </div>
                    </bk-option>
                </bk-select>
            </div>
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
            <bk-table-column :label="$t('repoName')" show-overflow-tooltip>
                <template #default="{ row }">
                    <Icon class="mr5 table-svg" size="16" :name="row.repoType" />
                    <span class="hover-btn" @click="toPackageList(row)">{{replaceRepoName(row.name)}}</span>
                    <span v-if="MODE_CONFIG === 'ci' && ['custom', 'pipeline'].includes(row.name)"
                        class="mr5 repo-tag SUCCESS" :data-name="$t('built-in')"></span>
                    <span v-if="row.configuration.settings.system" class="mr5 repo-tag" :data-name="$t('system')"></span>
                    <span v-if="row.public" class="mr5 repo-tag WARNING" :data-name="$t('public')"></span>
                </template>
            </bk-table-column>
            <bk-table-column :label="$t('repoQuota')" width="250">
                <template #default="{ row }">
                    <bk-popover class="repo-quota" placement="top" :disabled="!row.quota">
                        <bk-progress v-if="row.quota" size="large" :percent="((row.used || 0) / row.quota)" :show-text="false"></bk-progress>
                        <span class="ml5" v-else>--</span>
                        <div slot="content">
                            <div>{{ $t('totalQuota') }}: {{ convertFileSize(row.quota) }}</div>
                            <div>{{ $t('usedQuotaCapacity') }}: {{ convertFileSize(row.used) }}</div>
                        </div>
                    </bk-popover>
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
            <bk-table-column :label="$t('operation')" width="100">
                <template #default="{ row }">
                    <operation-list
                        :list="[
                            { label: $t('setting'), clickEvent: () => toRepoConfig(row) },
                            (row.repoType !== 'generic' ||
                                (row.repoType === 'generic'
                                    && row.name !== 'custom'
                                    && row.name !== 'report'
                                    && row.name !== 'log'
                                    && row.name !== 'pipeline'
                                )) && { label: $t('delete'), clickEvent: () => deleteRepo(row) }
                        ]">
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
        <create-repo-dialog ref="createRepo" @refresh="handlerPaginationChange"></create-repo-dialog>
    </div>
</template>
<script>
    import OperationList from '@repository/components/OperationList'
    import createRepoDialog from '@repository/views/repoList/createRepoDialog'
    import { mapState, mapActions } from 'vuex'
    import { repoEnum } from '@repository/store/publicEnum'
    import { formatDate, convertFileSize, debounce } from '@repository/utils'
    import { cloneDeep } from 'lodash'
    const paginationParams = {
        count: 0,
        current: 1,
        limit: 20,
        limitList: [10, 20, 40]
    }
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
                    type: this.$route.query.type,
                    c: this.$route.query.c || 1,
                    l: this.$route.query.l || 20
                },
                value: 20,
                pagination: cloneDeep(paginationParams),
                debounceGetListData: null
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
                // 切换项目时需要将之前的筛选条件清空，页码相关的重置为 1/20，否则会保留之前的筛选条件
                this.initData()
            },
            '$route.query' () {
                if (Object.values(this.$route.query).filter(Boolean)?.length === 0) {
                    this.initData()
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
            convertFileSize,
            ...mapActions([
                'getRepoList',
                'deleteRepoList',
                'getRepoListWithoutPage'
            ]),
            initData () {
                // 切换项目或者点击菜单时需要将筛选条件清空，并将页码相关参数重置，否则会导致点击菜单的时候筛选条件还在，不符合产品要求(点击菜单清空筛选条件，重新请求最新数据)
                this.query = {
                    c: 1,
                    l: 20
                }
                // 此时需要将页码相关参数重置，否则会导致点击制品列表菜单后不能返回首页(页码为1，每页大小为20)
                this.pagination = cloneDeep(paginationParams)
                this.handlerPaginationChange()
            },
            getListData () {
                this.isLoading = true
                this.getRepoListWithoutPage({
                    projectId: this.projectId,
                    ...this.query
                }).then(({ records, totalRecords }) => {
                    this.pagination.count = records.length
                    let allRepo
                    if (this.MODE_CONFIG === 'ci') {
                        const resRecords = records.map(v => ({ ...v, repoType: v.type.toLowerCase() }))
                        allRepo = this.shiftListByName('pipeline', this.shiftListByName('custom', resRecords))
                    } else {
                        allRepo = records.map(v => ({ ...v, repoType: v.type.toLowerCase() }))
                    }
                    this.repoList = allRepo.slice((this.pagination.current - 1) * this.pagination.limit, this.pagination.current * this.pagination.limit >= records.length ? records.length : this.pagination.current * this.pagination.limit)
                }).finally(() => {
                    this.isLoading = false
                })
            },
            shiftListByName (name, records) {
                let target = null
                for (let i = 0; i < records.length; i++) {
                    if (records[i].name === name) {
                        target = records[i]
                        records.splice(i, 1)
                        break
                    }
                }
                if (target !== null) {
                    records.unshift(target)
                }
                return records
            },
            handlerPaginationChange ({ current = 1, limit = this.pagination.limit } = {}) {
                this.pagination.current = current
                this.pagination.limit = limit
                this.$router.replace({
                    query: this.query
                })
                // 此时需要加上防抖，否则在点击菜单的时候会直接触发bk-select的change事件，导致出现多个请求
                this.debounceGetListData ? this.debounceGetListData() : this.getListData()
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
                        repoName: name,
                        ...this.$route.query,
                        c: this.pagination.current,
                        l: this.pagination.limit
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
                        ...this.$route.query,
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
                            this.debounceGetListData ? this.debounceGetListData() : this.getListData()
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
    .repo-quota {
        display: block;
        margin-right: 20%;
        ::v-deep .bk-tooltip-ref {
            display: block;
        }
    }
}
</style>
