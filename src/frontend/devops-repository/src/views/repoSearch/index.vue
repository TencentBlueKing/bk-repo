<template>
    <div class="repo-search-container" v-bkloading="{ isLoading }">
        <div class="repo-search-tools flex-column">
            <div class="name-tool flex-center">
                <type-select :repo-list="repoEnum" :repo-type="repoType" @change="changeRepoType"></type-select>
                <bk-input
                    v-focus
                    style="width:390px"
                    v-model.trim="packageName"
                    size="large"
                    :placeholder="$t('pleaseInput') + $t('packageName')"
                    @enter="changePackageName()">
                </bk-input>
                <i class="name-search devops-icon icon-search flex-center" @click="changePackageName()"></i>
            </div>
            <div v-if="pagination.count" class="mt20 flex-between-center">
                <div class="result-count flex-align-center">
                    <span v-if="isSearching">搜索到相关结果</span>
                    <span v-else>全部制品共</span>
                    <span>{{ pagination.count }}个</span>
                </div>
                <div class="sort-tool flex-align-center">
                    <bk-select
                        style="width:150px;"
                        v-model="property"
                        :clearable="false"
                        @change="handlerPaginationChange()">
                        <bk-option id="name" name="名称排序"></bk-option>
                        <bk-option id="lastModifiedDate" name="时间排序"></bk-option>
                        <bk-option id="downloads" name="下载量排序"></bk-option>
                    </bk-select>
                    <div class="ml10 sort-order flex-center hover-btn" @click="changeDirection">
                        <Icon :name="`order-${direction.toLowerCase()}`" size="16"></Icon>
                    </div>
                </div>
            </div>
        </div>
        <main class="repo-search-result flex-align-center">
            <template v-if="resultList.length">
                <div class="mr20 repo-list">
                    <div class="repo-item flex-between-center"
                        :class="{ 'selected': repo.repoName === repoName }"
                        v-for="(repo, index) in repoList"
                        :key="repo.repoName || index"
                        :title="repo.repoName"
                        @click="changeRepoInput(repo.repoName)">
                        <span class="flex-1 text-overflow">{{ repo.repoName || '全部' }}</span>
                        <span class="repo-sum">{{ repo.total }}</span>
                    </div>
                </div>
                <infinite-scroll
                    ref="infiniteScroll"
                    class="package-list flex-1"
                    :is-loading="isLoading"
                    :has-next="resultList.length < pagination.count"
                    @load="handlerPaginationChange({ current: pagination.current + 1 }, true)">
                    <package-card
                        class="mb10"
                        v-for="pkg in resultList"
                        :key="pkg.repoName + (pkg.key || pkg.fullPath)"
                        :card-data="pkg"
                        readonly
                        @share="handlerShare"
                        @click.native="showCommonPackageDetail(pkg)">
                    </package-card>
                </infinite-scroll>
            </template>
            <empty-data v-else :is-loading="isLoading" class="flex-1" ex-style="align-self:start;margin-top:80px;"
                :config="{
                    imgSrc: '/ui/no-search.png',
                    title: '搜索结果为空',
                    subTitle: '请尝试修改搜索条件'
                }">
            </empty-data>
        </main>
        <generic-detail ref="genericDetail"></generic-detail>
        <generic-share-dialog ref="genericShareDialog"></generic-share-dialog>
    </div>
</template>
<script>
    import packageCard from '@repository/components/PackageCard'
    import InfiniteScroll from '@repository/components/InfiniteScroll'
    import genericDetail from '@repository/views/repoGeneric/genericDetail'
    import genericShareDialog from '@repository/views/repoGeneric/genericShareDialog'
    import typeSelect from './typeSelect'
    import { mapState, mapActions } from 'vuex'
    import { formatDate } from '@repository/utils'
    import { repoEnum } from '@repository/store/publicEnum'
    export default {
        name: 'repoSearch',
        components: { packageCard, InfiniteScroll, typeSelect, genericDetail, genericShareDialog },
        directives: {
            focus: {
                inserted (el) {
                    el.querySelector('input').focus()
                }
            }
        },
        data () {
            return {
                repoEnum,
                isLoading: false,
                property: this.$route.query.property || 'lastModifiedDate',
                direction: this.$route.query.direction || 'ASC',
                packageName: this.$route.query.packageName || '',
                repoType: this.$route.query.repoType || 'generic',
                repoList: [],
                repoName: this.$route.query.repoName || '',
                pagination: {
                    current: 1,
                    limit: 20,
                    count: 0
                },
                resultList: []
            }
        },
        computed: {
            ...mapState(['userList']),
            projectId () {
                return this.$route.params.projectId
            },
            isSearching () {
                const { packageName, repoType, repoName } = this.$route.query
                return Boolean(packageName || repoType || repoName)
            }
        },
        created () {
            this.handlerPaginationChange()
        },
        methods: {
            formatDate,
            ...mapActions(['searchPackageList', 'searchRepoList']),
            searchRepoHandler () {
                this.searchRepoList({
                    projectId: this.projectId,
                    repoType: this.repoType,
                    packageName: this.packageName || ''
                }).then(list => {
                    this.repoList = list
                })
            },
            searckPackageHandler (load) {
                if (this.isLoading) return
                this.isLoading = !load
                this.searchPackageList({
                    projectId: this.projectId,
                    repoType: this.repoType,
                    repoName: this.repoName,
                    packageName: this.packageName,
                    property: this.property,
                    direction: this.direction,
                    current: this.pagination.current,
                    limit: this.pagination.limit
                }).then(({ records, totalRecords }) => {
                    this.pagination.count = totalRecords
                    load ? this.resultList.push(...records) : (this.resultList = records)
                }).finally(() => {
                    this.isLoading = false
                })
            },
            showCommonPackageDetail (pkg) {
                if (pkg.fullPath) {
                    this.showDetail(pkg)
                    return
                }
                this.$router.push({
                    name: 'commonPackage',
                    params: {
                        projectId: this.projectId,
                        repoType: pkg.type.toLowerCase()
                    },
                    query: {
                        repoName: pkg.repoName,
                        package: pkg.key
                    }
                })
            },
            handlerPaginationChange ({ current = 1, limit = this.pagination.limit } = {}, load) {
                this.pagination.current = current
                this.pagination.limit = limit
                this.searckPackageHandler(load)
                if (!load) {
                    this.$refs.infiniteScroll && this.$refs.infiniteScroll.scrollToTop()
                    this.searchRepoHandler()
                    this.$router.replace({
                        query: {
                            repoType: this.repoType,
                            repoName: this.repoName,
                            packageName: this.packageName,
                            property: this.property,
                            direction: this.direction
                        }
                    })
                }
            },
            changeDirection () {
                this.direction = this.direction === 'ASC' ? 'DESC' : 'ASC'
                this.handlerPaginationChange()
            },
            changeRepoType (repoType) {
                this.repoType = repoType
                this.packageName = ''
                this.changePackageName()
            },
            changePackageName () {
                this.repoName = ''
                this.changeRepoInput()
            },
            changeRepoInput (repoName = '') {
                this.repoName = repoName
                this.handlerPaginationChange()
            },
            showDetail (pkg) {
                this.$refs.genericDetail.setData({
                    show: true,
                    loading: false,
                    projectId: pkg.projectId,
                    repoName: pkg.repoName,
                    folder: pkg.folder,
                    path: pkg.fullPath,
                    data: {}
                })
            },
            handlerShare (cardData) {
                this.$refs.genericShareDialog.setData({
                    projectId: cardData.projectId,
                    repoName: cardData.repoName,
                    show: true,
                    loading: false,
                    title: `${this.$t('share')} (${cardData.name})`,
                    path: cardData.fullPath,
                    user: [],
                    ip: [],
                    permits: '',
                    time: 7
                })
            }
        }
    }
</script>
<style lang="scss" scoped>
.repo-search-container {
    position: relative;
    height: 100%;
    padding: 20px 30px 0;
    background-color: white;
    .repo-search-tools {
        padding-bottom: 10px;
        z-index: 1;
        background-color: white;
        .name-tool {
            height: 48px;
            ::v-deep .bk-input-large {
                border-radius: 0;
                height: 48px;
                line-height: 48px;
            }
            .name-search {
                width: 81px;
                height: 100%;
                margin-left: -1px;
                color: white;
                font-size: 16px;
                font-weight: bold;
                background-color: var(--primaryColor);
                border-radius: 0 2px 2px 0;
                cursor: pointer;
            }
        }
        .result-count {
            font-size: 14px;
            color: var(--fontColor);
        }
        .sort-tool {
            color: var(--boxShadowColor);
            .sort-order {
                width: 32px;
                height: 32px;
                border: 1px solid currentColor;
                border-radius: 2px;
            }
        }
    }
    .repo-search-result {
        height: calc(100% - 110px);
        .repo-list {
            width: 200px;
            height: 100%;
            overflow-y: auto;
            .repo-item {
                padding: 0 10px;
                border-radius: 2px;
                line-height: 42px;
                background-color: var(--bgLighterColor);
                cursor: pointer;
                .repo-sum {
                    color: var(--fontTipColor);
                }
                &.selected {
                    color: white;
                    background-color: var(--primaryColor);
                    .repo-sum {
                        color: white;
                    }
                }
            }
        }
        .package-list {
            height: 100%;
        }
    }
}
</style>
