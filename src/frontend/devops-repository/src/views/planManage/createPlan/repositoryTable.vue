<template>
    <div class="repository-table-container">
        <bk-button v-show="!disabled" icon="plus" @click="showAddDialog = true">{{ $t('addRepo') }}</bk-button>
        <div v-show="replicaTaskObjects.length" class="mt10 repo-list">
            <div class="pl10 pr10 repo-item flex-between-center" v-for="(repo, ind) in replicaTaskObjects" :key="repo.fid">
                <div class="flex-align-center">
                    <Icon size="16" :name="repo.type.toLowerCase()" />
                    <span class="repo-name text-overflow" :title="repo.name">{{ repo.name }}</span>
                </div>
                <Icon v-show="!disabled" class="ml10 hover-btn" size="24" name="icon-delete" @click.native="replicaTaskObjects.splice(ind, 1)" />
            </div>
        </div>
        <repo-dialog :show="showAddDialog" :replica-task-objects="replicaTaskObjects" @confirm="confirm" @cancel="showAddDialog = false"></repo-dialog>
    </div>
</template>
<script>
    import repoDialog from './repoDialog'
    export default {
        name: 'repositoryTable',
        components: { repoDialog },
        props: {
            initData: {
                type: Array,
                default: () => []
            },
            disabled: {
                type: Boolean,
                default: false
            }
        },
        data () {
            return {
                showAddDialog: false,
                replicaTaskObjects: []
            }
        },
        computed: {
            projectId () {
                return this.$route.params.projectId
            }
        },
        watch: {
            initData: {
                handler (data) {
                    this.replicaTaskObjects = JSON.parse(JSON.stringify(data)).map(repo => {
                        return {
                            ...repo,
                            type: repo.repoType,
                            name: repo.remoteRepoName,
                            projectId: this.projectId,
                            fid: repo.remoteProjectId + repo.remoteRepoName
                        }
                    })
                },
                immediate: true
            }
        },
        methods: {
            confirm (repoList) {
                this.replicaTaskObjects = repoList
                this.$emit('clearError')
            },
            getConfig () {
                return new Promise((resolve, reject) => {
                    const replicaTaskObjects = this.replicaTaskObjects.map(v => {
                        return {
                            localRepoName: v.name,
                            remoteProjectId: v.projectId,
                            remoteRepoName: v.name,
                            repoType: v.type
                        }
                    })
                    // eslint-disable-next-line prefer-promise-reject-errors
                    replicaTaskObjects.length ? resolve(replicaTaskObjects) : reject()
                })
            }
        }
    }
</script>
<style lang="scss" scoped>
.repository-table-container {
    .repo-list {
        display: grid;
        grid-template: auto / repeat(4, 1fr);
        gap: 10px;
        .repo-item {
            height: 32px;
            border: 1px solid var(--borderWeightColor);
            background-color: var(--bgLighterColor);
            .repo-name {
                max-width: 160px;
                margin-left: 5px;
            }
        }
    }
}
</style>
