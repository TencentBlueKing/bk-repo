<template>
    <div class="repo-detail-container">
        <header class="repo-detail-header flex-align-center">
            <repo-select
                @repo-change="repoChange"
                @repo-click="repoChange"
                :repo-name="repoName"
                :has-click="repoType !== 'generic' && Boolean(breadcrumb.length)"
                :repo-list="repoList">
            </repo-select>
            <breadcrumb
                class="repo-detail-breadcrumb"
                :list="breadcrumb">
            </breadcrumb>
            <div class="repo-detail-tools flex-align-center">
                <icon v-if="showFilterIcon" class="hover-btn" name="filter" size="14" @click.native="showRepoSearch = !showRepoSearch"></icon>
                <div v-if="repoType !== 'generic'" class="ml10 hover-btn flex-align-center" @click="handlerShowGuide">
                    <icon class="mr5" name="hand-guide" size="16"></icon>
                    {{$t('guide')}}
                </div>
            </div>
        </header>
        <main class="repo-detail-main">
            <div class="repo-detail-search flex-align-center"
                :style="showRepoSearch ? 'margin-top: 20px' : ''">
                <bk-input
                    class="repo-search-select"
                    v-model="packageNameInput"
                    clearable
                    @enter="searchHandler"
                    @clear="searchHandler"
                    :placeholder="$t('pleaseInput') + $t('packageName')">
                </bk-input>
                <!-- <bk-search-select
                    :values="artiQuery"
                    :data="filterList"
                    clearable
                    :split-code="' & '"
                    :placeholder="$t('enterConfirm')"
                    @change="queryChangeHandler"
                    :show-condition="false"
                    :show-popover-tag-change="true">
                </bk-search-select> -->
                <i class="repo-detail-search-btn devops-icon icon-search" @click="searchHandler"></i>
                <bk-button v-if="repoType !== 'generic'" class="ml20" outline theme="primary" @click="fileSearch">
                    {{$t('searchForPkg')}}
                </bk-button>
                <bk-button class="align-right" outline theme="default" @click="resetQueryAndBack">
                    <i class="devops-icon icon-back2"></i>
                    {{$t('returnBack')}}
                </bk-button>
            </div>
            <router-view :ref="repoType" :style="`height: calc(100% - ${showRepoSearch ? '72px' : '20px'});transition: all .3s;`"></router-view>
        </main>
        <bk-sideslider :is-show.sync="showGuide" :quick-close="true" :width="850">
            <div slot="header" class="flex-align-center"><icon class="mr5" :name="repoType" size="32"></icon>{{ repoName + $t('guide') }}</div>
            <repo-guide class="pt20 pb20 pl10 pr10" slot="content" :article="articleGuide"></repo-guide>
        </bk-sideslider>
    </div>
</template>
<script>
    import Breadcrumb from '@/components/Breadcrumb'
    import RepoSelect from '@/components/RepoSelect'
    import repoGuide from './repoCommon/repoGuide'
    // import filterList from './filter'
    import repoGuideMixin from './repoGuideMixin'
    import { mapState, mapActions } from 'vuex'
    export default {
        name: 'repoDetail',
        components: {
            Breadcrumb,
            RepoSelect,
            repoGuide
        },
        mixins: [repoGuideMixin],
        data () {
            return {
                repoList: [],
                showRepoSearch: false,
                showGuide: false,
                packageNameInput: ''
                // artiQuery: []
            }
        },
        computed: {
            ...mapState(['breadcrumb', 'dockerDomain']),
            showFilterIcon () {
                return this.$route.name === 'commonList' || this.$route.name === 'repoGeneric'
            }
            // filterList () {
            //     return filterList[this.repoType]
            // }
        },
        watch: {
            repoName () {
                this.showRepoSearch = false
            },
            '$route.name' () {
                this.showRepoSearch = false
            }
        },
        created () {
            this.getRepoListAll({
                projectId: this.projectId
            }).then(res => {
                this.repoList = res.map(v => ({ ...v, type: v.type.toLowerCase() }))
            })
            !this.dockerDomain && this.getDockerDomain()
            this.setRepositoryHistory({
                projectId: this.projectId,
                repoType: this.repoType,
                name: this.repoName
            })
        },
        beforeRouteUpdate (to, from, next) {
            this.setRepositoryHistory({
                projectId: to.params.projectId,
                repoType: to.params.repoType,
                name: to.query.name
            })
            next()
        },
        methods: {
            ...mapActions(['getRepoListAll', 'getDockerDomain']),
            setRepositoryHistory (data) {
                const repositoryHistory = JSON.parse(localStorage.getItem('repositoryHistory') || '{}')
                repositoryHistory[data.projectId] = {
                    type: data.repoType || 'generic',
                    name: data.name
                }
                localStorage.setItem('repositoryHistory', JSON.stringify(repositoryHistory))
            },
            repoChange (repo) {
                this.$router.push({
                    name: repo.type === 'generic' ? 'repoGeneric' : 'repoCommon',
                    params: {
                        projectId: this.projectId,
                        repoType: repo.type
                    },
                    query: {
                        name: repo.name
                    }
                })
            },
            handlerShowGuide () {
                this.showGuide = true
            },
            // queryChangeHandler (query) {
            //     const keys = this.filterList.map(v => v.id)
            //     this.artiQuery = Object.values(
            //         query.reduce((target, item) => {
            //             if (keys.includes(item.id)) {
            //                 target[item.id] = item
            //             }
            //             return target
            //         }, {}))
            // },
            searchHandler () {
                // const query = this.artiQuery.reduce((target, item) => {
                //     target[item.id] = item.values.map(v => v.id).join('')
                //     return target
                // }, {})
                this.$refs[this.repoType].searchHandler({
                    name: this.packageNameInput
                })
            },
            resetQueryAndBack () {
                // this.artiQuery = []
                this.showRepoSearch = false
                this.$refs[this.repoType].resetQueryAndBack()
            },
            fileSearch () {
                // const file = (this.artiQuery.find(v => v.id === 'name') || { values: [{ id: '' }] }).values[0].id
                this.$router.push({
                    name: 'repoSearch',
                    query: {
                        type: this.repoType,
                        name: this.repoName,
                        packageName: this.packageNameInput
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
        height: 50px;
        padding: 0 20px;
        background-color: white;
        z-index: 10; // bk-search-select层级为9
        .repo-detail-breadcrumb {
            flex: 1;
        }
        .repo-detail-tools {
            font-size: 12px;
            color: $fontWeightColor;
        }
    }
    .repo-detail-main {
        height: calc(100% - 70px);
        margin-top: 20px;
        padding: 0 20px 20px;
        background-color: white;
        overflow: hidden;
        .repo-detail-search {
            position: relative;
            margin: -32px 0 20px;
            transition: all .3s;
            .repo-search-select {
                max-width: 350px;
            }
            .align-right {
                margin-left: auto;
            }
            .repo-detail-search-btn {
                position: relative;
                z-index: 1;
                padding: 9px;
                color: white;
                margin-left: -2px;
                border-radius: 0 2px 2px 0;
                background-color: #3a84ff;
                cursor: pointer;
                &:hover {
                    background-color: #699df4;
                }
            }
        }
    }
}
</style>
