<template>
  <el-dialog v-loading="loading" title="文件详情" :visible.sync="showDialog" :before-close="close">
    <el-descriptions :column="6" border>
      <el-descriptions-item label="文件名" span="4">{{ nodeDetail.name }}</el-descriptions-item>
      <el-descriptions-item label="大小" span="2">
        {{ nodeDetail.folder ? '--' : fileSize(nodeDetail.size) }}
      </el-descriptions-item>
      <el-descriptions-item label="路径" span="6">{{ nodeDetail.fullPath }}</el-descriptions-item>
      <el-descriptions-item label="创建人" span="3">{{ nodeDetail.createdBy }}</el-descriptions-item>
      <el-descriptions-item label="创建时间" span="3">{{ formatDate(nodeDetail.createdDate) }}</el-descriptions-item>
      <el-descriptions-item label="最后修改人" span="3">{{ nodeDetail.lastModifiedBy }}</el-descriptions-item>
      <el-descriptions-item label="最后修改时间" span="3">
        {{ formatDate(nodeDetail.lastModifiedDate) }}
      </el-descriptions-item>
      <el-descriptions-item label="过期时间" span="3">{{ formatDate(nodeDetail.expireDate) }}</el-descriptions-item>
      <el-descriptions-item label="删除日期" span="3">{{ formatDate(nodeDetail.deleted) }}</el-descriptions-item>
      <el-descriptions-item v-if="!nodeDetail.folder" label="SHA256" span="6">{{ nodeDetail.sha256 }}</el-descriptions-item>
      <el-descriptions-item v-if="!nodeDetail.folder" label="MD5" span="6">{{ nodeDetail.md5 }}</el-descriptions-item>
    </el-descriptions>
    <template v-if="nodeDetail.metadata && Object.keys(nodeDetail.metadata).length !== 0">
      <el-divider>元数据</el-divider>
      <el-descriptions :column="2" border>
        <template v-for="(metaValue, metaKey) in nodeDetail.metadata">
          <el-descriptions-item :key="metaKey" label="KEY" span="1">{{ metaKey }}</el-descriptions-item>
          <el-descriptions-item :key="metaKey" label="VALUE" span="1">{{ metaValue }}</el-descriptions-item>
        </template>
      </el-descriptions>
    </template>
  </el-dialog>
</template>

<script>
import { searchNodes } from '@/api/node'
import { convertFileSize, formatDate } from '@/utils/file'

export default {
  name: 'FileDetailDialog',
  props: {
    visible: Boolean,
    node: {
      type: Object,
      required: true
    }
  },
  data() {
    return {
      showDialog: this.visible,
      loading: false,
      nodeDetail: {}
    }
  },
  watch: {
    visible: function(newVal) {
      if (newVal) {
        this.showDialog = true
        this.loading = true
        searchNodes(
          this.node.projectId,
          this.node.repoName,
          this.node.fullPath,
          1,
          1,
          true,
          this.node.deleted
        ).then(res => {
          this.nodeDetail = res.data.records[0]
        }).finally(_ => {
          this.loading = false
        })
      } else {
        this.close()
      }
    }
  },
  methods: {
    close() {
      this.nodeDetail = {}
      this.showDialog = false
      this.$emit('update:visible', false)
    },
    fileSize(size) {
      return convertFileSize(size)
    },
    formatDate(date) {
      return formatDate(date)
    }
  }
}
</script>

<style scoped>

</style>
