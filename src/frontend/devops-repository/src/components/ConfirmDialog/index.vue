<template>
    <canway-dialog
        v-model="show"
        width="380"
        height-num="221"
        title="操作确认"
        @cancel="cancel">
        <div class="confirm-body">
            <div class="confirm-main">
                <i :class="`devops-icon icon-${getIcon()}`"></i>
                <span class="ml10">{{ message }}</span>
            </div>
            <span class="confirm-tip">{{ subMessage }}</span>
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
.confirm-body {
    min-height: 60px;
    padding-left: 26px;
    .confirm-main {
        font-size: 14px;
        font-weight: bold;
    }
    .devops-icon {
        width: 26px;
        height: 26px;
        color:white;
        border-radius: 50%;
        &.icon-exclamation {
            padding: 6px;
            font-size: 14px;
            background-color: var(--warningColor);
        }
        &.icon-close {
            padding: 7px;
            font-size: 12px;
            background-color: var(--dangerColor);
        }
    }
    .confirm-tip {
        padding-left: 40px;
        font-size: 12px;
        color: var(--fontSubsidiaryColor);
    }
}
</style>
