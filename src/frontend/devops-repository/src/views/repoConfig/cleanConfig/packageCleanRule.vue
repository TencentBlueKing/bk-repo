<template>
    <div class="rule-item flex-align-center" :data-suffix="$t('shortOr')">
        <span class="mr5">{{$t('metadataSatisfy')}}</span>
        <bk-input
            style="width:180px;"
            :value="defaultValue.field.replace(/^metadata\.(.*)$/, '$1')"
            @compositionstart.native="handleComposition"
            @compositionend.native="handleComposition"
            @change="field => change({ field: `metadata.${field}` })"
            :disabled="disabled"
            :placeholder="$t('key')">
        </bk-input>
        <select-input
            :select="defaultValue.operation"
            :select-list="typeList"
            :input="defaultValue.value"
            :disabled="disabled"
            :placeholder="$t('value')"
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
                    { id: 'EQ', name: this.$t('equal') },
                    { id: 'MATCH', name: this.$t('contain') },
                    { id: 'REGEX', name: this.$t('regular') }
                ],
                isOnComposition: true // 此处默认设置为true，在用户输入时保证会进入抛出change事件
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
            handleComposition (e) {
                if (e.type === 'compositionend') {
                    // 中文输入完成，触发change
                    this.isOnComposition = true
                    this.change({ field: `metadata.${e.target.value}` })
                } else if (e.type === 'compositionstart') {
                    // 中文输入开始，禁止抛出事件
                    this.isOnComposition = false
                }
            },
            trimSpecial (string) {
                let str = ''
                if (string !== '') {
                    const pattern = /[`~!@#$%^\-&*()_+=|{}':;',\\\[\]\<>\/?~！@#￥……&*（）——|{}【】'；：""'‘’。，、？\s]/g
                    str = string.replace(pattern, '')
                }
                return str
            },
            change ({
                field = this.defaultValue.field,
                select: operation = this.defaultValue.operation,
                input: value = this.defaultValue.value
            }) {
                // key 值不能有特殊符号
                const key = this.trimSpecial(field)
                // 过滤value字段空格
                if (value) {
                    value = value.replace(/(^\s*)|(\s*$)/g, '')
                }
                // 英文、数字输入正常抛出，中文输入开始到结束阶段不抛出
                // 此处注意，如果从始至终都没有输入中文，this.isOnComposition的值就是undefined，但感觉这样不太好，因此在上方定义此变量为响应式数据
                if (this.isOnComposition || this.isOnComposition === undefined) {
                    this.$emit('change', {
                        [key]: {
                            field: key,
                            operation,
                            value
                        }
                    })
                }
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
