<template>
    <div class="repository-table-container">
        <bk-button v-show="!disabled" icon="plus" @click="showAddDialog = true">{{ $t('addPath') }}</bk-button>
        <div v-show="pathObjects.length" class="mt10 repo-list">
            <div class="pl10 pr10 repo-item flex-between-center" v-for="(path, index) in pathObjects" :key="index">
                <div class="flex-align-center">
                    <Icon size="16" name="generic" />
                    <span class="repo-name text-overflow" :title="path">{{ path }}</span>
                </div>
                <Icon v-show="!disabled" class="ml10 hover-btn" size="24" name="icon-delete" @click.native="deletePath(index)" />
            </div>
        </div>
        <add-path-dialog :visible.sync="showAddDialog" @complete="confirm"></add-path-dialog>
    </div>
</template>
<script>
    import AddPathDialog from '@/components/AddPathDialog/addPathDialog'
    import { mapActions } from 'vuex'

    export default {
        name: 'RepositoryTable',
        components: { AddPathDialog },
        props: {
            initData: {
                type: Array,
                default: () => []
            },
            disabled: {
                type: Boolean,
                default: false
            },
            targetData: {
                type: Array,
                default: () => []
            }
        },
        data () {
            return {
                showAddDialog: false,
                folders: [],
                pathObjects: []
            }
        },
        computed: {
            projectId () {
                return this.$route.params.projectId
            },
            repoName () {
                return this.$route.query.repoName
            }
        },
        methods: {
            ...mapActions([
                'getFirstLevelFolder'
            ]),
            confirm (pathList) {
                this.pathObjects = pathList
                this.$emit('clearError', pathList)
            },
            deletePath (index) {
                this.pathObjects.splice(index, 1)
                const temp = this.initData.filter((value, index1) => {
                    return index1 !== index
                })
                this.$emit('clearError', temp)
            }
        }
    }
</script>
<style lang="scss" scoped>
.repository-table-container {
    .repo-list {
        display: grid;
        grid-template: auto / repeat(3, 1fr);
        gap: 10px;
        .repo-item {
            height: 32px;
            border: 1px solid var(--borderWeightColor);
            background-color: var(--bgLighterColor);
            .repo-name {
                max-width: 100px;
                margin-left: 5px;
            }
        }
    }
}
</style>
