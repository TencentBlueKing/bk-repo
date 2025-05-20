<template>
    <div class="user-container" v-bkloading="{ isLoading }">
        <div class="mt10 flex-between-center">
            <div class="ml20 flex-align-center">
                <bk-button icon="plus" theme="primary" @click="showCreateUser">{{ $t('create') }}</bk-button>
            </div>
        </div>
        <bk-table
            class="mt10"
            height="calc(100% - 100px)"
            :data="userListPages"
            :outer-border="false"
            :row-border="false"
            row-key="userId"
            size="small">
            <template #empty>
                <empty-data :is-loading="isLoading" :search="Boolean(isSearching)"></empty-data>
            </template>
            <bk-table-column :label="$t('account')" prop="userId"></bk-table-column>
            <bk-table-column :label="$t('createdDate')">
                <template #default="{ row }">{{formatDate(row.createdDate)}}</template>
            </bk-table-column>
            <bk-table-column :label="$t('associatedUser')">
                <template #default="{ row }">
                    <bk-user-display-name v-if="multiMode" :user-id="row.asstUsers.toLocaleString()"></bk-user-display-name>
                    <span v-else> {{ row.asstUsers.toLocaleString() }}</span>
                </template>
            </bk-table-column>
            <bk-table-column :label="$t('operation')" width="100">
                <template #default="{ row }">
                    <operation-list
                        :list="[
                            { label: $t('delete'), clickEvent: () => deleteUserHandler(row) },
                            { label: $t('createGroupToken'), clickEvent: () => showCreateToken(row) }
                        ]"></operation-list>
                </template>
            </bk-table-column>
        </bk-table>
        <canway-dialog
            v-model="editUserDialog.show"
            :title="editUserDialog.add ? $t('createUser') : $t('editUser')"
            width="500"
            height-num="400"
            @cancel="editUserDialog.show = false">
            <bk-form class="mr30" :label-width="90" :model="editUserDialog" :rules="rules" ref="editUserDialog">
                <bk-form-item :label="$t('type')" :required="true">
                    <bk-radio-group v-model="editUserDialog.group">
                        <bk-radio class="ml20" :value="true" :disabled="true">{{ $t('virtualUser')}}</bk-radio>
                    </bk-radio-group>
                </bk-form-item>
                <bk-form-item :label="$t('account')" :required="true" property="userId" error-display-type="normal">
                    <bk-input v-model.trim="editUserDialog.userId"
                        :disabled="!editUserDialog.add"
                        maxlength="32" show-word-limit
                        :placeholder="$t('assetUserTip')">
                    </bk-input>
                </bk-form-item>
                <bk-form-item v-if="editUserDialog.group" :required="true" property="asstUsers" :label="$t('associatedUser')">
                    <bk-user-display-name v-if="multiMode" :user-id="editUserDialog.asstUsers.toLocaleString()"></bk-user-display-name>
                    <bk-tag-input
                        v-else
                        v-model="editUserDialog.asstUsers"
                        :placeholder="$t('enterPlaceHolder')"
                        trigger="focus"
                        :create-tag-validator="tag => validateUser(tag)"
                        disabled="true"
                        allow-create>
                    </bk-tag-input>
                </bk-form-item>
            </bk-form>
            <template #footer>
                <bk-button theme="default" @click.stop="editUserDialog.show = false">{{$t('cancel')}}</bk-button>
                <bk-button class="ml10" :loading="editUserDialog.loading" theme="primary" @click="confirm">{{$t('confirm')}}</bk-button>
            </template>
        </canway-dialog>
        <create-token-dialog ref="createToken"></create-token-dialog>
    </div>
</template>
<script>
    import OperationList from '@repository/components/OperationList'
    import createTokenDialog from '@repository/views/repoToken/createTokenDialog'
    import { mapState, mapActions } from 'vuex'
    import { formatDate } from '@repository/utils'
    export default {
        name: 'user',
        components: { OperationList, createTokenDialog },
        data () {
            return {
                isLoading: false,
                userInput: '',
                userListPages: [],
                editUserDialog: {
                    show: false,
                    loading: false,
                    add: true,
                    userId: '',
                    name: '',
                    email: null,
                    phone: null,
                    group: true,
                    asstUsers: [],
                    projectId: this.$route.params.projectId
                },
                multiMode: BK_REPO_ENABLE_MULTI_TENANT_MODE === 'true',
                rules: {
                    userId: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + this.$t('space') + this.$t('account'),
                            trigger: 'blur'
                        },
                        {
                            validator: this.asynCheckUserIdType,
                            message: this.$t('assetUserTip'),
                            trigger: 'blur'
                        },
                        {
                            validator: this.asynCheckUserId,
                            message: this.$t('account') + this.$t('space') + this.$t('existed'),
                            trigger: 'blur'
                        }
                    ]
                }
            }
        },
        computed: {
            ...mapState(['userInfo', 'userList']),
            isSearching () {
                const { user } = this.$route.query
                return user
            }
        },
        created () {
            this.handlerChange()
        },
        methods: {
            formatDate,
            ...mapActions([
                'getRepoUserList',
                'getUserRelatedList',
                'createProjectUser',
                'deleteUser',
                'checkUserId',
                'getUserInfo'
            ]),
            asynCheckUserId () {
                return !this.editUserDialog.add || !(this.editUserDialog.userId in this.userList)
            },
            asynCheckUserIdType () {
                return this.editUserDialog.userId.startsWith('g_')
            },
            handlerChange () {
                this.$router.replace({
                    query: {
                        ...this.$route.query
                    }
                })
                this.getUserListHandler()
            },
            getUserListHandler () {
                this.isLoading = true
                return this.getUserRelatedList({
                    asstUser: this.userInfo.username
                }).then((res) => {
                    this.userListPages = res
                }).finally(() => {
                    this.isLoading = false
                })
            },
            showCreateUser () {
                this.$refs.editUserDialog && this.$refs.editUserDialog.clearError()
                this.editUserDialog = {
                    show: true,
                    loading: false,
                    add: true,
                    userId: '',
                    name: '',
                    email: null,
                    phone: null,
                    group: true,
                    asstUsers: [this.userInfo.username],
                    projectId: this.$route.params.projectId
                }
            },
            showCreateToken (row) {
                this.$refs.createToken.userName = row.userId
                this.$refs.createToken.showDialogHandler()
            },
            async confirm () {
                await this.$refs.editUserDialog.validate()
                this.editUserDialog.loading = true
                let { userId, name, email, phone, group, asstUsers, projectId } = this.editUserDialog
                name = userId
                const fn = this.createProjectUser
                fn({
                    body: {
                        userId,
                        name,
                        email,
                        phone,
                        group,
                        asstUsers,
                        projectId
                    }
                }).then(() => {
                    this.$bkMessage({
                        theme: 'success',
                        message: (this.editUserDialog.add ? this.$t('createUser') : this.$t('editUser')) + this.$t('space') + this.$t('success')
                    })
                    this.editUserDialog.show = false
                    this.editUserDialog.userId === this.userInfo.username && this.getUserInfo({ userId: this.userInfo.username })
                    this.getRepoUserList({ projectId: this.$route.params.projectId })
                    this.getUserListHandler()
                }).finally(() => {
                    this.editUserDialog.loading = false
                })
            },
            deleteUserHandler (row) {
                this.$confirm({
                    theme: 'danger',
                    message: this.$t('deleteUserTitle', [row.name]),
                    confirmFn: () => {
                        return this.deleteUser(row.userId).then(() => {
                            this.getRepoUserList({ projectId: this.$route.params.projectId })
                            this.getUserListHandler()
                            this.$bkMessage({
                                theme: 'success',
                                message: this.$t('delete') + this.$t('space') + this.$t('success')
                            })
                        })
                    }
                })
            },
            async validateUser (tag) {
                const res = await this.validateEntityUser(tag)
                if (!res) {
                    this.editUserDialog.asstUsers.splice(this.editUserDialog.asstUsers.indexOf(tag), 1)
                }
            }
        }
    }
</script>
<style lang="scss" scoped>
.user-container {
    height: 100%;
    overflow: hidden;
}
</style>
