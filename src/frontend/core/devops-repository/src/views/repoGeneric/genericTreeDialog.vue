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
            :placeholder="$t('keyWordEnter')"
            clearable
            right-icon="bk-icon icon-search">
        </bk-input>
        <div class="mt10 dialog-tree-container">
            <repo-tree
                ref="dialogTree"
                :tree="genericTree"
                :important-search="importantSearch"
                :open-list="genericTreeData.openList"
                :open-type="genericTreeData.type"
                :selected-node="genericTreeData.selectedNode"
                @icon-click="iconClickHandler"
                @item-click="itemClickHandler">
            </repo-tree>
        </div>
        <template #footer>
            <bk-button @click="genericTreeData.show = false">{{ $t('cancel') }}</bk-button>
            <bk-button class="ml10" :loading="genericTreeData.loading" theme="primary" @click="submit">{{ $t('confirm') }}</bk-button>
        </template>
        <iam-deny-dialog :visible.sync="showIamDenyDialog" :show-data="showData"></iam-deny-dialog>
    </canway-dialog>
</template>
<script>
    import { mapState, mapActions } from 'vuex'
    import RepoTree from '@repository/components/RepoTree'
    import iamDenyDialog from '@repository/components/IamDenyDialog/IamDenyDialog'
    export default {
        name: 'genericTreeDialog',
        components: { RepoTree, iamDenyDialog },
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
                },
                showIamDenyDialog: false,
                showData: {}
            }
        },
        computed: {
            ...mapState(['genericTree', 'userInfo']),
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
                'copyNode',
                'getPermissionUrl'
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
                const { type, path, selectedNode, folder } = this.genericTreeData
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
                    const destTreeData = {
                        projectId: this.projectId,
                        repoName: selectedNode.repoName || selectedNode.name,
                        fullPath: `${selectedNode.fullPath || '/'}`,
                        folder: folder
                    }
                    if (this.repoName === (selectedNode.repoName || selectedNode.name)) {
                        destTreeData.roadMap = selectedNode.roadMap
                    }
                    // 复制和移动文件夹时需要更新选中的文件夹的上层目录，此时需要将相关数据返回父组件
                    this.$emit('refresh', destTreeData)
                    this.$bkMessage({
                        theme: 'success',
                        message: this.$t(type) + this.$t('space') + this.$t('success')
                    })
                }).catch(err => {
                    if (err.status === 403) {
                        this.getPermissionUrl({
                            body: {
                                projectId: this.projectId,
                                action: 'WRITE',
                                resourceType: 'REPO',
                                uid: this.userInfo.name,
                                repoName: this.repoName
                            }
                        }).then(res => {
                            if (res !== '') {
                                this.showIamDenyDialog = true
                                this.showData = {
                                    projectId: this.projectId,
                                    repoName: this.repoName,
                                    action: 'WRITE',
                                    url: res
                                }
                            } else {
                                this.$bkMessage({
                                    theme: 'error',
                                    message: err.message
                                })
                            }
                        })
                    } else {
                        this.$bkMessage({
                            theme: 'error',
                            message: err.message
                        })
                    }
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
