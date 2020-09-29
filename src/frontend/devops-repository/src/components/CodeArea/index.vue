<template>
    <div class="code-area">
        <div v-for="(code, index) in codeList" :key="code + Math.random()" class="code-main">
            <span v-if="lineNumber" class="code-index">{{index + 1}}</span>
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
    background-color: #191929;
    color: white;
    padding: 10px 40px;
    min-height: 48px;
    word-break: break-all;
    .code-main {
        position: relative;
        .code-index {
            position: absolute;
            margin-left: -30px;
        }
        .code-pre {
            font-family: Helvetica Neue,Arial,PingFang SC,Hiragino Sans GB,Microsoft Yahei,WenQuanYi Micro Hei,sans-serif;
            white-space: pre-wrap;
            margin: 0;
        }
    }
    .code-copy {
        position: absolute;
        top: 10px;
        right: 10px;
        font-size: 24px;
        color: #191929;
        cursor: pointer;
    }
    &:hover .code-copy {
        color: white;
        &:hover {
            color: $iconPrimaryColor;
        }
    }
}
</style>
