<template>
    <div class="user-container" v-bkloading="{ isLoading }">
        <div class="mb20 flex-align-center">
            <bk-input
                class="user-search"
                v-model.trim="userInput"
                clearable
                :placeholder="'请输入账号/中文名'"
                @enter="handlerPaginationChange()"
                @clear="handlerPaginationChange()">
            </bk-input>
            <i class="user-search-btn devops-icon icon-search" @click="handlerPaginationChange()"></i>
            <div class="create-user flex-align-center">
                <bk-checkbox
                    v-model="showAdmin"
                    :true-value="true"
                    :false-value="''"
                    @change="handlerPaginationChange()">仅查看管理员</bk-checkbox>
                <bk-button v-if="mode === 'standalone'" class="ml20" theme="primary" @click.stop="showCreateUser">{{ $t('create') + $t('user') }}</bk-button>
            </div>
        </div>
        <bk-table
            class="user-table"
            height="calc(100% - 120px)"
            :data="userList"
            :outer-border="false"
            :row-border="false"
            row-key="userId"
            size="small"
        >
            <bk-table-column :label="$t('account')" prop="userId" width="200"></bk-table-column>
            <bk-table-column :label="$t('chineseName')" prop="name"></bk-table-column>
            <bk-table-column :label="$t('email')" prop="email"></bk-table-column>
            <bk-table-column label="电话" prop="phone"></bk-table-column>
            <bk-table-column :label="$t('createdDate')">
                <span slot-scope="props">{{formatDate(props.row.createdDate)}}</span>
            </bk-table-column>
            <bk-table-column label="账号权限">
                <div slot-scope="props" class="flex-align-center">
                    <bk-switcher class="mr10" :key="props.row.id" v-model="props.row.admin" @change="changeAdminStatus(props.row)"></bk-switcher>
                    <div>{{props.row.admin ? '管理员' : '普通用户'}}</div>
                </div>
            </bk-table-column>
            <bk-table-column :label="$t('account') + $t('status')">
                <div slot-scope="props" class="flex-align-center">
                    <bk-switcher v-if="mode === 'standalone'" class="mr10" :key="props.row.id" v-model="props.row.locked" @change="changeUserStatus(props.row)"></bk-switcher>
                    <div>{{`${props.row.locked ? '已' : '未'}锁定`}}</div>
                </div>
            </bk-table-column>
            <bk-table-column v-if="mode === 'standalone'" :label="$t('operation')" width="150">
                <div slot-scope="props" class="flex-align-center">
                    <i class="mr20 devops-icon icon-edit hover-btn" @click="showEditUser(props.row)"></i>
                    <!-- <i class="devops-icon icon-delete hover-btn" @click="deleteUser(props.row)"></i> -->
                </div>
            </bk-table-column>
        </bk-table>
        <bk-pagination
            class="mt10"
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
        <bk-dialog
            v-model="editUserDialog.show"
            :title="editUserDialog.add ? '新建用户' : '编辑用户'"
            width="600"
            :close-icon="false"
            :quick-close="false"
            :draggable="false">
            <bk-form class="mr50" :label-width="110" :model="editUserDialog" :rules="rules" ref="editUserDialog">
                <bk-form-item class="mt30" :label="$t('account')" :required="true" property="userId">
                    <bk-input v-model.trim="editUserDialog.userId" :disabled="!editUserDialog.add" maxlength="32" :placeholder="$t('userIdPlacehodler')"></bk-input>
                </bk-form-item>
                <bk-form-item class="mt30" :label="$t('chineseName')" :required="true" property="name">
                    <bk-input v-model.trim="editUserDialog.name"></bk-input>
                </bk-form-item>
                <bk-form-item class="mt30" :label="$t('email')" :required="true" property="email">
                    <bk-input v-model.trim="editUserDialog.email" type="email"></bk-input>
                </bk-form-item>
                <bk-form-item class="mt30" label="电话">
                    <bk-input v-model.trim="editUserDialog.phone"></bk-input>
                </bk-form-item>
            </bk-form>
            <div slot="footer">
                <bk-button :loading="editUserDialog.loading" theme="primary" @click.stop.prevent="confirm">{{$t('submit')}}</bk-button>
                <bk-button theme="default" @click.stop="editUserDialog.show = false">{{$t('cancel')}}</bk-button>
            </div>
        </bk-dialog>
    </div>
</template>
<script>
    import { mapState, mapActions } from 'vuex'
    import { formatDate } from '@/utils'
    export default {
        name: 'user',
        data () {
            return {
                isLoading: false,
                showAdmin: '',
                userInput: '',
                userList: [],
                pagination: {
                    count: 1,
                    current: 1,
                    limit: 10,
                    limitList: [10, 20, 40]
                },
                editUserDialog: {
                    show: false,
                    loading: false,
                    add: true,
                    userId: '',
                    name: '',
                    email: '',
                    phone: ''
                },
                rules: {
                    userId: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + this.$t('account'),
                            trigger: 'blur'
                        },
                        {
                            regex: /^[a-zA-Z0-9]{1,32}$/,
                            message: this.$t('account') + this.$t('include') + this.$t('userIdPlacehodler'),
                            trigger: 'blur'
                        },
                        {
                            validator: this.asynCheckUserId,
                            message: this.$t('account') + this.$t('repeat'),
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
                            regex: /\@/,
                            message: this.$t('pleaseInput') + this.$t('legit') + this.$t('email'),
                            trigger: 'blur'
                        }
                    ]
                }
            }
        },
        computed: {
            ...mapState(['userInfo']),
            mode () {
                return MODE_CONFIG
            }
        },
        created () {
            this.handlerPaginationChange()
        },
        methods: {
            formatDate,
            ...mapActions([
                'getUserList',
                'createUser',
                'editUser',
                'deleteUser',
                'checkUserId',
                'getRepoUserList',
                'getUserInfo'
            ]),
            asynCheckUserId () {
                if (!this.editUserDialog.add) return true
                return this.checkUserId({
                    userId: this.editUserDialog.userId
                }).then(res => !res)
            },
            handlerPaginationChange ({ current = 1, limit = this.pagination.limit } = {}) {
                this.pagination.current = current
                this.pagination.limit = limit
                this.getUserListHandler()
            },
            getUserListHandler () {
                this.isLoading = true
                return this.getUserList({
                    user: this.userInput,
                    admin: this.showAdmin,
                    current: this.pagination.current,
                    limit: this.pagination.limit
                }).then(({ records, totalRecords }) => {
                    this.userList = records
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
                    phone: ''
                }
            },
            async confirm () {
                await this.$refs.editUserDialog.validate()
                this.editUserDialog.loading = true
                const { userId, name, email, phone } = this.editUserDialog
                const fn = this.editUserDialog.add ? this.createUser : this.editUser
                fn({
                    body: {
                        userId,
                        name,
                        email,
                        phone
                    }
                }).then(res => {
                    this.$bkMessage({
                        theme: 'success',
                        message: (this.editUserDialog.add ? '新建用户' : '编辑用户') + this.$t('success')
                    })
                    this.editUserDialog.show = false
                    this.editUserDialog.userId === this.userInfo.username && this.getUserInfo({ userId: this.userInfo.username })
                    this.getUserListHandler()
                }).finally(() => {
                    // 更新用户列表缓存
                    this.editUserDialog.add && this.getRepoUserList()
                    this.editUserDialog.loading = false
                })
            },
            showEditUser (row) {
                this.$refs.editUserDialog && this.$refs.editUserDialog.clearError()
                this.editUserDialog = {
                    show: true,
                    loading: false,
                    add: false,
                    ...row
                }
            },
            deleteUser (row) {
                // this.$bkInfo({
                //     type: 'error',
                //     title: this.$t('deleteUserTitle', [row.name]),
                //     showFooter: true,
                //     confirmFn: () => {
                //         this.deleteUser({
                //             projectId: this.projectId,
                //             name
                //         }).then(() => {
                //             this.getListData()
                //             this.$bkMessage({
                //                 theme: 'success',
                //                 message: this.$t('delete') + this.$t('success')
                //             })
                //         })
                //     }
                // })
            },
            changeUserStatus ({ userId, locked }) {
                this.editUser({
                    body: {
                        userId,
                        locked
                    }
                }).then(res => {
                    this.$bkMessage({
                        theme: 'success',
                        message: `${locked ? '已' : '未'}锁定`
                    })
                }).finally(() => {
                    this.getRepoUserList()
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
                        message: `设置为${admin ? '管理员' : '普通用户'}`
                    })
                }).finally(() => {
                    this.getRepoUserList()
                    this.getUserListHandler()
                })
            }
        }
    }
</script>
<style lang="scss" scoped>
.user-container {
    height: calc(100% + 40px);
    margin-bottom: -40px;
    .user-search {
        width: 250px;
    }
    .user-search-btn {
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
    .user-table {
        .icon-edit {
            font-size: 14px;
        }
        .icon-delete {
            font-size: 16px;
        }
    }
}
</style>
