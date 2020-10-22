<template>
    <div class="repo-search-container">
        <header class="repo-search-header">
            <div class="flex-align-center">
                <icon size="24" :name="$route.query.type"></icon>
                <span class="mr5 ml10 hover-btn" @click="goBack">{{$route.query.name}}</span>
                <i class="devops-icon icon-angle-right"></i>
                <span class="ml5">{{$t('searchForPkg')}}</span>
            </div>
            <icon class="hover-btn" name="filter" size="16" @click.native="showRepoSearch = !showRepoSearch"></icon>
        </header>
        <div class="repo-search-main flex-column" v-if="showRepoSearch">
            <div>
                <bk-input
                    class="mr20 file-name-search"
                    v-model="packageNameInput"
                    :placeholder="$t('pleaseInput') + $t('packageName')"
                    clearable>
                </bk-input>
                <bk-button :loading="isLoading" theme="primary" @click="searckPackageHandler">{{$t('search')}}</bk-button>
            </div>
            <div class="repo-type-search">
                <bk-radio-group v-model="repoType" @change="searckPackageHandler" class="repo-type-radio-group">
                    <bk-radio-button v-for="repo in repoEnum" :key="repo" :value="repo">
                        <div class="flex-center repo-type-radio">
                            <icon size="60" :name="repo" />
                            <span>{{repo}}</span>
                            <div v-show="repoType === repo" class="top-right-selected">
                                <i class="devops-icon icon-check-1"></i>
                            </div>
                        </div>
                    </bk-radio-button>
                </bk-radio-group>
            </div>
        </div>
        <main class="repo-search-result flex-column" v-bkloading="{ isLoading }"
            :style="{
                height: `calc(100% - ${showRepoSearch ? 300 : 80}px)`
            }">
            <template v-if="resultList.length">
                <main class="mb10 result-list">
                    <div class="hover-btn flex-column result-item"
                        @click="toRepoDetail(result)"
                        v-for="result in resultList"
                        :key="result.repoName + result.key">
                        <div class="flex-align-center">
                            <icon size="20" :name="repoType" />
                            <span class="ml10 result-repo-name">{{result.name}}</span>
                            <span class="ml10 package-card-data" v-if="result.type === 'MAVEN'">
                                [Group ID: {{ result.key.replace(/^.*\/\/(.+):.*$/, '$1') }}]
                            </span>
                            <span class="ml10">({{result.repoName}})</span>
                        </div>
                        <div class="result-card flex-align-center">
                            <div :title="result.latest">{{ `${$t('latestVersion')}: ${result.latest}` }}</div>
                            <div>{{ `${$t('versionCount')}: ${result.versions}` }}</div>
                            <div>{{ `${$t('downloads')}: ${result.downloads}` }}</div>
                            <div>{{ `${$t('lastModifiedDate')}: ${formatDate(result.lastModifiedDate)}` }}</div>
                            <div>{{ `${$t('lastModifiedBy')}: ${userList[result.lastModifiedBy] ? userList[result.lastModifiedBy].name : result.lastModifiedBy}` }}</div>
                        </div>
                    </div>
                </main>
                <bk-pagination
                    size="small"
                    align="right"
                    @change="current => handlerPaginationChange({ current })"
                    @limit-change="limit => handlerPaginationChange({ limit })"
                    :current.sync="pagination.current"
                    :limit="pagination.limit"
                    :count="pagination.count"
                    :limit-list="pagination.limitList">
                </bk-pagination>
            </template>
            <empty-data v-else></empty-data>
        </main>
    </div>
</template>
<script>
    import emptyData from '@/components/emptyData'
    import { mapState, mapActions } from 'vuex'
    import { repoEnum } from '@/store/publicEnum'
    import { formatDate } from '@/utils'
    export default {
        name: 'repoSearch',
        components: { emptyData },
        data () {
            return {
                repoEnum,
                showRepoSearch: true,
                isLoading: false,
                packageNameInput: this.$route.query.packageName || '',
                repoType: this.$route.query.type,
                pagination: {
                    current: 1,
                    limit: 10,
                    count: 20,
                    limitList: [10, 20, 40]
                },
                resultList: []
            }
        },
        computed: {
            ...mapState(['userList']),
            projectId () {
                return this.$route.params.projectId
            }
        },
        created () {
            this.handlerPaginationChange()
        },
        methods: {
            formatDate,
            ...mapActions([
                'searchPackageList'
            ]),
            searckPackageHandler () {
                this.isLoading = true
                this.searchPackageList({
                    projectId: this.projectId,
                    repoType: this.repoType,
                    packageName: this.packageNameInput,
                    current: this.pagination.current,
                    limit: this.pagination.limit
                }).then(({ records, totalRecords }) => {
                    this.pagination.count = totalRecords
                    this.resultList = records
                }).finally(() => {
                    this.isLoading = false
                })
            },
            toRepoDetail (pkg) {
                this.$router.push({
                    name: 'commonPackage',
                    params: {
                        projectId: this.projectId,
                        repoType: this.repoType
                    },
                    query: {
                        name: pkg.repoName,
                        package: pkg.key
                    }
                })
            },
            handlerPaginationChange ({ current = 1, limit = this.pagination.limit } = {}) {
                this.pagination.current = current
                this.pagination.limit = limit
                this.searckPackageHandler()
            },
            goBack () {
                const { type, name } = this.$route.query
                this.$router.push({
                    name: 'repoCommon',
                    params: {
                        projectId: this.projectId,
                        repoType: type
                    },
                    query: {
                        name
                    }
                })
            }
        }
    }
</script>
<style lang="scss" scoped>
@import '@/scss/conf';
.repo-search-container {
    height: 100%;
    .repo-search-header {
        height: 60px;
        padding: 0 20px;
        display: flex;
        align-items: center;
        justify-content: space-between;
        font-size: 14px;
        background-color: white;
    }
    .repo-search-main {
        height: 200px;
        margin-top: 20px;
        padding: 20px;
        background-color: white;
        .file-name-search {
            width: 600px;
        }
        .repo-type-search {
            margin-top: 15px;
            margin-bottom: -20px;
            padding-top: 15px;
            border-top: 1px solid $borderWeightColor;
            .repo-type-radio-group {
                /deep/ .bk-form-radio-button {
                    margin: 0 20px 20px 0;
                    .bk-radio-button-text {
                        height: auto;
                        line-height: initial;
                        padding: 0;
                    }
                }
                .repo-type-radio {
                    position: relative;
                    padding: 10px;
                    width: 100px;
                    height: 100px;
                    flex-direction: column;
                    .top-right-selected {
                        position: absolute;
                        margin: -70px -70px 0 0;
                        border-width: 16px;
                        border-style: solid;
                        border-color: $primaryColor $primaryColor transparent transparent;
                        i {
                            position: absolute;
                            margin-top: -12px;
                            font-size: 12px;
                            color: white;
                        }
                    }
                }
            }
        }
    }
    .repo-search-result {
        margin-top: 20px;
        padding: 20px;
        background-color: white;
        .result-list {
            flex: 1;
            overflow: auto;
            border-bottom: 1px solid $borderWeightColor;
            .result-item{
                justify-content: space-around;
                padding: 5px 20px;
                margin-bottom: 20px;
                height: 70px;
                border: 1px solid $borderWeightColor;
                border-radius: 5px;
                background-color: #fdfdfe;
                cursor: pointer;
                &:hover {
                    border-color: $iconPrimaryColor;
                }
                .result-repo-name {
                    font-size: 16px;
                    font-weight: bold;
                }
                .result-card {
                    font-size: 14px;
                    font-weight: normal;
                    div {
                        padding-right: 40px;
                        width: 140px;
                        overflow: hidden;
                        text-overflow: ellipsis;
                        white-space: nowrap;
                        &:nth-child(3n + 1) {
                            width: 300px;
                        }
                        &:nth-child(5) {
                            width: auto;
                        }
                    }
                }
            }
        }
    }
}
</style>
