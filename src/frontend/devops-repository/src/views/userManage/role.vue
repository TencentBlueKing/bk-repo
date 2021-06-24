<template>
    <div class="role-container" v-bkloading="{ isLoading }">
        <div class="mb20 flex-align-center">
            <bk-input
                class="role-search"
                v-model.trim="role"
                :right-icon="'bk-icon icon-search'"
                clearable
                :placeholder="`共有${roleList.length}个用户组`">
            </bk-input>
            <div class="create-user flex-align-center">
                <bk-button theme="primary" @click.stop="createRoleHandler">{{ $t('create') + $t('userGroup') }}</bk-button>
            </div>
        </div>
        <bk-table
            class="role-table"
            height="calc(100% - 80px)"
            :data="filterRoleList"
            :outer-border="false"
            :row-border="false"
            :row-style="{ cursor: 'pointer' }"
            size="small"
            @row-click="showUsers"
        >
            <bk-table-column label="用户组名称" prop="name" width="200"></bk-table-column>
            <bk-table-column label="描述" prop="description"></bk-table-column>
            <bk-table-column :label="$t('operation')" width="150">
                <div slot-scope="props" class="flex-align-center">
                    <i class="mr20 devops-icon icon-edit hover-btn" @click.stop="editRoleHandler(props.row)"></i>
                    <i class="devops-icon icon-delete hover-btn" @click.stop="deleteRoleHandler(props.row)"></i>
                </div>
            </bk-table-column>
        </bk-table>
        <bk-dialog
            v-model="editRoleConfig.show"
            theme="primary"
            width="500"
            :close-icon="false"
            header-position="center"
            :title="editRoleConfig.id ? '编辑用户组' : '添加用户组'">
            <bk-form :label-width="80" :model="editRoleConfig" :rules="rules" ref="roleForm">
                <bk-form-item label="名称" :required="true" property="name" error-display-type="normal">
                    <bk-input v-model.trim="editRoleConfig.name"></bk-input>
                </bk-form-item>
                <bk-form-item label="简介">
                    <bk-input type="textarea" v-model.trim="editRoleConfig.description"></bk-input>
                </bk-form-item>
            </bk-form>
            <div slot="footer">
                <bk-button theme="primary" @click="confirm">确定</bk-button>
                <bk-button @click="editRoleConfig.show = false">取消</bk-button>
            </div>
        </bk-dialog>
        <bk-sideslider
            class="show-userlist-sideslider"
            :quick-close="true"
            :is-show.sync="editRoleUsers.show"
            :title="editRoleUsers.title"
            :width="500">
            <div class="show-userlist-content" slot="content">
                <div class="add-user flex-align-center">
                    <div style="width:280px">
                        <bk-select class="select-user"
                            v-model="editRoleUsers.addUsers"
                            multiple
                            searchable
                            placeholder="请选择用户"
                            :enable-virtual-scroll="Object.values(userList).length > 3000"
                            :list="Object.values(userList).filter(user => user.id !== 'anonymous')">
                            <bk-option v-for="option in Object.values(userList).filter(user => user.id !== 'anonymous')"
                                :key="option.id"
                                :id="option.id"
                                :name="option.name">
                            </bk-option>
                        </bk-select>
                    </div>
                    <bk-button theme="primary" class="ml10" @click="handleAddUsers">添加</bk-button>
                    <bk-button theme="warning" class="ml10" @click="handleDeleteUsers">批量移除</bk-button>
                </div>
                <bk-table
                    class="mt20"
                    :data="editRoleUsers.users"
                    :max-height="1055"
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
                        <template slot-scope="props">
                            <div>{{userList[props.row] ? userList[props.row].name : props.row}}</div>
                        </template>
                    </bk-table-column>
                </bk-table>
            </div>
        </bk-sideslider>
    </div>
</template>
<script>
    import { mapState, mapActions } from 'vuex'
    export default {
        name: 'role',
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
            projectId () {
                return this.$route.params.projectId
            },
            filterRoleList () {
                return this.roleList.filter(v => v.name.indexOf(this.role) !== -1)
            }
        },
        created () {
            this.getRoleList()
        },
        methods: {
            ...mapActions([
                'getRepoRoleList',
                'createRole',
                'editRole',
                'deleteRole'
            ]),
            getRoleList () {
                this.isLoading = true
                return this.getRepoRoleList({
                    projectId: this.projectId
                }).then(res => {
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
                this.editRoleMixin([].concat(this.editRoleUsers.users, this.editRoleUsers.addUsers), '新增用户成功')
            },
            handleDeleteUsers () {
                this.editRoleMixin(this.editRoleUsers.users.filter(v => !this.editRoleUsers.deleteUsers.find(w => w === v)), '移除用户成功')
            },
            editRoleMixin (userIds, message) {
                this.editRoleUsers.loading = true
                return this.editRole({
                    id: this.editRoleUsers.id,
                    body: {
                        projectId: this.projectId,
                        userIds
                    }
                }).then(() => {
                    this.$bkMessage({
                        theme: 'success',
                        message

                    })
                    this.getRepoRoleList({
                        projectId: this.projectId
                    }).then(res => {
                        this.roleList = res
                        this.showUsers(res.find(v => v.id === this.editRoleUsers.id))
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
                        projectId: this.projectId,
                        name,
                        description
                    }
                }).then(res => {
                    this.$bkMessage({
                        theme: 'success',
                        message: (this.editRoleConfig.id ? '编辑用户组' : '新建用户组') + this.$t('success')
                    })
                    this.editRoleConfig.show = false
                    this.getRoleList()
                }).finally(() => {
                    this.editRoleConfig.loading = false
                })
            },
            deleteRoleHandler ({ id, name }) {
                this.$bkInfo({
                    type: 'error',
                    title: this.$t('deleteRoleTitle', [name]),
                    subTitle: this.$t('deleteRoleSubTitle'),
                    showFooter: true,
                    confirmFn: () => {
                        this.deleteRole({
                            id
                        }).then(() => {
                            this.$bkMessage({
                                theme: 'success',
                                message: this.$t('delete') + this.$t('success')
                            })
                            this.getRoleList()
                        })
                    }
                })
            }
        }
    }
</script>
<style lang="scss" scoped>
.role-container {
    height: calc(100% + 40px);
    margin-bottom: -40px;
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
    .role-table {
        .icon-edit {
            font-size: 14px;
        }
        .icon-delete {
            font-size: 16px;
        }
        .icon-arrows-up {
            border-bottom: 1px solid;
        }
    }
}
.show-userlist-sideslider{
    .bk-dialog-header-inner {
        font-size: 16px;
        text-align: center;
    }
    .show-userlist-content{
        height: 100%;
        min-height: 400px;
        padding: 20px;
    }
    .show-resource-content{
        height: 100%;
        padding: 20px;
    }
}
</style>
