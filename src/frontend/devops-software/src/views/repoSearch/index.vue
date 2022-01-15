<template>
    <div class="repo-search-container" v-bkloading="{ isLoading }">
        <div class="repo-search-tools flex-column">
            <div class="name-tool flex-center">
                <type-select :repo-list="repoEnum.filter(r => r !== 'generic')" :repo-type="repoType" @change="changeRepoType"></type-select>
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
            <div v-if="pagination.count" class="mt20 flex-between-center" style="align-items:flex-end;">
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
                        @change="changeSortType">
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
                <repo-tree
                    class="repo-tree"
                    ref="dialogTree"
                    :tree="repoList"
                    :open-list="openList"
                    :selected-node="selectedNode"
                    @icon-click="iconClickHandler"
                    @item-click="itemClickHandler">
                    <template #icon><span></span></template>
                    <template #text="{ item: { name, sum } }">
                        <div class="flex-1 flex-between-center">
                            <span class="text-overflow">{{ name }}</span>
                            <span class="mr10">{{ sum }}</span>
                        </div>
                    </template>
                </repo-tree>
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
    import repoTree from '@repository/components/RepoTree'
    import packageCard from '@repository/components/PackageCard'
    import InfiniteScroll from '@repository/components/InfiniteScroll'
    import genericDetail from '@repository/views/repoGeneric/genericDetail'
    import genericShareDialog from '@repository/views/repoGeneric/genericShareDialog'
    import typeSelect from '@repository/views/repoSearch/typeSelect'
    import { mapState, mapActions } from 'vuex'
    import { formatDate } from '@repository/utils'
    import { repoEnum } from '@repository/store/publicEnum'
    export default {
        name: 'repoSearch',
        components: { repoTree, packageCard, InfiniteScroll, typeSelect, genericDetail, genericShareDialog },
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
                projectId: this.$route.query.projectId || '',
                packageName: this.$route.query.packageName || '',
                repoType: this.$route.query.repoType || 'docker',
                repoList: [{
                    name: '全部',
                    roadMap: '0',
                    children: []
                }],
                selectedNode: {},
                openList: [],
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
            isSearching () {
                const { packageName, repoType, repoName } = this.$route.query
                return Boolean(packageName || repoType || repoName)
            }
        },
        created () {
            // 更新程度：类型 》 包名 》 树/排序 》 滚动加载
            this.changeRepoType(this.repoType)
        },
        methods: {
            formatDate,
            ...mapActions(['searchPackageList', 'searchRepoList']),
            refreshRoute () {
                this.$router.replace({
                    query: {
                        projectId: this.projectId,
                        repoType: this.repoType,
                        repoName: this.repoName,
                        packageName: this.packageName,
                        property: this.property,
                        direction: this.direction
                    }
                })
            },
            searchRepoHandler () {
                this.searchRepoList({
                    projectId: this.projectId,
                    repoType: this.repoType,
                    packageName: this.packageName || ''
                }).then(list => {
                    this.repoList = [{
                        name: '全部',
                        roadMap: '0',
                        children: list.map((node, i) => {
                            return {
                                name: node.projectId,
                                projectId: node.projectId,
                                roadMap: '0,' + i,
                                children: node.repos.map((child, j) => {
                                    return {
                                        name: this.replaceRepoName(child.repoName),
                                        projectId: node.projectId,
                                        repoName: child.repoName,
                                        roadMap: '0,' + i + ',' + j,
                                        leaf: true,
                                        sum: child.packages || child.nodes
                                    }
                                }),
                                sum: node.sum
                            }
                        })
                    }]
                })
            },
            searckPackageHandler (scrollLoad) {
                if (this.isLoading) return
                this.isLoading = !scrollLoad
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
                    scrollLoad ? this.resultList.push(...records) : (this.resultList = records)
                }).finally(() => {
                    this.isLoading = false
                })
            },
            handlerPaginationChange ({ current = 1, limit = this.pagination.limit } = {}, scrollLoad = false) {
                this.pagination.current = current
                this.pagination.limit = limit
                this.searckPackageHandler(scrollLoad)
                !scrollLoad && this.$refs.infiniteScroll && this.$refs.infiniteScroll.scrollToTop()
            },
            changeSortType () {
                this.refreshRoute()
                this.handlerPaginationChange()
            },
            changeDirection () {
                this.direction = this.direction === 'ASC' ? 'DESC' : 'ASC'
                this.refreshRoute()
                this.handlerPaginationChange()
            },
            changeRepoType (repoType) {
                this.repoType = repoType
                this.packageName = ''
                this.changePackageName()
            },
            changePackageName () {
                this.searchRepoHandler()
                this.itemClickHandler(this.repoList[0]) // 重置树
            },
            iconClickHandler (node) {
                const openList = this.openList
                if (openList.includes(node.roadMap)) {
                    openList.splice(0, openList.length, ...openList.filter(v => v !== node.roadMap))
                } else {
                    openList.push(node.roadMap)
                }
            },
            itemClickHandler (node) {
                this.selectedNode = node
                this.openList.push(node.roadMap)

                this.projectId = node.projectId
                this.repoName = node.repoName

                this.refreshRoute()
                this.handlerPaginationChange()
            },
            showCommonPackageDetail (pkg) {
                if (pkg.fullPath) {
                    this.showDetail(pkg)
                    return
                }
                this.$router.push({
                    name: 'commonPackage',
                    params: {
                        projectId: pkg.projectId,
                        repoType: pkg.type.toLowerCase()
                    },
                    query: {
                        repoName: pkg.repoName,
                        package: pkg.key
                    }
                })
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
    background-color: white;
    .repo-search-tools {
        padding: 20px 20px 10px;
        z-index: 1;
        background-color: white;
        border-bottom: 1px solid var(--borderColor);
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
            color: var(--fontSubsidiaryColor);
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
        height: calc(100% - 130px);
        .repo-tree {
            width: 200px;
            height: 100%;
            overflow: auto;
            border-right: 1px solid var(--borderColor);
        }
        .package-list {
            padding: 10px 20px 0;
        }
    }
}
</style>
