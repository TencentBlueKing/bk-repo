<template>
    <div class="login-container flex-align-center" v-if="showLoginDialog">
        <div class="login-bg"></div>
        <div class="login-main flex-column flex-align-center">
            <img width="150" height="50" src="/ui/logo_login.png" />
            <bk-form ref="loginForm" class="login-form" :label-width="0">
                <bk-form-item>
                    <bk-input
                        style="width: 320px"
                        v-model.trim="loginForm.username"
                        placeholder="请输入用户名">
                    </bk-input>
                </bk-form-item>
                <bk-form-item>
                    <bk-input
                        style="width: 320px"
                        v-model.trim="loginForm.password"
                        placeholder="请输入密码"
                        type="password"
                        :native-attributes="{
                            autocomplete: 'on'
                        }">
                    </bk-input>
                </bk-form-item>
                <bk-form-item>
                    <div v-show="loginFailed" class="flex-align-center login-error-tip">
                        <i class="mr5 bk-icon icon-exclamation-circle"></i>
                        {{ disableLogin ? `登录失败次数过多，请${wait}s后重试` : loginFailedTip }}
                    </div>
                    <bk-button
                        class="login-button"
                        :disabled="!loginForm.username || !loginForm.password || disableLogin"
                        theme="primary"
                        :loading="loginForm.loading"
                        @click="submitLogin">
                        {{$t('login')}}
                    </bk-button>
                </bk-form-item>
            </bk-form>
        </div>
    </div>
</template>
<script>
    import { mapState, mapMutations, mapActions } from 'vuex'
    export default {
        name: 'login',
        data () {
            return {
                disableLogin: false,
                loginFailedTip: this.$t('loginErrorTip'),
                loginFailCounter: 0,
                wait: 0,
                loginFailed: false,
                loginForm: {
                    loading: false,
                    username: '',
                    password: ''
                },
                countdownInterval: null
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
                    clearInterval(this.countdownInterval)
                }
            },
            loginFailCounter (val) {
                if (val > 4) this.countdown()
            }
        },
        mounted () {
            const time = localStorage.getItem('login_time')
            localStorage.removeItem('login_time')
            const wait = time - new Date().getTime()
            if (wait > 0) {
                this.countdown(Math.floor(wait / 1000))
            }
        },
        methods: {
            ...mapMutations(['SHOW_LOGIN_DIALOG']),
            ...mapActions(['bkrepoLogin', 'getRSAKey']),
            submitLogin () {
                this.loginFailed = false
                this.getRSAKey().then(rsaKey => {
                    const formData = new FormData()
                    formData.append('uid', this.loginForm.username)
                    const encrypt = new window.JSEncrypt()
                    encrypt.setPublicKey(rsaKey)
                    formData.append('token', encrypt.encrypt(this.loginForm.password))
                    this.bkrepoLogin(formData).then(res => {
                        if (res) {
                            this.$bkMessage({
                                theme: 'success',
                                message: this.$t('login') + this.$t('success')
                            })
                            const afterLoginUrl = sessionStorage.getItem('afterLogin')
                            sessionStorage.removeItem('afterLogin')
                            afterLoginUrl && window.open(afterLoginUrl, '_self')
                            location.href = ''
                            this.loginFailCounter = 0
                        } else {
                            this.loginFailedTip = this.$t('loginErrorTip')
                            this.loginFailed = true
                            this.loginFailCounter++
                        }
                    }).catch(e => {
                        this.loginFailedTip = e.message
                        this.loginFailed = true
                        this.loginFailCounter++
                    })
                })
            },
            enterEvent (e) {
                if (e.keyCode === 13 && this.formData.username && this.formData.password && !this.disableLogin) this.submitLogin()
            },
            countdown (counter = 60) {
                this.wait = counter
                this.loginFailed = true
                this.disableLogin = true
                this.countdownInterval = setInterval(() => {
                    this.wait--
                    if (this.wait < 1) {
                        clearInterval(this.countdownInterval)
                        this.disableLogin = false
                        this.loginFailed = false
                        this.loginFailCounter = 0
                    } else {
                        const now = new Date().getTime()
                        localStorage.setItem('login_time', (now + this.wait * 1000).toString())
                    }
                }, 1000)
            }
        }
    }
</script>
<style lang="scss" scoped>
.login-container{
    position: fixed;
    top: 0;
    right: 0;
    bottom: 0;
    left: 0;
    z-index: 10000;
    background-color: var(--bgWeightColor);
    .login-bg {
        flex: 1;
        height: 100%;
        background-image: url('/ui/bg_login.png');
        background-position: center left;
        background-size: cover;
    }
    .login-main {
        position: relative;
        height: 100%;
        overflow-y: auto;
        padding-top: 20vh;
        padding-bottom: 10vh;
        width: 440px;
        background-color: white;
        transition: width .5s;
        .login-form {
            margin-top: 90px;
            ::v-deep .bk-form-input {
                height: 46px;
                line-height: 46px;
                border-radius: 4px;
            }
            .login-button {
                width: 100%;
                height: 46px;
                margin-top: 10px;
                font-size: 14px;
                border-radius: 4px;
                &.is-disabled {
                    background: rgba(58, 132, 255, 0.4);
                }
            }
        }
        .login-error-tip {
            position: absolute;
            margin-top: -20px;
            color: var(--dangerColor);
        }
    }
}
@media screen and (min-width: 1600px) {
    .login-container .login-main {
        width: 600px;
    }
}
</style>
