<template>
    <div class="repo-npm-container" v-bkloading="{ isLoading }">
        <npm-version-detail v-if="npmVersion"></npm-version-detail>
        <npm-detail v-else-if="npmName"
            :version-list="currentNpmPkg.versionList || []"
            @show-version-detail="showNpmPkgVersionDetail">
        </npm-detail>
        <main v-else-if="npmPkgList.length" class="repo-npm-main">
            <bk-table
                class="repo-npm-table"
                :data="npmPkgList"
                height="100%"
                :outer-border="false"
                :row-border="false"
                size="small"
                :pagination="pagination"
                @row-click="showNpmPkgDetail"
                @page-change="current => handlerPaginationChange({ current })"
                @page-limit-change="limit => handlerPaginationChange({ limit })"
            >
                <bk-table-column :label="$t('packageName')" prop="name"></bk-table-column>
                <bk-table-column :label="$t('latestVersion')" prop="latest"></bk-table-column>
                <bk-table-column :label="$t('artiStatus')">
                    <template v-if="props.row.stageTag" slot-scope="props">
                        <span class="mr5 repo-tag" v-for="tag in props.row.stageTag.split(',')"
                            :key="props.row.fullPath + tag">{{ tag }}</span>
                    </template>
                </bk-table-column>
                <bk-table-column :label="$t('size')">
                    <template slot-scope="props">
                        <bk-button text
                            v-show="props.row.folder && !props.row.hasOwnProperty('folderSize')"
                            :disabled="props.row.sizeLoading"
                            @click="calculateFolderSize(props.row)">{{ $t('calculate') }}</bk-button>
                        <span v-show="!props.row.folder || props.row.hasOwnProperty('folderSize')">
                            {{ convertFileSize(props.row.size || props.row.folderSize || 0) }}
                        </span>
                    </template>
                </bk-table-column>
                <bk-table-column :label="$t('lastModifiedDate')">
                    <template slot-scope="props">{{ new Date(props.row.lastModifiedDate).toLocaleString() }}</template>
                </bk-table-column>
                <bk-table-column :label="$t('lastModifiedBy')" prop="lastModifiedBy"></bk-table-column>
                <bk-table-column :label="$t('operation')" width="80">
                    <template slot-scope="props">
                        <i class="devops-icon icon-delete hover-btn" @click.stop="deleNpmPkg(props.row)"></i>
                    </template>
                </bk-table-column>
            </bk-table>
        </main>
        <div v-else class="flex-column flex-center">{{ $t('noData') }}</div>
    </div>
</template>
<script>
    import { convertFileSize } from '@/utils'
    import { mapMutations, mapActions } from 'vuex'
    import npmDetail from './npmDetail'
    import npmVersionDetail from './npmVersionDetail'
    export default {
        name: 'repoNpm',
        components: {
            npmDetail,
            npmVersionDetail
        },
        data () {
            return {
                convertFileSize,
                isLoading: false,
                npmPkgList: [],
                pagination: {
                    count: 1,
                    current: 1,
                    limit: 10,
                    'limit-list': [10, 20, 40]
                },
                query: {
                    name: '',
                    stageTag: ''
                }
            }
        },
        computed: {
            projectId () {
                return this.$route.params.projectId
            },
            repoName () {
                return this.$route.query.name
            },
            npmName () {
                return this.$route.query.npm
            },
            npmVersion () {
                return this.$route.query.version
            },
            currentNpmPkg () {
                return this.npmPkgList.find(npm => npm.name === this.npmName) || {}
            },
            currentVersion () {
                return (this.currentNpmPkg.versionList || []).find(v => v.version === this.npmVersion)
            }

        },
        watch: {
            '$route.query' () {
                this.setBreadcrumb()
            },
            '$route.query.name' (repoName) {
                this.handlerPaginationChange()
            },
            '$route.query.npm' (npmName) {
                if (!npmName) return
                this.getVersionList()
            }
        },
        created () {
            this.handlerPaginationChange().then(() => {
                this.getVersionList()
            })
        },
        beforeDestroy () {
            this.SET_BREADCRUMB([])
        },
        methods: {
            ...mapMutations(['SET_BREADCRUMB']),
            ...mapActions([
                'getNpmPkgList',
                'deleteNpmPkg',
                'getNpmPkgVersionList',
                'deleteNpmPkgVersion'
            ]),
            searchHandler (query) {
                if (query) {
                    this.query = {
                        name: query.name[0] || '',
                        stageTag: query.stageTag[0] || ''
                    }
                }
                this.isLoading = true
                return this.getNpmPkgList({
                    projectId: this.projectId,
                    repoName: this.repoName,
                    current: this.pagination.current,
                    limit: this.pagination.limit
                }).then(({ records, totalRecords }) => {
                    this.npmPkgList = records
                    this.pagination.count = totalRecords
                    this.setBreadcrumb()
                }).finally(() => {
                    this.isLoading = false
                })
            },
            resetQueryAndBack () {
                this.query = {
                    name: '',
                    stageTag: ''
                }
                this.handlerPaginationChange()
            },
            handlerPaginationChange ({ current = 1, limit = this.pagination.limit } = {}) {
                this.pagination.current = current
                this.pagination.limit = limit
                return this.searchHandler()
            },
            getVersionList () {
                if (!this.npmName) return
                const npmPkg = this.npmPkgList.find(v => v.name === this.npmName)
                this.getNpmPkgVersionList({
                    projectId: this.projectId,
                    repoName: this.repoName,
                    npmName: this.npmName
                }).then(({ records }) => {
                    this.$set(npmPkg, 'versionList', records)
                    this.version && this.setBreadcrumb()
                })
            },
            showNpmPkgDetail (row) {
                this.$router.push({
                    path: this.$route.path,
                    query: {
                        name: this.$route.query.name,
                        npm: row.name
                    }
                })
            },
            showNpmPkgVersionDetail (version) {
                this.$router.push({
                    path: this.$route.path,
                    query: {
                        name: this.repoName,
                        npm: this.npmName,
                        version
                    }
                })
            },
            deleNpmPkg (row) {
                this.$bkInfo({
                    type: 'error',
                    title: this.$t('deleteNpmTitle', [row.name]),
                    subTitle: this.$t('deleteNpmSubTitle'),
                    showFooter: true,
                    confirmFn: () => {
                        // this.deleteDocker({
                        //     projectId: this.projectId,
                        //     repoName: this.repoName,
                        //     dockerName: row.name
                        // }).then(data => {
                        //     this.searchHandler()
                        //     this.$bkMessage({
                        //         theme: 'success',
                        //         message: this.$t('delete') + this.$t('success')
                        //     })
                        // })
                    }
                })
            },
            setBreadcrumb () {
                const breadcrumb = []
                if (this.npmName) {
                    breadcrumb.push({
                        name: this.npmName,
                        value: this.npmName,
                        list: this.npmPkgList.map(v => ({ name: v.name, value: v.name })),
                        changeHandler: name => {
                            this.showNpmPkgDetail(this.npmPkgList.find(v => v.name === name))
                        },
                        cilckHandler: this.showNpmPkgDetail
                    })
                    if (this.npmVersion && this.currentNpmPkg.versionList) {
                        breadcrumb.push({
                            name: this.npmVersion,
                            value: this.npmVersion,
                            list: this.currentNpmPkg.versionList.map(v => ({ name: v.version, value: v.version })),
                            changeHandler: name => {
                                this.showNpmPkgVersionDetail(name)
                            }
                        })
                    }
                }
                this.SET_BREADCRUMB(breadcrumb)
            }
        }
    }
</script>
<style lang="scss" scoped>
.repo-npm-container {
    .repo-npm-main {
        height: 100%;
        .repo-npm-table {
            .icon-delete {
                font-size: 16px;
            }
        }
    }
}
</style>
