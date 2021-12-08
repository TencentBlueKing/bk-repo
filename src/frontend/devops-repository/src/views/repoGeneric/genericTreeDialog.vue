<template>
    <canway-dialog
        v-model="genericTreeData.show"
        :title="genericTreeData.title"
        width="600"
        height-num="616"
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
                :important-search="importantSearch"
                :open-list="genericTreeData.openList"
                :selected-node="genericTreeData.selectedNode"
                @icon-click="iconClickHandler"
                @item-click="itemClickHandler">
            </repo-tree>
        </div>
        <div slot="footer">
            <bk-button @click="genericTreeData.show = false">{{ $t('cancel') }}</bk-button>
            <bk-button class="ml10" :loading="genericTreeData.loading" theme="primary" @click="submit">{{ $t('confirm') }}</bk-button>
        </div>
    </canway-dialog>
</template>
<script>
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
                    openList: [],
                    selectedNode: {}
                }
            }
        },
        methods: {
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
                    ...data
                }
                setTimeout(this.$refs.dialogTree.computedSize, 0)
            },
            submit () {
                this.$emit('submit', this.genericTreeData)
            }
        }
    }
</script>
<style lang="scss" scoped>
.dialog-tree-container {
    height: 420px;
    overflow: auto;
}
</style>
