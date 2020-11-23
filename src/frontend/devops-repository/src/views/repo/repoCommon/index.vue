<template>
    <router-view :query-for-list="query"></router-view>
</template>
<script>
    import { mapMutations } from 'vuex'
    import commonMixin from './commonMixin'
    export default {
        name: 'repoCommon',
        mixins: [commonMixin],
        data () {
            return {
                query: {
                    name: '',
                    stageTag: ''
                }
            }
        },
        watch: {
            '$route.query' () {
                this.setBreadcrumb()
            }
        },
        created () {
            this.setBreadcrumb()
        },
        beforeDestroy () {
            this.SET_BREADCRUMB([])
        },
        methods: {
            ...mapMutations(['SET_BREADCRUMB']),
            searchHandler (query) {
                this.query = {
                    name: query.name,
                    stageTag: query.stageTag
                }
            },
            resetQueryAndBack () {
                this.query = {
                    name: '',
                    stageTag: ''
                }
            },
            setBreadcrumb () {
                const breadcrumb = []
                if (this.packageKey) {
                    breadcrumb.push({
                        name: this.packageKey.replace(/^.*:\/\/(?:.*:)*([^:]+)$/, '$1'),
                        value: this.packageKey,
                        cilckHandler: pkg => {
                            this.$router.push({
                                name: 'commonPackage',
                                query: {
                                    name: this.repoName,
                                    package: pkg.value
                                }
                            })
                        }
                    })
                    if (this.version) {
                        breadcrumb.push({
                            name: this.version,
                            value: this.version
                        })
                    }
                }
                this.SET_BREADCRUMB(breadcrumb)
            }
        }
    }
</script>
