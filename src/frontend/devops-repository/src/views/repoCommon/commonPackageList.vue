<template>
    <div class="common-package-container" v-bkloading="{ isLoading }">
        <header class="mb10 pl20 pr20 common-package-header flex-align-center">
            <Icon class="package-img" size="30" :name="repoType" />
            <div class="ml10 common-package-title">
                <div class="mb5 repo-title text-overflow" :title="repoName">
                    {{ repoName }}
                </div>
                <!-- <div class="repo-description text-overflow"
                    :title="currentRepo.description">
                    {{ currentRepo.description || '【仓库描述】' }}
                </div> -->
            </div>
            <div class="flex-end-center flex-1">
                <bk-button class="ml10 flex-align-center" text theme="primary" @click="showGuide = true">
                    <span class="flex-align-center">
                        <Icon class="mr5" name="hand-guide" size="16" />
                        {{$t('guide')}}
                    </span>
                </bk-button>
            </div>
        </header>
        <!-- 存在包, 加载中默认存在包 -->
        <template v-if="packageList.length || $route.query.packageName || isLoading">
            <div class="package-search-tools flex-between-center">
                <bk-input
                    class="w250"
                    v-model.trim="packageNameVal"
                    :placeholder="$t('artifactPlaceHolder')"
                    clearable
                    @enter="handlerPaginationChange()"
                    @clear="handlerPaginationChange()"
                    right-icon="bk-icon icon-search">
                </bk-input>
                <div class="sort-tool flex-align-center">
                    <bk-select
                        style="width:150px;"
                        v-model="property"
                        :clearable="false"
                        @change="handlerPaginationChange()">
                        <bk-option id="name" :name="$t('nameSorting')"></bk-option>
                        <bk-option id="lastModifiedDate" :name="$t('lastModifiedTimeSorting')"></bk-option>
                        <bk-option id="downloads" :name="$t('downloadSorting')"></bk-option>
                    </bk-select>
                    <bk-popover :content="$t('toggle') + `${direction === 'ASC' ? $t('desc') : $t('asc')}`" placement="top">
                        <div class="ml10 sort-order flex-center" @click="changeDirection">
                            <Icon :name="`order-${direction.toLowerCase()}`" size="16"></Icon>
                        </div>
                    </bk-popover>
                </div>
            </div>
            <div class="common-package-list">
                <!-- 有数据 -->
                <template v-if="packageList.length">
                    <infinite-scroll
                        ref="infiniteScroll"
                        :is-loading="isLoading"
                        :has-next="packageList.length < pagination.count"
                        @load="handlerPaginationChange({ current: pagination.current + 1 }, true)">
                        <div class="mb10 list-count">{{ $t('totalVersionCount', [pagination.count])}}</div>
                        <package-card
                            class="mb10"
                            v-for="pkg in packageList"
                            :key="pkg.key"
                            :card-data="pkg"
                            :readonly="!permission.delete"
                            @click.native="showCommonPackageDetail(pkg)"
                            @delete-card="deletePackageHandler(pkg)">
                        </package-card>
                    </infinite-scroll>
                </template>
                <!-- 无数据 -->
                <template v-else>
                    <empty-data :is-loading="isLoading" ex-style="padding-top: 130px;" search></empty-data>
                </template>
            </div>
        </template>
        <!-- 不存在包 -->
        <template v-else>
            <empty-guide class="empty-guide" :article="articleGuide"></empty-guide>
        </template>

        <bk-sideslider :is-show.sync="showGuide" :quick-close="true" :width="600">
            <template #header>
                <div class="flex-align-center"><icon class="mr5" :name="repoType" size="32"></icon>{{ replaceRepoName(repoName) + $t('guide') }}</div>
            </template>
            <template #content>
                <repo-guide class="pt20 pb20 pl10 pr10" :article="articleGuide"></repo-guide>
            </template>
        </bk-sideslider>
        <iam-deny-dialog :visible.sync="showIamDenyDialog" :show-data="showData"></iam-deny-dialog>
    </div>
</template>
<script>
    import InfiniteScroll from '@repository/components/InfiniteScroll'
    import packageCard from '@repository/components/PackageCard'
    import repoGuide from '@repository/views/repoCommon/repoGuide'
    import emptyGuide from '@repository/views/repoCommon/emptyGuide'
    import repoGuideMixin from '@repository/views/repoCommon/repoGuideMixin'
    import iamDenyDialog from '@repository/components/IamDenyDialog/IamDenyDialog'
    import { mapState, mapActions } from 'vuex'
    export default {
        name: 'commonPackageList',
        components: { InfiniteScroll, packageCard, repoGuide, emptyGuide, iamDenyDialog },
        mixins: [repoGuideMixin],
        data () {
            return {
                isLoading: false,
                packageNameVal: this.$route.query.packageName,
                property: this.$route.query.property || 'lastModifiedDate',
                direction: this.$route.query.direction || 'DESC',
                packageList: [],
                pagination: {
                    current: 1,
                    limit: 20,
                    count: 0,
                    limitList: [10, 20, 40]
                },
                showGuide: false,
                showIamDenyDialog: false,
                showData: {}
            }
        },
        computed: {
            ...mapState(['repoListAll', 'permission', 'userInfo']),
            currentRepo () {
                return this.repoListAll.find(repo => repo.name === this.repoName) || {}
            }
        },
        created () {
            this.getRepoListAll({ projectId: this.projectId })
            this.handlerPaginationChange()
        },
        methods: {
            ...mapActions([
                'getRepoListAll',
                'searchPackageList',
                'deletePackage',
                'getPermissionUrl'
            ]),
            changeDirection () {
                this.direction = this.direction === 'ASC' ? 'DESC' : 'ASC'
                this.handlerPaginationChange()
            },
            handlerPaginationChange ({ current = 1, limit = this.pagination.limit } = {}, load) {
                this.pagination.current = current
                this.pagination.limit = limit
                this.getPackageListHandler(load)
                if (!load) {
                    this.$refs.infiniteScroll && this.$refs.infiniteScroll.scrollToTop()
                    this.$router.replace({
                        query: {
                            ...this.$route.query,
                            packageName: this.packageNameVal,
                            property: this.property,
                            direction: this.direction
                        }
                    })
                }
            },
            getPackageListHandler (load) {
                if (this.isLoading) return
                this.isLoading = !load
                return this.searchPackageList({
                    projectId: this.projectId,
                    repoType: this.repoType,
                    repoName: this.repoName,
                    packageName: this.packageNameVal,
                    property: this.property,
                    direction: this.direction,
                    current: this.pagination.current,
                    limit: this.pagination.limit
                }).then(({ records, totalRecords }) => {
                    load ? this.packageList.push(...records) : (this.packageList = records)
                    this.pagination.count = totalRecords
                }).catch(err => {
                    if (err.status === 403) {
                        this.getPermissionUrl({
                            body: {
                                projectId: this.projectId,
                                action: 'READ',
                                resourceType: 'REPO',
                                uid: this.userInfo.name,
                                repoName: this.repoName
                            }
                        }).then(res => {
                            if (res !== '') {
                                this.showIamDenyDialog = true
                                this.showData = {
                                    projectId: this.projectId,
                                    repoName: this.repoName,
                                    action: 'READ',
                                    url: res
                                }
                            }
                        })
                    }
                }).finally(() => {
                    this.isLoading = false
                })
            },
            deletePackageHandler (pkg) {
                this.$confirm({
                    theme: 'danger',
                    message: this.$t('deletePackageTitle', { name: '' }),
                    subMessage: pkg.key,
                    confirmFn: () => {
                        return this.deletePackage({
                            projectId: this.projectId,
                            repoType: this.repoType,
                            repoName: this.repoName,
                            packageKey: pkg.key
                        }).then(() => {
                            this.handlerPaginationChange()
                            this.$bkMessage({
                                theme: 'success',
                                message: this.$t('delete') + this.$t('space') + this.$t('success')
                            })
                        }).catch(e => {
                            if (e.status === 403) {
                                this.getPermissionUrl({
                                    body: {
                                        projectId: this.projectId,
                                        action: 'DELETE',
                                        resourceType: 'REPO',
                                        uid: this.userInfo.name,
                                        repoName: this.repoName
                                    }
                                }).then(res => {
                                    if (res !== '') {
                                        this.showIamDenyDialog = true
                                        this.showData = {
                                            projectId: this.projectId,
                                            repoName: this.repoName,
                                            action: 'DELETE',
                                            packageName: pkg.name,
                                            url: res
                                        }
                                    }
                                })
                            }
                        })
                    }
                })
            },
            showCommonPackageDetail (pkg) {
                this.$router.push({
                    name: 'commonPackage',
                    query: {
                        // 需要保留之前制品列表页的筛选项和页码相关参数
                        ...this.$route.query,
                        repoName: this.repoName,
                        packageKey: pkg.key,
                        // 此时需要将version清除掉，否则在进入仓库详情页后再返回包列表页，然后选择其他的包进入版本详情页，
                        // 会导致出现无效请求，且packageKey为最新版本的，但是版本号是之前版本的，进而导致请求出错
                        // 因为在版本详情页存在一个version的watch，只有version有值的时候才会请求版本详情
                        version: undefined
                    }
                })
            }
        }
    }
</script>
<style lang="scss" scoped>
.common-package-container {
    height: 100%;
    .common-package-header{
        height: 60px;
        background-color: white;
        .package-img {
            border-radius: 4px;
        }
        .common-package-title {
            .repo-title {
                max-width: 500px;
                font-size: 16px;
                font-weight: 500;
                color: #081E40;
            }
            // .repo-description {
            //     max-width: 70vw;
            //     padding: 5px 15px;
            //     background-color: var(--bgWeightColor);
            //     border-radius: 2px;
            // }
        }
    }
    .package-search-tools {
        padding: 10px 20px;
        background-color: white;
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
    .common-package-list {
        height: calc(100% - 150px);
        padding: 0 20px;
        background-color: white;
        .list-count {
            font-size: 12px;
            color: var(--fontSubsidiaryColor);
        }
    }
    .empty-guide {
        height: calc(100% - 100px);
        background-color: white;
        overflow-y: auto;
    }
}
</style>
