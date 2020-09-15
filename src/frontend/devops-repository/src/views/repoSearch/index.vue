<template>
    <div class="repo-search-container">
        <header class="repo-search-header">
            <icon size="24" :name="this.$route.query.type"></icon>
            <span class="mr5 ml10 hover-btn" @click="goBack">{{this.$route.query.name}}</span>
            <i class="devops-icon icon-angle-right"></i>
            <span class="ml5">{{$t('searchForPkg')}}</span>
        </header>
        <div class="repo-search-main flex-column">
            <div>
                <bk-input
                    class="mr20 file-name-search"
                    v-model="fileNameInput"
                    :placeholder="$t('pleaseInput') + $t('file') + $t('name')"
                    clearable>
                </bk-input>
                <bk-button :loading="isLoading" theme="primary" @click="searchHandler">{{$t('search')}}</bk-button>
            </div>
            <div class="repo-type-search">
                <bk-radio-group v-model="repoType" class="repo-type-radio-group">
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
        <main class="repo-search-result flex-column">
            <template v-if="resultList.length">
                <main class="mb10 result-list" v-bkloading="{ isLoading }">
                    <div class="hover-btn result-item"
                        @click="toRepoDetail(result)"
                        v-for="result in resultList"
                        :key="result.repoName + result.fullPath">
                        <div class="mb10 flex-align-center">
                            <icon size="24" :name="currentRepoType" />
                            <span class="ml20 mr20 result-repo-name">{{result.repoName}}</span>
                            <template v-if="result.stageTag">
                                <span class="mr5 result-tag" v-for="tag in result.stageTag.split(',')" :key="tag + result.repoName + result.fullPath">
                                    {{tag}}
                                </span>
                            </template>
                        </div>
                        <div class="flex-column-center">
                            <span class="mr20">{{new Date(result.lastModifiedDate).toLocaleString()}}</span>
                            <span class="result-path">{{result.fullPath}}</span>
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
            <template v-else>
                <div class="flex-column flex-center">{{ $t('noData') }}</div>
            </template>
        </main>
    </div>
</template>
<script>
    import { mapActions } from 'vuex'
    export default {
        name: 'repoSearch',
        data () {
            return {
                isLoading: false,
                fileNameInput: this.$route.query.file,
                repoType: this.$route.query.type,
                currentRepoType: '',
                repoEnum: ['generic', 'docker', 'maven', 'pypi', 'npm', 'helm', 'composer', 'rpm'],
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
            projectId () {
                return this.$route.params.projectId
            }
        },
        created () {
            this.searchHandler()
        },
        methods: {
            ...mapActions([
                'genericSearch'
            ]),
            searchHandler () {
                this.isLoading = true
                let request = Promise.resolve({ records: [], totalRecords: 0 })
                this.currentRepoType = this.repoType
                switch (this.repoType) {
                    case 'generic':
                        request = this.genericSearch({
                            projectId: this.projectId,
                            name: this.fileNameInput,
                            current: this.pagination.current,
                            limit: this.pagination.limit
                        })
                        break
                }
                request.then(({ records, totalRecords }) => {
                    this.pagination.count = totalRecords
                    this.resultList = records
                }).finally(() => {
                    this.isLoading = false
                })
            },
            toRepoDetail (file) {
                this.$router.push({
                    name: this.$route.query.type,
                    query: {
                        name: file.repoName
                    }
                })
            },
            handlerPaginationChange ({ current = 1, limit = this.pagination.limit }) {
                this.pagination.current = current
                this.pagination.limit = limit
                this.searchHandler()
            },
            goBack () {
                const { type, name } = this.$route.query
                this.$router.push({
                    name: type,
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
            padding-top: 15px;
            border-top: 2px solid $borderWeightColor;
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
        height: calc(100% - 300px);
        margin-top: 20px;
        padding: 20px;
        background-color: white;
        .result-list {
            flex: 1;
            overflow: auto;
            .result-item{
                padding: 10px;
                margin-bottom: 20px;
                .result-repo-name {
                    font-size: 16px;
                }
                .result-tag {
                    display: inline-block;
                    padding: 5px;
                    border-radius: 5px;
                    font-size: 14px;
                    background-color: $primaryLightColor;
                }
                .result-path {
                    display: inline-block;
                    max-width: 250px;
                    overflow: hidden;
                    text-overflow: ellipsis;
                    white-space: nowrap;
                }
                &:hover {
                    background-color: #f0f1f5;
                }
            }
        }
    }
}
</style>
