<template>
    <div class="rule-item flex-align-center" :data-suffix="$t('shortOr')">
        <span>{{ $t('genericRule') }}</span>
        <select-input
            class="ml5"
            :select="name.operation"
            :select-list="typeList"
            :input="name.value"
            :disabled="disabled"
            @change="r => change('name', r)">
        </select-input>
        <span>{{ $t('genericRuleSpan') }}</span>
        <select-input
            class="ml5"
            :select="version.operation"
            :select-list="typeList"
            :input="version.value"
            :disabled="disabled"
            @change="r => change('version', r)">
        </select-input>
        <Icon v-show="!disabled" class="ml10 hover-btn" size="24" name="icon-delete" @click.native="$emit('delete')" />
    </div>
</template>
<script>
    import SelectInput from '@repository/components/SelectInput'
    export default {
        name: 'packageRule',
        components: { SelectInput },
        props: {
            disabled: Boolean,
            name: {
                type: Object,
                default: () => ({
                    operation: 'EQ',
                    value: ''
                })
            },
            version: {
                type: Object,
                default: () => ({
                    operation: 'EQ',
                    value: ''
                })
            }
        },
        data () {
            return {
                typeList: [
                    { id: 'EQ', name: this.$t('equal') },
                    { id: 'MATCH', name: this.$t('contain') },
                    { id: 'REGEX', name: this.$t('regular') }
                ]
            }
        },
        methods: {
            change (type, rule) {
                this.$emit('change', {
                    name: this.name,
                    version: this.version,
                    [type]: {
                        field: type,
                        operation: rule.select,
                        value: rule.input
                    }
                })
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
