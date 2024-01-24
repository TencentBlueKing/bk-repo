<template>
    <div class="user-container" v-bkloading="{ isLoading }">
        <div class="mt10 flex-between-center">
            <div class="ml20 flex-align-center">
                <bk-button icon="plus" theme="primary" @click="showCreateUser">{{ $t('create') }}</bk-button>
                <!-- <bk-button class="ml10" @click="downloadTemplate">下载模板</bk-button>
                <bk-button class="ml10">
                    <span>批量导入</span>
                    <input type="file" accept=".xlsx" @change="importUsersHandler" title="" placeholder="">
                </bk-button> -->
            </div>
            <div class="mr20 flex-align-center">
                <bk-input
                    v-model.trim="userInput"
                    class="w250"
                    :placeholder="$t('userPlaceHolder')"
                    clearable
                    @enter="handlerPaginationChange()"
                    @clear="handlerPaginationChange()"
                    right-icon="bk-icon icon-search">
                </bk-input>
                <bk-select
                    class="ml10 w250"
                    v-model="showAdmin"
                    :placeholder="$t('accountPermission')"
                    @change="handlerPaginationChange()">
                    <bk-option id="true" :name="$t('administrator')"></bk-option>
                    <bk-option id="false" :name="$t('normalUser')"></bk-option>
                </bk-select>
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
            <bk-table-column :label="$t('chineseName')" prop="name"></bk-table-column>
            <bk-table-column :label="$t('email')" prop="email">
                <template #default="{ row }">{{ transEmail(row.email) }}</template>
            </bk-table-column>
            <bk-table-column :label="$t('telephone')" prop="phone">
                <template #default="{ row }">{{ transPhone(row.phone) }}</template>
            </bk-table-column>
            <bk-table-column :label="$t('createdDate')">
                <template #default="{ row }">{{ formatDate(row.createdDate) }}</template>
            </bk-table-column>
            <bk-table-column :label="$t('administrator')">
                <template #default="{ row }">
                    <bk-switcher class="m5" v-model="row.admin" size="small" theme="primary" @change="changeAdminStatus(row)"></bk-switcher>
                </template>
            </bk-table-column>
            <bk-table-column :label="$t('activateAccount')">
                <template #default="{ row }">
                    <bk-switcher class="m5" :value="!row.locked" size="small" theme="primary" @change="changeUserStatus(row)"></bk-switcher>
                </template>
            </bk-table-column>
            <bk-table-column :label="$t('operation')" width="100">
                <template #default="{ row }">
                    <operation-list
                        :list="[
                            { label: $t('edit'), clickEvent: () => showEditUser(row) },
                            { label: $t('resetPassword'), clickEvent: () => resetUserPwd(row) },
                            { label: $t('delete'), clickEvent: () => deleteUserHandler(row) }
                        ]"></operation-list>
                </template>
            </bk-table-column>
        </bk-table>
        <bk-pagination
            class="p10"
            size="small"
            align="right"
            show-total-count
            @change="current => handlerPaginationChange({ current })"
            @limit-change="limit => handlerPaginationChange({ limit })"
            :current.sync="pagination.current"
            :limit="pagination.limit"
            :count="pagination.count"
            :limit-list="pagination.limitList">
        </bk-pagination>
        <canway-dialog
            v-model="editUserDialog.show"
            :title="editUserDialog.add ? $t('createUser') : $t('editUser')"
            width="500"
            height-num="400"
            @cancel="editUserDialog.show = false">
            <bk-form class="mr30" :label-width="90" :model="editUserDialog" :rules="rules" ref="editUserDialog">
                <bk-form-item :label="$t('type')" :required="true">
                    <bk-radio-group v-model="editUserDialog.group">
                        <bk-radio :value="false" :disabled="!editUserDialog.add">{{ $t('entityUser')}}</bk-radio>
                        <bk-radio class="ml20" :value="true" :disabled="!editUserDialog.add">{{ $t('virtualUser')}}</bk-radio>
                    </bk-radio-group>
                </bk-form-item>
                <bk-form-item :label="$t('account')" :required="true" property="userId" error-display-type="normal">
                    <bk-input v-model.trim="editUserDialog.userId"
                        :disabled="!editUserDialog.add"
                        maxlength="32" show-word-limit
                        :placeholder="$t('userIdPlaceHolder')">
                    </bk-input>
                </bk-form-item>
                <bk-form-item :label="$t('chineseName')" :required="true" property="name" error-display-type="normal">
                    <bk-input v-model.trim="editUserDialog.name" maxlength="32" show-word-limit></bk-input>
                </bk-form-item>
                <bk-form-item :label="$t('email')" :required="true" property="email" error-display-type="normal">
                    <bk-input v-model.trim="editUserDialog.email"></bk-input>
                </bk-form-item>
                <bk-form-item :label="$t('telephone')">
                    <bk-input v-model.trim="editUserDialog.phone"></bk-input>
                </bk-form-item>
                <bk-form-item v-if="editUserDialog.group" :required="true" property="asstUsers" :label="$t('associatedUser')">
                    <bk-tag-input
                        v-model="editUserDialog.asstUsers"
                        :placeholder="$t('enterPlaceHolder')"
                        trigger="focus"
                        :create-tag-validator="tag => validateUser(tag)"
                        allow-create>
                    </bk-tag-input>
                </bk-form-item>
            </bk-form>
            <template #footer>
                <bk-button theme="default" @click.stop="editUserDialog.show = false">{{$t('cancel')}}</bk-button>
                <bk-button class="ml10" :loading="editUserDialog.loading" theme="primary" @click="confirm">{{$t('confirm')}}</bk-button>
            </template>
        </canway-dialog>
    </div>
</template>
<script>
    import OperationList from '@repository/components/OperationList'
    import { mapState, mapActions } from 'vuex'
    import { formatDate } from '@repository/utils'
    import { transformEmail, transformPhone } from '@repository/utils/privacy'
    export default {
        name: 'user',
        components: { OperationList },
        data () {
            return {
                isLoading: false,
                showAdmin: '',
                userInput: '',
                userListPages: [],
                pagination: {
                    count: 0,
                    current: 1,
                    limit: 20,
                    limitList: [10, 20, 40]
                },
                editUserDialog: {
                    show: false,
                    loading: false,
                    add: true,
                    userId: '',
                    name: '',
                    email: '',
                    phone: '',
                    group: false,
                    asstUsers: []
                },
                rules: {
                    userId: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + this.$t('space') + this.$t('account'),
                            trigger: 'blur'
                        },
                        {
                            regex: /^[a-zA-Z][a-zA-Z0-9_-|@]{1,31}$/,
                            message: this.$t('account') + this.$t('space') + this.$t('include') + this.$t('space') + this.$t('userIdPlaceHolder'),
                            trigger: 'blur'
                        },
                        {
                            validator: this.asynCheckUserId,
                            message: this.$t('account') + this.$t('space') + this.$t('existed'),
                            trigger: 'blur'
                        }
                    ],
                    name: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + this.$t('space') + this.$t('chineseName'),
                            trigger: 'blur'
                        }
                    ],
                    email: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + this.$t('space') + this.$t('email'),
                            trigger: 'blur'
                        },
                        {
                            regex: /^\w[-_.\w]*@\w[-_\w]*\.\w[-_.\w]*$/,
                            message: this.$t('pleaseInput') + this.$t('space') + this.$t('legit') + this.$t('space') + this.$t('email'),
                            trigger: 'blur'
                        }
                    ],
                    asstUsers: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + this.$t('space') + this.$t('associated user'),
                            trigger: 'blur'
                        }
                    ]
                }
            }
        },
        computed: {
            ...mapState(['userInfo', 'userList']),
            isSearching () {
                const { user, admin } = this.$route.query
                return user || admin
            }
        },
        created () {
            const { user, admin } = this.$route.query
            this.userInput = user
            this.showAdmin = admin
            this.handlerPaginationChange()
        },
        methods: {
            formatDate,
            ...mapActions([
                'getRepoUserList',
                'getUserList',
                'createUser',
                'editUser',
                'deleteUser',
                'resetPwd',
                'checkUserId',
                'getUserInfo',
                'importUsers',
                'validateEntityUser'
            ]),
            asynCheckUserId () {
                return !this.editUserDialog.add || !(this.editUserDialog.userId in this.userList)
                // return this.checkUserId({
                //     userId: this.editUserDialog.userId
                // }).then(res => !res)
            },
            handlerPaginationChange ({ current = 1, limit = this.pagination.limit } = {}) {
                this.pagination.current = current
                this.pagination.limit = limit
                this.$router.replace({
                    query: {
                        ...this.$route.query,
                        user: this.userInput,
                        admin: this.showAdmin
                    }
                })
                this.getUserListHandler()
            },
            getUserListHandler () {
                this.isLoading = true
                return this.getUserList({
                    user: this.userInput || undefined,
                    admin: this.showAdmin || undefined,
                    current: this.pagination.current,
                    limit: this.pagination.limit
                }).then(({ records, totalRecords }) => {
                    this.userListPages = records
                    this.pagination.count = totalRecords
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
                    email: '',
                    phone: '',
                    group: false,
                    asstUsers: []
                }
            },
            importUsersHandler (e) {
                const file = e.target.files[0]
                if (!(/\.xlsx$/.test(file.name))) {
                    this.$bkMessage({
                        theme: 'error',
                        message: this.$t('wrongFileType')
                    })
                    e.target.value = ''
                    return
                }
                const reader = new FileReader()
                reader.onload = (f) => {
                    const ab = f.target.result
                    const promise = window.XLSX ? Promise.resolve() : window.loadLibScript('/ui/libs/xlsx.mini.js')
                    promise.then(() => {
                        const wb = window.XLSX.read(new Uint8Array(ab), { type: 'array' })
                        const wsname = wb.SheetNames[0]
                        const ws = wb.Sheets[wsname]
                        const data = window.XLSX.utils.sheet_to_json(ws, { header: ['userId', 'name', 'email', 'phone'], range: 1 })
                        this.requestImportUsers(data).finally(() => {
                            e.target.value = ''
                        })
                    })
                }
                reader.readAsArrayBuffer(file)
            },
            requestImportUsers (data) {
                const errMessage = {
                    userId: [],
                    name: [],
                    email: []
                }
                const catchUser = new Set()
                data.forEach(({ userId, name, email }, index) => {
                    if (userId in this.userList || catchUser.has(userId) || userId.lengt > 32 || !(/^[a-zA-Z][a-zA-Z0-9_-]{1,31}$/.test(userId))) {
                        errMessage.userId.push(index + 2)
                    } else {
                        catchUser.add(userId)
                    }
                    if (!name || name.lengt > 32) {
                        errMessage.name.push(index + 2)
                    }
                    if (!(/^\w[-_.\w]*@\w[-_\w]*\.\w[-_.\w]*$/.test(email))) {
                        errMessage.email.push(index + 2)
                    }
                })
                if (errMessage.userId.length || errMessage.name.length || errMessage.email.length) {
                    const message = (errMessage.userId.length ? this.$t('formatWrongTip', [errMessage.userId]) : '')
                        + (errMessage.name.length ? this.$t('chineseNameWrongTip', [errMessage.name]) : '')
                        + (errMessage.email.length ? this.$t('emailWrongTip', [errMessage.email]) : '')
                    this.$bkMessage({
                        theme: 'error',
                        message
                    })
                    return Promise.resolve()
                } else {
                    this.isLoading = true
                    return this.importUsers({ body: data }).then(() => {
                        this.$bkMessage({
                            theme: 'success',
                            message: this.$t('userImport') + this.$t('success')
                        })
                        this.getRepoUserList()
                        this.handlerPaginationChange()
                    }).finally(() => {
                        this.isLoading = false
                    })
                }
            },
            downloadTemplate () {
                window.open('/ui/users_import.xlsx', '_self')
            },
            async confirm () {
                await this.$refs.editUserDialog.validate()
                this.editUserDialog.loading = true
                const { userId, name, email, phone, group, asstUsers } = this.editUserDialog
                const fn = this.editUserDialog.add ? this.createUser : this.editUser
                fn({
                    body: {
                        userId,
                        name,
                        email,
                        phone,
                        group,
                        asstUsers
                    }
                }).then(res => {
                    this.$bkMessage({
                        theme: 'success',
                        message: (this.editUserDialog.add ? this.$t('createUser') : this.$t('editUser')) + this.$t('space') + this.$t('success')
                    })
                    this.editUserDialog.show = false
                    this.editUserDialog.userId === this.userInfo.username && this.getUserInfo({ userId: this.userInfo.username })
                    this.getRepoUserList()
                    this.getUserListHandler()
                }).finally(() => {
                    this.editUserDialog.loading = false
                })
            },
            showEditUser (row) {
                this.$refs.editUserDialog && this.$refs.editUserDialog.clearError()
                this.editUserDialog = {
                    show: true,
                    loading: false,
                    add: false,
                    userId: '',
                    name: '',
                    email: '',
                    phone: '',
                    group: false,
                    asstUsers: [],
                    ...row
                }
            },
            deleteUserHandler (row) {
                this.$confirm({
                    theme: 'danger',
                    message: this.$t('deleteUserTitle', [row.name]),
                    confirmFn: () => {
                        return this.deleteUser(row.userId).then(() => {
                            this.getRepoUserList()
                            this.getUserListHandler()
                            this.$bkMessage({
                                theme: 'success',
                                message: this.$t('delete') + this.$t('space') + this.$t('success')
                            })
                        })
                    }
                })
            },
            resetUserPwd (row) {
                this.$confirm({
                    theme: 'danger',
                    message: this.$t('resetUserMsg', { 0: row.name }),
                    confirmFn: () => {
                        return this.resetPwd(row.userId).then(() => {
                            this.$bkMessage({
                                theme: 'success',
                                message: this.$t('resetPassword') + this.$t('space') + this.$t('success')
                            })
                        })
                    }
                })
            },
            changeUserStatus ({ userId, locked }) {
                this.editUser({
                    body: {
                        userId,
                        locked: !locked
                    }
                }).then(() => {
                    this.$bkMessage({
                        theme: 'success',
                        message: !locked ? this.$t('forbidden') : this.$t('enabled')
                    })
                }).finally(() => {
                    this.getUserListHandler()
                })
            },
            changeAdminStatus ({ userId, admin }) {
                this.editUser({
                    body: {
                        userId,
                        admin
                    }
                }).then(res => {
                    this.$bkMessage({
                        theme: 'success',
                        message: this.$t('set') + this.$t('space') + `${admin ? this.$t('administrator') : this.$t('normalUser')}`
                    })
                }).finally(() => {
                    this.getUserListHandler()
                })
            },
            async validateUser (tag) {
                const res = await this.validateEntityUser(tag)
                if (!res) {
                    this.editUserDialog.asstUsers.splice(this.editUserDialog.asstUsers.indexOf(tag), 1)
                }
            },
            transEmail (email) {
                if (email === null) return email
                return transformEmail(email)
            },
            transPhone (phone) {
                if (phone === null || phone === '') return '/'
                return transformPhone(phone)
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
