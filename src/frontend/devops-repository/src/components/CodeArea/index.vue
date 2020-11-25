<template>
    <div class="code-area"
        :style="{
            'background-color': bgColor,
            'color': color
        }">
        <div v-for="code in codeList" :key="code + Math.random()"
            :class="{
                'code-main': true,
                'line-number': lineNumber && codeList.length > 1
            }">
            <pre class="code-pre">{{ code }}</pre>
        </div>
        <i class="code-copy devops-icon icon-clipboard" @click="copyCode()"></i>
    </div>
</template>
<script>
    import Clipboard from 'clipboard'
    export default {
        name: 'codeArea',
        props: {
            codeList: {
                type: Array,
                default: []
            },
            lineNumber: {
                type: Boolean,
                default: true
            },
            bgColor: {
                type: String,
                default: '#555e66'
            },
            color: {
                type: String,
                default: '#ffffff'
            }
        },
        methods: {
            copyCode () {
                // eslint-disable-next-line prefer-const
                const clipboard = new Clipboard('.code-area', {
                    text: () => {
                        return this.codeList.join('\n')
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
.code-area {
    position: relative;
    line-height: 2;
    padding: 10px 40px;
    min-height: 48px;
    word-break: break-all;
    counter-reset: row-num;
    .code-main {
        position: relative;
        &.line-number:before {
            position: absolute;
            margin-left: -30px;
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
        top: 10px;
        right: 10px;
        font-size: 24px;
        cursor: pointer;
    }
    &:hover .code-copy {
        visibility: visible;
        &:hover {
            color: $iconPrimaryColor;
        }
    }
}
</style>
