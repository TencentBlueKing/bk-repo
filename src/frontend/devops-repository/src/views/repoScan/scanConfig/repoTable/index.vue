<template>
    <div class="repository-table-container">
        <bk-radio-group v-model="showAddBtn">
            <bk-radio :disabled="disabled" :value="false">{{ $t('allRepo') }}</bk-radio>
            <bk-radio :disabled="disabled" class="mt10" :value="true">
                <span>{{ $t('specifiedRepo') }}</span>
                <bk-button v-show="showAddBtn && !disabled" class="ml10" icon="plus" @click="showAddDialog = true">
                    {{ $t('addRepo') }}</bk-button>
            </bk-radio>
        </bk-radio-group>
        <div v-show="showAddBtn && defaultRepos.length" class="mt10 repo-list">
            <div class="pl10 pr10 repo-item flex-between-center" v-for="(repo, ind) in repoList" :key="repo.name">
                <div class="flex-align-center">
                    <Icon size="16" :name="repo.type.toLowerCase()" />
                    <span class="repo-name text-overflow" :title="repo.name">{{ repo.name }}</span>
                </div>
                <Icon v-show="!disabled" class="ml10 hover-btn" size="24" name="icon-delete" @click.native="defaultRepos.splice(ind, 1)" />
            </div>
        </div>
        <repo-dialog :show="showAddDialog"
            :default-repos="defaultRepos"
            :repo-list="repoListLimit"
            @confirm="confirm"
            @cancel="showAddDialog = false">
        </repo-dialog>
    </div>
</template>
<script>
    import repoDialog from './repoDialog'
    import { mapState, mapActions } from 'vuex'
    export default {
        name: 'repoTable',
        components: { repoDialog },
        props: {
            initData: {
                type: Array,
                default: () => []
            },
            disabled: {
                type: Boolean,
                default: false
            },
            scanType: String
        },
        data () {
            return {
                showAddDialog: false,
                showAddBtn: false,
                defaultRepos: []
            }
        },
        computed: {
            ...mapState(['repoListAll']),
            projectId () {
                return this.$route.params.projectId
            },
            repoList () {
                return this.defaultRepos.map(name => this.repoListAll.find(r => r.name === name)).filter(Boolean)
            },
            repoListLimit () {
                const repoTypeLimit = [this.scanType.replace(/^([A-Z]+).*$/, '$1')]
                return this.repoListAll
                    .filter(r => repoTypeLimit.includes(r.type))
                    .sort((a, b) => {
                        return Boolean(a.type > b.type) || -1
                    })
            }
        },
        watch: {
            initData: {
                handler (data) {
                    this.defaultRepos = [...data]
                    this.showAddBtn = Boolean(data.length)
                },
                immediate: true
            }
        },
        created () {
            this.getRepoListAll({ projectId: this.projectId })
        },
        methods: {
            ...mapActions(['getRepoListAll']),
            confirm (data) {
                this.defaultRepos = data
                this.$emit('clearError')
            },
            getConfig () {
                return new Promise((resolve, reject) => {
                    if (!this.showAddBtn) resolve([])
                    else {
                        this.defaultRepos.length ? resolve(this.defaultRepos) : reject(new Error())
                    }
                })
            }
        }
    }
</script>
<style lang="scss" scoped>
.repository-table-container {
    ::v-deep .bk-form-radio {
        height: 32px;
        display: flex;
        align-items: center;
        .bk-radio-text {
            display: flex;
            align-items: center;
        }
    }
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
