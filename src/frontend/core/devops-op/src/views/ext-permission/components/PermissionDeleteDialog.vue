<template>
  <el-dialog v-loading="loading" title="删除权限" :visible.sync="showDialog" :before-close="close" width="700px">
    <template v-if="!deleteResult">
      <el-descriptions :column="3" border>
        <el-descriptions-item label="项目Id" span="3">{{ permission.projectId }}</el-descriptions-item>
        <el-descriptions-item label="仓库名" span="3">{{ permission.repoName }}</el-descriptions-item>
        <el-descriptions-item label="url" span="3">{{ permission.url }}</el-descriptions-item>
        <el-descriptions-item label="适用范围" span="3">{{ permission.scope }}</el-descriptions-item>
        <el-descriptions-item label="是否启用" span="3">{{ permission.enabled }}</el-descriptions-item>
      </el-descriptions>
      <div slot="footer">
        <el-button @click="close">取 消</el-button>
        <el-button type="danger" @click="deletePermission">删除</el-button>
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
import { deleteExtPermission } from '@/api/ext-permission'

export default {
  name: 'PermissionDeleteDialog',
  props: {
    visible: Boolean,
    permission: {
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
    deletePermission() {
      this.loading = true
      deleteExtPermission(this.permission.id).then(() => {
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
