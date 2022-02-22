<template>
    <div class="code-area"
        :style="{
            '--bgColor': bgColor,
            '--color': color
        }">
        <div v-for="code in codeList" :key="code + Math.random()"
            :class="{
                'code-main': true,
                'line-number': showLineNumber && codeList.length > 1
            }">
            <pre class="code-pre">{{ code }}</pre>
        </div>
        <bk-button class="code-copy" theme="primary" @click="copyCode">{{$t('copy')}}</bk-button>
    </div>
</template>
<script>
    import { copyToClipboard } from '@repository/utils'
    export default {
        name: 'codeArea',
        props: {
            codeList: {
                type: Array,
                default: () => []
            },
            showLineNumber: {
                type: Boolean,
                default: true
            },
            bgColor: {
                type: String,
                default: '#E6EDF6'
            },
            color: {
                type: String,
                default: '#081E40'
            }
        },
        methods: {
            copyCode () {
                copyToClipboard(this.codeList.join('\n')).then(() => {
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
.code-area {
    position: relative;
    line-height: 2;
    padding: 13px 45px 13px 30px;
    min-height: 50px;
    word-break: break-all;
    counter-reset: row-num;
    color: var(--color);
    background-color: var(--bgHoverLighterColor);
    border-radius: 2px;
    .code-main {
        position: relative;
        &.line-number:before {
            position: absolute;
            margin-left: -20px;
            counter-increment: row-num;
            content: counter(row-num);
        }
        .code-pre {
            font-family: Helvetica Neue,Arial,PingFang SC,Hiragino Sans GB,Microsoft Yahei,WenQuanYi Micro Hei,sans-serif;
            white-space: pre-wrap;
            margin: 0;
        }
    }
    .code-copy {
        position: absolute;
        visibility: hidden;
        top: 0;
        right: 0;
    }
    &:hover {
        .code-copy {
            visibility: visible;
        }
    }
}
</style>
