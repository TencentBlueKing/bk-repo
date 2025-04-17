<template>
  <el-dialog v-loading="loading" title="恢复文件" :visible.sync="showDialog" :before-close="close" width="700px">
    <template v-if="!restoreResult">
      <el-descriptions :column="3" border>
        <el-descriptions-item label="文件名" span="3">{{ node.name }}</el-descriptions-item>
        <el-descriptions-item label="路径" span="3">{{ node.fullPath }}</el-descriptions-item>
        <el-descriptions-item label="删除日期">{{ formatDate(node.deleted) }}</el-descriptions-item>
      </el-descriptions>
      <div slot="footer">
        <el-button @click="close">取 消</el-button>
        <el-button type="primary" @click="restore">开始恢复</el-button>
      </div>
    </template>
    <el-result v-else :icon="resultIcon" :title="resultTitle">
      <template slot="extra">
        <template v-if="resultIcon === 'success'">
          <span>恢复 </span>
          <el-tag type="success">{{ restoreResult.restoreCount }}</el-tag>
          <span> 个文件，</span>
          <span>跳过 </span>
          <el-tag type="info">{{ restoreResult.skipCount }}</el-tag>
          <span> 个文件，</span>
          <span>覆盖 </span>
          <el-tag type="warning">{{ restoreResult.conflictCount }}</el-tag>
          <span> 个文件</span>
        </template>
        <el-divider />
        <el-button type="primary" @click="close">返 回</el-button>
      </template>
    </el-result>
  </el-dialog>
</template>

<script>
import { formatDate } from '@/utils/file'
import { restoreNode } from '@/api/node'

export default {
  name: 'FileRestoreDialog',
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
      restoreResult: undefined
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
      this.restoreResult = undefined
      this.showDialog = false
      this.$emit('update:visible', false)
    },
    formatDate(date) {
      return formatDate(date)
    },
    restore() {
      this.loading = true
      const deleteDate = new Date(this.node.deleted)
      restoreNode(this.node.projectId, this.node.repoName, this.node.fullPath, deleteDate.getTime()).then(res => {
        this.restoreResult = res.data
        this.resultTitle = '恢复成功'
        this.resultIcon = 'success'
        this.$emit('restore-success', this.node)
      }).catch(_ => {
        this.restoreResult = {}
        this.resultTitle = '恢复失败'
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
