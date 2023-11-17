<template>
    <bk-dialog
        :position="{ top }"
        header-position="left"
        :title="title"
        width="600"
        :close-icon="false"
        v-model="isShow">
        <slot></slot>
        <div v-if="backUp" class="mainBackUpBody">
            <div>
                <Icon v-if="!complete && !backUp" name="loading" size="20" class="svg-loading" />
                <Icon v-else-if="!complete && backUp" name="circle-2-1" size="20" style="color: #3a84ff" class="svg-loading" />
                <Icon v-else name="check" size="20" class="svg-complete" />
                <span class="mainMessage">{{ message }}</span>
            </div>
            <span class="subMessage">{{ subMessage }}</span>
        </div>
        <div v-else class="mainBody">
            <div>
                <Icon v-if="!complete && !backUp" name="loading" size="20" class="svg-loading" />
                <Icon v-else-if="!complete && backUp" name="circle-2-1" size="20" style="color: #3a84ff" class="svg-loading" />
                <Icon v-else name="check" size="20" class="svg-complete" />
                <span class="mainMessage">{{ message }}</span>
            </div>
            <span class="subMessage">{{ subMessage }}</span>
        </div>
        <template #footer>
            <slot name="footer">
                <bk-button @click="close" v-if="!complete">{{ cancelMessage }}</bk-button>
                <bk-button theme="primary" @click="isShow = false" v-if="complete">{{ confirmMessage }}</bk-button>
            </slot>
        </template>
    </bk-dialog>
</template>
<script>
    import { debounce } from '@repository/utils'
    export default {
        name: 'loadingDialog',
        props: {
            heightNum: {
                type: [String, Number],
                default: 600
            }
        },
        data () {
            return {
                MODE_CONFIG,
                title: '',
                isShow: false,
                message: this.$t('loadingMsg'),
                complete: false,
                backUp: false,
                subMessage: '',
                cancelMessage: this.$t('cancel'),
                confirmMessage: this.$t('confirm')
            }
        },
        computed: {
            top () {
                return 300
            }
        },
        mounted () {
            this.resizeFn = debounce(this.getBodyHeight, 1000)
            window.addEventListener('resize', this.resizeFn)
        },
        beforeDestroy () {
            this.message = this.$t('loadingMsg')
            window.removeEventListener('resize', this.resizeFn)
        },
        methods: {
            getBodyHeight () {
                this.bodyHeight = document.body.getBoundingClientRect().height
            },
            close () {
                this.isShow = false
                if (this.backUp) {
                    this.$emit('closeLoading')
                }
            }
        }
    }
</script>
<style lang="scss">
body .bk-dialog-wrapper .bk-dialog-body {
    min-height: auto;
}
.mainBody {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 200px;
    .svg-loading {
        margin-right: 10px;
        animation: rotate-loading 1s linear infinite;
    }
    .svg-complete {
        margin-right: 10px;
    }
    .mainMessage{
        font-size: 16px;
    }
    .subMessage{
        margin-top: 10px;
    }
    @keyframes rotate-loading {
        0% {
            transform: rotateZ(0);
        }

        100% {
            transform: rotateZ(360deg);
        }
    }
}

.mainBackUpBody {
    display: flex;
    align-items: flex-start;
    flex-direction: column;
    justify-content: center;
    height: 80px;
    .svg-loading {
        margin-right: 10px;
        animation: rotate-loading 1s linear infinite;
    }
    .svg-complete {
        margin-right: 10px;
    }
    .mainMessage{
        font-size: 16px;
        font-weight: bold;
        word-break: break-all;
    }
    .subMessage{
        margin-top: 20px;
        font-size: 14px;
        color: #979797;
    }
    @keyframes rotate-loading {
        0% {
            transform: rotateZ(0);
        }

        100% {
            transform: rotateZ(360deg);
        }
    }
}
</style>
