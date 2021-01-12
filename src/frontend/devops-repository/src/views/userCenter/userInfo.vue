<template>
    <div class="user-info-container">
        <div class="user-info-left">
            <div class="user-info-avatar">
                <!-- <img v-if="userInfo.logoAddr" class="avatar-addr" :src="userInfo.logoAddr" /> -->
                <div :class="[
                    'avatar-letter',
                    `avatar-letter-${['green', 'yellow', 'red', 'blue'][userInfo.phone % 4] || 'yellow'}`
                ]">
                    {{ userInfo.username[0] }}
                </div>
                <!-- <span class="avatar-edit">{{ $t('editLabel') }}</span> -->
            </div>
            <div class="user-info-name">
                {{userInfo.name}}
            </div>
        </div>
        <div class="user-info-right">
            <header class="user-info-header">{{userInfo.username}}</header>
            <bk-form class="mt20" style="width:400px;" :label-width="120">
                <bk-form-item v-for="item in formItem" :key="item.key" :label="item.label">
                    <div v-if="!editItem.key || editItem.key !== item.key" class="flex-align-center">
                        <span>{{userInfo[item.key] || '--'}}</span>
                        <i v-if="!editItem.key" class="ml20 devops-icon icon-edit hover-btn" @click="editUserInfo(item)"></i>
                    </div>
                    <div v-else class="flex-align-center">
                        <bk-input v-model="editItem.value"></bk-input>
                        <i class="ml20 devops-icon icon-check-1" @click="confirmEdit"></i>
                        <i class="ml20 devops-icon icon-close" @click="cancelEdit"></i>
                    </div>
                </bk-form-item>
                <bk-form-item>
                    <bk-button theme="default" @click.stop.prevent="showModifyPwd">{{$t('modifyPwd')}}</bk-button>
                </bk-form-item>
            </bk-form>
        </div>
        <bk-dialog
            v-model="pwdDialog.show"
            title="修改密码"
            width="650"
            :close-icon="false"
            :quick-close="false"
            :draggable="false">
            <bk-form class="mr20" :label-width="90" :model="pwdDialog" :rules="rules" ref="modifyPwdForm">
                <bk-form-item label="原密码" :required="true" property="oldPwd" error-display-type="normal">
                    <bk-input
                        class="login-input"
                        v-model="pwdDialog.oldPwd"
                        type="password"
                        maxlength="32"
                        :native-attributes="{
                            autocomplete: 'current-password'
                        }"
                        left-icon="bk-icon icon-lock">
                    </bk-input>
                </bk-form-item>
                <bk-form-item label="新密码" :required="true" property="newPwd" error-display-type="normal">
                    <bk-input
                        class="login-input"
                        v-model="pwdDialog.newPwd"
                        type="password"
                        maxlength="32"
                        :native-attributes="{
                            autocomplete: 'new-password'
                        }"
                        :placeholder="$t('pwdPlacehodler')"
                        left-icon="bk-icon icon-lock">
                    </bk-input>
                </bk-form-item>
                <bk-form-item label="确认密码" :required="true" property="check" error-display-type="normal">
                    <bk-input
                        class="login-input"
                        v-model="pwdDialog.check"
                        type="password"
                        maxlength="32"
                        :native-attributes="{
                            autocomplete: 'new-password'
                        }"
                        left-icon="bk-icon icon-lock">
                    </bk-input>
                </bk-form-item>
            </bk-form>
            <div slot="footer">
                <bk-button :loading="pwdDialog.loading" theme="primary" @click.stop.prevent="confirmModifyPwd">{{$t('submit')}}</bk-button>
                <bk-button class="ml10" theme="default" @click.stop="pwdDialog.show = false">{{$t('cancel')}}</bk-button>
            </div>
        </bk-dialog>
    </div>
</template>
<script>
    import { mapState, mapActions } from 'vuex'
    export default {
        name: 'userInfo',
        data () {
            return {
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
        created () {
            this.getUserInfo({
                userId: this.userInfo.username
            })
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
                    this.getUserInfo({
                        userId: this.userInfo.username
                    }).then(this.cancelEdit)
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
@import '@/scss/conf';
.user-info-container {
    display: flex;
    padding: 40px;
    .user-info-left {
        padding: 20px;
        display: flex;
        flex-direction: column;
        align-items: center;
        color: #fff;
        .user-info-avatar {
            width: 110px;
            height: 110px;
            border-radius: 50%;
            font-size: 0;
            overflow: hidden;
            box-shadow: 0 0 5px 5px rgba(0, 0, 0, 0.1);
            .avatar-addr {
                width: 100%;
                height: 100%;
            }
            .avatar-letter {
                display: flex;
                justify-content: center;
                align-items: center;
                height: 100%;
                font-size: 54px;
                &-green {
                    background-color: #30D878;
                }
                &-yellow {
                    background-color: #FFB400;
                }
                &-red {
                    background-color: #FF5656;
                }
                &-blue {
                    background-color: #3C96FF;
                }
            }
            .avatar-edit {
                display: none;
            }
            &:hover {
                .avatar-edit {
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    position: relative;
                    margin-top: -100%;
                    width: 100%;
                    height: 100%;
                    background: rgba(0, 0, 0, 0.4);
                    border-radius: 16px;
                    font-size: 24px;
                    cursor: pointer;
                }
            }
        }
        .user-info-name {
            position: relative;
            width: 100%;
            height: 40px;
            display: flex;
            justify-content: center;
            align-items: center;
            margin-top: 20px;
            background-color: #9faeca;
            &:before, &:after {
                content: '';
                position: absolute;
                z-index: 1;
                border-style: solid;
            }
            &:before {
                left: 0;
                border-width: 20px 0 20px 15px;
                border-color: transparent transparent transparent white;
            }
            &:after {
                right: 0;
                border-width: 20px 15px 20px 0;
                border-color: transparent white transparent transparent;
            }
        }
    }
    .user-info-right {
        padding: 20px;
        display: flex;
        flex-direction: column;
        .user-info-header {
            margin-left: 100px;
            font-size: 36px;
        }
        .icon-edit, .icon-check-1, .icon-close {
            font-size: 12px;
        }
        .icon-check-1, .icon-close {
            color: $primaryColor;
            cursor: pointer;
        }
        .icon-check-1 {
            font-size: 14px;
        }
        /deep/ .bk-form {
            font-size: 16px;
            .bk-label-text {
                font-size: 16px;
            }
        }
    }
}
</style>
