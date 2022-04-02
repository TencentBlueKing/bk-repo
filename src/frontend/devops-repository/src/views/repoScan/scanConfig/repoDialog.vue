<template>
    <canway-dialog
        :value="show"
        width="800"
        height-num="561"
        title="添加仓库"
        @cancel="cancel"
        @confirm="confirm">
        <bk-transfer
            :title="['仓库列表', '已选仓库']"
            :source-list="repoList"
            :target-list="targetList"
            display-key="name"
            setting-key="name"
            searchable
            show-overflow-tips
            @change="changeSelect">
            <template #source-option="{ name, type }">
                <Icon size="16" :name="type.toLowerCase()" />
                <span class="ml10 text-overflow" style="max-width:280px" :title="name">{{ name }}</span>
            </template>
            <template #target-option="{ name, type }">
                <Icon size="16" :name="type.toLowerCase()" />
                <span class="ml10 text-overflow" style="max-width:280px" :title="name">{{ name }}</span>
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
            defaultRepos: Array,
            scanType: String
        },
        data () {
            return {
                targetList: [],
                checkedRepository: []
            }
        },
        computed: {
            ...mapState(['repoListAll']),
            projectId () {
                return this.$route.params.projectId
            },
            repoList () {
                return this.repoListAll
                    .filter(r => r.type === this.scanType.toLowerCase())
                    .sort((a, b) => {
                        return Boolean(a.type > b.type) || -1
                    })
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
