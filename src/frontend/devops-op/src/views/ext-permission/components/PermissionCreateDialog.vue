<template>
  <el-dialog v-loading="loading" title="新建权限" :visible.sync="showDialog" :before-close="close">
    <template v-if="!createResult">
      <el-form ref="form" :model="permission" :rules="rules" label-width="150px">
        <el-form-item label="项目Id">
          <el-input v-model="permission.projectId" />
        </el-form-item>
        <el-form-item label="仓库名">
          <el-input v-model="permission.repoName" />
        </el-form-item>
        <el-form-item label="url">
          <el-input v-model="permission.url" />
        </el-form-item>
        <el-form-item label="请求头" prop="headers">
          <el-input v-model="permission.headers" />
        </el-form-item>
        <el-form-item label="适用微服务范围">
          <el-input v-model="permission.scope" />
        </el-form-item>
        <el-form-item label="是否启用" prop="enabled">
          <el-select v-model="permission.enabled" placeholder="请选择其否启用">
            <el-option label="启用" :value="true" />
            <el-option label="未启用" :value="false" />
          </el-select>
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button @click="close">取 消</el-button>
        <el-button type="primary" @click="createPermission">确定</el-button>
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
import { createExtPermission } from '@/api/ext-permission'

export default {
  name: 'PermissionCreateDialog',
  props: {
    visible: Boolean
  },
  data() {
    var validateHeaders = (rule, value, callback) => {
      try {
        console.log(value)
        JSON.parse(value)
        callback()
      } catch (error) {
        callback(new Error('{"key":"value"}格式错误'))
      }
    }
    return {
      rules: {
        headers: [{ validator: validateHeaders, trigger: 'blur' }]
      },
      showDialog: this.visible,
      loading: false,
      permission: {},
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
    createPermission() {
      this.$refs['form'].validate((valid) => {
        console.log(valid)
        if (!valid) {
          return
        }
      })
      this.loading = true
      this.permission.headers = JSON.parse(this.permission.headers)
      const promise = createExtPermission(
        this.permission.projectId,
        this.permission.repoName,
        this.permission.url,
        this.permission.headers,
        this.permission.scope,
        this.permission.enabled
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
    },
    close() {
      this.permission = {}
      this.showDialog = false
      this.$emit('update:visible', false)
    }
  }
}
</script>

<style scoped>

</style>
