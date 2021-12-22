<template>
    <div class="user-info-container">
        <bk-form class="mt50" :label-width="180">
            <bk-form-item v-for="item in formItem" :key="item.key" :label="item.label">
                <div v-if="!editItem.key || editItem.key !== item.key" class="flex-align-center">
                    <span>{{userInfo[item.key] || '--'}}</span>
                    <bk-button class="ml20 flex-align-center"
                        v-if="MODE_CONFIG === 'standalone' && !editItem.key"
                        text
                        @click="editUserInfo(item)">
                        {{$t('modify')}}
                    </bk-button>
                </div>
                <div v-else class="flex-align-center">
                    <bk-input class="w250" v-focus v-model.trim="editItem.value" maxlength="32" show-word-limit></bk-input>
                    <bk-button class="ml10" theme="default" @click="cancelEdit">{{$t('cancel')}}</bk-button>
                    <bk-button class="ml10" theme="primary" @click="confirmEdit">{{$t('confirm')}}</bk-button>
                </div>
            </bk-form-item>
            <bk-form-item label="密码：">
                <div class="flex-align-center">
                    <span>********</span>
                    <bk-button class="ml20 flex-align-center"
                        v-if="MODE_CONFIG === 'standalone' && !editItem.key"
                        text
                        @click="showModifyPwd()">
                        {{$t('modify')}}
                    </bk-button>
                </div>
            </bk-form-item>
        </bk-form>
        <canway-dialog
            v-model="pwdDialog.show"
            title="修改密码"
            width="550"
            height-num="297"
            @cancel="pwdDialog.show = false">
            <bk-form class="mr20" :label-width="90" :model="pwdDialog" :rules="rules" ref="modifyPwdForm">
                <bk-form-item label="原密码" :required="true" property="oldPwd">
                    <bk-input
                        class="login-input"
                        v-model.trim="pwdDialog.oldPwd"
                        type="password"
                        maxlength="32"
                        :native-attributes="{
                            autocomplete: 'current-password'
                        }"
                        left-icon="bk-icon icon-lock">
                    </bk-input>
                </bk-form-item>
                <bk-form-item label="新密码" :required="true" property="newPwd">
                    <bk-input
                        class="login-input"
                        v-model.trim="pwdDialog.newPwd"
                        type="password"
                        maxlength="32"
                        :native-attributes="{
                            autocomplete: 'new-password'
                        }"
                        :placeholder="$t('pwdPlacehodler')"
                        left-icon="bk-icon icon-lock">
                    </bk-input>
                </bk-form-item>
                <bk-form-item label="确认密码" :required="true" property="check">
                    <bk-input
                        class="login-input"
                        v-model.trim="pwdDialog.check"
                        type="password"
                        maxlength="32"
                        :native-attributes="{
                            autocomplete: 'new-password'
                        }"
                        left-icon="bk-icon icon-lock">
                    </bk-input>
                </bk-form-item>
            </bk-form>
            <template #footer>
                <bk-button theme="default" @click.stop="pwdDialog.show = false">{{$t('cancel')}}</bk-button>
                <bk-button class="ml10" :loading="pwdDialog.loading" theme="primary" @click="confirmModifyPwd">{{$t('confirm')}}</bk-button>
            </template>
        </canway-dialog>
    </div>
</template>
<script>
    import { mapState, mapActions } from 'vuex'
    export default {
        name: 'userInfo',
        directives: {
            focus: {
                inserted (el) {
                    el.querySelector('input').focus()
                }
            }
        },
        data () {
            return {
                MODE_CONFIG,
                formItem: [
                    { label: this.$t('chineseName'), key: 'name' },
                    { label: '联系电话', key: 'phone' },
                    { label: this.$t('email'), key: 'email' }
                ],
                editItem: {
                    key: '',
                    value: ''
                },
                pwdDialog: {
                    show: false,
                    loading: false,
                    oldPwd: '',
                    newPwd: '',
                    check: ''
                },
                rules: {
                    oldPwd: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + this.$t('password'),
                            trigger: 'blur'
                        }
                    ],
                    newPwd: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + this.$t('password'),
                            trigger: 'blur'
                        },
                        {
                            min: 8,
                            message: this.$t('pwdPlacehodler'),
                            trigger: 'blur'
                        },
                        {
                            validator: str => {
                                return str.search(/ /) === -1 && [/[0-9]/, /[a-z]/, /[A-Z]/, /[\W_]/].map(reg => str.search(reg)).every(v => v !== -1)
                            },
                            message: this.$t('pwdPlacehodler'),
                            trigger: 'blur'
                        }
                    ],
                    check: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + this.$t('password'),
                            trigger: 'blur'
                        },
                        {
                            validator: () => this.pwdDialog.newPwd === this.pwdDialog.check,
                            message: '密码不一致',
                            trigger: 'blur'
                        }
                    ]
                }
            }
        },
        computed: {
            ...mapState(['userInfo'])
        },
        methods: {
            ...mapActions([
                'logout',
                'editUser',
                'getUserInfo',
                'modifyPwd'
            ]),
            editUserInfo (item) {
                this.editItem = {
                    key: item.key,
                    value: this.userInfo[item.key]
                }
            },
            confirmEdit () {
                this.editUser({
                    body: {
                        userId: this.userInfo.username,
                        [this.editItem.key]: this.editItem.value
                    }
                }).then(res => {
                    this.$bkMessage({
                        theme: 'success',
                        message: this.$t('edit') + this.$t('success')
                    })
                    this.cancelEdit()
                    this.getUserInfo({
                        userId: this.userInfo.username
                    })
                })
            },
            cancelEdit () {
                this.editItem = {
                    key: '',
                    value: ''
                }
            },
            showModifyPwd () {
                this.$refs.modifyPwdForm && this.$refs.modifyPwdForm.clearError()
                this.pwdDialog = {
                    show: true,
                    loading: false,
                    oldPwd: '',
                    newPwd: '',
                    check: ''
                }
            },
            async confirmModifyPwd () {
                await this.$refs.modifyPwdForm.validate()
                const formData = new FormData()
                formData.append('oldPwd', this.pwdDialog.oldPwd)
                formData.append('newPwd', this.pwdDialog.newPwd)
                this.pwdDialog.loading = true
                this.modifyPwd({
                    userId: this.userInfo.username,
                    formData
                }).then(() => {
                    this.$bkMessage({
                        theme: 'success',
                        message: '修改密码' + this.$t('success') + '，请重新登录'
                    })
                    this.pwdDialog.show = false
                    setTimeout(this.logout, 3000)
                }).finally(() => {
                    this.pwdDialog.loading = false
                })
            }
        }
    }
</script>
<style lang="scss" scoped>
.user-info-container {
    background-color: white;
}
</style>
