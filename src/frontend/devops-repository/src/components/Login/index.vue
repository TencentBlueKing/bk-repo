<template>
    <bk-dialog
        v-model="showLoginDialog"
        :close-icon="false"
        :quick-close="false"
        :draggable="false"
        :show-footer="false">
        <div class="flex-center login-logo">
            <svg
                :width="42"
                :height="42"
                style="fill: currentColor"
            >
                <use xlink:href="#color-logo-bkrepo" />
            </svg>
            <header class="ml20 login-title">{{ $t('bkrepo') }}</header>
        </div>
        <div v-show="loginFailed" class="flex-align-center login-error-tip">
            <i class="mr5 bk-icon icon-exclamation-circle-shape"></i>
            {{ $t('loginErrorTip') }}
        </div>
        <bk-form ref="loginForm" class="login-form" :label-width="0">
            <bk-form-item>
                <bk-input
                    class="login-input"
                    v-model.trim="loginForm.username"
                    size="large"
                    :placeholder="$t('username')"
                    left-icon="bk-icon icon-user">
                </bk-input>
            </bk-form-item>
            <bk-form-item>
                <bk-input
                    class="login-input"
                    v-model.trim="loginForm.password"
                    type="password"
                    size="large"
                    :native-attributes="{
                        autocomplete: 'on'
                    }"
                    :placeholder="$t('password')"
                    left-icon="bk-icon icon-lock">
                </bk-input>
            </bk-form-item>
            <bk-form-item>
                <bk-button
                    class="login-button"
                    size="large"
                    :loading="loginForm.loading"
                    theme="primary"
                    @click="submitLogin">
                    {{$t('login')}}
                </bk-button>
            </bk-form-item>
        </bk-form>
    </bk-dialog>
</template>
<script>
    import { mapState, mapMutations, mapActions } from 'vuex'
    export default {
        name: 'login',
        data () {
            return {
                loginFailed: false,
                loginForm: {
                    loading: false,
                    username: '',
                    password: ''
                }
            }
        },
        computed: {
            ...mapState(['showLoginDialog'])
        },
        watch: {
            showLoginDialog (val) {
                if (val) {
                    document.addEventListener('keydown', this.enterEvent)
                } else {
                    document.removeEventListener('keydown', this.enterEvent)
                }
            }
        },
        methods: {
            ...mapMutations(['SHOW_LOGIN_DIALOG']),
            ...mapActions(['bkrepoLogin']),
            submitLogin () {
                this.loginFailed = false
                if (!this.loginForm.username || !this.loginForm.password) {
                    this.loginFailed = true
                    return
                }
                const formData = new FormData()
                formData.append('uid', this.loginForm.username)
                formData.append('token', this.loginForm.password)
                this.bkrepoLogin(formData).then(res => {
                    if (res) {
                        this.$bkMessage({
                            theme: 'success',
                            message: this.$t('login') + this.$t('success')
                        })
                        this.SHOW_LOGIN_DIALOG(false)
                        location.href = ''
                    } else {
                        this.loginFailed = true
                    }
                })
            },
            enterEvent (e) {
                if (e.keyCode === 13) this.submitLogin()
            }
        }
    }
</script>
<style lang="scss" scoped>
@import '@/scss/conf';
.login-logo {
    padding-bottom: 20px;
    border-bottom: 1px solid $borderWeightColor;
    svg {
        margin-left: -20px;
    }
    .login-title {
        font-size: 24px;
        letter-spacing: 1.5px;
    }
}
.login-error-tip {
    position: absolute;
    margin-left: 25px;
    margin-top: 10px;
    font-size: 12px;
    color: $failColor;
}
.login-form {
    margin: 40px 0 30px;
    .login-button {
        width: 100%;
        height: 40px;
        font-size: 12px;
        margin-top: 20px;
    }
}
</style>
