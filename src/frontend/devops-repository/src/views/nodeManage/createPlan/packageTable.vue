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
                @change="packageConstraints = []">
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
            从当前仓库添加制品
        </div>
        <div class="package-list" :class="{ 'mt10': disabled }" v-show="packageConstraints.length">
            <div class="package-info flex-align-center" v-for="(pkg, ind) in packageConstraints" :key="pkg.fid">
                <Icon size="16" :name="pkg.type.toLowerCase()" />
                <span class="package-mata text-overflow" :title="pkg.repoName">{{ pkg.repoName }}</span>
                <span class="package-mata text-overflow" :title="pkg.key">{{ pkg.key }}</span>
                <span class="package-mata text-overflow" :title="pkg.versions.join(',')">{{ pkg.versions.join(',') }}</span>
                <i v-show="!disabled" class="devops-icon icon-delete hover-btn" @click="packageConstraints.splice(ind, 1)"></i>
            </div>
        </div>
        <package-dialog
            :show="showAddDialog"
            :repo="selectedRepo"
            :package-constraints="packageConstraints"
            @confirm="confirm"
            @cancel="showAddDialog = false">
        </package-dialog>
    </div>
</template>
<script>
    import { mapState } from 'vuex'
    import packageDialog from './packageDialog'
    export default {
        name: 'packageTable',
        components: { packageDialog },
        props: {
            initData: Array,
            disabled: Boolean
        },
        data () {
            return {
                showAddDialog: false,
                selectedRepoName: '',
                packageConstraints: []
            }
        },
        computed: {
            ...mapState(['repoListAll']),
            projectId () {
                return this.$route.params.projectId
            },
            repoGroupList () {
                return this.repoListAll
                    .filter(repo => repo.type !== 'GENERIC')
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
                    const { remoteRepoName, remoteProjectId, repoType, packageConstraints = [] } = JSON.parse(JSON.stringify(data))[0] || {}
                    this.selectedRepoName = remoteRepoName
                    this.packageConstraints = packageConstraints.map(pkg => ({
                        ...pkg,
                        key: pkg.packageKey,
                        repoName: remoteRepoName,
                        type: repoType,
                        fid: remoteProjectId + remoteRepoName + pkg.packageKey
                    }))
                },
                immediate: true
            }
        },
        methods: {
            confirm (pkgList) {
                this.packageConstraints = pkgList
                this.$emit('clearError')
            },
            getConfig () {
                return new Promise((resolve, reject) => {
                    const replicaTaskObjects = [{
                        localRepoName: this.selectedRepoName,
                        remoteProjectId: this.projectId,
                        remoteRepoName: this.selectedRepoName,
                        repoType: this.selectedRepo.type,
                        packageConstraints: this.packageConstraints.map(pkg => ({ packageKey: pkg.key, versions: pkg.versions }))
                    }]
                    // eslint-disable-next-line prefer-promise-reject-errors
                    this.selectedRepoName && this.packageConstraints.length ? resolve(replicaTaskObjects) : reject()
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
            padding: 0 10px;
            border-bottom: 1px solid $borderWeightColor;
            .repo-name {
                margin-left: 10px;
                max-width: 500px;
            }
        }
    }
    .repo-add {
        display: inline-flex;
        align-items: center;
    }
    .package-list {
        width: 600px;
        border: 1px solid $borderWeightColor;
        border-bottom-width: 0;
        .package-info {
            padding: 0 10px;
            font-size: 12px;
            height: 28px;
            border-bottom: 1px solid $borderWeightColor;
            .package-mata  {
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
}
</style>
