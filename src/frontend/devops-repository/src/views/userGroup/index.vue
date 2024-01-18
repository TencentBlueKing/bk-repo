<template>
    <div class="role-container" v-bkloading="{ isLoading }">
        <div class="ml20 mr20 mt10 flex-between-center">
            <bk-button class="ml20" icon="plus" theme="primary" @click="createRoleHandler">{{ $t('create') }}</bk-button>
        </div>
        <bk-table
            class="mt10 role-table"
            height="calc(100% - 60px)"
            :data="filterRoleList"
            :outer-border="false"
            :row-border="false"
            size="small">
            <template #empty>
                <empty-data :is-loading="isLoading" :search="Boolean(role)"></empty-data>
            </template>
            <bk-table-column :label="$t('roleName')" show-overflow-tooltip>
                <template #default="{ row }">
                    {{row.name}}
                </template>
            </bk-table-column>
            <bk-table-column :label="$t('description')" show-overflow-tooltip>
                <template #default="{ row }">{{row.description || '/'}}</template>
            </bk-table-column>
            <bk-table-column :label="$t('user')" show-overflow-tooltip>
                <template #default="{ row }">{{row.users || '/'}}</template>
            </bk-table-column>
            <bk-table-column :label="$t('operation')" width="70">
                <template #default="{ row }">
                    <operation-list
                        :list="[
                            { label: $t('edit'), clickEvent: () => editRoleHandler(row) },
                            { label: $t('delete'), clickEvent: () => deleteRoleHandler(row) }
                        ]"></operation-list>
                </template>
            </bk-table-column>
        </bk-table>
        <canway-dialog
            v-model="editRoleConfig.show"
            theme="primary"
            width="500"
            height-num="301"
            :title="editRoleConfig.id ? $t('editUserGroupTitle') : $t('addUserGroupTitle')"
            @cancel="editRoleConfig.show = false">
            <bk-form :label-width="80" :model="editRoleConfig" :rules="rules" ref="roleForm">
                <bk-form-item :label="$t('roleName')" :required="true" property="name" error-display-type="normal">
                    <bk-input v-model.trim="editRoleConfig.name" maxlength="32" show-word-limit></bk-input>
                </bk-form-item>
                <bk-form-item :label="$t('description')">
                    <bk-input type="textarea" v-model.trim="editRoleConfig.description" maxlength="200"></bk-input>
                </bk-form-item>
                <bk-form-item :label="$t('user')" property="users" error-display-type="normal">
                    <bk-tag-input
                        v-model="editRoleConfig.users"
                        :placeholder="$t('enterPlaceHolder')"
                        trigger="focus"
                        :has-delete-icon="true"
                        allow-create>
                    </bk-tag-input>
                </bk-form-item>
            </bk-form>
            <template #footer>
                <bk-button @click="editRoleConfig.show = false">{{ $t('cancel') }}</bk-button>
                <bk-button class="ml10" theme="primary" @click="confirm">{{ $t('confirm') }}</bk-button>
            </template>
        </canway-dialog>
    </div>
</template>
<script>
    import OperationList from '@repository/components/OperationList'
    import { mapState, mapActions } from 'vuex'
    export default {
        name: 'role',
        components: { OperationList },
        data () {
            return {
                isLoading: false,
                role: '',
                roleList: [],
                editRoleConfig: {
                    show: false,
                    loading: false,
                    roleId: '',
                    name: '',
                    users: [],
                    description: '',
                    id: ''
                },
                users: [],
                rules: {
                    name: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + this.$t('space') + this.$t('name'),
                            trigger: 'blur'
                        }
                    ]
                }
            }
        },
        computed: {
            ...mapState(['userList']),
            filterRoleList () {
                return this.roleList.filter(v => v.name.indexOf(this.role) !== -1)
            },
            selectList () {
                return Object.values(this.users)
                    .filter(v => !~this.editRoleUsers.users.findIndex(w => w === v.id))
            },
            projectId () {
                return this.$route.params.projectId
            }
        },
        created () {
            this.getRoleListHandler()
        },
        methods: {
            ...mapActions([
                'getProjectRoleList',
                'createRole',
                'editRole',
                'deleteRole',
                'getProjectUserList'
            ]),
            getRoleListHandler () {
                this.isLoading = true
                return this.getProjectRoleList({ projectId: this.projectId }).then(res => {
                    this.roleList = res
                }).finally(() => {
                    this.isLoading = false
                })
            },
            createRoleHandler () {
                this.$refs.roleForm && this.$refs.roleForm.clearError()
                this.editRoleConfig = {
                    show: true,
                    loading: false,
                    id: '',
                    name: '',
                    description: '',
                    users: []
                }
            },
            editRoleHandler (row) {
                this.$refs.roleForm && this.$refs.roleForm.clearError()
                const { name, description } = row
                this.editRoleConfig = {
                    show: true,
                    loading: false,
                    roleId: row.roleId,
                    users: row.users,
                    id: row.id,
                    name,
                    description
                }
            },
            async confirm () {
                await this.$refs.roleForm.validate()
                this.editRoleConfig.loading = true
                const fn = this.editRoleConfig.id ? this.editRole : this.createRole
                fn({
                    id: this.editRoleConfig.id,
                    body: {
                        roleId: this.editRoleConfig.roleId,
                        name: this.editRoleConfig.name,
                        type: 'PROJECT',
                        projectId: this.projectId,
                        admin: false,
                        description: this.editRoleConfig.description,
                        userIds: this.editRoleConfig.users
                    }
                }).then(res => {
                    this.$bkMessage({
                        theme: 'success',
                        message: (this.editRoleConfig.id ? this.$t('editUserGroupTitle') : this.$t('addUserGroupTitle')) + this.$t('space') + this.$t('success')
                    })
                    this.editRoleConfig.show = false
                    this.getRoleListHandler()
                }).finally(() => {
                    this.editRoleConfig.loading = false
                })
            },
            deleteRoleHandler ({ id, name }) {
                this.$confirm({
                    theme: 'danger',
                    message: this.$t('deleteRoleTitle', [name]),
                    subMessage: this.$t('deleteRoleSubTitle'),
                    confirmFn: () => {
                        return this.deleteRole({
                            id
                        }).then(() => {
                            this.$bkMessage({
                                theme: 'success',
                                message: this.$t('delete') + this.$t('space') + this.$t('success')
                            })
                            this.getRoleListHandler()
                        })
                    }
                })
            }
        }
    }
</script>
<style lang="scss" scoped>
.role-container {
    height: 100%;
    background-color: white;
    ::v-deep .bk-table td,
    ::v-deep .bk-table th {
        height: 44px;
    }
    .repo-quota {
        display: block;
        margin-right: 20%;
        ::v-deep .bk-tooltip-ref {
            display: block;
        }
    }
    .create-user {
        flex: 1;
        justify-content: flex-end;
    }
}
</style>
