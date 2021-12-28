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
                <repo-tree
                    class="mr20 repo-tree"
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
    </div>
</template>
<script>
    import repoTree from '@repository/components/RepoTree'
    import packageCard from '@repository/components/PackageCard'
    import InfiniteScroll from '@repository/components/InfiniteScroll'
    import genericDetail from '@repository/views/repoGeneric/genericDetail'
    import typeSelect from '@repository/views/repoSearch/typeSelect'
    import { mapState, mapActions } from 'vuex'
    import { formatDate } from '@repository/utils'
    import { repoEnum } from '@repository/store/publicEnum'
    export default {
        name: 'repoSearch',
        components: { repoTree, packageCard, InfiniteScroll, typeSelect, genericDetail },
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
                repoList: [],
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
            this.handlerPaginationChange()
        },
        methods: {
            formatDate,
            ...mapActions(['searchPackageList', 'searchRepoList']),
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
                this.projectId = node.projectId
                this.repoName = node.repoName
                this.openList.push(node.roadMap)
                this.handlerPaginationChange()
            },
            searchRepoHandler () {
                this.searchRepoList({
                    projectId: this.projectId,
                    repoType: this.repoType,
                    packageName: this.packageName
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
                                        name: child.repoName,
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
                    if (this.selectedNode.roadMap !== '0') {
                        this.itemClickHandler(this.repoList[0])
                    }
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
                        projectId: pkg.projectId,
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
                            projectId: this.projectId,
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
        .repo-tree {
            width: 200px;
            height: 100%;
            overflow: auto;
        }
        .package-list {
            height: 100%;
        }
    }
}
</style>
