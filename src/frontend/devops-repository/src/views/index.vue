<template>
    <router-view></router-view>
</template>
<script>
    export default {
        beforeRouteEnter (to, from, next) {
            const projectId = to.params.projectId
            const repositoryHistory = JSON.parse(localStorage.getItem('repositoryHistory') || '{}')[projectId] || { type: 'generic', name: 'custom' }
            if (to.name !== 'repoList') {
                next()
            } else {
                next({
                    name: repositoryHistory.type,
                    params: to.params,
                    query: {
                        name: repositoryHistory.name
                    }
                })
            }
        }
    }
</script>
