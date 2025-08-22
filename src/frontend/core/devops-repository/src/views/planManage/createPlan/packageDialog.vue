<template>
    <canway-dialog
        :value="show"
        width="800"
        height-num="561"
        :title="$t('addArtifact')"
        @cancel="$emit('cancel')"
        @confirm="confirmPackageData">
        <div class="search-package" v-bkloading="{ isLoading }">
            <bk-transfer
                :title="[$t('artifactList'), $t('selectedArtifact')]"
                :source-list="packageList"
                :target-list="targetList"
                display-key="key"
                setting-key="fid"
                searchable
                show-overflow-tips
                @change="changeSelect">
                <template #source-option="{ key, type }">
                    <div class="flex-align-center flex-1">
                        <Icon size="16" :name="type.toLowerCase()" />
                        <span class="ml10 flex-1 text-overflow" :title="key">{{ key }}</span>
                        <i class="bk-icon icon-arrows-right"></i>
                    </div>
                </template>
                <template #target-option="row">
                    <div class="flex-1 flex-align-center">
                        <Icon size="16" :name="row.type.toLowerCase()" />
                        <span class="ml10 flex-1 text-overflow" :title="row.key">{{ row.key }}</span>
                        <div class="ml10" style="width:200px" @click.stop="">
                            <bk-select
                                :placeholder="$t('pleaseSelect')"
                                size="small"
                                searchable
                                multiple
                                show-select-all
                                ext-popover-cls="show-select-all"
                                display-tag
                                :value="checkedVersions[row.fid]"
                                @change="versions => selectVersions(row, versions)">
                                <bk-option v-for="option in (versionStorage[row.fid] || [])"
                                    :key="option.name"
                                    :id="option.name"
                                    :name="option.name">
                                </bk-option>
                            </bk-select>
                        </div>
                        <i class="bk-icon icon-close"></i>
                    </div>
                </template>
            </bk-transfer>
        </div>
    </canway-dialog>
</template>
<script>
    import { mapActions } from 'vuex'
    export default {
        name: 'packageDialog',
        props: {
            show: Boolean,
            repo: Object,
            packageConstraints: Array
        },
        data () {
            return {
                isLoading: false,
                packageList: [],
                checkedPackage: [],
                checkedVersions: {},
                totalRecords: 0,
                versionStorage: {}
            }
        },
        computed: {
            projectId () {
                return this.$route.params.projectId
            },
            targetList () {
                return this.packageConstraints.map(v => v.fid)
            }
        },
        watch: {
            show (val) {
                if (!val) return
                this.checkedPackage = this.packageConstraints.slice()
                this.checkedVersions = this.checkedPackage.reduce((target, p) => {
                    target[p.fid] = p.versions
                    return target
                }, {})
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
                    packageName: '',
                    current: 1,
                    limit: 1000
                }).then(({ records, totalRecords }) => {
                    this.totalRecords = totalRecords
                    this.packageList = records.map(pkg => ({
                        ...pkg,
                        fid: pkg.projectId + pkg.repoName + pkg.key,
                        versions: []
                    }))
                }).finally(() => {
                    this.isLoading = false
                })
            },
            changeSelect (sourceList, targetList) {
                targetList.forEach(pkg => {
                    !this.versionStorage[pkg.fid] && this.handleVersionList(pkg)
                    !(pkg.fid in this.checkedVersions) && (this.checkedVersions[pkg.fid] = [])
                })
                this.checkedPackage = targetList
            },
            selectVersions (row, versions) {
                this.checkedVersions[row.fid] = versions
            },
            handleVersionList (pkg) {
                this.getVersionList({
                    projectId: pkg.projectId,
                    repoName: pkg.repoName,
                    packageKey: pkg.key,
                    current: 1,
                    limit: 1000
                }).then(({ records }) => {
                    this.$set(this.versionStorage, pkg.fid, records)
                })
            },
            async confirmPackageData () {
                const validate = this.checkedPackage.every(pkg => {
                    return this.checkedVersions[pkg.fid]?.length
                })
                if (validate) {
                    this.$emit('confirm', this.checkedPackage.map(p => ({ ...p, versions: this.checkedVersions[p.fid] })))
                    this.$emit('cancel')
                } else {
                    this.$bkMessage({
                        theme: 'error',
                        message: '请选择制品版本'
                    })
                }
            }
        }
    }
</script>
<style lang="scss" scoped>
.search-package {
    ::v-deep .bk-transfer{
        .transfer {
            left: 34.2%;
        }
        .target-list {
            flex: 2;
        }
    }
}
</style>
