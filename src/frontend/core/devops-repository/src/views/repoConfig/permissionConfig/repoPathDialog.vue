<template>
    <canway-dialog
        :value="show"
        width="800"
        height-num="561"
        :title="title"
        @cancel="$emit('cancel')"
        @confirm="confirmPackageData">
        <bk-transfer
            :title="[$t('folderList'), $t('selectedFolder')]"
            :source-list="pathList"
            :target-list="targetList"
            display-key="displayName"
            setting-key="name"
            searchable
            show-overflow-tips
            @change="changeSelect">
            <template #source-option="{ displayName }">
                <div class="flex-align-center flex-1">
                    <Icon size="16" name="generic" />
                    <span class="ml10 flex-1 text-overflow" :title="displayName">{{ displayName }}</span>
                    <i class="bk-icon icon-arrows-right"></i>
                </div>
            </template>
            <template #target-option="{ displayName }">
                <div class="flex-align-center flex-1">
                    <Icon size="16" name="generic" />
                    <span class="ml10 flex-1 text-overflow" :title="displayName">{{ displayName }}</span>
                    <i class="bk-icon icon-close"></i>
                </div>
            </template>
        </bk-transfer>
    </canway-dialog>
</template>
<script>
    import { mapActions } from 'vuex'
    export default {
        name: 'repoDialog',
        props: {
            show: Boolean,
            pathObjects: Array
        },
        data () {
            return {
                checkedPaths: [],
                title: this.$t('addPath'),
                folders: [],
                init: false
            }
        },
        computed: {
            pathList () {
                return this.folders.map(item => {
                    return {
                        ...item,
                        displayName: item.metadata?.displayName || item.name
                    }
                })
            },
            targetList () {
                return this.pathObjects.map(v => v.name)
            },
            projectId () {
                return this.$route.params.projectId
            },
            repoName () {
                return this.$route.query.repoName
            }
        },
        watch: {
            show (newVal) {
                if (newVal === true && !this.init) {
                    this.getFirstLevelFolder({
                        projectId: this.projectId,
                        repoName: this.repoName
                    }).then(res => {
                        this.folders = res.records
                        this.init = true
                    })
                }
            }
        },
        methods: {
            ...mapActions([
                'getFirstLevelFolder'
            ]),
            changeSelect (sourceList, targetList) {
                this.checkedPaths = targetList
            },
            async confirmPackageData () {
                this.$emit('confirm', this.checkedPaths)
                this.$emit('cancel')
            }
        }
    }
</script>
