<template>
    <div class="common-package-list-container" v-bkloading="{ isLoading }">
        <template v-if="packageList.length">
            <main class="mb10 common-package-main">
                <package-card
                    class="mb20"
                    v-for="pkg in packageList"
                    :key="pkg.key"
                    :card-data="pkg"
                    @click.native="showCommonPackageDetail(pkg)"
                    @delete-card="deletePackageHandler(pkg)">
                </package-card>
            </main>
            <bk-pagination
                class="repo-common-pagination"
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
        <empty-guide v-else class="empty-guide" :article="articleGuide"></empty-guide>
    </div>
</template>
<script>
    import packageCard from './packageCard'
    import emptyGuide from './emptyGuide'
    import commonMixin from './commonMixin'
    import repoGuideMixin from '../repoGuideMixin'
    import { mapActions } from 'vuex'
    export default {
        name: 'commonPackageList',
        components: { packageCard, emptyGuide },
        mixins: [commonMixin, repoGuideMixin],
        props: {
            queryForList: {
                type: Object,
                default: {}
            }
        },
        data () {
            return {
                isLoading: false,
                pagination: {
                    current: 1,
                    limit: 10,
                    count: 20,
                    limitList: [10, 20, 40]
                },
                packageList: []
            }
        },
        watch: {
            'queryForList.name' () {
                this.handlerPaginationChange()
            },
            repoName () {
                this.handlerPaginationChange()
            }
        },
        created () {
            this.getPackageListHandler().catch(() => {
                this.$router.push({
                    name: 'repoList'
                })
            })
        },
        methods: {
            ...mapActions([
                'getPackageList',
                'deletePackage'
            ]),
            handlerPaginationChange ({ current = 1, limit = this.pagination.limit } = {}) {
                this.pagination.current = current
                this.pagination.limit = limit
                this.getPackageListHandler()
            },
            getPackageListHandler () {
                this.isLoading = true
                return this.getPackageList({
                    projectId: this.projectId,
                    repoName: this.repoName,
                    current: this.pagination.current,
                    limit: this.pagination.limit,
                    packageName: this.queryForList.name
                }).then(({ records, totalRecords }) => {
                    this.packageList = records
                    this.pagination.count = totalRecords
                }).finally(() => {
                    this.isLoading = false
                })
            },
            deletePackageHandler (pkg) {
                this.$bkInfo({
                    type: 'error',
                    title: this.$t('deletePackageTitle', [pkg.type, pkg.name]),
                    subTitle: this.$t('deletePackageSubTitle', [pkg.name]),
                    showFooter: true,
                    confirmFn: () => {
                        this.deletePackage({
                            projectId: this.projectId,
                            repoType: this.repoType,
                            repoName: this.repoName,
                            packageKey: pkg.key
                        }).then(data => {
                            this.handlerPaginationChange()
                            this.$bkMessage({
                                theme: 'success',
                                message: this.$t('delete') + this.$t('success')
                            })
                        })
                    }
                })
            },
            showCommonPackageDetail (pkg) {
                this.$router.push({
                    name: 'commonPackage',
                    query: {
                        name: this.repoName,
                        package: pkg.key
                    }
                })
            }
        }
    }
</script>
<style lang="scss" scoped>
@import '@/scss/conf';
.common-package-list-container {
    .common-package-main {
        height: calc(100% - 42px);
        flex: 1;
        border-bottom: 1px solid $borderWeightColor;
    }
    .empty-guide {
        height: 100%;
        flex: 1;
        overflow-y: auto;
    }
}
</style>
