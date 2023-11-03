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
                    :placeholder="$t('pleaseInput') + $t('space') + $t('packageName')"
                    @enter="changePackageName()">
                </bk-input>
                <i class="name-search devops-icon icon-search flex-center" @click="changePackageName()"></i>
            </div>
            <div class="mt20 flex-between-center" style="align-items:flex-end;">
                <div class="result-count flex-align-center">
                </div>
                <div v-if="resultList.length !== 0 || repoList[0].children.length !== 0" class="sort-tool flex-align-center">
                    <bk-select
                        style="width:220px;"
                        v-model="property"
                        :clearable="false"
                        @change="changeSortType">
                        <bk-option id="name" :name="$t('nameSorting')"></bk-option>
                        <bk-option id="lastModifiedDate" :name="$t('lastModifiedTimeSorting')"></bk-option>
                        <bk-option id="createdDate" :name="$t('creatTimeSorting')"></bk-option>
                        <bk-option id="downloads" :name="$t('downloadSorting')"></bk-option>
                    </bk-select>
                    <bk-popover :content="focusContent + ' ' + `${direction === 'ASC' ? $t('desc') : $t('asc')}`" placement="top">
                        <div class="ml10 sort-order flex-center" @click="changeDirection">
                            <Icon :name="`order-${direction.toLowerCase()}`" size="16"></Icon>
                        </div>
                    </bk-popover>
                </div>
            </div>
        </div>
        <main class="repo-search-result flex-align-center">
            <template v-if="resultList.length !== 0 || repoList[0].children.length !== 0">
                <repo-tree
                    class="repo-tree"
                    ref="dialogTree"
                    :tree="repoList"
                    :open-list="openList"
                    :selected-node="selectedNode"
                    @icon-click="iconClickHandler"
                    @item-click="itemClickHandler">
                    <template #icon><span></span></template>
                    <template #text="{ item: { name } }">
                        <div class="flex-1 flex-between-center">
                            <span class="text-overflow" :title="name.length > 19 ? name : ''">{{ name }}</span>
                        </div>
                    </template>
                </repo-tree>
                <infinite-scroll
                    ref="infiniteScroll"
                    class="package-list flex-1"
                    :is-loading="isLoading"
                    :has-next="hasNext"
                    @load="handlerPaginationChange({ current: pagination.current + 1 }, true)">
                    <package-card
                        class="mb10"
                        v-for="pkg in resultList"
                        :key="pkg.repoName + (pkg.key || pkg.fullPath)"
                        :card-data="pkg"
                        readonly
                        @show-detail="showDetail"
                        @share="handlerShare"
                        @click.native="showCommonPackageDetail(pkg)">
                    </package-card>
                </infinite-scroll>
            </template>
            <empty-data v-else :is-loading="isLoading" class="flex-1" ex-style="align-self:start;margin-top:130px;"></empty-data>
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
                direction: this.$route.query.direction || 'DESC',
                packageName: this.$route.query.packageName || '',
                repoType: this.$route.query.repoType || 'generic',
                repoList: [{
                    name: this.$t('total'),
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
                resultList: [],
                hasNext: true,
                focusContent: this.$t('toggle'),
                repoNames: [],
                init: false
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
            // 更新程度：类型 》 包名 》 树/排序 》 滚动加载
            this.changeRepoType(this.repoType)
        },
        methods: {
            formatDate,
            ...mapActions(['searchPackageList', 'searchRepoList']),
            refreshRoute () {
                this.$router.replace({
                    query: {
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
                }).then(([item]) => {
                    this.repoList = [{
                        name: this.$t('total'),
                        roadMap: '0',
                        children: item.repos.map((child, i) => {
                            return {
                                name: this.replaceRepoName(child.repoName),
                                repoName: child.repoName,
                                roadMap: '0,' + i,
                                leaf: true,
                                sum: child.packages || child.nodes
                            }
                        }),
                        sum: item.sum
                    }]
                })
            },
            searckPackageHandler (scrollLoad) {
                if (this.isLoading) return
                this.isLoading = !scrollLoad
                this.hasNext = true
                this.searchPackageList({
                    projectId: this.projectId,
                    repoType: this.repoType,
                    repoName: this.repoName,
                    repoNames: this.repoNames,
                    packageName: this.packageName,
                    property: this.property,
                    direction: this.direction,
                    current: this.pagination.current,
                    limit: this.pagination.limit
                }).then(({ records, totalRecords }) => {
                    this.pagination.count = totalRecords
                    if (records.length < this.pagination.limit) {
                        this.hasNext = false
                    }
                    scrollLoad ? this.resultList.push(...records) : (this.resultList = records)
                }).finally(() => {
                    this.isLoading = false
                })
            },
            handlerPaginationChange ({ current = 1, limit = this.pagination.limit } = {}, scrollLoad = false) {
                this.pagination.current = current
                this.pagination.limit = limit
                this.changeQuery(scrollLoad)
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
                // 制品搜索页，当切换制品类型时将搜索的包名参数重置为空，否则不重置，解决复制url导致搜索参数丢失的问题
                if (this.repoType !== repoType) {
                    this.packageName = ''
                    this.repoType = repoType
                }
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

                this.repoName = node.repoName

                this.refreshRoute()
                this.handlerPaginationChange()
            },
            showCommonPackageDetail (pkg) {
                if (pkg.fullPath) {
                    // generic
                    this.$router.push({
                        name: 'repoGeneric',
                        params: {
                            projectId: pkg.projectId
                        },
                        query: {
                            repoName: pkg.repoName,
                            path: pkg.fullPath
                        }
                    })
                } else {
                    this.$router.push({
                        name: 'commonPackage',
                        params: {
                            projectId: pkg.projectId,
                            repoType: pkg.type.toLowerCase()
                        },
                        query: {
                            repoName: pkg.repoName,
                            packageKey: pkg.key
                        }
                    })
                }
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
            },
            // ci模式下generic的查询repoName的NIN条件会和repoType的In组装条件会异常（更改为传递查询repoName，去掉repoType传递）
            changeQuery (scrollLoad) {
                if (this.repoType === 'generic' && MODE_CONFIG === 'ci' && !this.init) {
                    this.searchRepoList({
                        projectId: this.projectId,
                        repoType: this.repoType,
                        packageName: this.packageName || ''
                    }).then(([item]) => {
                        item.repos.forEach(item => {
                            this.repoNames.push(item.repoName)
                        })
                        this.init = true
                        this.searckPackageHandler(scrollLoad)
                        !scrollLoad && this.$refs.infiniteScroll && this.$refs.infiniteScroll.scrollToTop()
                    })
                } else {
                    this.init = true
                    this.searckPackageHandler(scrollLoad)
                    !scrollLoad && this.$refs.infiniteScroll && this.$refs.infiniteScroll.scrollToTop()
                }
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
            color: var(--fontSubsidiaryColor);
            .sort-order {
                width: 30px;
                height: 30px;
                border: 1px solid var(--borderWeightColor);
                border-radius: 2px;
                cursor: pointer;
                &:hover {
                    color: var(--primaryColor);
                    border-color: currentColor;
                    background-color: var(--bgHoverLighterColor);
                }
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
