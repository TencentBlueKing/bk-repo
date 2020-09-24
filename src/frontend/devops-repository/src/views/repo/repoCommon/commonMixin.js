export default {
    computed: {
        projectId () {
            return this.$route.params.projectId
        },
        repoType () {
            return this.$route.params.repoType
        },
        repoName () {
            return this.$route.query.name
        },
        packageKey () {
            return this.$route.query.package
        },
        version () {
            return this.$route.query.version
        }
    }
}
