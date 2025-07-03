<template>
    <div class="select-input-container flex-align-center">
        <bk-select class="select-container"
            :placeholder="$t('pleaseSelect')"
            :value="select"
            :clearable="false"
            :disabled="disabled"
            @change="s => change('select', s)">
            <bk-option
                v-for="option in selectList"
                :key="option[selectId]"
                :id="option[selectId]"
                :name="option[selectName]">
            </bk-option>
        </bk-select>
        <bk-input
            class="input-container"
            :value="input"
            @input="i => change('input', i)"
            :disabled="disabled"
            :placeholder="placeholder">
        </bk-input>
    </div>
</template>
<script>
    export default {
        name: 'selectInput',
        props: {
            select: String,
            input: String,
            placeholder: String,
            disabled: Boolean,
            selectId: {
                type: String,
                default: 'id'
            },
            selectName: {
                type: String,
                default: 'name'
            },
            selectList: {
                type: Array,
                default: () => []
            }
        },
        methods: {
            change (type, value) {
                this.$emit('change', {
                    select: this.select,
                    input: this.input,
                    [type]: value
                })
            }
        }
    }
</script>
<style lang="scss" scoped>
.select-input-container {
    .select-container {
        min-width: 100px;
    }
    .input-container {
        min-width: 180px;
    }
}
</style>
