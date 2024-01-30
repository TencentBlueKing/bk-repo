<template>
    <canway-dialog
        v-model="show"
        width="800"
        height-num="603"
        :title="type === 'create' ? $t('createPermission') : $t('updatePermission')"
        ref="permDialog"
        @confirm="confirm"
        @cancel="cancel">
        <bk-form class="mb20 plan-form" :label-width="120" :model="permissionForm" :rules="rules" ref="permissionForm">
            <bk-form-item :label="$t('name')" :required="true" property="name" error-display-type="normal">
                <bk-input class="w480" v-model.trim="permissionForm.name"></bk-input>
            </bk-form-item>
            <bk-form-item :label="$t('filePath')" :required="true" property="includePattern" error-display-type="normal">
                <template>
                    <node-table
                        ref="pathConfig"
                        :init-data="permissionForm.includePattern"
                        @clearError="clearError">
                    </node-table>
                </template>
            </bk-form-item>
            <bk-form-item :label="$t('user')" property="users" error-display-type="normal">
                <bk-tag-input
                    class="w480"
                    v-model="permissionForm.users"
                    :placeholder="$t('enterPlaceHolder')"
                    trigger="focus"
                    :has-delete-icon="true"
                    allow-create>
                </bk-tag-input>
            </bk-form-item>
            <bk-form-item :label="$t('associatedUseGroup')" property="roles" error-display-type="normal">
                <bk-tag-input
                    class="w480"
                    v-model="permissionForm.roles"
                    :placeholder="$t('enterPlaceHolder')"
                    trigger="focus"
                    :list="roleList"
                    :has-delete-icon="true">
                </bk-tag-input>
            </bk-form-item>
        </bk-form>
    </canway-dialog>
</template>

<script>
    import nodeTable from '@/views/repoConfig/permissionConfig/nodeTable'
    import { mapActions, mapState } from 'vuex'

    export default {
        name: 'createPermission',
        components: { nodeTable },
        props: {
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
                show: false,
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
                this.permissionForm.includePattern = val
                this.$refs.permissionForm.clearError()
            },
            cancel () {
                this.reset()
            },
            async confirm () {
                await this.$refs.permissionForm.validate()
                if (this.type === 'create') {
                    const body = {
                        resourceType: 'NODE',
                        permName: this.permissionForm.name,
                        projectId: this.projectId,
                        repos: [this.repoName],
                        includePattern: this.permissionForm.includePattern,
                        users: this.permissionForm.users,
                        roles: this.permissionForm.roles,
                        actions: ['MANAGE'],
                        createBy: this.userInfo.userId,
                        updatedBy: this.userInfo.userId
                    }
                    this.createPermissionDeployInRepo({
                        body: body
                    }).then(() => {
                        this.reset()
                        this.$emit('refresh')
                    })
                } else {
                    const body = {
                        name: this.permissionForm.name,
                        projectId: this.projectId,
                        path: this.permissionForm.includePattern,
                        permissionId: this.permissionForm.id,
                        users: this.permissionForm.users,
                        roles: this.permissionForm.roles
                    }
                    this.UpdatePermissionConfigInRepo({
                        body: body
                    }).then(() => {
                        this.reset()
                        this.$emit('refresh')
                    })
                }
            },
            reset () {
                this.show = false
                this.permissionForm = {
                    id: [],
                    users: [],
                    roles: [],
                    includePattern: [],
                    name: ''
                }
                this.type = 'create'
                this.$refs.pathConfig.replicaTaskObjects = []
                this.$refs.permissionForm.clearError()
            }
        }
    }
</script>

<style scoped>

</style>
