<template>
    <div class="repository-table-container">
        <div v-show="replicaTaskObjects.length" class="repo-list">
            <div class="repo-item flex-align-center" v-for="(repo, ind) in replicaTaskObjects" :key="repo.fid">
                <div class="flex-align-center">
                    <Icon size="16" :name="repo.type.toLowerCase()" />
                    <span class="repo-name text-overflow" :title="repo.name">{{ repo.name }}</span>
                </div>
                <i v-show="!disabled" class="devops-icon icon-delete hover-btn" @click="replicaTaskObjects.splice(ind, 1)"></i>
            </div>
        </div>
        <div v-show="!disabled" class="repo-add hover-btn" @click="showAddDialog = true">
            <i class="mr5 devops-icon icon-plus-square"></i>
            添加仓库
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
@import '@/scss/conf';
.repository-table-container {
    .repo-list {
        width: 600px;
        border: 1px solid $borderWeightColor;
        border-bottom-width: 0;
        .repo-item {
            justify-content: space-between;
            height: 28px;
            padding: 0 10px;
            font-size: 12px;
            border-bottom: 1px solid $borderWeightColor;
            .repo-name {
                flex: 1;
                margin: 0 5px;
            }
            &:hover {
                color: $primaryColor;
                background-color: #e1ecff;
            }
            .icon-delete {
                font-size: 16px;
            }
        }
    }
    .repo-add {
        display: inline-flex;
        align-items: center;
    }
}
</style>
