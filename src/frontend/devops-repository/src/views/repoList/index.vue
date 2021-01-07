<template>
    <div class="repo-list-container">
        <header class="repo-list-header">
            <div class="repo-list-search">
                <label class="form-label">{{$t('repoType')}}:</label>
                <bk-select
                    v-model="query.type"
                    class="form-input"
                    @change="getListData"
                    :placeholder="$t('allTypes')">
                    <bk-option
                        v-for="type in repoEnum"
                        :key="type"
                        :id="type"
                        :name="type">
                        <div class="repo-name">
                            <icon size="24" :name="type" />
                            <span class="ml10">{{type}}</span>
                        </div>
                    </bk-option>
                </bk-select>
            </div>
            <div class="repo-list-search">
                <label class="form-label">{{$t('repoName')}}:</label>
                <bk-input
                    v-model="query.name"
                    class="form-input"
                    :placeholder="$t('enterSearch')"
                    :clearable="true"
                    @enter="getListData"
                    @clear="getListData"
                    :right-icon="'bk-icon icon-search'">
                </bk-input>
            </div>
            <div class="create-repo-btn">
                <bk-button :theme="'primary'" @click="toCreateRepo">
                    {{$t('create') + $t('repository')}}
                </bk-button>
            </div>
        </header>
        <main class="repo-list-table" v-bkloading="{ isLoading }">
            <bk-table
                :data="repoList"
                height="100%"
                :outer-border="false"
                :row-border="false"
                size="small"
                :pagination="pagination"
                @page-change="current => handlerPaginationChange({ current })"
                @page-limit-change="limit => handlerPaginationChange({ limit })"
            >
                <bk-table-column :label="$t('repoName')">
                    <template slot-scope="props">
                        <div class="repo-name" @click="toRepoDetail(props.row)">
                            <icon size="24" :name="props.row.repoType" />
                            <span class="ml10">{{props.row.name}}</span>
                        </div>
                    </template>
                </bk-table-column>
                <bk-table-column :label="$t('createdDate')">
                    <template slot-scope="props">
                        {{ formatDate(props.row.createdDate) }}
                    </template>
                </bk-table-column>
                <bk-table-column :label="$t('createdBy')">
                    <template slot-scope="props">
                        {{ userList[props.row.createdBy] ? userList[props.row.createdBy].name : props.row.createdBy }}
                    </template>
                </bk-table-column>
                <bk-table-column :label="$t('operation')" width="100">
                    <template slot-scope="props">
                        <i class="hover-btn devops-icon icon-cog mr10" @click="showRepoConfig(props.row)"></i>
                        <i v-if="props.row.repoType !== 'generic'" class="hover-btn devops-icon icon-delete" @click="deleteRepo(props.row)"></i>
                    </template>
                </bk-table-column>
            </bk-table>
        </main>
    </div>
</template>
<script>
    import { mapState, mapActions } from 'vuex'
    import { repoEnum } from '@/store/publicEnum'
    import { formatDate } from '@/utils'
    export default {
        name: 'repoList',
        data () {
            return {
                repoEnum: ['generic', ...repoEnum],
                isLoading: false,
                repoList: [],
                query: {
                    name: '',
                    type: ''
                },
                pagination: {
                    count: 1,
                    current: 1,
                    limit: 10,
                    'limit-list': [10, 20, 40]
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
            ...mapActions(['getRepoList', 'deleteRepoList']),
            async getListData () {
                this.isLoading = true
                const { records, totalRecords } = await this.getRepoList({
                    projectId: this.projectId,
                    ...this.pagination,
                    ...this.query
                }).finally(() => {
                    this.isLoading = false
                })
                this.repoList = records.map(v => ({ ...v, repoType: v.type.toLowerCase() }))
                this.pagination.count = totalRecords
            },
            handlerPaginationChange ({ current = 1, limit = this.pagination.limit } = {}) {
                this.pagination.current = current
                this.pagination.limit = limit
                this.getListData()
            },
            toCreateRepo () {
                this.$router.push({
                    name: 'createRepo'
                })
            },
            toRepoDetail ({ repoType, name }) {
                this.$router.push({
                    name: repoType === 'generic' ? 'repoGeneric' : 'repoCommon',
                    params: {
                        projectId: this.projectId,
                        repoType
                    },
                    query: {
                        name
                    }
                })
            },
            showRepoConfig ({ repoType, name }) {
                this.$router.push({
                    name: 'repoConfig',
                    params: {
                        ...this.$route.params,
                        repoType
                    },
                    query: {
                        name
                    }
                })
            },
            deleteRepo ({ name }) {
                this.$bkInfo({
                    type: 'error',
                    title: this.$t('deleteRepoTitle'),
                    subTitle: this.$t('deleteRepoSubTitle'),
                    showFooter: true,
                    confirmFn: () => {
                        this.deleteRepoList({
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
@import '@/scss/conf';
.repo-list-container {
    height: 100%;
    padding: 0 20px;
    background-color: white;
    .repo-list-header {
        height: 50px;
        margin-bottom: 10px;
        display: flex;
        align-items: center;
        .repo-list-search {
            display: flex;
            align-items: center;
            flex-basis: 400px;
            .form-label {
                font-size: 14px;
                flex-basis: 80px;
            }
            .form-input {
                flex-basis: 300px;
            }
        }
        .create-repo-btn {
            flex: 1;
            display: flex;
            justify-content: flex-end;
        }
    }
    .repo-list-table {
        height: calc(100% - 60px);
    }
}
.devops-icon {
    font-size: 16px;
}
.repo-name {
    height: 44px;
    display: flex;
    align-items: center;
    font-size: 14px;
    cursor: pointer;
    &:hover {
        color: $primaryColor;
    }
}
</style>
