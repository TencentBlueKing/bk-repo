<template>
    <canway-dialog
        v-model="pwdDialog.show"
        :title="$t('modifyPassword')"
        width="550"
        height-num="297"
        @cancel="pwdDialog.show = false">
        <bk-form class="mr20" :label-width="90" :model="pwdDialog" :rules="rules" ref="modifyPwdForm">
            <bk-form-item :label="$t('currentPassword')" :required="true" property="oldPwd">
                <bk-input
                    class="login-input"
                    v-model.trim="pwdDialog.oldPwd"
                    type="password"
                    maxlength="25"
                    :native-attributes="{
                        autocomplete: 'current-password'
                    }"
                    :placeholder="$t('pleaseInput')"
                    left-icon="bk-icon icon-lock">
                </bk-input>
            </bk-form-item>
            <bk-form-item :label="$t('newPassword')" :required="true" property="newPwd">
                <bk-input
                    class="login-input"
                    v-model.trim="pwdDialog.newPwd"
                    type="password"
                    maxlength="25"
                    :native-attributes="{
                        autocomplete: 'new-password'
                    }"
                    :placeholder="$t('pwdPlacehodler')"
                    left-icon="bk-icon icon-lock">
                </bk-input>
            </bk-form-item>
            <bk-form-item :label="$t('confirmPassword')" :required="true" property="check">
                <bk-input
                    class="login-input"
                    v-model.trim="pwdDialog.check"
                    type="password"
                    maxlength="32"
                    :native-attributes="{
                        autocomplete: 'new-password'
                    }"
                    :placeholder="$t('pleaseInput')"
                    left-icon="bk-icon icon-lock">
                </bk-input>
            </bk-form-item>
        </bk-form>
        <template #footer>
            <bk-button theme="default" @click.stop="pwdDialog.show = false">{{$t('cancel')}}</bk-button>
            <bk-button class="ml10" :loading="pwdDialog.loading" theme="primary" @click="confirmModifyPwd">{{$t('confirm')}}</bk-button>
        </template>
    </canway-dialog>
</template>

<script>
    import { mapActions } from 'vuex'
    export default {
        name: 'modifyPasswordDialog',
        data () {
            return {
                pwdDialog: {
                    show: false,
                    loading: false,
                    oldPwd: '',
                    newPwd: '',
                    check: ''
                },
                userId: '',
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
                            message: this.$t('passwordWrongMsg'),
                            trigger: 'blur'
                        }
                    ]
                }
            }
        },
        methods: {
            ...mapActions([
                'modifyPwd',
                'getRSAKey'
            ]),
            showDialogHandler () {
                this.$refs.modifyPwdForm.clearError()
                this.pwdDialog.show = true
            },
            async confirmModifyPwd () {
                await this.$refs.modifyPwdForm.validate()
                const formData = new FormData()
                const encrypt = new window.JSEncrypt()
                const rsaKey = await this.getRSAKey()
                encrypt.setPublicKey(rsaKey)
                formData.append('oldPwd', encrypt.encrypt(this.pwdDialog.oldPwd))
                formData.append('newPwd', encrypt.encrypt(this.pwdDialog.newPwd))
                this.pwdDialog.loading = true
                this.modifyPwd({
                    userId: this.userId,
                    formData
                }).then(() => {
                    this.$bkMessage({
                        theme: 'success',
                        message: this.$t('modifyPassword') + this.$t('space') + this.$t('success')
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

<style scoped>

</style>
