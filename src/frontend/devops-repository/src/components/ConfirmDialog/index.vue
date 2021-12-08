<template>
    <canway-dialog
        v-model="show"
        width="480"
        height-num="275"
        title="操作确认"
        @cancel="cancel">
        <div class="p20 confirm-body flex-align-center">
            <i :class="`devops-icon icon-${getIcon()}`"></i>
            <span class="ml10">{{ message }}</span>
        </div>
        <span class="confirm-tip">{{ subMessage }}</span>
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
                        return 'check-1'
                    case 'warning':
                        return 'exclamation'
                    case 'danger':
                        return 'close'
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
                    res.then(this.cancel)
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
::v-deep .bk-dialog-body {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    min-height: 150px;
}
.confirm-body {
    .devops-icon {
        width: 38px;
        height: 38px;
        color:white;
        border-radius: 50%;
    }
    .icon-exclamation {
        padding: 9px;
        font-size: 20px;
        background-color: var(--warningColor);
    }
    .icon-close {
        padding: 11px;
        font-size: 16px;
        background-color: var(--dangerColor);
    }
}
.confirm-tip {
    font-size: 12px;
    color: var(--fontSubsidiaryColor);
}
</style>
