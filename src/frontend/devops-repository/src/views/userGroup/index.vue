<template>
    <div class="role-container" v-bkloading="{ isLoading }">
        <div class="ml20 mr20 mt10 flex-between-center">
            <bk-button icon="plus" theme="primary" @click="createRoleHandler">{{ $t('create') }}</bk-button>
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
                <template #default="{ row }">
                    <span class="hover-btn" @click="showSetting(row.users, row.id)">{{row.users || '/'}}</span></template>
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
            @cancel="cancel">
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
                        :placeholder="$t('enterPlaceHolder') + $t('parseTip')"
                        trigger="focus"
                        :paste-fn="parseFn"
                        :has-delete-icon="true"
                        allow-create>
                    </bk-tag-input>
                </bk-form-item>
            </bk-form>
            <template #footer>
                <bk-button @click="cancel">{{ $t('cancel') }}</bk-button>
                <bk-button class="ml10" theme="primary" @click="confirm">{{ $t('confirm') }}</bk-button>
            </template>
        </canway-dialog>
        <bk-sideslider :is-show.sync="defaultSettings.isShow" :title="defaultSettings.title" :quick-close="true" :width="800">
            <div slot="content">
                <div class="ml10 mr10 mt10 flex-between-center">
                    <div>
                        <bk-input v-model="defaultSettings.newUser" class="w250" />
                        <bk-button icon="plus" theme="primary" @click="addNewUsers">{{ $t('add') }}</bk-button>
                    </div>
                    <bk-input v-model="defaultSettings.search" :placeholder="$t('search')" class="w250" @change="filterUsers" />
                </div>
                <div v-show="defaultSettings.users.length" class="mt10 user-list">
                    <div class="pl10 pr10 user-item flex-between-center" v-for="(user, index) in defaultSettings.users" :key="index">
                        <div class="flex-align-center">
                            <span class="user-name text-overflow" :title="user">{{ user }}</span>
                        </div>
                        <Icon class="ml10 hover-btn" size="24" name="icon-delete" @click.native="deleteUser(index)" />
                    </div>
                </div>
            </div>
            <div slot="footer">
                <bk-button style="margin-left: 24px;" theme="primary" @click="updateUsers()">
                    {{ $t('confirm') }}
                </bk-button>
                <bk-button style="margin-left: 4px;" theme="default" @click="closeSetting()">{{ $t('cancel') }}</bk-button>
            </div>
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
                },
                defaultSettings: {
                    isShow: false,
                    title: this.$t('user'),
                    users: [],
                    id: '',
                    newUser: '',
                    search: '',
                    originUsers: []
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
            parseFn (data) {
                if (data !== '') {
                    const users = data.toString().split(',')
                    for (let i = 0; i < users.length; i++) {
                        users[i] = users[i].toString().trim()
                        this.editRoleConfig.users.push(users[i])
                    }
                    this.editRoleConfig.user = Array.from(new Set(this.editRoleConfig.users))
                }
            },
            cancel () {
                this.editRoleConfig.show = false
                this.getRoleListHandler()
            },
            showSetting (users, id) {
                this.defaultSettings.isShow = true
                this.defaultSettings.users = users
                this.defaultSettings.originUsers = users
                this.defaultSettings.id = id
            },
            addNewUsers () {
                const temp = []
                let has = false
                for (let i = 0; i < this.defaultSettings.users.length; i++) {
                    temp.push(this.defaultSettings.users[i])
                    if (this.defaultSettings.users[i] === this.defaultSettings.newUser) {
                        has = true
                        break
                    }
                }
                if (!has) {
                    temp.push(this.defaultSettings.newUser)
                    this.defaultSettings.users = temp
                    this.defaultSettings.originUsers = temp
                }
                this.defaultSettings.newUser = ''
            },
            filterUsers () {
                this.defaultSettings.users = this.defaultSettings.originUsers
                if (this.defaultSettings.search !== '') {
                    this.defaultSettings.users = this.defaultSettings.users.filter(user => user.toLowerCase().includes(this.defaultSettings.search.toLowerCase()))
                }
            },
            updateUsers () {
                this.editRole({
                    id: this.defaultSettings.id,
                    body: {
                        userIds: this.defaultSettings.originUsers
                    }
                }).then(_ => {
                    this.$bkMessage({
                        theme: 'success',
                        message: this.$t('editUserGroupTitle') + this.$t('space') + this.$t('success')
                    })
                    this.defaultSettings.users = []
                    this.defaultSettings.isShow = false
                    this.getRoleListHandler()
                })
            },
            deleteUser (index) {
                const temp = []
                for (let i = 0; i < this.defaultSettings.users.length; i++) {
                    if (i !== index) {
                        temp.push(this.defaultSettings.users[i])
                    }
                }
                this.defaultSettings.users = temp
                this.defaultSettings.originUsers = temp
            },
            closeSetting () {
                this.defaultSettings.users = []
                this.defaultSettings.isShow = false
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
    .user-list {
        display: grid;
        grid-template: auto / repeat(4, 1fr);
        gap: 10px;
        .user-item {
            margin-left: 10px;
            margin-right: 10px;
            height: 32px;
            border: 1px solid var(--borderWeightColor);
            background-color: var(--bgLighterColor);
            .user-name {
                max-width: 80px;
                margin-left: 5px;
            }
        }
    }
}
</style>
