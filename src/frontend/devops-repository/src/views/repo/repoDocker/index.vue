<template>
    <div class="repo-docker-container flex-column">
        <template v-if="$route.query.tag">
            <docker-tag-detail :tag="currentTag"></docker-tag-detail>
        </template>
        <template v-else-if="$route.query.docker">
            <docker-detail
                :docker="currentDocker"
                :docker-tag-list="currentDocker.tagList || []"
                @delete-tag="deleteTag"
                @show-tag-detail="showDockerTagDetail">
            </docker-detail>
        </template>
        <template v-else-if="dockerList.length">
            <main class="mb10 repo-docker-main" v-bkloading="{ isLoading }">
                <docker-card
                    class="mb20"
                    v-for="docker in dockerList"
                    @click.native="showDockerDetail(docker)"
                    @delete-docker="deleteDockerHandler(docker)"
                    :key="docker.name"
                    :card-data="docker">
                </docker-card>
            </main>
            <bk-pagination
                class="repo-docker-pagination"
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
        <template v-else>
            <div class="flex-column flex-center">{{ $t('noData') }}</div>
        </template>
    </div>
</template>
<script>
    import dockerCard from './dockerCard'
    import dockerDetail from './dockerDetail'
    import dockerTagDetail from './dockerTagDetail'
    import { mapMutations, mapActions } from 'vuex'
    export default {
        name: 'repoDcoker',
        components: { dockerCard, dockerDetail, dockerTagDetail },
        data () {
            return {
                isLoading: false,
                pagination: {
                    current: 1,
                    limit: 10,
                    count: 20,
                    limitList: [10, 20, 40]
                },
                dockerList: [],
                queryName: ''
            }
        },
        computed: {
            projectId () {
                return this.$route.params.projectId
            },
            repoName () {
                return this.$route.query.name
            },
            currentDocker () {
                return this.dockerList.find(docker => docker.name === this.$route.query.docker) || {}
            },
            currentTag () {
                return ((this.currentDocker || {}).tagList || []).find(v => v.tag === this.$route.query.tag)
            }
        },
        watch: {
            '$route.query' () {
                this.setBreadcrumb()
            },
            '$route.query.name' (repoName) {
                this.initPage()
            },
            '$route.query.docker' (dockerName) {
                if (!dockerName) return
                this.getTagList()
            }
        },
        async mounted () {
            this.initPage()
        },
        beforeDestroy () {
            this.SET_BREADCRUMB([])
        },
        methods: {
            ...mapMutations(['SET_BREADCRUMB']),
            ...mapActions([
                'getDockerList',
                'deleteDocker',
                'getDockerTagList',
                'deleteDockerTag'
            ]),
            initPage () {
                this.searchHandler()
            },
            searchHandler (query) {
                if (query) this.queryName = query.name.join('')
                this.isLoading = true
                this.getDockerList({
                    projectId: this.projectId,
                    repoName: this.repoName,
                    current: this.pagination.current,
                    limit: this.pagination.limit,
                    dockerName: this.queryName
                }).then(({ records, totalRecords }) => {
                    this.dockerList = records
                    this.pagination.count = totalRecords
                    this.setBreadcrumb()
                    this.getTagList()
                }).finally(() => {
                    this.isLoading = false
                })
            },
            deleteDockerHandler (docker) {
                this.$bkInfo({
                    type: 'error',
                    title: this.$t('deleteDockerTitle', [docker.name]),
                    subTitle: this.$t('deleteRepoSubTitle'),
                    showFooter: true,
                    confirmFn: () => {
                        this.deleteDocker({
                            projectId: this.projectId,
                            repoName: this.repoName,
                            dockerName: docker.name
                        }).then(data => {
                            this.searchHandler()
                            this.$bkMessage({
                                theme: 'success',
                                message: this.$t('delete') + this.$t('success')
                            })
                        })
                    }
                })
            },
            deleteTag (tagName) {
                this.$bkInfo({
                    type: 'error',
                    title: this.$t('deleteTagTitle', [tagName]),
                    subTitle: this.$t('deleteRepoSubTitle'),
                    showFooter: true,
                    confirmFn: () => {
                        this.deleteDockerTag({
                            projectId: this.projectId,
                            repoName: this.repoName,
                            dockerName: this.$route.query.docker,
                            tagName
                        }).then(data => {
                            this.getTagList()
                            this.$bkMessage({
                                theme: 'success',
                                message: this.$t('delete') + this.$t('success')
                            })
                        })
                    }
                })
            },
            resetQueryAndBack () {
                this.queryName = ''
                this.initPage()
            },
            handlerPaginationChange ({ current = 1, limit = this.pagination.limit }) {
                this.pagination.current = current
                this.pagination.limit = limit
                this.searchHandler()
            },
            showDockerDetail (docker) {
                this.$router.push({
                    path: this.$route.path,
                    query: {
                        name: this.$route.query.name,
                        docker: docker.name
                    }
                })
            },
            getTagList () {
                if (!this.$route.query.docker) return
                const docker = this.dockerList.find(v => v.name === this.$route.query.docker)
                this.getDockerTagList({
                    projectId: this.projectId,
                    repoName: this.repoName,
                    dockerName: this.currentDocker.name
                }).then(({ records }) => {
                    this.$set(docker, 'tagList', records)
                    this.setBreadcrumb()
                })
            },
            showDockerTagDetail (tag) {
                this.$router.push({
                    path: this.$route.path,
                    query: {
                        name: this.$route.query.name,
                        docker: this.currentDocker.name,
                        tag: tag.tag
                    }
                })
            },
            setBreadcrumb () {
                const breadcrumb = []
                const dockerName = this.$route.query.docker
                const tagName = this.$route.query.tag
                if (dockerName) {
                    breadcrumb.push({
                        name: dockerName,
                        value: dockerName,
                        list: this.dockerList.map(v => ({ name: v.name, value: v.name })),
                        changeHandler: name => {
                            this.showDockerDetail(this.dockerList.find(v => v.name === name))
                        },
                        cilckHandler: this.showDockerDetail
                    })
                    if (tagName && this.currentDocker && this.currentDocker.tagList) {
                        breadcrumb.push({
                            name: tagName,
                            value: tagName,
                            list: this.currentDocker.tagList.map(v => ({ name: v.tag, value: v.tag })),
                            changeHandler: name => {
                                this.showDockerTagDetail(this.currentDocker.tagList.find(v => v.tag === name))
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
@import '@/scss/conf';
.repo-docker-container {
    .repo-docker-main {
        flex: 1;
        border-bottom: 1px solid $borderWeightColor;
    }
}
</style>
