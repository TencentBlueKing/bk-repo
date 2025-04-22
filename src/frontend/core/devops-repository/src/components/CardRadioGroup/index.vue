<template>
    <bk-radio-group
        v-bind="$attrs"
        class="card-radio-group"
        @change="change">
        <bk-radio-button
            class="mr20"
            v-for="({ value, label, tip }) in list"
            :key="value"
            :value="value"
            :disabled="disabled">
            <div class="card-radio flex-column" :class="{ 'checked': value === $attrs.value }">
                <span class="card-label">{{ label }}</span>
                <span class="card-tip">{{ tip }}</span>
                <span v-show="value === $attrs.value" class="top-right-selected">
                    <i class="devops-icon icon-check-1"></i>
                </span>
            </div>
        </bk-radio-button>
    </bk-radio-group>
</template>
<script>
    export default {
        name: 'cardRadioGroup',
        model: {
            prop: 'value',
            event: 'change'
        },
        props: {
            list: {
                type: Array,
                default: []
            },
            disabled: {
                type: Boolean,
                default: false
            }
        },
        methods: {
            change (val) {
                this.$emit('change', val)
            }
        }
    }
</script>
<style lang="scss" scoped>
.card-radio-group {
    ::v-deep .bk-form-radio-button {
        .bk-radio-button-text {
            height: auto;
            line-height: initial;
            padding: 0;
            border: 0 none;
        }
    }
    .card-radio {
        position: relative;
        padding: 10px;
        min-width: 165px;
        height: 70px;
        text-align: left;
        border: 1px solid transparent;
        background-color: var(--bgHoverColor);
        &.checked {
            border-color: var(--primaryColor);
            .card-label {
                color: var(--primaryColor);
            }
            .card-tip {
                color: var(--fontPrimaryColor);
            }
        }
        .card-label {
            font-weight: bold;
            color: var(--fontPrimaryColor);
        }
        .card-tip {
            margin-top: 10px;
            font-size: 12px;
            color: var(--fontSubsidiaryColor);
        }
        .top-right-selected {
            position: absolute;
            top: 0;
            right: 0;
            border-width: 16px;
            border-style: solid;
            border-color: var(--primaryColor) var(--primaryColor) transparent transparent;
            i {
                position: absolute;
                margin-top: -12px;
                color: white;
            }
        }
    }
}
</style>
