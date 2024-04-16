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
                    <span class="hover-btn">{{row.users.length ? row.users : '/'}}</span></template>
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
            width="800"
            class="update-role-group-dialog"
            height-num="800"
            :title="editRoleConfig.id ? $t('editUserGroupTitle') : $t('addUserGroupTitle')"
            @cancel="cancel">
            <bk-process
                style="margin-top: -10px; margin-bottom: 10px"
                :list="loadingList"
                :cur-process="loadingProcess"
                :display-key="'content'"
                @process-changed="changeEvent"
                :controllable="true" />
            <bk-form :label-width="80" :model="editRoleConfig" :rules="rules" ref="roleForm" v-if="loadingProcess === 1">
                <bk-form-item :label="$t('roleName')" :required="true" property="name" error-display-type="normal">
                    <bk-input v-model.trim="editRoleConfig.name" maxlength="32" show-word-limit></bk-input>
                </bk-form-item>
                <bk-form-item :label="$t('description')">
                    <bk-input type="textarea" v-model.trim="editRoleConfig.description" maxlength="200"></bk-input>
                </bk-form-item>
            </bk-form>
            <div v-if="loadingProcess === 2" style="display: flex">
                <div class="ml10 mr10 mt10" style="width: 50%; text-align: center">
                    <div>
                        <div style="align-items: center">
                            <bk-input :type="'textarea'" :placeholder="$t('userGroupPlaceholder')" v-model="editRoleConfig.newUser" class="w350 usersTextarea" />
                        </div>
                        <div class="mt5" style="display: flex">
                            <div class="mr10" style="text-align: left">
                                <bk-button style="width: 240px" theme="primary" @click="parseFn">{{ $t('add') }}</bk-button>
                            </div>
                            <div style="text-align: left">
                                <bk-button style="width: 110px" @click="editRoleConfig.newUser = ''">{{ $t('clear') }}</bk-button>
                            </div>
                        </div>
                    </div>
                </div>
                <div class="ml10 mr10 mt10" style="width: 50%;text-align: center">
                    <div style="display: flex">
                        <bk-input v-model="editRoleConfig.search" :placeholder="$t('search')" style="width: 280px" @change="filterUsers" />
                        <bk-button class="ml10" theme="primary" @click="copy">{{ $t('copyAll') }}</bk-button>
                    </div>
                    <div v-show="editRoleConfig.users.length" class="mt10 update-user-list">
                        <div class="pl10 pr10 update-user-item flex-between-center" v-for="(user, index) in editRoleConfig.users" :key="index">
                            <div class="flex-align-center">
                                <span class="update-user-name text-overflow" :title="user">{{ user }}</span>
                            </div>
                            <Icon class="ml10 hover-btn" size="24" name="icon-delete" @click.native="deleteUser(index)" />
                        </div>
                    </div>
                </div>
            </div>
            <template #footer>
                <bk-button @click="cancel">{{ $t('cancel') }}</bk-button>
                <bk-button v-if="loadingProcess === 1" class="ml10" theme="primary" @click="next">{{ $t('next') }}</bk-button>
                <bk-button v-if="loadingProcess === 2" class="ml10" theme="primary" @click="next">{{ $t('previous') }}</bk-button>
                <bk-button v-if="loadingProcess === 2" class="ml10" theme="primary" @click="confirm">{{ $t('confirm') }}</bk-button>
            </template>
        </canway-dialog>
    </div>
</template>
<script>
    import OperationList from '@repository/components/OperationList'
    import { mapState, mapActions } from 'vuex'
    import { copyToClipboard } from '@/utils'
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
                loadingProcess: 1,
                loadingList: [
                    {
                        content: this.$t('baseInfo'),
                        isLoading: true
                    },
                    {
                        content: this.$t('staffing'),
                        isLoading: true
                    }
                ]
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
            confirm () {
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
                    this.loadingProcess = 1
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
            parseFn () {
                const data = this.editRoleConfig.newUser
                if (data !== '') {
                    this.editRoleConfig.users = []
                    const users = data.toString().replace(/\n/g, ',').replace(/\s/g, ',').split(',')
                    for (let i = 0; i < users.length; i++) {
                        users[i] = users[i].toString().trim()
                        if (users[i] !== '') {
                            this.editRoleConfig.users.push(users[i])
                        }
                    }
                    this.editRoleConfig.users = Array.from(new Set(this.editRoleConfig.users))
                    this.editRoleConfig.originUsers = this.editRoleConfig.users
                    this.editRoleConfig.newUser = ''
                }
            },
            cancel () {
                this.editRoleConfig.show = false
                this.getRoleListHandler()
                this.loadingProcess = 1
            },
            filterUsers () {
                this.editRoleConfig.users = this.editRoleConfig.originUsers
                if (this.editRoleConfig.search !== '') {
                    this.editRoleConfig.users = this.editRoleConfig.users.filter(user => user.toLowerCase().includes(this.editRoleConfig.search.toLowerCase()))
                }
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
            async next () {
                if (this.loadingProcess === 1) {
                    await this.$refs.roleForm.validate()
                    this.loadingProcess = 2
                } else {
                    this.loadingProcess = 1
                }
            },
            changeProcess () {
                this.$refs.roleForm.validate(valid => {
                    if (valid) {
                        this.loadingProcess === 1 ? this.loadingProcess = 2 : this.loadingProcess = 1
                    }
                })
            },
            copy () {
                const text = this.editRoleConfig.originUsers.join('\n')
                copyToClipboard(text).then(() => {
                    this.$bkMessage({
                        theme: 'success',
                        message: this.$t('copy') + this.$t('space') + this.$t('success')
                    })
                }).catch(() => {
                    this.$bkMessage({
                        theme: 'error',
                        message: this.$t('copy') + this.$t('space') + this.$t('fail')
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
    .user-list {
        display: grid;
        grid-template: auto / repeat(4, 1fr);
        gap: 10px;
        max-height: calc(100% - 154px);
        overflow-y: auto;
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
.update-role-group-dialog {
    .bk-dialog-body {
        height: 500px;
    }
    ::v-deep .usersTextarea .bk-textarea-wrapper .bk-form-textarea{
        height: 500px;
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
