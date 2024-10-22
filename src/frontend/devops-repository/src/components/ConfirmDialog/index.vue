<template>
    <canway-dialog
        v-model="show"
        width="380"
        height-num="221"
        :title="$t('operationConfirmation')"
        @cancel="cancel">
        <div class="confirm-body">
            <div class="confirm-main">
                <i :class="`bk-icon icon-${getIcon()}`"></i>
                <span class="ml10 message-content">{{ message }}</span>
            </div>
            <span class="confirm-tip" :title="subMessage">{{ subMessage }}</span>
        </div>
        <template #footer>
            <bk-button @click="cancel">{{$t('cancel')}}</bk-button>
            <bk-button class="ml10" :loading="loading" :theme="theme" @click="confirm">{{$t('confirm')}}</bk-button>
        </template>
    </canway-dialog>
</template>
<script>
    import Vue from 'vue'
    export default {
        name: 'confirmDialog',
        data () {
            return {
                show: false,
                loading: false,
                theme: 'warning',
                message: '',
                subMessage: '',
                confirmFn: () => {}
            }
        },
        mounted () {
            Vue.prototype.$confirm = this.showConfiirmDialog
        },
        methods: {
            getIcon () {
                switch (this.theme) {
                    case 'success':
                        return 'check-circle-shape'
                    case 'warning':
                        return 'exclamation-circle-shape'
                    case 'danger':
                        return 'close-circle-shape'
                }
            },
            showConfiirmDialog ({ theme = 'warning', message = '', subMessage = '', confirmFn = () => {} }) {
                this.show = true
                this.loading = false
                this.theme = theme
                this.message = message
                this.subMessage = subMessage
                this.confirmFn = confirmFn
            },
            confirm () {
                this.loading = true
                const res = this.confirmFn()
                if (res instanceof Promise) {
                    res.then(this.cancel).finally(() => {
                        this.loading = false
                    })
                } else {
                    this.cancel()
                }
            },
            cancel () {
                this.loading = false
                this.show = false
            }
        }
    }
</script>
<style lang="scss" scoped>
.confirm-body {
    min-height: 60px;
    padding-left: 16px;
    .confirm-main {
        display: flex;
        font-size: 14px;
        font-weight: bold;
    }
    .bk-icon {
        font-size: 26px;
        margin-top: -2px;
        &.icon-check-circle-shape {
            color: var(--successColor);
        }
        &.icon-exclamation-circle-shape {
            color:var(--warningColor);
        }
        &.icon-close-circle-shape {
            color: var(--dangerColor);
        }
    }
    .message-content {
        white-space: normal;
        overflow: visible;
        text-overflow: clip;
        word-break: break-all;
    }
    .confirm-tip {
        font-size: 12px;
        color: var(--fontSubsidiaryColor);
        margin-left: 35px;
        display: inline-block;
        margin-top: 12px;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
        width: 90%;
    }
}
</style>
