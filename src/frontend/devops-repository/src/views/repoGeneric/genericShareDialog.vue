<template>
    <canway-dialog
        v-model="genericShare.show"
        :title="genericShare.title"
        width="520"
        :height-num="shareUrl ? 352 : 426"
        @cancel="cancel">
        <!-- <div v-if="shareUrl" class="share-result">
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
        </div> -->
        <bk-form style="margin-top:-15px" ref="genericShareForm" :label-width="360" form-type="vertical">
            <bk-form-item :label="$t('authorizedUser')">
                <bk-tag-input
                    v-model="genericShare.user"
                    :list="Object.values(userList).filter(user => user.id !== 'anonymous')"
                    :search-key="['id', 'name']"
                    :placeholder="$t('sharePlaceHolder')"
                    trigger="focus"
                    allow-create
                    :paste-fn="parseFn"
                    has-delete-icon>
                </bk-tag-input>
            </bk-form-item>
            <bk-form-item :label="$t('authorizedIp')">
                <bk-tag-input
                    v-model="genericShare.ip"
                    :placeholder="$t('sharePlaceHolder1')"
                    trigger="focus"
                    :create-tag-validator="tag => {
                        return /((2(5[0-5]|[0-4]\d))|[0-1]?\d{1,2})(\.((2(5[0-5]|[0-4]\d))|[0-1]?\d{1,2})){3}/g.test(tag)
                    }"
                    allow-create>
                </bk-tag-input>
            </bk-form-item>
            <bk-form-item :label="$t('visits')">
                <bk-input v-model="genericShare.permits" :placeholder="$t('sharePlaceHolder2')"></bk-input>
            </bk-form-item>
            <bk-form-item :label="$t('validity')">
                <bk-select
                    v-model="genericShare.time"
                    :clearable="false"
                    :placeholder="$t('sharePlaceHolder3')">
                    <bk-option :id="1" name="1"></bk-option>
                    <bk-option :id="7" name="7"></bk-option>
                    <bk-option :id="30" name="30"></bk-option>
                    <bk-option :id="0" :name="$t('permanent')"></bk-option>
                </bk-select>
            </bk-form-item>
        </bk-form>
        <template #footer>
            <bk-button v-if="!shareUrl" theme="default" @click="cancel">{{ $t('cancel') }}</bk-button>
            <bk-button class="ml10" :loading="genericShare.loading" theme="primary" @click="shareUrl ? cancel() : submit()">{{$t('confirm')}}</bk-button>
        </template>
        <iam-deny-dialog :visible.sync="showIamDenyDialog" :show-data="showData"></iam-deny-dialog>
    </canway-dialog>
</template>
<script>
// import QRCode from '@repository/components/QRCode'
    import iamDenyDialog from '@repository/components/IamDenyDialog/IamDenyDialog'
    import { mapActions, mapState } from 'vuex'
    import { copyToClipboard } from '@repository/utils'
    export default {
        name: 'genericShare',
        // components: { QRCode },
        components: { iamDenyDialog },
        data () {
            return {
                shareUrl: '',
                sending: false,
                genericShare: {
                    projectId: '',
                    repoName: '',
                    show: false,
                    loading: false,
                    title: '',
                    path: '',
                    user: [],
                    ip: [],
                    permits: '',
                    time: 0
                },
                showIamDenyDialog: false,
                showData: {}
            }
        },
        computed: {
            ...mapState(['userList', 'userInfo'])
        },
        methods: {
            ...mapActions(['shareArtifactory', 'sendEmail', 'getPermissionUrl']),
            parseFn (data) {
                if (data !== '') {
                    const users = data.toString().split(',')
                    for (let i = 0; i < users.length; i++) {
                        users[i] = users[i].toString().trim()
                    }
                    const newUser = this.genericShare.user.concat(users)
                    this.genericShare.user = Array.from(new Set(newUser))
                }
            },
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
                const { projectId, repoName, path, ip, user, time, permits } = this.genericShare
                this.genericShare.loading = true
                this.shareArtifactory({
                    projectId,
                    repoName,
                    fullPathSet: [path],
                    type: 'DOWNLOAD',
                    host: `${location.origin}/web/generic`,
                    needsNotify: Boolean(user.length),
                    ...(ip.length ? { authorizedIpSet: ip } : {}),
                    ...(user.length ? { authorizedUserSet: user } : {}),
                    ...(Number(time) > 0 ? { expireSeconds: Number(time) * 86400 } : {}),
                    ...(Number(permits) > 0 ? { permits: Number(permits) } : {})
                }).then(([{ url }]) => {
                    // this.shareUrl = url
                    this.$bkMessage({
                        theme: 'success',
                        message: this.$t('share') + this.$t('space') + this.$t('success')
                    })
                    this.cancel()
                }).catch(e => {
                    if (e.status === 403) {
                        this.getPermissionUrl({
                            body: {
                                projectId: projectId,
                                action: 'READ',
                                resourceType: 'NODE',
                                uid: this.userInfo.name,
                                path: path,
                                repoName: repoName
                            }
                        }).then(res => {
                            if (res !== '') {
                                this.showIamDenyDialog = true
                                this.showData = {
                                    projectId: projectId,
                                    repoName: repoName,
                                    action: 'READ',
                                    path: path,
                                    url: res
                                }
                            } else {
                                this.$bkMessage({
                                    theme: 'error',
                                    message: e.message
                                })
                            }
                        })
                    } else {
                        this.$bkMessage({
                            theme: 'error',
                            message: e.message
                        })
                    }
                }).finally(() => {
                    this.genericShare.loading = false
                })
            },
            copyShareUrl (text) {
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
