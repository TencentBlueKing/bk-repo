<template>
    <canway-dialog
        :value="show"
        width="800"
        height-num="561"
        :title="$t('addRepo')"
        @cancel="cancel"
        @confirm="confirm">
        <bk-transfer
            :title="[$t('repositoryList'), $t('selectedRepo')]"
            :source-list="repoList"
            :target-list="targetList"
            display-key="name"
            setting-key="name"
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
    export default {
        name: 'repoDialog',
        props: {
            show: Boolean,
            defaultRepos: Array,
            repoList: Array
        },
        data () {
            return {
                targetList: [],
                checkedRepository: []
            }
        },
        computed: {
            projectId () {
                return this.$route.params.projectId
            }
        },
        watch: {
            show (val) {
                val && (this.targetList = this.defaultRepos)
            }
        },
        methods: {
            changeSelect (sourceList, targetList) {
                this.checkedRepository = targetList.map(r => r.name)
            },
            confirm () {
                this.$emit('confirm', this.checkedRepository)
                this.$emit('cancel')
            },
            cancel () {
                // init
                this.targetList = []
                this.$emit('cancel')
            }
        }
    }
</script>
