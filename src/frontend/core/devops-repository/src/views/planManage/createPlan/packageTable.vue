<template>
    <div class="package-table-container">
        <bk-select
            class="w250"
            v-model="selectedRepoName"
            searchable
            :disabled="disabled"
            :placeholder="$t('selectRepoPlaceHolder')"
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
        <bk-button v-show="!disabled && selectedRepoName" class="mt10" icon="plus" @click="showAddDialog = true">{{ $t('addArtifact') }}</bk-button>
        <div class="mt10 package-list" v-show="packageConstraints.length">
            <div class="pl10 pr10 package-item flex-between-center" v-for="(pkg, ind) in packageConstraints" :key="pkg.fid">
                <div class="flex-align-center">
                    <Icon size="16" :name="pkg.type.toLowerCase()" />
                    <span class="ml5 package-meta text-overflow" :title="pkg.key">{{ pkg.key }}</span>
                </div>
                <span class="ml10 package-meta text-overflow flex-1" :title="pkg.versions.join(',')">{{ pkg.versions.join(',') }}</span>
                <Icon v-show="!disabled" class="ml10 hover-btn" size="24" name="icon-delete" @click.native="packageConstraints.splice(ind, 1)" />
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
                handler (data) {
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
        display: grid;
        grid-template: auto / repeat(2, 1fr);
        gap: 10px;
        .package-item {
            height: 32px;
            border: 1px solid var(--borderWeightColor);
            background-color: var(--bgLighterColor);
            .package-meta  {
                max-width: 250px;
            }
        }
    }
}
</style>
