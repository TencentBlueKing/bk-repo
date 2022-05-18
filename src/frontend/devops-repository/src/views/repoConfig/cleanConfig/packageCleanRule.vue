<template>
    <div class="rule-item flex-align-center">
        <span class="mr5">制品元数据满足</span>
        <bk-input
            style="width:180px;"
            :value="defaultValue.field.replace(/^metadata\.(.*)$/, '$1')"
            @input="field => change({ field: `metadata.${field}` })"
            :disabled="disabled"
            placeholder="属性键">
        </bk-input>
        <select-input
            :select="defaultValue.operation"
            :select-list="typeList"
            :input="defaultValue.value"
            :disabled="disabled"
            placeholder="属性值"
            @change="r => change(r)">
        </select-input>
        <Icon v-show="!disabled" class="ml10 hover-btn" size="24" name="icon-delete" @click.native="$emit('delete')" />
    </div>
</template>
<script>
    import SelectInput from '@repository/components/SelectInput'
    export default {
        name: 'metadataRule',
        components: { SelectInput },
        props: {
            disabled: Boolean
        },
        data () {
            return {
                defaultValue: {
                    field: '',
                    operation: 'EQ',
                    value: ''
                },
                typeList: [
                    { id: 'EQ', name: '等于' },
                    { id: 'MATCH', name: '包含' },
                    { id: 'REGEX', name: '正则匹配' }
                ]
            }
        },
        watch: {
            $attrs: {
                handler (val) {
                    this.defaultValue = {
                        field: '',
                        operation: 'EQ',
                        value: ''
                    }
                    const meta = Object.values(val).find(meta => /^metadata\./.test(meta.field))
                    meta && (this.defaultValue = { ...meta })
                },
                immediate: true,
                deep: true
            }
        },
        methods: {
            change ({
                field = this.defaultValue.field,
                select: operation = this.defaultValue.operation,
                input: value = this.defaultValue.value
            }) {
                this.$emit('change', {
                    [field]: {
                        field,
                        operation,
                        value
                    }
                })
            }
        }
    }
</script>
<style lang="scss" scoped>
.rule-item {
    &:not(:nth-child(1)):before {
        content: '或';
        position: absolute;
        margin-left: -22px;
        color: var(--fontSubsidiaryColor);
    }
}
</style>
