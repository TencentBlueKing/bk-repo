<template>
    <div class="flex-align-center mt10" :data-suffix="$t('shortOr')">
        <bk-select
            style="width:120px;"
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
            @compositionstart.native="handleComposition"
            @compositionend.native="handleComposition"
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
                    if (val === oldValue || (oldValue.indexOf('metadata') > -1 && val === 'metadata')) {
                        // 当之前的值和新的值相等时或者说之前的值是元数据并且新值选择的依旧是元数据，此时都是不需要在重新改变当前的输入框的值的
                        return
                    }
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
                    if (Object.values(val)?.[0]?.field === 'id') {
                        // 此时表明选择的是全部
                        this.type = 'all'
                    }
                    // 触发type改变导致input回置
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
                    const data = {}
                    key && (data[key] = { field: key, operation, value })
                    this.$emit('change', data)
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
