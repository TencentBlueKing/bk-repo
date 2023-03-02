<template>
    <div class="rule-item flex-align-center" :data-suffix="$t('shortOr')">
        <bk-input
            style="width:180px;"
            :value="path.value"
            @input="path => change({ path })"
            :disabled="disabled"
            :placeholder="$t('inputFilePathTip')">
        </bk-input>
        <bk-select
            style="width:100px;"
            v-model="type"
            :clearable="false"
            :disabled="disabled">
            <bk-option id="name" :name="$t('fileName')"></bk-option>
            <bk-option id="metadata" :name="$t('metadata')"></bk-option>
            <bk-option id="all" :name="$t('total')"></bk-option>
        </bk-select>
        <bk-input
            v-show="type === 'metadata'"
            style="width:180px;"
            :value="defaultValue.field.replace(/^metadata\.(.*)$/, '$1')"
            @input="field => change({ field: `metadata.${field}` })"
            :disabled="disabled"
            :placeholder="$t('key')">
        </bk-input>
        <select-input
            v-show="type !== 'all'"
            :select="defaultValue.operation"
            :select-list="typeList"
            :input="defaultValue.value"
            :disabled="disabled"
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
            disabled: Boolean,
            path: {
                type: Object,
                default: () => ({
                    field: 'path',
                    operation: 'REGEX',
                    value: ''
                })
            }
        },
        data () {
            return {
                defaultValue: {
                    field: '',
                    operation: 'EQ',
                    value: ''
                },
                typeList: [
                    { id: 'EQ', name: this.$t('equal') },
                    { id: 'MATCH', name: this.$t('contain') },
                    { id: 'REGEX', name: this.$t('regular') }
                ]
            }
        },
        computed: {
            type: {
                get () {
                    if (this.defaultValue.field === 'name') return 'name'
                    if (this.defaultValue.field) return 'metadata'
                    return 'all'
                },
                set (val) {
                    const oldValue = this.defaultValue.field
                    // 被动改变
                    if (val === oldValue) return
                    // 用户输入
                    let field = ''
                    if (val === 'name') field = 'name'
                    if (val === 'metadata') field = 'metadata.'
                    this.change({ field, input: '' })
                }
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
                path = this.path.value,
                field = this.defaultValue.field,
                select: operation = this.defaultValue.operation,
                input: value = this.defaultValue.value
            }) {
                const data = {
                    path: { field: 'path', operation: 'REGEX', value: path }
                }
                field && (data[field] = { field, operation, value })
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
