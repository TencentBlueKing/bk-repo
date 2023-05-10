<template>
    <bk-dialog v-model="showIamDenyDialog" :visible.sync="showIamDenyDialog" :before-close="close" style="text-align: center">
        <img src="/ui/no-permission.svg" />
        <p>该操作需要以下权限</p>
        <bk-table
            class="mt10"
            height="calc(100% - 100px)"
            :data="formData"
            :outer-border="false"
            :row-border="false"
            size="small">
            <bk-table-column label="需申请的权限" width="276px">
                <template #default="{ row }"><p>{{ replaceAction(row.action) }}</p></template>
            </bk-table-column>
            <bk-table-column label="关联的资源实例" width="276px">
                <template #default="{ row }">
                    <p v-if="row.path && row.path !== ''">
                        节点路径：{{ row.path }}
                    </p>
                    <p v-else-if="row.repoName === ''">
                        项目名：{{ row.projectId }}
                    </p>
                    <p v-else>
                        仓库名：{{ row.repoName }}
                    </p>
                </template>
            </bk-table-column>
        </bk-table>
        <div slot="footer">
            <bk-button @click="open">去申请</bk-button>
            <bk-button type="primary" @click="close">取 消</bk-button>
        </div>
    </bk-dialog>
</template>

<script>
    export default {
        name: 'IamDenyDialog',
        props: {
            visible: Boolean,
            showData: {
                type: Object,
                default: undefined
            }
        },
        data () {
            return {
                showIamDenyDialog: this.visible,
                formData: []
            }
        },
        watch: {
            visible: function (newVal) {
                if (newVal) {
                    this.showIamDenyDialog = true
                    this.formData = []
                    this.formData.push(this.showData)
                } else {
                    this.close()
                }
            }
        },
        methods: {
            close () {
                this.showIamDenyDialog = false
                this.$emit('update:visible', false)
            },
            open () {
                window.location.href = this.showData.url
            },
            replaceAction (action) {
                switch (action) {
                    case 'READ':
                        return '查看'
                    case 'WRITE':
                        return '写入'
                    case 'MANAGE':
                        return '管理'
                    case 'DELETE':
                        return '删除'
                    case 'UPDATE':
                        return '更新'
                    default:
                        return action
                }
            }
        }
    }
</script>

<style lang="scss" scoped>
::v-deep .bk-dialog-wrapper .bk-dialog-content.bk-dialog-content-drag{
    width: 600px !important;
}

</style>
