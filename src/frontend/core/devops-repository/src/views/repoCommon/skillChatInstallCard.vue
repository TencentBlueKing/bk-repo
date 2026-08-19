<template>
    <div class="skill-chat-install">
        <div class="prereq">
            <div class="prereq-title">使用前请确保以下条件均已满足</div>
            <ul class="prereq-list">
                <li>智能体已具备<strong>执行命令</strong>的能力</li>
                <li>
                    本机已配置仓库 Token（环境变量或已保存凭证），不要把 Token 发给智能体
                    <bk-button text theme="primary" @click.stop="createToken">
                        {{ $t('createToken') }}
                    </bk-button>
                    <bk-button text theme="primary" @click.stop="viewToken">
                        查看个人令牌
                    </bk-button>
                </li>
                <li>下方 Token 仅用于本页 Zip 下载命令，不会写入对话提示词</li>
            </ul>
            <bk-input
                class="mt10"
                v-model.trim="tokenInput"
                :placeholder="$t('accessTokenPlaceholder')"
                clearable>
            </bk-input>
            <create-token-dialog ref="createToken" @token="onTokenCreated"></create-token-dialog>
        </div>
        <div class="prompt-card">
            <div class="prompt-header flex-align-center">
                <span class="flex-1">
                    复制提示词，发送给智能体即可安装「{{ skillName }}」
                </span>
                <bk-button text theme="primary" @click="copyPrompt">{{ $t('copy') }}</bk-button>
            </div>
            <pre class="prompt-text">{{ prompt }}</pre>
        </div>
    </div>
</template>
<script>
    import { mapState, mapMutations } from 'vuex'
    import { copyToClipboard } from '@repository/utils'
    import createTokenDialog from '@repository/views/repoToken/createTokenDialog'
    export default {
        name: 'skillChatInstallCard',
        components: { createTokenDialog },
        props: {
            prompt: {
                type: String,
                default: ''
            },
            skillName: {
                type: String,
                default: '<SKILL_SLUG>'
            }
        },
        computed: {
            ...mapState(['dependAccessTokenValue', 'userInfo']),
            tokenInput: {
                get () {
                    return this.dependAccessTokenValue || ''
                },
                set (value) {
                    this.SET_DEPEND_ACCESS_TOKEN_VALUE(value)
                }
            }
        },
        methods: {
            ...mapMutations(['SET_DEPEND_ACCESS_TOKEN_VALUE']),
            createToken () {
                this.$refs.createToken.userName = this.userInfo.username
                this.$refs.createToken.showDialogHandler()
            },
            viewToken () {
                this.$router.push({
                    name: 'repoToken',
                    params: { projectId: this.$route.params.projectId }
                })
            },
            onTokenCreated (id) {
                this.SET_DEPEND_ACCESS_TOKEN_VALUE(id)
            },
            copyPrompt () {
                copyToClipboard(this.prompt).then(() => {
                    this.$bkMessage({
                        theme: 'success',
                        message: this.$t('copy') + this.$t('success')
                    })
                }).catch(() => {
                    this.$bkMessage({
                        theme: 'error',
                        message: this.$t('copy') + this.$t('fail')
                    })
                })
            }
        }
    }
</script>
<style lang="scss" scoped>
.skill-chat-install {
    .prereq {
        padding: 12px 16px;
        margin-bottom: 12px;
        color: var(--fontPrimaryColor);
        background-color: var(--warningBgColor);
        border: 1px solid #FFE0B0;
        border-radius: 2px;
        .prereq-title {
            margin-bottom: 8px;
            font-weight: 600;
        }
        .prereq-list {
            padding-left: 18px;
            li {
                list-style: disc;
                line-height: 1.8;
            }
        }
    }
    .prompt-card {
        padding: 12px 16px;
        border: 1px solid var(--borderWeightColor);
        border-radius: 2px;
        background-color: white;
        .prompt-header {
            margin-bottom: 8px;
            color: var(--fontPrimaryColor);
            font-weight: 600;
        }
        .prompt-text {
            margin: 0;
            white-space: pre-wrap;
            word-break: break-all;
            line-height: 1.8;
            color: var(--fontSubsidiaryColor);
        }
    }
}
</style>
