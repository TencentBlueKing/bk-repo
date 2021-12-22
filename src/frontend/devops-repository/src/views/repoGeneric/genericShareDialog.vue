<template>
    <canway-dialog
        v-model="genericShare.show"
        :title="genericShare.title"
        width="520"
        :height-num="shareUrl ? 352 : 282"
        @cancel="cancel">
        <div v-if="shareUrl" class="share-result">
            <bk-form class="flex-1" form-type="vertical">
                <bk-form-item label="共享链接地址">
                    <bk-input :value="shareUrl" readonly></bk-input>
                    <bk-button class="mt5" theme="primary" @click="copyShareUrl(shareUrl)">复制链接</bk-button>
                </bk-form-item>
                <bk-form-item label="邮件方式分享">
                    <bk-tag-input
                        v-model="genericShare.user"
                        :list="Object.values(userList).filter(user => user.id !== 'anonymous')"
                        :search-key="['id', 'name']"
                        :title="genericShare.user.map(u => userList[u] ? userList[u].name : u)"
                        placeholder="请输入用户"
                        trigger="focus"
                        allow-create
                        has-delete-icon>
                    </bk-tag-input>
                    <bk-button class="mt5" :disabled="!Boolean(genericShare.user.length)" theme="primary" :loading="sending" @click="sendEmailHandler">发送邮件</bk-button>
                </bk-form-item>
            </bk-form>
            <div class="ml20 flex-column">
                <span class="qrcode-label">移动端二维码下载</span>
                <QRCode class="share-qrcode" :text="shareUrl" :size="150" />
            </div>
        </div>
        <bk-form v-else style="margin-top:-15px" ref="genericShareForm" :label-width="90" form-type="vertical">
            <!-- <bk-form-item label="授权用户" property="user">
                <bk-tag-input
                    v-model="genericShare.user"
                    :list="Object.values(userList).filter(user => user.id !== 'anonymous')"
                    :search-key="['id', 'name']"
                    placeholder="授权访问用户，为空则任意用户可访问，按Enter键确认"
                    trigger="focus"
                    allow-create
                    has-delete-icon>
                </bk-tag-input>
            </bk-form-item> -->
            <!-- <bk-form-item label="授权IP" property="ip">
                <bk-tag-input
                    v-model="genericShare.ip"
                    placeholder="授权访问IP，为空则任意IP可访问，按Enter键确认"
                    trigger="focus"
                    allow-create>
                </bk-tag-input>
            </bk-form-item> -->
            <bk-form-item label="访问次数">
                <bk-input v-model="genericShare.permits" placeholder="请输入访问次数，为空则不限制"></bk-input>
            </bk-form-item>
            <bk-form-item :label="`${$t('validity')}(${$t('day')})`">
                <bk-select
                    v-model="genericShare.time"
                    :clearable="false"
                    placeholder="请选择过期时间">
                    <bk-option :id="1" name="1"></bk-option>
                    <bk-option :id="7" name="7"></bk-option>
                    <bk-option :id="30" name="30"></bk-option>
                    <bk-option :id="0" name="永久"></bk-option>
                </bk-select>
            </bk-form-item>
        </bk-form>
        <template #footer>
            <bk-button v-if="!shareUrl" theme="default" @click="cancel">{{ $t('cancel') }}</bk-button>
            <bk-button class="ml10" :loading="genericShare.loading" theme="primary" @click="shareUrl ? cancel() : submit()">{{$t('confirm')}}</bk-button>
        </template>
    </canway-dialog>
</template>
<script>
    import Clipboard from 'clipboard'
    import { mapActions, mapState } from 'vuex'
    import QRCode from '@repository/components/QRCode'
    export default {
        name: 'genericShare',
        components: { QRCode },
        data () {
            return {
                shareUrl: '',
                sending: false,
                genericShare: {
                    show: false,
                    loading: false,
                    title: '',
                    path: '',
                    user: [],
                    ip: [],
                    permits: '',
                    time: 0
                }
            }
        },
        computed: {
            ...mapState(['userList']),
            projectId () {
                return this.$route.params.projectId
            },
            repoName () {
                return this.$route.query.repoName
            }
        },
        methods: {
            ...mapActions(['shareArtifactory', 'sendEmail']),
            setData (data) {
                this.genericShare = {
                    ...this.genericShare,
                    ...data
                }
                this.shareUrl = ''
                this.$refs.genericShareForm && this.$refs.genericShareForm.clearError()
            },
            submit () {
                this.$refs.genericShareForm.validate().then(() => {
                    this.submitShare()
                })
            },
            cancel () {
                this.$refs.genericShareForm && this.$refs.genericShareForm.clearError()
                this.genericShare.show = false
            },
            submitShare () {
                const { path, time, permits } = this.genericShare
                this.genericShare.loading = true
                this.shareArtifactory({
                    projectId: this.projectId,
                    repoName: this.repoName,
                    fullPathSet: [path],
                    type: 'DOWNLOAD',
                    host: location.origin,
                    needsNotify: true,
                    // ...(data.ip.length ? { authorizedIpSet: data.ip } : {}),
                    // ...(user.length ? { authorizedUserSet: user } : {}),
                    ...(Number(time) > 0 ? { expireSeconds: Number(time) * 86400 } : {}),
                    ...(Number(permits) > 0 ? { permits: Number(permits) } : {})
                }).then(([{ url }]) => {
                    this.shareUrl = url
                }).finally(() => {
                    this.genericShare.loading = false
                })
            },
            copyShareUrl (text) {
                const clipboard = new Clipboard('body', {
                    text: () => text
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
            },
            sendEmailHandler () {
                const users = this.genericShare.user
                if (this.sending || !users.length) return
                this.sending = true
                this.sendEmail({
                    url: this.shareUrl,
                    users
                }).then(() => {
                    this.$bkMessage({
                        theme: 'success',
                        message: '发送邮件成功'
                    })
                }).finally(() => {
                    this.sending = false
                })
            }
        }
    }
</script>
<style lang="scss" scoped>
.share-result {
    display: flex;
    height: 206px;
    margin-top: -15px;
    .qrcode-label {
        color: var(--fontPrimaryColor);
        font-size: 12px;
        line-height: 32px;
    }
    .share-qrcode {
        border: 1px dashed;
    }
}
</style>
