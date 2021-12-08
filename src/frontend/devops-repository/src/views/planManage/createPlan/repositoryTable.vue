<template>
    <div class="repository-table-container">
        <div v-show="!disabled" class="repo-add flex-center" @click="showAddDialog = true">
            <i class="mr5 devops-icon icon-plus-circle"></i>
            添加仓库
        </div>
        <div v-show="replicaTaskObjects.length" class="mt10 repo-list">
            <div class="pl20 repo-item flex-align-center" v-for="(repo, ind) in replicaTaskObjects" :key="repo.fid">
                <div class="flex-align-center">
                    <Icon size="16" :name="repo.type.toLowerCase()" />
                    <span class="repo-name text-overflow" :title="repo.name">{{ repo.name }}</span>
                </div>
                <i v-show="!disabled" class="devops-icon icon-delete flex-center hover-btn hover-danger" @click="replicaTaskObjects.splice(ind, 1)"></i>
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
            initData: Array,
            disabled: Boolean
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
                handler: function (data) {
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
        width: 600px;
        border: 1px solid var(--borderWeightColor);
        border-bottom-width: 0;
        .repo-item {
            justify-content: space-between;
            height: 32px;
            border-bottom: 1px solid var(--borderWeightColor);
            background-color: var(--bgLighterColor);
            .repo-name {
                flex: 1;
                margin: 0 5px;
            }
            .icon-delete {
                width: 50px;
                height: 100%;
                background-color: var(--bgHoverColor);
            }
        }
    }
    .repo-add {
        width: 120px;
        height: 32px;
        color: var(--primaryColor);
        background-color: var(--bgHoverColor);
        cursor: pointer;
    }
}
</style>
