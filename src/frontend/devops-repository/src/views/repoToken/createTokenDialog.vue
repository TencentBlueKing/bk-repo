<template>
    <canway-dialog
        v-model="show"
        width="540"
        height-num="245"
        :title="$t('createAccessToken')"
        @cancel="cancel">
        <div v-if="token" class="flex-align-center">
            <i class="flex-center devops-icon icon-check-1"></i>
            <div style="width: 400px">
                <span class="token-title">{{ $t('create') + $t('space') + $t('success') }}</span>
                <div @click="copyToken(token)" class="mt10 mb10 hover-btn flex-align-center">
                    {{ $t('tokenIs') + token }}
                    <i class="ml10 devops-icon icon-clipboard"></i>
                </div>
                <span class="token-tip">{{ $t('tokenCopyTip') }}</span>
            </div>
        </div>
        <bk-form v-else :label-width="100" :model="tokenFormData" :rules="rules" ref="tokenForm">
            <bk-form-item :label="$t('name')" :required="true" property="name" error-display-type="normal">
                <bk-input v-model.trim="tokenFormData.name" maxlength="32" show-word-limit></bk-input>
            </bk-form-item>
            <bk-form-item :label="$t('expireOn')" property="expiredAt">
                <bk-date-picker
                    style="width:100%"
                    v-model="tokenFormData.expiredAt"
                    :options="{
                        disabledDate: (date) => date < new Date()
                    }"
                    :placeholder="$t('tokenExpireTip')">
                </bk-date-picker>
            </bk-form-item>
        </bk-form>
        <template #footer>
            <bk-button v-if="token" theme="primary" @click="cancel">{{$t('confirm')}}</bk-button>
            <template v-else>
                <bk-button @click="cancel">{{$t('cancel')}}</bk-button>
                <bk-button class="ml10" :loading="loading" theme="primary" @click="confirm">{{$t('confirm')}}</bk-button>
            </template>
        </template>
    </canway-dialog>
</template>
<script>
    import { mapState, mapActions } from 'vuex'
    import { copyToClipboard } from '@repository/utils'
    export default {
        name: 'createToken',
        data () {
            return {
                show: false,
                loading: false,
                userName: '',
                tokenFormData: {
                    name: '',
                    expiredAt: ''
                },
                rules: {
                    name: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + this.$t('space') + 'Token' + this.$t('space') + this.$t('name'),
                            trigger: 'blur'
                        }
                    ]
                },
                token: ''
            }
        },
        computed: {
            ...mapState(['userInfo'])
        },
        methods: {
            ...mapActions(['addToken']),
            showDialogHandler () {
                this.show = true
                this.tokenFormData = {
                    name: '',
                    expiredAt: ''
                }
                this.token = ''
                this.$refs.tokenForm && this.$refs.tokenForm.clearError()
            },
            async confirm () {
                await this.$refs.tokenForm.validate()
                this.loading = true
                this.addToken({
                    projectId: this.$route.params.projectId,
                    username: this.userName,
                    name: this.tokenFormData.name,
                    expiredAt: this.tokenFormData.expiredAt instanceof Date ? this.tokenFormData.expiredAt.toISOString() : ''
                }).then(({ id }) => {
                    this.$emit('token', id)
                    this.token = id
                }).finally(() => {
                    this.loading = false
                })
            },
            cancel () {
                this.show = false
                if (this.token) {
                    this.$emit('refresh')
                }
            },
            copyToken (text) {
                copyToClipboard(text).then(() => {
                    this.$bkMessage({
                        theme: 'success',
                        message: this.$t('copy') + this.$t('space') + this.$t('success')
                    })
                }).catch(() => {
                    this.$bkMessage({
                        theme: 'error',
                        message: this.$t('copy') + this.$t('space') + this.$t('fail')
                    })
                })
            }
        }
    }
</script>
<style lang="scss" scoped>
.icon-check-1 {
    width: 58px !important;
    height: 58px;
    line-height: 58px;
    font-size: 30px;
    color: white;
    border-radius: 50%;
    background-color: var(--successColor);
    margin-left: 10px;
    margin-right: 10px;
}
.token-title {
    font-size: 17px;
    font-weight: bold;
}
.token-tip {
    color: var(--warningColor);
}
</style>
