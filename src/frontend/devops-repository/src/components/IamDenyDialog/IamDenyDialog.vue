<template>
    <bk-dialog v-model="showIamDenyDialog" :visible.sync="showIamDenyDialog" :before-close="close" style="text-align: center">
        <img src="/ui/no-permission.svg" />
        <p>{{ $t('iamTip') }}</p>
        <bk-table
            class="mt10"
            height="calc(100% - 100px)"
            :data="formData"
            :outer-border="false"
            :row-border="false"
            size="small">
            <bk-table-column :label="$t('requiredPermission')" width="276px">
                <template #default="{ row }"><p>{{ replaceAction(row.action) }}</p></template>
            </bk-table-column>
            <bk-table-column :label="$t('associatedResource')" width="276px">
                <template #default="{ row }">
                    <p v-if="row.path && row.path !== ''">
                        {{ $t('nodePath') + ':' + row.path }}
                    </p>
                    <p v-else-if="row.packageName && row.packageName !== ''">
                        {{ $t('packageName') + ':' + row.packageName }}
                        {{ row.packageVersion ? ',' + $t('version') + ':' + row.packageVersion : '' }}
                    </p>
                    <p v-else-if="row.repoName === ''">
                        {{ $t('projectName') + ':' + row.projectId }}
                    </p>
                    <p v-else>
                        {{ $t('repoName') + ':' + row.repoName }}
                    </p>
                </template>
            </bk-table-column>
        </bk-table>
        <div slot="footer">
            <bk-button @click="open">{{ $t('apply') }}</bk-button>
            <bk-button type="primary" @click="close">{{ $t('cancel') }}</bk-button>
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
                window.open(this.showData.url, '_blank')
            },
            replaceAction (action) {
                switch (action) {
                    case 'READ':
                        return this.$t('actionEnum.READ')
                    case 'WRITE':
                        return this.$t('actionEnum.WRITE')
                    case 'MANAGE':
                        return this.$t('actionEnum.MANAGE')
                    case 'DELETE':
                        return this.$t('actionEnum.DELETE')
                    case 'UPDATE':
                        return this.$t('actionEnum.UPDATE')
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
