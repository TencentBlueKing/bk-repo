<template>
    <div class="rule-item flex-align-center" :data-suffix="$t('and')">
        <bk-input
            style="width:230px;"
            :value="path"
            @input="path => change({ path })"
            :disabled="disabled"
            :placeholder="type === 'generic' ? $t('inputFilePathTip') : $t('cleanPackageRuleTip')">
        </bk-input>
        <Icon v-show="!disabled" class="ml10 hover-btn" size="24" name="icon-delete" @click.native="$emit('delete')" />
    </div>
</template>
<script>
    export default {
        name: 'metadataRule',
        props: {
            disabled: Boolean,
            path: {
                type: String,
                default: ''
            },
            type: {
                type: String,
                default: 'generic'
            }
        },
        watch: {
            $attrs: {
                handler (val) {
                    const meta = Object.values(val).find(meta => meta.field)
                    // 触发type改变导致input回置
                    meta && (this.defaultValue = { ...meta })
                },
                immediate: true,
                deep: true
            }
        },
        methods: {
            change ({
                path = this.path
            }) {
                const data = { path }
                this.$emit('change', data)
            }
        }
    }
</script>
<style lang="scss" scoped>
.rule-item {
    &:not(:nth-child(1)):before {
        content: attr(data-suffix);
        position: absolute;
        margin-left: -22px;
        color: var(--fontSubsidiaryColor);
    }
}
</style>
