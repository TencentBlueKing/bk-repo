<template>
    <bk-dialog
        :value="show"
        width="800"
        title="添加制品"
        :position="{ top: 100 }"
        :mask-close="false"
        :close-icon="false"
    >
        <div style="min-height: 400px">
            <div class="search-package">
                <div class="mb10 flex-align-center">
                    <label class="search-package-label">查询制品：</label>
                    <bk-input class="w250 search-package-display" v-model="packageName" placeholder="请输入制品名称"></bk-input>
                    <bk-button class="ml20" :loading="isLoading" theme="primary" @click="handleSearchPackage">{{$t('search')}}</bk-button>
                </div>
                <div class="ml60 package-list-total">{{ totalRecords }} 个制品匹配</div>
                <div class="ml60 package-list" v-bkloading="{ isLoading }">
                    <template v-if="packageList.length">
                        <div class="package-list-item flex-align-center" v-for="pkg in packageList" :key="pkg.fid">
                            <div class="package-repo flex-align-center" :title="pkg.repoName">
                                <Icon class="mr5" size="16" :name="pkg.type.toLowerCase()"></Icon>
                                <div class="repo-name text-overflow">{{ pkg.repoName }}</div>
                            </div>
                            <div class="package-name text-overflow" :title="pkg.key">
                                {{ pkg.key }}
                            </div>
                            <div class="package-btn flex-align-center">
                                <bk-button text size="small"
                                    :disabled="Boolean(checkedPackage.find(v => v.fid === pkg.fid))"
                                    @click="checkPackage(pkg)">
                                    {{ checkedPackage.find(v => v.fid === pkg.fid) ? '已选择' : '选择' }}
                                </bk-button>
                            </div>
                        </div>
                    </template>
                    <empty-data ex-style="margin-top: 20px" v-else></empty-data>
                </div>
                <div class="mt20 checked-package" v-if="checkedPackage.length">
                    <label style="font-size:14px">已选择制品</label>
                    <div class="package-list-header flex-align-center">
                        <div style="flex:1">制品</div>
                        <div class="flex-align-center" style="flex-basis:260px">
                            版本
                            <i v-bk-tooltips="{ content: '指制品的版本号或者镜像Tag' }" class="ml5 devops-icon icon-info-circle"></i>
                        </div>
                    </div>
                    <div class="package-list">
                        <div class="package-list-item flex-align-center" v-for="(cPkg, cIndex) in checkedPackage" :key="cPkg.fid">
                            <div class="package-repo flex-align-center" :title="cPkg.repoName">
                                <Icon class="mr5" size="16" :name="cPkg.type.toLowerCase()"></Icon>
                                <div class="repo-name text-overflow">{{ cPkg.repoName }}</div>
                            </div>
                            <div class="package-name text-overflow" :title="cPkg.key">
                                {{ cPkg.key }}
                            </div>
                            <div class="package-version">
                                <bk-select
                                    size="small"
                                    searchable
                                    multiple
                                    show-select-all
                                    display-tag
                                    :loading="cPkg.loading"
                                    v-model="cPkg.versions">
                                    <bk-option v-for="option in (versionStorage[cPkg.fid] || [])"
                                        :key="option.name"
                                        :id="option.name"
                                        :name="option.name">
                                    </bk-option>
                                </bk-select>
                            </div>
                            <div class="package-btn flex-align-center">
                                <bk-button text size="small" @click="checkedPackage.splice(cIndex, 1)">移除</bk-button>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
        <template #footer>
            <bk-button theme="primary" @click="confirmPackageData">{{$t('add')}}</bk-button>
            <bk-button @click="$emit('cancel')">{{$t('cancel')}}</bk-button>
        </template>
    </bk-dialog>
</template>
<script>
    import { mapActions } from 'vuex'
    import emptyData from '@/components/EmptyData'
    export default {
        name: 'packageDialog',
        components: { emptyData },
        props: {
            show: Boolean,
            repo: Object,
            packageConstraints: Array
        },
        data () {
            return {
                isLoading: false,
                packageName: '',
                packageList: [],
                checkedPackage: [],
                totalRecords: 0,
                versionStorage: {}
            }
        },
        computed: {
            projectId () {
                return this.$route.params.projectId
            }
        },
        watch: {
            show (val) {
                if (!val) return
                this.checkedPackage = this.packageConstraints.slice()
                this.handleSearchPackage()
            }
        },
        methods: {
            ...mapActions(['searchPackageList', 'getVersionList']),
            handleSearchPackage () {
                this.isLoading = true
                this.searchPackageList({
                    projectId: this.projectId,
                    repoType: this.repo.type,
                    repoName: this.repo.name,
                    packageName: this.packageName,
                    current: 1,
                    limit: 100
                }).then(({ records, totalRecords }) => {
                    this.totalRecords = totalRecords
                    this.packageList = records.map(pkg => ({ ...pkg, fid: pkg.projectId + pkg.repoName + pkg.key }))
                }).finally(() => {
                    this.isLoading = false
                })
            },
            checkPackage (pkg) {
                const cPkg = {
                    ...pkg,
                    loading: false,
                    versions: []
                }
                this.checkedPackage.push(cPkg)
                !this.versionStorage[pkg.fid] && this.handleVersionList(cPkg)
            },
            handleVersionList (pkg) {
                this.$set(pkg, 'loading', true)
                this.getVersionList({
                    projectId: pkg.projectId,
                    repoName: pkg.repoName,
                    packageKey: pkg.key,
                    current: 1,
                    limit: 1000
                }).then(({ records }) => {
                    this.$set(this.versionStorage, pkg.fid, records)
                }).finally(() => {
                    this.$set(pkg, 'loading', false)
                })
            },
            async confirmPackageData () {
                const validate = this.checkedPackage.every(pkg => {
                    return pkg.versions.length
                })
                if (validate) {
                    this.$emit('confirm', this.checkedPackage)
                    this.$emit('cancel')
                } else {
                    this.$bkMessage({
                        theme: 'error',
                        message: '请选择包版本'
                    })
                }
            }
        }
    }
</script>
<style lang="scss" scoped>
@import '@/scss/conf';
.search-package {
    font-size: 12px;
    &-label {
        width: 60px;
        text-align: right;
    }
    &-main {
        flex: 1;
    }
    .ml60 {
        margin-left: 60px;
    }
}
.package-list-header {
    min-height: 32px;
    padding: 5px 10px;
    font-size: 12px;
    color: $fontLigtherColor;
    border-bottom: 1px solid $borderWeightColor;
}
.package-list {
    max-height: 185px;
    overflow-y: auto;
    &-total {
        color: $primaryColor;
    }
    &-item {
        min-height: 32px;
        padding: 5px 10px;
        font-size: 12px;
        color: $fontBoldColor;
        border-bottom: 1px solid $borderWeightColor;
        .package-repo {
            flex-basis: 150px;
            .repo-name {
                width: 120px;
            }
        }
        .package-name {
            flex: 1;
            margin: 0 10px;
        }
        .package-version {
            flex-basis: 200px;
        }
        .package-btn {
            justify-content: flex-end;
            flex-basis: 60px;
        }
    }
}
</style>
