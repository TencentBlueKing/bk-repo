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
            <bk-table-column :label="$t('roleID')" show-overflow-tooltip>
                <template #default="{ row }">{{ row.roleId }}</template>
            </bk-table-column>
            <bk-table-column :label="$t('roleName')" show-overflow-tooltip>
                <template #default="{ row }">
                    <span class="hover-btn" @click="showUsers(row)">{{row.name}}</span>
                </template>
            </bk-table-column>
            <bk-table-column :label="$t('description')" show-overflow-tooltip>
                <template #default="{ row }">{{row.description || '/'}}</template>
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
                <bk-form-item :label="$t('roleID')" property="roleId" error-display-type="normal">
                    <bk-input v-model.trim="editRoleConfig.roleId" maxlength="32" show-word-limit></bk-input>
                </bk-form-item>
                <bk-form-item :label="$t('roleName')" :required="true" property="name" error-display-type="normal">
                    <bk-input v-model.trim="editRoleConfig.name" maxlength="32" show-word-limit></bk-input>
                </bk-form-item>
                <bk-form-item :label="$t('description')">
                    <bk-input type="textarea" v-model.trim="editRoleConfig.description" maxlength="200"></bk-input>
                </bk-form-item>
            </bk-form>
            <template #footer>
                <bk-button @click="editRoleConfig.show = false">{{ $t('cancel') }}</bk-button>
                <bk-button class="ml10" theme="primary" @click="confirm">{{ $t('confirm') }}</bk-button>
            </template>
        </canway-dialog>
        <bk-sideslider
            class="show-userlist-sideslider"
            :quick-close="true"
            :is-show.sync="editRoleUsers.show"
            :title="editRoleUsers.title"
            :width="500">
            <template #content>
                <div class="m10 flex-align-center">
                    <bk-tag-input
                        style="width: 300px"
                        v-model="editRoleUsers.addUsers"
                        :list="Object.values(selectList)"
                        :search-key="['id', 'name']"
                        :title="editRoleUsers.addUsers.map(u => userList[u] ? userList[u].name : u)"
                        :placeholder="$t('createUsersTip')"
                        trigger="focus"
                        allow-create>
                    </bk-tag-input>
                    <bk-button :disabled="!editRoleUsers.addUsers.length" theme="primary" class="ml10" @click="handleAddUsers">{{ $t('add') }}}</bk-button>
                    <bk-button :disabled="!editRoleUsers.deleteUsers.length" theme="default" class="ml10" @click="handleDeleteUsers">{{ $t('batchRemove') }}</bk-button>
                </div>
                <bk-table
                    :data="editRoleUsers.users"
                    height="calc(100% - 60px)"
                    stripe
                    border
                    size="small"
                    @select="list => {
                        editRoleUsers.deleteUsers = list
                    }"
                    @select-all="list => {
                        editRoleUsers.deleteUsers = list
                    }">
                    <bk-table-column type="selection" width="60"></bk-table-column>
                    <bk-table-column :label="$t('user')">
                        <template #default="{ row }">{{userList[row] ? userList[row].name : row}}</template>
                    </bk-table-column>
                </bk-table>
            </template>
        </bk-sideslider>
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
                    description: ''
                },
                editRoleUsers: {
                    show: false,
                    loading: false,
                    title: '',
                    id: '',
                    users: [],
                    addUsers: [],
                    deleteUsers: []
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
            this.getProjectUsers()
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
            showUsers (row) {
                this.editRoleUsers = {
                    show: true,
                    loading: false,
                    title: row.name,
                    id: row.id,
                    users: row.users,
                    addUsers: [],
                    deleteUsers: []
                }
            },
            handleAddUsers () {
                if (!this.editRoleUsers.addUsers.length) return
                this.editRoleMixin([].concat(this.editRoleUsers.users, this.editRoleUsers.addUsers), this.$t('add') + this.$t('space') + this.$t('success'))
            },
            handleDeleteUsers () {
                if (!this.editRoleUsers.deleteUsers.length) return
                this.editRoleMixin(this.editRoleUsers.users.filter(v => !this.editRoleUsers.deleteUsers.find(w => w === v)), this.$t('delete') + this.$t('space') + this.$t('success'))
            },
            editRoleMixin (userIds, message) {
                this.editRoleUsers.loading = true
                return this.editRole({
                    id: this.editRoleUsers.id,
                    body: {
                        userIds
                    }
                }).then(() => {
                    this.$bkMessage({
                        theme: 'success',
                        message

                    })
                    this.getRoleListHandler().then(() => {
                        this.showUsers(this.roleList.find(v => v.id === this.editRoleUsers.id))
                    })
                }).finally(() => {
                    this.editRoleUsers.loading = false
                })
            },
            createRoleHandler () {
                this.$refs.roleForm && this.$refs.roleForm.clearError()
                this.editRoleConfig = {
                    show: true,
                    loading: false,
                    id: '',
                    name: '',
                    description: ''
                }
            },
            editRoleHandler (row) {
                this.$refs.roleForm && this.$refs.roleForm.clearError()
                const { name, description } = row
                this.editRoleConfig = {
                    show: true,
                    loading: false,
                    roleId: row.roleId,
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
                        description: this.editRoleConfig.description
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
            },
            getProjectUsers () {
                this.getProjectUserList({ projectId: this.projectId, isAdmin: false }).then(res => {
                    res.forEach(user => {
                        const userMap = {
                            id: user.userId,
                            name: user.userId
                        }
                        this.users.push(userMap)
                    })
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
