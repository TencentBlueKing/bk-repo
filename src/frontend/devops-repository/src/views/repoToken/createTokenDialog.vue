<template>
    <bk-dialog
        v-model="show"
        width="600"
        :title="token ? '' : $t('createToken')"
        :mask-close="false"
        :close-icon="false"
    >
        <template v-if="token">
            <div class="flex-align-center">
                <i class="flex-center devops-icon icon-check-1"></i>
                <div>
                    <h3>{{ $t('createToken') + $t('success') }}</h3>
                    <div @click="copyToken" class="mt10 mb10 hover-btn flex-align-center">
                        {{ $t('tokenIs') + token }}
                        <i class="ml10 devops-icon icon-clipboard"></i>
                    </div>
                    <span class="token-tip">{{ $t('tokenCopyTip') }}</span>
                </div>
            </div>
            <div slot="footer">
                <bk-button theme="primary" @click="cancel">{{$t('confirm')}}</bk-button>
            </div>
        </template>
        <template v-else>
            <bk-form :label-width="100" :model="tokenFormData" :rules="rules" ref="tokenForm">
                <bk-form-item :label="$t('name')" :required="true" property="name">
                    <bk-input v-model="tokenFormData.name"></bk-input>
                </bk-form-item>
                <bk-form-item :label="$t('expiress')" property="expiredAt">
                    <bk-date-picker
                        v-model="tokenFormData.expiredAt"
                        :options="{
                            disabledDate: (date) => date < new Date()
                        }"
                        :placeholder="$t('tokenExpiressTip')">
                    </bk-date-picker>
                </bk-form-item>
            </bk-form>
            <div slot="footer">
                <bk-button :loading="loading" theme="primary" @click="confirm">{{$t('confirm')}}</bk-button>
                <bk-button @click="cancel">{{$t('cancel')}}</bk-button>
            </div>
        </template>
    </bk-dialog>
</template>
<script>
    import Clipboard from 'clipboard'
    import { mapState, mapActions } from 'vuex'
    export default {
        name: 'createToken',
        data () {
            return {
                show: false,
                loading: false,
                tokenFormData: {
                    name: '',
                    expiredAt: ''
                },
                rules: {
                    name: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + 'Token' + this.$t('name'),
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
            },
            async confirm () {
                await this.$refs.tokenForm.validate()
                this.loading = true
                this.addToken({
                    username: this.userInfo.username,
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
                this.tokenFormData = {
                    name: '',
                    expiredAt: ''
                }
                this.token = ''
                this.$refs.tokenForm && this.$refs.tokenForm.clearError()
                this.$emit('refresh')
            },
            copyToken () {
                // eslint-disable-next-line prefer-const
                const clipboard = new Clipboard('body', {
                    text: () => {
                        return this.token
                    }
                })
                clipboard.on('success', (e) => {
                    this.$bkMessage({
                        theme: 'success',
                        message: this.$t('copy') + this.$t('success')
                    })
                    clipboard.destroy()
                })
                clipboard.on('error', (e) => {
                    this.$bkMessage({
                        theme: 'error',
                        message: this.$t('copy') + this.$t('fail')
                    })
                    clipboard.destroy()
                })
            }
        }
    }
</script>
<style lang="scss" scoped>
@import '@/scss/conf';
.icon-check-1 {
    width: 58px;
    height: 58px;
    margin: 0 auto;
    line-height: 58px;
    font-size: 30px;
    color: #fff;
    border-radius: 50%;
    background-color: #2dcb56;
}
.token-tip {
    color: #E16F1D;
}
</style>
