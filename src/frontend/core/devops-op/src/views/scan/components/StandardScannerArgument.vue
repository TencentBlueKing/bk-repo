<template>
  <div>
    <el-select slot="prepend" v-model="argument.type" size="mini" style="width: 110px" placeholder="请选择">
      <el-option v-for="type in types" :key="type" :label="type" :value="type" />
    </el-select>
    <el-input v-model="argument.key" size="mini" style="width: 20%;margin-left: 5px" placeholder="参数KEY" />
    <el-input v-model="argument.value" size="mini" style="width: 55%;margin-left: 5px" placeholder="参数value" clearable @clear="clearValue" />
    <el-button slot="append" size="mini" style="margin-left: 10px" type="danger" @click="remove()">删除</el-button>
  </div>
</template>

<script>
export default {
  name: 'StandardScannerArgument',
  props: {
    argument: {
      type: Object,
      default: undefined
    }
  },
  data() {
    return {
      types: ['NUMBER', 'STRING', 'BOOLEAN']
    }
  },
  methods: {
    remove() {
      this.$emit('remove')
    },
    clearValue() {
      this.argument.value = null
    }
  }
}
</script>

<style scoped>

</style>
