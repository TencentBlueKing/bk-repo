<template>
    <div class="path-table-container">
        <bk-select
            class="w250"
            v-model="selectedRepoName"
            searchable
            :disabled="disabled"
            :placeholder="$t('selectRepoPlaceHolder')"
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
        <bk-button v-show="!disabled && selectedRepoName" class="mt10" icon="plus" @click="showAddDialog = true">
            {{ $t('addFilePath') }}</bk-button>
        <div class="mt10 path-list" v-show="pathConstraints.length">
            <div class="pl10 pr10 path-item flex-between-center" v-for="(path, ind) in pathConstraints" :key="path">
                <span class="path-name text-overflow" :title="path">{{ path }}</span>
                <Icon v-show="!disabled" class="ml10 hover-btn" size="24" name="icon-delete" @click.native="pathConstraints.splice(ind, 1)" />
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
                    .filter(r => {
                        return ['GENERIC'].includes(r.type)
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
.path-table-container {
    .path-list {
        display: grid;
        grid-template: auto / repeat(3, 1fr);
        gap: 10px;
        .path-item {
            height: 32px;
            border: 1px solid var(--borderWeightColor);
            background-color: var(--bgLighterColor);
            .path-name {
                max-width: 240px;
                margin-left: 5px;
            }
        }
    }
}
</style>
