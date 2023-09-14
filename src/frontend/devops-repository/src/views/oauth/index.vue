<template>
    <div class="oauth-container" v-bkloading="{ isLoading }">
        <bk-form :label-width="0">
            <bk-form-item>
                <h2>授权</h2>
            </bk-form-item>
            <bk-form-item>
                <p>请授权应用{{ appId }}申请访问您({{ userId }})的帐户:</p>
            </bk-form-item>
            <bk-form-item>
                <bk-checkbox :true-value="true" :false-value="false" v-model="projectScope" :disabled="true">项目权限</bk-checkbox><br>
                <bk-checkbox :true-value="true" :false-value="false" v-model="repoScope" :disabled="true">仓库权限</bk-checkbox><br>
                <bk-checkbox :true-value="true" :false-value="false" v-model="nodeScope" :disabled="true">节点权限</bk-checkbox><br>
            </bk-form-item>
            <bk-form-item>
                <bk-button :theme="'primary'" @click="redirect">确认授权</bk-button>
            </bk-form-item>
        </bk-form>
        
    </div>
</template>
<script>
    import { mapActions } from 'vuex'
    import { formatDate } from '@repository/utils'
    import cookies from 'js-cookie'
    export default {
        name: 'oauth',
        data () {
            return {
                isLoading: false,
                userId: '',
                appId: '',
                projectScope: false,
                repoScope: false,
                nodeScope: false,
                redirectUrl: ''
            }
        },
        computed: {
            currentLanguage () {
                return cookies.get('blueking_language') || 'zh-cn'
            }
        },
        mounted () {
            this.authorize()
        },
        methods: {
            formatDate,
            ...mapActions([
                'getAuthorizeInfo'
            ]),
            authorize () {
                this.isLoading = true
                console.log(this.$route.query.client_id, this.$route.query.state, this.$route.query.scope, this.$route.query.nonce)
                return this.getAuthorizeInfo({
                    clientId: this.$route.query.client_id,
                    state: this.$route.query.state,
                    scope: this.$route.query.scope,
                    nonce: this.$route.query.nonce,
                    codeChallenge: this.$route.query.code_challenge,
                    codeChallengeMethod: this.$route.query.code_challenge_method
                }).then(authorizeInfo => {
                    console.log(authorizeInfo.scope.includes('PROJECT'))
                    this.userId = authorizeInfo.userId
                    this.appId = authorizeInfo.appId
                    this.redirectUrl = authorizeInfo.redirectUrl
                    this.projectScope = authorizeInfo.scope.includes('PROJECT')
                    this.repoScope = authorizeInfo.scope.includes('REPO')
                    this.nodeScope = authorizeInfo.scope.includes('NODE')
                }).finally(() => {
                    this.isLoading = false
                })
            },
            redirect () {
                window.location.href = this.redirectUrl
            }
        }
    }
</script>
<style lang="scss" scoped>
        .oauth-container {
            width: 100%;
            margin: 0 auto;
            padding: 20px;
            background-color: #fff;
            border: 1px solid #ccc;
            border-radius: 5px;
            box-shadow: 0 2px 5px rgba(0, 0, 0, 0.1);
        }

        .oauth-container form {
            text-align: center;
        }
</style>
