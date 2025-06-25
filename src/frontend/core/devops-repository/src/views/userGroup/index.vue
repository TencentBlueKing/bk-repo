<template>
    <div class="role-container" v-bkloading="{ isLoading }">
        <div class="ml20 mr20 mt10">
            <bk-button icon="plus" theme="primary" @click="createRoleHandler">{{ $t('create') }}</bk-button>
            <bk-button icon="plus" theme="primary" @click="importRoleHandler">{{ $t('import') }}</bk-button>
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
            <bk-table-column :label="$t('user')" width="800px" show-overflow-tooltip>
                <template #default="{ row }">
                    <span class="hover-btn">{{row.users.length ? row.users : '/'}}</span></template>
            </bk-table-column>
            <bk-table-column :label="$t('userSource')" show-overflow-tooltip>
                <template #default="{ row }">
                    <span class="hover-btn">{{ transformSource(row.source) }}</span></template>
            </bk-table-column>
            <bk-table-column :label="$t('operation')" width="100">
                <template #default="{ row }">
                    <operation-list
                        :list="[
                            !row.source && { label: $t('edit'), clickEvent: () => editRoleHandler(row) },
                            { label: $t('delete'), clickEvent: () => deleteRoleHandler(row) }
                        ]"></operation-list>
                </template>
            </bk-table-column>
        </bk-table>
        <canway-dialog
            v-model="editRoleConfig.show"
            theme="primary"
            width="800"
            class="update-role-group-dialog"
            height-num="603"
            :title="editRoleConfig.id ? $t('editUserGroupTitle') : $t('addUserGroupTitle')"
            @cancel="cancel">
            <bk-form :label-width="80" :model="editRoleConfig" :rules="rules" ref="roleForm">
                <bk-form-item :label="$t('roleName')" :required="true" property="name" error-display-type="normal">
                    <bk-input v-model.trim="editRoleConfig.name" maxlength="32" show-word-limit></bk-input>
                </bk-form-item>
                <bk-form-item :label="$t('description')">
                    <bk-input type="textarea" v-model.trim="editRoleConfig.description" maxlength="200" :placeholder="$t('pleaseInput')"></bk-input>
                </bk-form-item>
                <bk-form-item :label="$t('staffing')">
                    <bk-button icon="plus" @click="showAddDialog">{{ $t('add') + $t('space') + $t('user') }}</bk-button>
                    <div v-show="editRoleConfig.users.length" class="mt10 user-list">
                        <div class="pl10 pr10 user-item flex-between-center" v-for="(user, index) in editRoleConfig.users" :key="index">
                            <div class="flex-align-center">
                                <span class="user-name text-overflow" :title="user">{{ user }}</span>
                            </div>
                            <Icon class="ml10 hover-btn" size="24" name="icon-delete" @click.native="deleteUser(index)" />
                        </div>
                    </div>
                </bk-form-item>
            </bk-form>
            <template #footer>
                <bk-button @click="cancel">{{ $t('cancel') }}</bk-button>
                <bk-button class="ml10" theme="primary" @click="confirm">{{ $t('confirm') }}</bk-button>
            </template>
        </canway-dialog>
        <add-user-dialog ref="addUserDialog" :visible.sync="showAddUserDialog" @complete="handleAddUsers"></add-user-dialog>
        <import-user-dialog :visible.sync="showImportUserDialog" @complete="getRoleListHandler"></import-user-dialog>
    </div>
</template>
<script>
    import OperationList from '@repository/components/OperationList'
    import { mapState, mapActions } from 'vuex'
    import AddUserDialog from '@/components/AddUserDialog/addUserDialog'
    import importUserDialog from '@/views/userGroup/importUserDialog'
    export default {
        name: 'role',
        components: { AddUserDialog, OperationList, importUserDialog },
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
                    id: '',
                    search: '',
                    newUser: '',
                    originUsers: []
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
                },
                showAddUserDialog: false,
                showData: {},
                openImport: false,
                importUsers: [],
                importDate: [],
                showImportUserDialog: false
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
                'getProjectUserList',
                'getUserGroupByExternal'
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
                this.$refs.roleForm.clearError()
                this.editRoleConfig = {
                    show: true,
                    loading: false,
                    id: '',
                    name: '',
                    description: '',
                    users: [],
                    originUsers: []
                }
            },
            editRoleHandler (row) {
                this.$refs.roleForm.clearError()
                const { name, description } = row
                this.editRoleConfig = {
                    show: true,
                    loading: false,
                    roleId: row.roleId,
                    users: row.users,
                    id: row.id,
                    name,
                    description,
                    originUsers: row.users
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
                        userIds: this.editRoleConfig.originUsers
                    }
                }).then(res => {
                    if (!this.editRoleConfig.id && this.editRoleConfig.users.length > 0) {
                        this.editRole({
                            id: res,
                            body: {
                                userIds: this.editRoleConfig.users
                            }
                        }).then(_ => {
                            this.$bkMessage({
                                theme: 'success',
                                message: (this.editRoleConfig.id ? this.$t('editUserGroupTitle') : this.$t('addUserGroupTitle')) + this.$t('space') + this.$t('success')
                            })
                            this.editRoleConfig.show = false
                            this.getRoleListHandler()
                        })
                        return
                    }
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
            },
            cancel () {
                this.editRoleConfig.show = false
                this.getRoleListHandler()
            },
            deleteUser (index) {
                const temp = []
                for (let i = 0; i < this.editRoleConfig.users.length; i++) {
                    if (i !== index) {
                        temp.push(this.editRoleConfig.users[i])
                    }
                }
                this.editRoleConfig.users = temp
                this.editRoleConfig.originUsers = temp
            },
            showAddDialog () {
                this.showAddUserDialog = true
                this.$refs.addUserDialog.editUserConfig = {
                    users: this.editRoleConfig.users,
                    originUsers: this.editRoleConfig.originUsers,
                    search: '',
                    newUser: ''
                }
            },
            handleAddUsers (users) {
                this.editRoleConfig.originUsers = users
                this.editRoleConfig.users = users
            },
            importRoleHandler () {
                this.showImportUserDialog = true
            },
            transformSource (sourceId) {
                if (!sourceId) {
                    return '/'
                } else if (sourceId === 'DEVOPS') {
                    return this.$t('bkci')
                } else {
                    return '/'
                }
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
}
.update-role-group-dialog {
    .bk-dialog-body {
        height: 500px;
    }
    ::v-deep .usersTextarea .bk-textarea-wrapper .bk-form-textarea{
        height: 500px;
    }
    .user-list {
        display: grid;
        grid-template: auto / repeat(3, 1fr);
        gap: 10px;
        max-height: 300px;
        overflow-y: auto;
        .user-item {
            height: 32px;
            border: 1px solid var(--borderWeightColor);
            background-color: var(--bgLighterColor);
            .user-name {
                max-width: 100px;
                margin-left: 5px;
            }
        }
    }
    .update-user-list {
        display: grid;
        grid-template: auto / repeat(1, 1fr);
        gap: 10px;
        max-height: 500px;
        overflow-y: auto;
        .update-user-item {
            height: 32px;
            border: 1px solid var(--borderWeightColor);
            background-color: var(--bgLighterColor);
            .update-user-name {
                max-width: 100px;
                margin-left: 5px;
            }
        }
    }
}
</style>
