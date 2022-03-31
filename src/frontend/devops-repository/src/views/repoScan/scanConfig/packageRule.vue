<template>
    <div class="rule-item flex-align-center">
        <span>制品名称满足规则</span>
        <select-input
            class="ml5"
            :select="nameRule.type"
            :select-list="typeList"
            :input="nameRule.value"
            :disabled="disabled"
            @change="r => change('name', r)">
        </select-input>
        <span>，并且制品版本满足规则</span>
        <select-input
            class="ml5"
            :select="versionRule.type"
            :select-list="typeList"
            :input="versionRule.value"
            :disabled="disabled"
            @change="r => change('version', r)">
        </select-input>
        <Icon v-show="!disabled" class="ml10 hover-btn" size="14" name="icon-delete" @click.native="$emit('delete')" />
    </div>
</template>
<script>
    import SelectInput from '@repository/components/SelectInput'
    export default {
        name: 'packageRule',
        components: { SelectInput },
        props: {
            disabled: Boolean,
            nameRule: {
                type: Object,
                default: () => ({
                    type: 'EQ',
                    value: ''
                })
            },
            versionRule: {
                type: Object,
                default: () => ({
                    type: 'EQ',
                    value: ''
                })
            }
        },
        data () {
            return {
                typeList: [
                    { id: 'EQ', name: '等于' },
                    { id: 'IN', name: '包含' },
                    { id: 'REGEX', name: '正则匹配' }
                ]
            }
        },
        methods: {
            change (type, rule) {
                this.$emit('change', {
                    nameRule: this.nameRule,
                    versionRule: this.versionRule,
                    [`${type}Rule`]: {
                        type: rule.select,
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
        content: '或者';
        position: absolute;
        margin-left: -30px;
    }
}
</style>
