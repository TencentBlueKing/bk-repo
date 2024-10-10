<template>
  <el-dialog title="创建扫描任务" :visible.sync="showDialog" :before-close="close" width="700px">
    <div v-loading="loading">
      <el-select
        v-model="selectedScanner"
        placeholder="请选择使用的扫描器"
      >
        <el-option v-for="scanner in scanners" :key="scanner.name" :value="scanner" :label="scanner.name" />
      </el-select>
      <div slot="footer">
        <el-button @click="close">取 消</el-button>
        <el-button type="primary" :disabled="!selectedScanner" @click="doScan">确 定</el-button>
      </div>
    </div>
  </el-dialog>
</template>

<script>
import { scan, scanners } from '@/api/scan'

export default {
  name: 'FileScanDialog',
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
      scanners: [],
      selectedScanner: undefined
    }
  },
  watch: {
    visible: function(newVal) {
      if (newVal) {
        this.showDialog = true
        this.loading = true
        scanners().then(res => {
          this.scanners = res.data
          if (this.scanners.length > 0) {
            this.scanner = this.scanners[0]
          }
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
      this.selectedScanner = undefined
      this.showDialog = false
      this.$emit('update:visible', false)
    },
    doScan() {
      scan(this.scanner.name, this.node.projectId, this.node.repoName, this.node.fullPath).then(_ => {
        this.$message.success('创建任务成功')
        this.close()
      })
    }
  }
}
</script>

<style scoped>

</style>
