<template>
    <canway-dialog
        v-model="showDialog"
        width="800"
        height-num="603"
        :title="type === 'create' ? $t('createPermission') : $t('updatePermission')"
        ref="permDialog"
        @confirm="confirm"
        @cancel="cancel">
        <bk-form class="mb20 permission-form" :label-width="120" :model="showData" :rules="rules" ref="permissionForm">
            <bk-form-item :label="$t('name')" :required="true" property="name" error-display-type="normal">
                <bk-input class="w480" v-model.trim="showData.name"></bk-input>
            </bk-form-item>
            <bk-form-item :label="$t('filePath')" :required="true" property="includePattern" error-display-type="normal">
                <template>
                    <node-table
                        ref="pathConfig"
                        :init-data="showData.includePattern"
                        @clearError="clearError">
                    </node-table>
                </template>
            </bk-form-item>
            <bk-form-item :label="$t('staffing')">
                <bk-button icon="plus" @click="showAddDialog">{{ $t('add') + $t('space') + $t('user') }}</bk-button>
                <div v-show="showData.users.length" class="mt10 user-list">
                    <div class="pl10 pr10 user-item flex-between-center" v-for="(user, index) in showData.users" :key="index">
                        <div class="flex-align-center">
                            <span class="user-name text-overflow" :title="user">{{ user }}</span>
                        </div>
                        <Icon class="ml10 hover-btn" size="24" name="icon-delete" @click.native="deleteUser(index)" />
                    </div>
                </div>
            </bk-form-item>
            <bk-form-item :label="$t('associatedUseGroup')" property="roles" error-display-type="normal">
                <div style="display: flex;">
                    <bk-tag-input
                        class="w480"
                        v-model="showData.roles"
                        :placeholder="$t('enterPlaceHolder')"
                        trigger="focus"
                        :list="roleList"
                        :has-delete-icon="true">
                    </bk-tag-input>
                    <bk-link theme="primary" @click="manageUserGroup" style="margin-right: auto;margin-left: 10px">{{ $t('manage') + $t('space') + $t('userGroup') }}</bk-link>
                </div>
            </bk-form-item>
        </bk-form>
        <add-user-dialog ref="addUserDialog" :visible.sync="showAddUserDialog" @complete="handleAddUsers"></add-user-dialog>
    </canway-dialog>
</template>

<script>
    import nodeTable from '@/views/repoConfig/permissionConfig/nodeTable'
    import AddUserDialog from '@/components/AddUserDialog/addUserDialog'
    import { mapActions, mapState } from 'vuex'
    import _ from 'lodash'

    export default {
        name: 'CreatePermission',
        components: { nodeTable, AddUserDialog },
        props: {
            show: {
                type: Boolean,
                default: false
            },
            permissionForm: {
                type: Object,
                default: {
                    id: '',
                    users: [],
                    roles: [],
                    includePattern: [],
                    name: ''
                }
            },
            type: {
                type: String,
                default: 'create'
            }
        },
        data () {
            return {
                showDialog: this.show,
                isLoading: false,
                title: '',
                roleList: [],
                rules: {
                    name: [
                        {
                            required: true,
                            message: this.$t('planNameTip'),
                            trigger: 'blur'
                        }
                    ],
                    includePattern: [
                        {
                            required: true,
                            message: this.$t('createPathsTip'),
                            trigger: 'blur'
                        }
                    ]
                },
                showAddUserDialog: false,
                showData: {
                    id: '',
                    users: [],
                    roles: [],
                    includePattern: [],
                    name: ''
                }
            }
        },
        computed: {
            ...mapState(['userInfo']),
            projectId () {
                return this.$route.params.projectId
            },
            repoName () {
                return this.$route.query.repoName
            }
        },
        watch: {
            show: function (newVal) {
                if (newVal) {
                    this.showDialog = true
                    if (this.type === 'create') {
                        this.showData = {
                            id: '',
                            users: [],
                            roles: [],
                            includePattern: [],
                            name: ''
                        }
                    } else {
                        this.showData = _.cloneDeep(this.permissionForm)
                    }
                    this.$refs.pathConfig.pathObjects = this.showData.includePattern
                }
            }
        },
        created () {
            this.getProjectRoleList({ projectId: this.projectId }).then(res => {
                res.forEach(role => {
                    this.roleList.push({
                        id: role.id,
                        name: role.name
                    })
                })
            })
        },
        methods: {
            ...mapActions([
                'createPermissionDeployInRepo',
                'UpdatePermissionConfigInRepo',
                'getProjectRoleList'
            ]),
            clearError (val) {
                this.showData.includePattern = val
                this.$refs.permissionForm.clearError()
            },
            cancel () {
                this.reset()
                this.closeDialog()
            },
            closeDialog () {
                this.showDialog = false
                this.$emit('close')
            },
            async confirm () {
                await this.$refs.permissionForm.validate()
                if (this.type === 'create') {
                    const body = {
                        resourceType: 'NODE',
                        permName: this.showData.name,
                        projectId: this.projectId,
                        repos: [this.repoName],
                        includePattern: this.showData.includePattern,
                        users: this.showData.users,
                        roles: this.showData.roles,
                        actions: ['MANAGE'],
                        createBy: this.userInfo.userId,
                        updatedBy: this.userInfo.userId
                    }
                    this.createPermissionDeployInRepo({
                        body: body
                    }).then(() => {
                        this.reset()
                        this.closeDialog()
                    })
                } else {
                    const body = {
                        name: this.showData.name,
                        projectId: this.projectId,
                        path: this.showData.includePattern,
                        permissionId: this.showData.id,
                        users: this.showData.users,
                        roles: this.showData.roles
                    }
                    this.UpdatePermissionConfigInRepo({
                        body: body
                    }).then(() => {
                        this.reset()
                        this.closeDialog()
                    })
                }
            },
            reset () {
                this.$refs.pathConfig.replicaTaskObjects = []
                this.$refs.permissionForm.clearError()
                this.$emit('refresh')
            },
            manageUserGroup () {
                this.$router.replace({
                    name: 'userGroup'
                })
            },
            deleteUser (index) {
                const temp = []
                for (let i = 0; i < this.permissionForm.users.length; i++) {
                    if (i !== index) {
                        temp.push(this.permissionForm.users[i])
                    }
                }
                this.showData.users = temp
            },
            showAddDialog () {
                this.showAddUserDialog = true
                this.$refs.addUserDialog.editUserConfig = {
                    users: this.showData.users,
                    originUsers: this.showData.users,
                    search: '',
                    newUser: ''
                }
            },
            handleAddUsers (users) {
                this.showData.users = users
            }
        }
    }
</script>

<style lang="scss" scoped>
.permission-form {
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
}
</style>
