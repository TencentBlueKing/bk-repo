<template>
  <el-dialog v-loading="loading" title="删除插件" :visible.sync="showDialog" :before-close="close" width="700px">
    <template v-if="!deleteResult">
      <el-descriptions :column="3" border>
        <el-descriptions-item label="id" span="3">{{ plugin.id }}</el-descriptions-item>
        <el-descriptions-item label="版本" span="3">{{ plugin.version }}</el-descriptions-item>
        <el-descriptions-item label="适用范围" span="3">{{ plugin.scope }}</el-descriptions-item>
        <el-descriptions-item label="代码库地址" span="3">{{ plugin.gitUrl }}</el-descriptions-item>
        <el-descriptions-item label="描述" span="3">{{ plugin.description }}</el-descriptions-item>
      </el-descriptions>
      <div slot="footer">
        <el-button @click="close">取 消</el-button>
        <el-button type="danger" @click="deletePlugin">删除</el-button>
      </div>
    </template>
    <el-result v-else :icon="resultIcon" :title="resultTitle">
      <template slot="extra">
        <el-divider />
        <el-button type="primary" @click="close">返 回</el-button>
      </template>
    </el-result>
  </el-dialog>
</template>

<script>
import { convertFileSize, formatDate } from '@/utils/file'
import { deletePlugin } from '@/api/plugin'

export default {
  name: 'PluginDeleteDialog',
  props: {
    visible: Boolean,
    plugin: {
      type: Object,
      required: true
    }
  },
  data() {
    return {
      loading: false,
      showDialog: this.visible,
      resultIcon: 'success',
      resultTitle: '',
      deleteResult: undefined
    }
  },
  watch: {
    visible: function(newVal) {
      if (newVal) {
        this.showDialog = true
      } else {
        this.close()
      }
    }
  },
  methods: {
    close() {
      this.deleteResult = undefined
      this.showDialog = false
      this.$emit('update:visible', false)
    },
    formatDate(date) {
      return formatDate(date)
    },
    fileSize(size) {
      return convertFileSize(size)
    },
    deletePlugin() {
      this.loading = true
      deletePlugin(this.plugin.id).then(() => {
        this.deleteResult = true
        this.resultTitle = '删除成功'
        this.resultIcon = 'success'
        this.$emit('delete-success')
      }).catch(_ => {
        this.deleteResult = {}
        this.resultTitle = '删除失败'
        this.resultIcon = 'error'
      }).finally(() => {
        this.loading = false
      })
    }
  }
}
</script>

<style scoped>

</style>
