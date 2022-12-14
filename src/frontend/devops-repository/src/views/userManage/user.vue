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
                    placeholder="请输入账号/中文名, 按Enter键搜索"
                    clearable
                    @enter="handlerPaginationChange()"
                    @clear="handlerPaginationChange()"
                    right-icon="bk-icon icon-search">
                </bk-input>
                <bk-select
                    class="ml10 w250"
                    v-model="showAdmin"
                    placeholder="账号权限"
                    @change="handlerPaginationChange()">
                    <bk-option id="true" name="系统管理员"></bk-option>
                    <bk-option id="false" name="普通用户"></bk-option>
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
            <bk-table-column :label="$t('email')" prop="email"></bk-table-column>
            <bk-table-column label="电话" prop="phone">
                <template #default="{ row }">{{row.phone || '/'}}</template>
            </bk-table-column>
            <bk-table-column :label="$t('createdDate')">
                <template #default="{ row }">{{formatDate(row.createdDate)}}</template>
            </bk-table-column>
            <bk-table-column label="系统管理员">
                <template #default="{ row }">
                    <bk-switcher class="m5" v-model="row.admin" size="small" theme="primary" @change="changeAdminStatus(row)"></bk-switcher>
                </template>
            </bk-table-column>
            <bk-table-column label="启用账号">
                <template #default="{ row }">
                    <bk-switcher class="m5" :value="!row.locked" size="small" theme="primary" @change="changeUserStatus(row)"></bk-switcher>
                </template>
            </bk-table-column>
            <bk-table-column :label="$t('operation')" width="70">
                <template #default="{ row }">
                    <operation-list
                        :list="[
                            { label: '编辑', clickEvent: () => showEditUser(row) },
                            { label: '重置密码', clickEvent: () => resetUserPwd(row) },
                            { label: '删除', clickEvent: () => deleteUserHandler(row) }
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
            :title="editUserDialog.add ? '创建用户' : '编辑用户'"
            width="500"
            height-num="400"
            @cancel="editUserDialog.show = false">
            <bk-form class="mr30" :label-width="90" :model="editUserDialog" :rules="rules" ref="editUserDialog">
                <bk-form-item label="类型" :required="true">
                    <bk-radio-group v-model="editUserDialog.group">
                        <bk-radio :value="false" :disabled="!editUserDialog.add">实体用户</bk-radio>
                        <bk-radio class="ml20" :value="true" :disabled="!editUserDialog.add">虚拟用户</bk-radio>
                    </bk-radio-group>
                </bk-form-item>
                <bk-form-item :label="$t('account')" :required="true" property="userId" error-display-type="normal">
                    <bk-input v-model.trim="editUserDialog.userId"
                        :disabled="!editUserDialog.add"
                        maxlength="32" show-word-limit
                        :placeholder="$t('userIdPlacehodler')">
                    </bk-input>
                </bk-form-item>
                <bk-form-item :label="$t('chineseName')" :required="true" property="name" error-display-type="normal">
                    <bk-input v-model.trim="editUserDialog.name" maxlength="32" show-word-limit></bk-input>
                </bk-form-item>
                <bk-form-item :label="$t('email')" :required="true" property="email" error-display-type="normal">
                    <bk-input v-model.trim="editUserDialog.email"></bk-input>
                </bk-form-item>
                <bk-form-item label="电话">
                    <bk-input v-model.trim="editUserDialog.phone"></bk-input>
                </bk-form-item>
                <bk-form-item v-if="editUserDialog.group" :required="true" property="asstUsers" label="关联用户">
                    <bk-tag-input
                        v-model="editUserDialog.asstUsers"
                        placeholder="请输入，按Enter键确认"
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
                            message: this.$t('pleaseInput') + this.$t('account'),
                            trigger: 'blur'
                        },
                        {
                            regex: /^[a-zA-Z][a-zA-Z0-9_-]{1,31}$/,
                            message: this.$t('account') + this.$t('include') + this.$t('userIdPlacehodler'),
                            trigger: 'blur'
                        },
                        {
                            validator: this.asynCheckUserId,
                            message: this.$t('account') + '已被占用',
                            trigger: 'blur'
                        }
                    ],
                    name: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + this.$t('chineseName'),
                            trigger: 'blur'
                        }
                    ],
                    email: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + this.$t('email'),
                            trigger: 'blur'
                        },
                        {
                            regex: /^\w[-_.\w]*@\w[-_\w]*\.\w[-_.\w]*$/,
                            message: this.$t('pleaseInput') + this.$t('legit') + this.$t('email'),
                            trigger: 'blur'
                        }
                    ],
                    asstUsers: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + '关联账户',
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
                        message: '文件类型错误'
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
                    const message = (errMessage.userId.length ? `第${errMessage.userId}行账号已存在或格式错误` : '')
                        + (errMessage.name.length ? `第${errMessage.name}行中文名格式错误` : '')
                        + (errMessage.email.length ? `第${errMessage.email}行邮箱格式错误` : '')
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
                            message: '用户导入' + this.$t('success')
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
                        message: (this.editUserDialog.add ? '新建用户' : '编辑用户') + this.$t('success')
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
                                message: this.$t('delete') + this.$t('success')
                            })
                        })
                    }
                })
            },
            resetUserPwd (row) {
                this.$confirm({
                    theme: 'danger',
                    message: `确认重置账户 ${row.name} 的密码为默认密码？`,
                    confirmFn: () => {
                        return this.resetPwd(row.userId).then(() => {
                            this.$bkMessage({
                                theme: 'success',
                                message: '重置密码' + this.$t('success')
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
                        message: !locked ? '已禁用' : '已启用'
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
                        message: `设置为${admin ? '系统管理员' : '普通用户'}`
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
