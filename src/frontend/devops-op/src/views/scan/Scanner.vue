<template>
  <div class="app-container">
    <el-table :data="scanners" style="width: 100%">
      <el-table-column
        prop="name"
        label="Name"
        width="180"
      />
      <el-table-column
        prop="type"
        label="Type"
        width="180"
      />
      <el-table-column
        prop="version"
        label="Version"
        width="180"
      />
      <el-table-column
        prop="maxScanDurationPerMb"
        label="1MB最大允许扫描时间"
        width="180"
      />
      <el-table-column
        prop="description"
        label="描述"
        width="360"
      />
      <el-table-column align="right">
        <template slot="header">
          <el-button type="primary" @click="showCreateOrUpdateDialog(true)">创建</el-button>
        </template>
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="primary"
            @click="showCreateOrUpdateDialog(false, scope.$index, scope.row)"
          >
            编辑
          </el-button>
          <el-button
            v-if="!scope.row.default"
            size="mini"
            type="danger"
            @click="handleDelete(scope.$index, scope.row)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <create-or-update-scanner-dialog
      :create-mode="createMode"
      :updating-scanner="updatingScanner"
      :visible.sync="showDialog"
      @created="handleCreated($event)"
      @updated="handleUpdated($event)"
    />
  </div>
</template>

<script>

import { deleteScanner, scanners } from '@/api/scan'
import CreateOrUpdateScannerDialog from '@/views/scan/components/CreateOrUpdateScannerDialog'

export default {
  name: 'Scanner',
  components: { CreateOrUpdateScannerDialog },
  data() {
    return {
      showDialog: false,
      createMode: true,
      updatingIndex: undefined,
      updatingScanner: undefined,
      scanners: []
    }
  },
  created() {
    this.loading = true
    this.scanners = []
    scanners().then(res => {
      this.scanners.push(...res.data)
    })
  },
  methods: {
    handleCreated(scanner) {
      this.scanners.splice(this.scanners.length, 0, scanner)
    },
    handleUpdated(scanner) {
      this.scanners.splice(this.updatingIndex, 1, scanner)
    },
    showCreateOrUpdateDialog(create, index, scanner) {
      this.showDialog = true
      this.createMode = create
      this.updatingIndex = index
      this.updatingScanner = scanner
    },
    handleDelete(index, scanner) {
      this.$confirm(`是否确定删除${scanner.name}`, '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        deleteScanner(scanner.name).then(_ => {
          this.scanners.splice(index, 1)
          this.$message.success('删除成功')
        })
      }).catch(() => {
        this.$message({
          type: 'info',
          message: '已取消'
        })
      })
    }
  }
}
</script>

<style scoped>

</style>
