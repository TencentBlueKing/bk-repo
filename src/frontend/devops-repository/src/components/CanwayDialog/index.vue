<template>
    <bk-dialog
        v-bind="$attrs"
        :position="{ top }"
        :close-icon="false">
        <template #tools>
            <div class="canway-dialog-header flex-align-center">
                <slot name="header">
                    <span class="mr20 canway-dialog-title text-overflow" :title="title">{{ title }}</span>
                </slot>
                <i class="bk-icon icon-close hover-btn" @click="$emit('cancel')"></i>
            </div>
        </template>
        <slot></slot>
        <template #footer>
            <slot name="footer">
                <bk-button @click="$emit('cancel')">{{$t('cancel')}}</bk-button>
                <bk-button class="ml10" theme="primary" @click="$emit('confirm')">{{$t('confirm')}}</bk-button>
            </slot>
        </template>
    </bk-dialog>
</template>
<script>
    import { debounce } from '@repository/utils'
    export default {
        name: 'canwayDialog',
        props: {
            title: String,
            heightNum: {
                type: [String, Number],
                default: 600
            }
        },
        data () {
            return {
                resizeFn: null,
                bodyHeight: document.body.getBoundingClientRect().height
            }
        },
        computed: {
            top () {
                // 25 = ci顶部导航高度 / 2
                const offset = MODE_CONFIG === 'ci' ? 25 : 0
                const dialogHeight = this.heightNum > 280 ? this.heightNum : 280
                const top = (this.bodyHeight - dialogHeight) / 2 - offset
                return top > 0 ? top : 0
            }
        },
        mounted () {
            this.resizeFn = debounce(this.getBodyHeight, 1000)
            window.addEventListener('resize', this.resizeFn)
        },
        beforeDestroy () {
            window.removeEventListener('resize', this.resizeFn)
        },
        methods: {
            getBodyHeight () {
                this.bodyHeight = document.body.getBoundingClientRect().height
            }
        }
    }
</script>
<style lang="scss">
.canway-dialog-header {
    height: 56px;
    margin-bottom: 27px;
    padding: 0 20px;
    justify-content: space-between;
    border-bottom: 1px solid var(--borderColor);
    cursor: default;
    .canway-dialog-title {
        font-size: 16px;
        font-weight: 500;
    }
    .icon-close {
        color: var(--fontSubsidiaryColor);
        font-size: 22px;
        font-weight: 400;
        &:hover {
            color: var(--primaryColor);
            background-color: var(--bgHoverLighterColor);
        }
    }
}
</style>
