<template>
  <el-dialog v-loading="loading" title="新建Webhook" :visible.sync="showDialog" :before-close="close">
    <template v-if="!createResult">
      <el-form ref="form" :model="webhook" :rules="rules" label-width="150px">
        <el-form-item label="url" prop="url">
          <el-input v-model="webhook.url" />
        </el-form-item>
        <el-form-item label="请求头" prop="headers">
          <el-input v-model="webhook.headers" />
        </el-form-item>
        <el-form-item label="触发事件" prop="triggers">
          <el-select v-model="webhook.triggers" multiple placeholder="请选择触发事件">
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
        <el-form-item label="关联对象类型" prop="associatitonType">
          <el-select v-model="webhook.associationType" placeholder="请选择关联对象类型">
            <el-option label="系统" value="SYSTEM" />
            <el-option label="项目" value="PROJECT" />
            <el-option label="仓库" value="REPO" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="webhook.associationType != 'SYSTEM'" label="关联对象id" prop="associatitonId">
          <el-input v-model="webhook.associationId" placeholder="请输入关联对象id" />
        </el-form-item>
        <el-form-item label="事件资源key正则模式" prop="resourceKeyPattern">
          <el-input v-model="webhook.resourceKeyPattern" placeholder="请输入事件资源key正则模式" />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button @click="close">取 消</el-button>
        <el-button type="primary" @click="createWebhook">确定</el-button>
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
import { createWebhook } from '@/api/webhook'

export default {
  name: 'WebhookCreateDialog',
  props: {
    visible: Boolean
  },
  data() {
    var validateHeaders = (rule, value, callback) => {
      try {
        JSON.parse(value)
        callback()
      } catch (error) {
        callback(new Error('{"key":"value"}格式错误'))
      }
    }
    return {
      rules: {
        url: [{ required: true, message: '请输入url', trigger: 'blur' }],
        headers: [{ validator: validateHeaders, trigger: 'blur' }],
        triggers: [{ required: true, message: '请选择触发事件', trigger: 'blur' }],
        associationType: [{ required: true, message: '请选择关联对象类型', trigger: 'blur' }]
      },
      showDialog: this.visible,
      loading: false,
      webhook: {},
      resultIcon: 'success',
      resultTitle: '',
      createResult: undefined
    }
  },
  watch: {
    visible: function(newVal) {
      if (newVal) {
        this.showDialog = true
        this.loading = true
        this.createResult = undefined
        this.loading = false
      } else {
        this.close()
      }
    }
  },
  methods: {
    createWebhook() {
      this.$refs['form'].validate((valid) => {
        if (!valid) {
          return
        }
        this.loading = true
        this.webhook.headers = JSON.parse(this.webhook.headers)
        const promise = createWebhook(
          this.webhook.url,
          this.webhook.headers,
          this.webhook.triggers,
          this.webhook.associationType,
          this.webhook.associationId,
          this.webhook.resourceKeyPattern
        )
        promise.then(() => {
          this.createResult = true
          this.resultTitle = '创建成功'
          this.resultIcon = 'success'
          this.$emit('create-success')
        }).catch(_ => {
          this.createResult = {}
          this.resultTitle = '创建失败'
          this.resultIcon = 'error'
        }).finally(() => {
          this.loading = false
        })
      })
    },
    close() {
      this.webhook = {}
      this.showDialog = false
      this.$emit('update:visible', false)
    }
  }
}
</script>

<style scoped>

</style>
