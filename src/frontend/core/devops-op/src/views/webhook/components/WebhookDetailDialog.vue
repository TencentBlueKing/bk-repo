<template>
  <el-dialog v-loading="loading" title="修改Webhook" :visible.sync="showDialog" :before-close="close">
    <template v-if="!updateResult">
      <el-form ref="form" :model="webhookDetail" label-width="150px">
        <el-form-item label="url">
          <el-input v-model="webhookDetail.url" />
        </el-form-item>
        <el-form-item label="请求头">
          <el-input v-model="webhookDetail.headers" />
        </el-form-item>
        <el-form-item label="触发事件">
          <el-select v-model="webhookDetail.triggers" multiple placeholder="请选择触发事件">
            <el-option label="创建项目" value="PROJECT_CREATED" />
            <el-option label="创建仓库" value="REPO_CREATED" />
            <el-option label="更新仓库" value="REPO_UPDATED" />
            <el-option label="删除仓库" value="REPO_DELETED" />
            <el-option label="刷新仓库信息" value="REPO_REFRESHED" />
            <el-option label="创建节点" value="NODE_CREATED" />
            <el-option label="重命名节点" value="NODE_RENAMED" />
            <el-option label="移动节点" value="NODE_MOVED" />
            <el-option label="复制节点" value="NODE_COPIED" />
            <el-option label="删除节点" value="NODE_DELETED" />
            <el-option label="下载节点" value="NODE_DOWNLOADED" />
            <el-option label="删除元数据" value="METADATA_DELETED" />
            <el-option label="添加元数据" value="METADATA_SAVED" />
            <el-option label="创建包版本" value="VERSION_CREATED" />
            <el-option label="删除包版本" value="VERSION_DELETED" />
            <el-option label="下载包" value="VERSION_DOWNLOAD" />
            <el-option label="更新包版本" value="VERSION_UPDATED" />
            <el-option label="晋级包版本" value="VERSION_STAGED" />
          </el-select>
        </el-form-item>
        <el-form-item label="资源key正则">
          <el-input v-model="webhookDetail.resourceKeyPattern" />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button @click="close">取 消</el-button>
        <el-button type="primary" @click="updateWebhook">确定</el-button>
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
import { updateWebhook } from '@/api/webhook'

export default {
  name: 'WebhookDetailDialog',
  props: {
    visible: Boolean,
    webhook: {
      type: Object,
      required: true
    }
  },
  data() {
    return {
      showDialog: this.visible,
      loading: false,
      webhookDetail: {},
      resultIcon: 'success',
      resultTitle: '',
      updateResult: undefined
    }
  },
  watch: {
    visible: function(newVal) {
      if (newVal) {
        this.showDialog = true
        this.loading = true
        this.webhookDetail = JSON.parse(JSON.stringify(this.webhook))
        this.webhookDetail.headers = JSON.stringify(this.webhookDetail.headers)
        this.updateResult = undefined
        this.loading = false
      } else {
        this.close()
      }
    }
  },
  methods: {
    updateWebhook() {
      this.loading = true
      this.webhookDetail.headers = JSON.parse(this.webhookDetail.headers)
      const promise = updateWebhook(
        this.webhookDetail.id,
        this.webhookDetail.url,
        this.webhookDetail.headers,
        this.webhookDetail.triggers,
        this.webhookDetail.resourceKeyPattern
      )
      promise.then(() => {
        this.updateResult = true
        this.resultTitle = '更新成功'
        this.resultIcon = 'success'
        this.$emit('update-success')
      }).catch(_ => {
        this.updateResult = {}
        this.resultTitle = '更新失败'
        this.resultIcon = 'error'
      }).finally(() => {
        this.loading = false
      })
    },
    close() {
      this.webhookDetail = {}
      this.showDialog = false
      this.$emit('update:visible', false)
    }
  }
}
</script>

<style scoped>

</style>
