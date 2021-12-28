<template>
    <div class="package-table-container">
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
        <div v-show="!disabled && selectedRepoName" class="mt10 package-add flex-center" @click="showAddDialog = true">
            <i class="mr5 devops-icon icon-plus-circle"></i>
            添加制品
        </div>
        <div class="mt10 package-list" v-show="packageConstraints.length">
            <div class="pl20 package-item flex-align-center" v-for="(pkg, ind) in packageConstraints" :key="pkg.fid">
                <Icon size="16" :name="pkg.type.toLowerCase()" />
                <span class="package-meta text-overflow" :title="pkg.key">{{ pkg.key }}</span>
                <span class="package-meta text-overflow" :title="pkg.versions.join(',')">{{ pkg.versions.join(',') }}</span>
                <i v-show="!disabled" class="devops-icon icon-delete flex-center hover-btn hover-danger" @click="packageConstraints.splice(ind, 1)"></i>
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
                    .filter(r => {
                        return ['DOCKER', 'MAVEN', 'NPM'].includes(r.type)
                    })
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
.package-table-container {
    .package-list {
        width: 600px;
        border: 1px solid var(--borderWeightColor);
        border-bottom-width: 0;
        .package-item {
            justify-content: space-between;
            height: 32px;
            border-bottom: 1px solid var(--borderWeightColor);
            background-color: var(--bgLighterColor);
            .package-meta  {
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
    .package-add {
        width: 120px;
        height: 32px;
        color: var(--primaryColor);
        background-color: var(--bgHoverColor);
        cursor: pointer;
    }
}
</style>
