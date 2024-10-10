<template>
  <el-dialog v-loading="loading" title="删除文件" :visible.sync="showDialog" :before-close="close" width="700px">
    <template v-if="!deleteResult">
      <el-descriptions :column="3" border>
        <el-descriptions-item label="文件名" span="3">{{ node.name }}</el-descriptions-item>
        <el-descriptions-item label="路径" span="3">{{ node.fullPath }}</el-descriptions-item>
      </el-descriptions>
      <div slot="footer">
        <el-button @click="close">取 消</el-button>
        <el-button type="danger" @click="deleteNode">删除</el-button>
      </div>
    </template>
    <el-result v-else :icon="resultIcon" :title="resultTitle">
      <template slot="extra">
        <template v-if="resultIcon === 'success'">
          <span>删除 </span>
          <el-tag type="success">{{ deleteResult.deletedNumber }}</el-tag>
          <span> 个文件，</span>
          <span>总大小 </span>
          <el-tag type="success">{{ fileSize(deleteResult.deletedSize) }}</el-tag>
        </template>
        <el-divider />
        <el-button type="primary" @click="close">返 回</el-button>
      </template>
    </el-result>
  </el-dialog>
</template>

<script>
import { convertFileSize, formatDate } from '@/utils/file'
import { deleteNode } from '@/api/node'

export default {
  name: 'FileDeleteDialog',
  props: {
    visible: Boolean,
    node: {
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
    deleteNode() {
      this.loading = true
      deleteNode(this.node.projectId, this.node.repoName, this.node.fullPath).then(res => {
        this.deleteResult = res.data
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
