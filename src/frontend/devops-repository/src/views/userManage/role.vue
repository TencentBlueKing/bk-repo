<template>
    <div class="role-container" v-bkloading="{ isLoading }">
        <div class="mt10 flex-between-center">
            <bk-button class="ml20" icon="plus" theme="primary" @click="createRoleHandler">{{ $t('create') }}</bk-button>
            <bk-input
                v-model.trim="role"
                class="mr20 w250"
                :placeholder="`共有${roleList.length}个用户组`"
                clearable
                right-icon="bk-icon icon-search">
            </bk-input>
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
            <bk-table-column label="用户组名称" show-overflow-tooltip>
                <template #default="{ row }">
                    <span class="hover-btn" @click="showUsers(row)">{{row.name}}</span>
                </template>
            </bk-table-column>
            <bk-table-column label="关联用户数" show-overflow-tooltip>
                <template #default="{ row }">{{ row.users.length }}</template>
            </bk-table-column>
            <bk-table-column :label="$t('description')" show-overflow-tooltip>
                <template #default="{ row }">{{row.description || '/'}}</template>
            </bk-table-column>
            <bk-table-column :label="$t('operation')" width="70">
                <template #default="{ row }">
                    <operation-list
                        :list="[
                            { label: '编辑', clickEvent: () => editRoleHandler(row) },
                            { label: '删除', clickEvent: () => deleteRoleHandler(row) }
                        ]"></operation-list>
                </template>
            </bk-table-column>
        </bk-table>
        <canway-dialog
            v-model="editRoleConfig.show"
            theme="primary"
            width="500"
            height-num="301"
            :title="editRoleConfig.id ? '编辑用户组' : '创建用户组'"
            @cancel="editRoleConfig.show = false">
            <bk-form :label-width="80" :model="editRoleConfig" :rules="rules" ref="roleForm">
                <bk-form-item label="名称" :required="true" property="name" error-display-type="normal">
                    <bk-input v-model.trim="editRoleConfig.name" maxlength="32" show-word-limit></bk-input>
                </bk-form-item>
                <bk-form-item label="简介">
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
                        placeholder="添加用户，按Enter键确认"
                        trigger="focus"
                        allow-create>
                    </bk-tag-input>
                    <bk-button :disabled="!editRoleUsers.addUsers.length" theme="primary" class="ml10" @click="handleAddUsers">添加</bk-button>
                    <bk-button :disabled="!editRoleUsers.deleteUsers.length" theme="default" class="ml10" @click="handleDeleteUsers">批量移除</bk-button>
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
                    <bk-table-column label="用户">
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
                    id: '',
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
                rules: {
                    name: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + this.$t('userGroup') + this.$t('name'),
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
                return Object.values(this.userList)
                    .filter(v => v.id !== 'anonymous')
                    .filter(v => !~this.editRoleUsers.users.findIndex(w => w === v.id))
            }
        },
        created () {
            this.getRoleListHandler()
        },
        methods: {
            ...mapActions([
                'getRoleList',
                'createRole',
                'editRole',
                'deleteRole'
            ]),
            getRoleListHandler () {
                this.isLoading = true
                return this.getRoleList().then(res => {
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
                this.editRoleMixin([].concat(this.editRoleUsers.users, this.editRoleUsers.addUsers), '新增用户成功')
            },
            handleDeleteUsers () {
                if (!this.editRoleUsers.deleteUsers.length) return
                this.editRoleMixin(this.editRoleUsers.users.filter(v => !this.editRoleUsers.deleteUsers.find(w => w === v)), '移除用户成功')
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
                    id: row.id,
                    name,
                    description
                }
            },
            async confirm () {
                await this.$refs.roleForm.validate()
                this.editRoleConfig.loading = true
                const { name, description } = this.editRoleConfig
                const fn = this.editRoleConfig.id ? this.editRole : this.createRole
                fn({
                    id: this.editRoleConfig.id,
                    body: {
                        name,
                        description,
                        type: 'SYSTEM'
                    }
                }).then(res => {
                    this.$bkMessage({
                        theme: 'success',
                        message: (this.editRoleConfig.id ? '编辑用户组' : '新建用户组') + this.$t('success')
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
                                message: this.$t('delete') + this.$t('success')
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
    overflow: hidden;
    .role-search {
        width: 250px;
    }
    .role-search-btn {
        position: relative;
        z-index: 1;
        padding: 9px;
        color: white;
        margin-left: -2px;
        border-radius: 0 2px 2px 0;
        background-color: #3a84ff;
        cursor: pointer;
        &:hover {
            background-color: #699df4;
        }
    }
    .create-user {
        flex: 1;
        justify-content: flex-end;
    }
}
</style>
