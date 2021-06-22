<template>
    <div class="repository-table-container">
        <div class="flex-align-center">
            <label class="mr5">源仓库：</label>
            <bk-select
                class="w250"
                v-model="selectedRepoName"
                searchable
                :disabled="disabled"
                placeholder="请选择源仓库"
                @change="pathConstraints = []">
                <bk-option-group
                    v-for="(list, type) in repoGroupList"
                    :name="type.toLowerCase()"
                    :key="type"
                    show-collapse>
                    <bk-option v-for="option in list"
                        :key="option.name"
                        :id="option.name"
                        :name="option.name">
                    </bk-option>
                </bk-option-group>
            </bk-select>
        </div>
        <div v-show="!disabled && selectedRepoName" class="repo-add hover-btn" @click="showAddDialog = true">
            <i class="mr5 devops-icon icon-plus-square"></i>
            添加文件路径
        </div>
        <div class="repo-list" :class="{ 'mt10': disabled }" v-show="pathConstraints.length">
            <div class="repo-item flex-align-center" v-for="(path, ind) in pathConstraints" :key="path">
                <span class="repo-name text-overflow" :title="path">{{ path }}</span>
                <i v-show="!disabled" class="devops-icon icon-delete hover-btn" @click="pathConstraints.splice(ind, 1)"></i>
            </div>
        </div>
        <path-dialog :show="showAddDialog" :path-constraints="pathConstraints" @confirm="confirm" @cancel="showAddDialog = false"></path-dialog>
    </div>
</template>
<script>
    import { mapState } from 'vuex'
    import pathDialog from './pathDialog'
    export default {
        name: 'pathTable',
        components: { pathDialog },
        props: {
            initData: Array,
            disabled: Boolean
        },
        data () {
            return {
                showAddDialog: false,
                selectedRepoName: '',
                pathConstraints: []
            }
        },
        computed: {
            ...mapState(['repoListAll']),
            projectId () {
                return this.$route.params.projectId
            },
            repoGroupList () {
                return this.repoListAll
                    .filter(repo => repo.type === 'GENERIC')
                    .reduce((target, repo) => {
                        if (!target[repo.type]) target[repo.type] = []
                        target[repo.type].push(repo)
                        return target
                    }, {})
            },
            selectedRepo () {
                return this.repoListAll.find(v => v.name === this.selectedRepoName) || {}
            }
        },
        watch: {
            initData: {
                handler: function (data) {
                    const { remoteRepoName, pathConstraints = [] } = JSON.parse(JSON.stringify(data))[0] || {}
                    this.selectedRepoName = remoteRepoName
                    this.pathConstraints = pathConstraints.map(v => v.path)
                },
                immediate: true
            }
        },
        methods: {
            confirm (pathConstraints) {
                this.pathConstraints = pathConstraints
                this.$emit('clearError')
            },
            getConfig () {
                return new Promise((resolve, reject) => {
                    const replicaTaskObjects = [{
                        localRepoName: this.selectedRepoName,
                        remoteProjectId: this.projectId,
                        remoteRepoName: this.selectedRepoName,
                        repoType: this.selectedRepo.type,
                        pathConstraints: this.pathConstraints.map(path => ({ path }))
                    }]
                    // eslint-disable-next-line prefer-promise-reject-errors
                    this.selectedRepoName && this.pathConstraints.length ? resolve(replicaTaskObjects) : reject()
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
