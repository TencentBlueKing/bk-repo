<template>
  <el-dialog v-loading="loading" title="删除Webhook" :visible.sync="showDialog" :before-close="close" width="700px">
    <template v-if="!deleteResult">
      <el-descriptions :column="3" border>
        <el-descriptions-item label="url" span="3">{{ webhook.url }}</el-descriptions-item>
        <el-descriptions-item label="请求头" span="3">{{ webhook.headers }}</el-descriptions-item>
        <el-descriptions-item label="触发事件" span="3">{{ webhook.triggers }}</el-descriptions-item>
        <el-descriptions-item label="关联对象类型" span="3">{{ webhook.associationType }}</el-descriptions-item>
        <el-descriptions-item label="关联对象id" span="3">{{ webhook.associationId }}</el-descriptions-item>
      </el-descriptions>
      <div slot="footer">
        <el-button @click="close">取 消</el-button>
        <el-button type="danger" @click="deleteWebhook">删除</el-button>
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
import { deleteWebhook } from '@/api/webhook'

export default {
  name: 'WebhookDeleteDialog',
  props: {
    visible: Boolean,
    webhook: {
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
    deleteWebhook() {
      this.loading = true
      deleteWebhook(this.webhook.id).then(() => {
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
