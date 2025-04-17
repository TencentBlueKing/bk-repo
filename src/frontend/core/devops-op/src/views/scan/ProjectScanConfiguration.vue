<template>
  <div class="app-container">
    <el-table :data="configurations" style="width: 100%">
      <el-table-column prop="projectId" label="项目" width="180" />
      <el-table-column prop="subScanTaskCountLimit" label="子任务数限制" width="180" />
      <el-table-column prop="autoScanConfiguration" label="自动扫描" width="180">
        <template slot-scope="scope">
          <el-tag v-for="scanner in Object.keys(scope.row.autoScanConfiguration)" :key="scanner">{{ scanner }}</el-tag>
        </template>
      </el-table-column>
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
            v-if="scope.row.projectId"
            size="mini"
            type="danger"
            @click="handleDelete(scope.$index, scope.row)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <create-or-update-project-scan-configuration-dialog
      :create-mode="createMode"
      :updating-configuration="updatingConfiguration"
      :visible.sync="showDialog"
      @created="handleCreated($event)"
      @updated="handleUpdated($event)"
    />
  </div>
</template>

<script>

import { deleteProjectScanConfiguration, projectScanConfigurations } from '@/api/scan'
import CreateOrUpdateProjectScanConfigurationDialog
  from '@/views/scan/components/CreateOrUpdateProjectScanConfigurationDialog'

export default {
  name: 'ProjectScanConfiguration',
  components: { CreateOrUpdateProjectScanConfigurationDialog },
  data() {
    return {
      pageNumber: 0,
      pageSize: 20,
      projectId: undefined,
      showDialog: false,
      createMode: true,
      updatingIndex: undefined,
      updatingConfiguration: undefined,
      configurations: []
    }
  },
  created() {
    this.loading = true
    this.configurations = []
    projectScanConfigurations(this.pageNumber, this.pageSize, this.projectId).then(res => {
      this.configurations.push(...res.data.records)
    })
  },
  methods: {
    handleCreated(configuration) {
      this.configurations.splice(this.configurations.length, 0, configuration)
    },
    handleUpdated(configuration) {
      this.configurations.splice(this.updatingIndex, 1, configuration)
    },
    showCreateOrUpdateDialog(create, index, configuration) {
      this.showDialog = true
      this.createMode = create
      this.updatingIndex = index
      this.updatingConfiguration = configuration
    },
    handleDelete(index, configuration) {
      this.$confirm(`是否确定删除${configuration.projectId}扫描配置`, '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        deleteProjectScanConfiguration(configuration.projectId).then(_ => {
          this.configurations.splice(index, 1)
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
