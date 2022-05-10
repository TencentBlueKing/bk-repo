<template>
  <el-dialog v-loading="loading" title="新建权限" :visible.sync="showDialog" :before-close="close">
    <template v-if="!createResult">
      <el-form ref="form" :model="permission" :rules="rules" label-width="150px">
        <el-form-item label="项目Id" prop="projectId">
          <el-input v-model="permission.projectId" />
        </el-form-item>
        <el-form-item label="仓库名" prop="repoName">
          <el-input v-model="permission.repoName" />
        </el-form-item>
        <el-form-item label="url" prop="url">
          <el-input v-model="permission.url" />
        </el-form-item>
        <el-form-item label="请求头" prop="headers">
          <el-input v-model="permission.headers" />
        </el-form-item>
        <el-form-item label="适用接口范围" prop="scope">
          <el-input v-model="permission.scope" />
        </el-form-item>
        <el-form-item label="平台账号白名单" prop="enabled">
          <el-input v-model="permission.platformWhiteList" placeholder="请输入平台账号白名单" />
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
        JSON.parse(value)
        callback()
      } catch (error) {
        callback(new Error('{"key":"value"}格式错误'))
      }
    }
    return {
      rules: {
        projectId: [{ required: true, message: '请输入项目id', trigger: 'blur' }],
        repoName: [{ required: true, message: '请输入仓库名称', trigger: 'blur' }],
        url: [{ required: true, message: '请输入url', trigger: 'blur' }],
        scope: [{ required: true, message: '请输入适用接口范围', trigger: 'blur' }],
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
        if (!valid) {
          return
        }
        this.loading = true
        this.permission.headers = JSON.parse(this.permission.headers)
        const promise = createExtPermission(
          this.permission.projectId,
          this.permission.repoName,
          this.permission.url,
          this.permission.headers,
          this.permission.scope,
          this.permission.platformWhiteList,
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
