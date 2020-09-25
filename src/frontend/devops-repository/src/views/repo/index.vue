<template>
    <div class="repo-detail-container">
        <header class="repo-detail-header flex-align-center">
            <repo-select
                @repo-change="repoChange"
                @repo-click="repoChange"
                :repo-name="$route.query.name"
                :has-click="Boolean(breadcrumb.length)"
                :repo-list="repoList">
            </repo-select>
            <breadcrumb
                class="repo-detail-breadcrumb"
                :list="breadcrumb">
            </breadcrumb>
            <div class="repo-detail-tools flex-align-center">
                <icon class="hover-btn" name="filter" size="16" @click.native="showRepoSearch = !showRepoSearch"></icon>
                <div v-if="$route.name !== 'generic'" class="ml20 hover-btn flex-align-center" @click="handlerShowGuide">
                    <icon class="mr5" name="hand-guide" size="18"></icon>
                    {{$t('guide')}}
                </div>
            </div>
        </header>
        <main class="repo-detail-main">
            <div class="repo-detail-search flex-align-center"
                :style="showRepoSearch ? 'margin-top: 20px' : ''">
                <bk-search-select
                    class="repo-search-select"
                    :values="artiQuery"
                    :data="filterList"
                    clearable
                    :split-code="' & '"
                    :placeholder="$t('enterConfirm')"
                    @change="queryChangeHandler"
                    :show-condition="false"
                    :show-popover-tag-change="true">
                </bk-search-select>
                <bk-button class="ml20" outline theme="primary" @click="searchHandler">
                    {{$t('searchInRepo')}}
                </bk-button>
                <bk-button class="ml20" outline theme="primary" @click="fileSearch">
                    {{$t('searchForPkg')}}
                </bk-button>
                <bk-button class="align-right" outline theme="default" @click="resetQueryAndBack">
                    <i class="devops-icon icon-back2"></i>
                    {{$t('returnBack')}}
                </bk-button>
            </div>
            <router-view :ref="$route.name" :style="`height: calc(100% - ${showRepoSearch ? '72px' : '20px'});transition: all .3s;`"></router-view>
        </main>
        <bk-sideslider :is-show.sync="showGuide" :quick-close="true" :width="800" :title="$route.query.name + $t('guide')">
            <component :is="guideType" slot="content"></component>
        </bk-sideslider>
    </div>
</template>
<script>
    import Breadcrumb from '@/components/Breadcrumb'
    import RepoSelect from '@/components/RepoSelect'
    import dockerGuide from './repoDocker/dockerGuide'
    import npmGuide from './repoNpm/npmGuide'
    import filterList from './filter'
    import { mapState, mapActions } from 'vuex'
    export default {
        name: 'repoDetail',
        components: {
            Breadcrumb,
            RepoSelect,
            'docker-guide': dockerGuide,
            'npm-guide': npmGuide
        },
        data () {
            return {
                repoList: [],
                showRepoSearch: false,
                showGuide: false,
                artiQuery: []
            }
        },
        computed: {
            ...mapState(['breadcrumb']),
            projectId () {
                return this.$route.params.projectId
            },
            guideType () {
                return `${this.$route.name}-guide`
            },
            filterList () {
                return filterList[this.$route.name]
            }
        },
        created () {
            this.getRepoListAll({
                projectId: this.projectId
            }).then(res => {
                this.repoList = res.map(v => ({ ...v, type: v.type.toLowerCase() }))
            })
            this.setRepositoryHistory()
        },
        beforeRouteUpdate (to, from, next) {
            this.setRepositoryHistory()
            next()
        },
        methods: {
            ...mapActions(['getRepoListAll']),
            setRepositoryHistory () {
                const repositoryHistory = JSON.parse(localStorage.getItem('repositoryHistory') || '{}')
                repositoryHistory[this.projectId] = {
                    type: this.$route.name,
                    name: this.$route.query.name
                }
                localStorage.setItem('repositoryHistory', JSON.stringify(repositoryHistory))
            },
            repoChange (repo) {
                this.$router.replace({
                    name: repo.type,
                    query: {
                        name: repo.name
                    }
                })
            },
            handlerShowGuide () {
                this.showGuide = true
            },
            queryChangeHandler (query) {
                const keys = this.filterList.map(v => v.id)
                this.artiQuery = Object.values(
                    query.reduce((target, item) => {
                        if (keys.includes(item.id)) {
                            target[item.id] = item
                        }
                        return target
                    }, {}))
            },
            searchHandler () {
                const query = this.artiQuery.reduce((target, item) => {
                    target[item.id] = item.values.map(v => v.id)
                    return target
                }, {})
                this.$refs[this.$route.name].searchHandler(query)
            },
            resetQueryAndBack () {
                this.artiQuery = []
                this.showRepoSearch = false
                this.$refs[this.$route.name].resetQueryAndBack()
            },
            fileSearch () {
                const file = (this.artiQuery.find(v => v.id === 'name') || { values: [{ id: '' }] }).values[0].id
                this.$router.push({
                    name: 'repoSearch',
                    query: {
                        type: this.$route.name,
                        name: this.$route.query.name,
                        file
                    }
                })
            }
        }
    }
</script>
<style lang="scss" scoped>
@import '@/scss/conf';
.repo-detail-container {
    height: 100%;
    .repo-detail-header {
        position: relative;
        height: 60px;
        padding: 0 20px;
        font-size: 14px;
        background-color: white;
        z-index: 10; // bk-search-select层级为9
        .repo-detail-breadcrumb {
            flex: 1;
        }
        .repo-detail-tools {
            color: $fontWeightColor;
        }
    }
    .repo-detail-main {
        height: calc(100% - 80px);
        margin-top: 20px;
        padding: 0 20px 20px;
        background-color: white;
        overflow: hidden;
        .repo-detail-search {
            margin: -32px 0 20px;
            transition: all .3s;
            .repo-search-select {
                min-width: 350px;
            }
            .align-right {
                margin-left: auto;
            }
        }
    }
}
</style>
