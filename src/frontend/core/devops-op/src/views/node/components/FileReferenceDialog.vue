<template>
  <el-dialog title="文件引用详情" :visible.sync="showDialog" :before-close="close">
    <el-descriptions :column="1" border>
      <el-descriptions-item label="SHA256">{{ fileReference.sha256 }}</el-descriptions-item>
      <el-descriptions-item label="所在存储">{{ fileReference.credentialsKey ? fileReference.credentialsKey : '默认存储' }}</el-descriptions-item>
      <el-descriptions-item label="当前存在文件数">{{ fileReference.count }}</el-descriptions-item>
    </el-descriptions>
  </el-dialog>
</template>

<script>
import { reference } from '@/api/reference'

export default {
  name: 'FileReferenceDialog',
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
      fileReference: {}
    }
  },
  watch: {
    visible: function(newVal) {
      if (newVal) {
        this.showDialog = true
        reference(this.node.sha256, this.node.projectId, this.node.repoName).then(res => {
          this.fileReference = res.data
        })
      } else {
        this.close()
      }
    }
  },
  methods: {
    close() {
      this.fileReference = {}
      this.showDialog = false
      this.$emit('update:visible', false)
    }
  }
}
</script>

<style scoped>

</style>
