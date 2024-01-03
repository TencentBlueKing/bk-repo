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
            <bk-form-item :label="$t('user')" :required="true" property="users" error-display-type="normal">
                <bk-tag-input
                    class="w480"
                    v-model="permissionForm.users"
                    :placeholder="$t('enterPlaceHolder')"
                    trigger="focus"
                    :has-delete-icon="true"
                    allow-create>
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
                    ],
                    users: [
                        {
                            required: true,
                            message: this.$t('createUsersTip'),
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
        methods: {
            ...mapActions([
                'createPermissionDeployInRepo',
                'UpdatePermissionConfigInRepo'
            ]),
            clearError (val) {
                this.permissionForm.includePattern = val
                this.$refs.permissionForm.clearError()
            },
            cancel () {
                this.reset()
                this.show = false
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
                        users: this.permissionForm.users
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
                    includePattern: [],
                    name: ''
                }
                this.type = 'create'
                this.$refs.pathConfig.replicaTaskObjects = []
            }
        }
    }
</script>

<style scoped>

</style>
