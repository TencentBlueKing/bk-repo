<template>
  <el-dialog v-loading="loading" title="修改权限" :visible.sync="showDialog" :before-close="close">
    <template v-if="!updateResult">
      <el-form ref="form" :model="permissionDetail" label-width="150px">
        <el-form-item label="项目Id">
          <el-input v-model="permissionDetail.projectId" />
        </el-form-item>
        <el-form-item label="仓库名">
          <el-input v-model="permissionDetail.repoName" />
        </el-form-item>
        <el-form-item label="url">
          <el-input v-model="permissionDetail.url" />
        </el-form-item>
        <el-form-item label="headers">
          <el-input v-model="permissionDetail.headers" />
        </el-form-item>
        <el-form-item label="使用范围">
          <el-input v-model="permissionDetail.scope" />
        </el-form-item>
        <el-form-item label="平台账号白名单" prop="enabled">
          <el-input v-model="permissionDetail.platformWhiteList" placeholder="请输入平台账号白名单" />
        </el-form-item>
        <el-form-item label="是否启用" prop="enabled">
          <el-select v-model="permissionDetail.enabled" placeholder="请选择其否启用">
            <el-option label="启用" :value="true" />
            <el-option label="未启用" :value="false" />
          </el-select>
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button @click="close">取 消</el-button>
        <el-button type="primary" @click="updatePermission">确定</el-button>
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
import { updateExtPermission } from '@/api/ext-permission'

export default {
  name: 'PermissionDetailDialog',
  props: {
    visible: Boolean,
    permission: {
      type: Object,
      required: true
    }
  },
  data() {
    return {
      showDialog: this.visible,
      loading: false,
      permissionDetail: {},
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
        this.permissionDetail = JSON.parse(JSON.stringify(this.permission))
        this.permissionDetail.headers = JSON.stringify(this.permissionDetail.headers)
        this.updateResult = undefined
        this.loading = false
      } else {
        this.close()
      }
    }
  },
  methods: {
    updatePermission() {
      this.loading = true
      this.permissionDetail.headers = JSON.parse(this.permissionDetail.headers)
      const promise = updateExtPermission(
        this.permissionDetail.id,
        this.permissionDetail.projectId,
        this.permissionDetail.repoName,
        this.permissionDetail.url,
        this.permissionDetail.headers,
        this.permissionDetail.scope,
        this.permissionDetail.platformWhiteList,
        this.permissionDetail.enabled
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
      this.permissionDetail = {}
      this.showDialog = false
      this.$emit('update:visible', false)
    }
  }
}
</script>

<style scoped>

</style>
