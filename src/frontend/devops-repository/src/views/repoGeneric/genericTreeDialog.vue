<template>
    <canway-dialog
        v-model="genericTreeData.show"
        :title="genericTreeData.title"
        width="600"
        height-num="563"
        @cancel="genericTreeData.show = false">
        <bk-input
            class="w250"
            v-model.trim="importantSearch"
            placeholder="请输入关键字"
            clearable
            right-icon="bk-icon icon-search">
        </bk-input>
        <div class="mt10 dialog-tree-container">
            <repo-tree
                ref="dialogTree"
                :tree="genericTree"
                :important-search="importantSearch"
                :open-list="genericTreeData.openList"
                :selected-node="genericTreeData.selectedNode"
                :open-type="genericTreeData.type"
                @icon-click="iconClickHandler"
                @item-click="itemClickHandler">
            </repo-tree>
        </div>
        <template #footer>
            <bk-button @click="genericTreeData.show = false">{{ $t('cancel') }}</bk-button>
            <bk-button class="ml10" :loading="genericTreeData.loading" theme="primary" @click="submit">{{ $t('confirm') }}</bk-button>
        </template>
    </canway-dialog>
</template>
<script>
    import { mapState, mapActions } from 'vuex'
    import RepoTree from '@repository/components/RepoTree'
    export default {
        name: 'genericTreeDialog',
        components: { RepoTree },
        data () {
            return {
                importantSearch: '',
                genericTreeData: {
                    show: false,
                    loading: false,
                    type: 'move',
                    title: '',
                    path: '',
                    openList: [],
                    selectedNode: {}
                }
            }
        },
        computed: {
            ...mapState(['genericTree']),
            projectId () {
                return this.$route.params.projectId
            },
            repoName () {
                return this.$route.query.repoName
            }
        },
        methods: {
            ...mapActions([
                'moveNode',
                'copyNode'
            ]),
            // 树组件选中文件夹
            itemClickHandler (node) {
                this.genericTreeData.selectedNode = node
                // 更新已展开文件夹数据
                const openList = this.genericTreeData.openList
                !openList.includes(node.roadMap) && openList.push(node.roadMap)
                // 请求子文件夹数据
                if (node.loading || (node.children && node.children.length)) return
                this.$emit('update', node)
            },
            iconClickHandler (node) {
                // 更新已展开文件夹数据
                const reg = new RegExp(`^${node.roadMap}`)
                const openList = this.genericTreeData.openList
                if (openList.includes(node.roadMap)) {
                    openList.splice(0, openList.length, ...openList.filter(v => !reg.test(v)))
                } else {
                    openList.push(node.roadMap)
                    // 请求子文件夹数据
                    if (node.loading || (node.children && node.children.length)) return
                    this.$emit('update', node)
                }
            },
            setTreeData (data) {
                this.genericTreeData = {
                    ...this.genericTreeData,
                    openList: ['0'],
                    selectedNode: this.genericTree[0],
                    ...data
                }
                setTimeout(this.$refs.dialogTree.computedSize, 0)
            },
            submit () {
                this.genericTreeData.loading = true
                const { type, path, selectedNode } = this.genericTreeData
                this[type + 'Node']({
                    body: {
                        srcProjectId: this.projectId,
                        srcRepoName: this.repoName,
                        srcFullPath: path,
                        destProjectId: this.projectId,
                        destRepoName: this.repoName,
                        destFullPath: `${selectedNode.fullPath || '/'}`,
                        overwrite: false
                    }
                }).then(() => {
                    this.genericTreeData.show = false
                    this.$emit('refresh')
                    this.$bkMessage({
                        theme: 'success',
                        message: this.$t(type) + this.$t('success')
                    })
                }).finally(() => {
                    this.genericTreeData.loading = false
                })
            }
        }
    }
</script>
<style lang="scss" scoped>
.dialog-tree-container {
    height: 360px;
    overflow: auto;
}
</style>
