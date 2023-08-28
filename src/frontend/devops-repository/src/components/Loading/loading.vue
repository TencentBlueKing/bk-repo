<template>
    <bk-dialog
        :position="{ top }"
        header-position="left"
        :title="title"
        width="600"
        v-model="isShow">
        <slot></slot>
        <div class="message">
            <Icon v-if="!complete" name="loading" size="20" class="svg-loading" />
            <Icon v-if="complete" name="check" size="20" class="svg-complete" />
            <span>{{ message }}</span>
        </div>
        <template #footer>
            <slot name="footer">
                <bk-button @click="isShow = false" v-if="!complete">{{$t('cancel')}}</bk-button>
                <bk-button theme="primary" @click="isShow = false" v-if="complete">{{$t('confirm')}}</bk-button>
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
                complete: false
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
            }
        }
    }
</script>
<style lang="scss" scoped>
.bk-dialog-content bk-dialog-content-drag{
    width: 500px !important;
}
.message {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 200px;
    font-size: 16px;
    .svg-loading {
        margin-right: 10px;
        animation: rotate-loading 1s linear infinite;
    }
    .svg-complete {
        margin-right: 10px;
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
