<template>
    <bk-dialog
        v-model="genericTreeData.show"
        :title="genericTreeData.title"
        :close-icon="false"
        :quick-close="false"
        width="600"
        height="600"
        header-position="left">
        <div class="dialog-tree-container">
            <repo-tree
                ref="dialogTree"
                :list="genericTree"
                :open-list="genericTreeData.openList"
                :selected-node="genericTreeData.selectedNode"
                @icon-click="iconClickHandler"
                @item-click="itemClickHandler">
            </repo-tree>
        </div>
        <div slot="footer">
            <bk-button :loading="genericTreeData.loading" theme="primary" @click="submit">{{ $t('confirm') }}</bk-button>
            <bk-button ext-cls="ml5" @click="genericTreeData.show = false">{{ $t('cancel') }}</bk-button>
        </div>
    </bk-dialog>
</template>
<script>
    import RepoTree from '@/components/RepoTree'
    import { mapState } from 'vuex'
    export default {
        name: 'genericTreeDialog',
        components: { RepoTree },
        data () {
            return {
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
        computed: {
            ...mapState(['genericTree'])
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
            },
            submit () {
                this.$emit('submit', this.genericTreeData)
            }
        }
    }
</script>
<style lang="scss" scoped>
.dialog-tree-container {
    max-height: 500px;
    overflow: auto;
}
</style>
