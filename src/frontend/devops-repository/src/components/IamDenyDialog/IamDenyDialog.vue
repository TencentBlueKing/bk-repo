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
            <bk-table-column label="需申请的权限" show-overflow-tooltip>
                <template #default="{ row }"><p>{{ replaceAction(row.action) }}</p></template>
            </bk-table-column>
            <bk-table-column label="关联的资源实例" show-overflow-tooltip>
                <template #default="{ row }">
                    仓库名：{{ row.repoName }}
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
                    default:
                        return action
                }
            }
        }
    }
</script>

<style lang="scss" scoped>
</style>
