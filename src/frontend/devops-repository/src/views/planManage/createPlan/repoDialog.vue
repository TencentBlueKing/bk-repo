<template>
    <canway-dialog
        :value="show"
        width="800"
        height-num="561"
        :title="title"
        @cancel="$emit('cancel')"
        @confirm="confirmPackageData">
        <bk-transfer
            :title="[$t('repositoryList'), $t('selectedRepo')]"
            :source-list="repoList"
            :target-list="targetList"
            display-key="name"
            setting-key="fid"
            searchable
            show-overflow-tips
            @change="changeSelect">
            <template #source-option="{ name, type }">
                <div class="flex-align-center flex-1">
                    <Icon size="16" :name="type.toLowerCase()" />
                    <span class="ml10 flex-1 text-overflow" :title="name">{{ name }}</span>
                    <i class="bk-icon icon-arrows-right"></i>
                </div>
            </template>
            <template #target-option="{ name, type }">
                <div class="flex-align-center flex-1">
                    <Icon size="16" :name="type.toLowerCase()" />
                    <span class="ml10 flex-1 text-overflow" :title="name">{{ name }}</span>
                    <i class="bk-icon icon-close"></i>
                </div>
            </template>
        </bk-transfer>
    </canway-dialog>
</template>
<script>
    import { mapState } from 'vuex'
    export default {
        name: 'repoDialog',
        props: {
            show: Boolean,
            replicaTaskObjects: Array
        },
        data () {
            return {
                checkedRepository: [],
                title: this.$t('addRepo')
            }
        },
        computed: {
            ...mapState(['repoListAll']),
            repoList () {
                return this.repoListAll
                    .filter(r => {
                        return ['DOCKER', 'MAVEN', 'NPM', 'GENERIC'].includes(r.type)
                    })
                    .map(repo => ({ ...repo, fid: repo.projectId + repo.name }))
                    .sort((a, b) => {
                        return Boolean(a.type > b.type) || -1
                    })
            },
            targetList () {
                return this.replicaTaskObjects.map(v => v.fid)
            }
        },
        methods: {
            changeSelect (sourceList, targetList) {
                this.checkedRepository = targetList
            },
            async confirmPackageData () {
                this.$emit('confirm', this.checkedRepository)
                this.$emit('cancel')
            }
        }
    }
</script>
