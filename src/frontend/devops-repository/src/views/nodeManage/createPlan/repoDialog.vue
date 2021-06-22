<template>
    <bk-dialog
        :value="show"
        width="800"
        title="添加仓库"
        :mask-close="false"
        :close-icon="false">
        <bk-transfer
            :title="['仓库列表', '已选仓库']"
            :source-list="repoList"
            :target-list="targetList"
            display-key="name"
            setting-key="fid"
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
        <template #footer>
            <bk-button theme="primary" @click="confirmPackageData">{{$t('add')}}</bk-button>
            <bk-button @click="$emit('cancel')">{{$t('cancel')}}</bk-button>
        </template>
    </bk-dialog>
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
                checkedRepository: []
            }
        },
        computed: {
            ...mapState(['repoListAll']),
            repoList () {
                return this.repoListAll.map(repo => ({ ...repo, fid: repo.projectId + repo.name }))
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
