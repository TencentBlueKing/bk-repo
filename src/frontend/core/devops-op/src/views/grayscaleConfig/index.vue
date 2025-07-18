<template>
  <div class="app-container">
    <el-table
      :data="projectConfigs"
      style="width: 100%"
    >
      <el-table-column
        prop="projectId"
        label="项目Id"
        width="180"
      />
      <el-table-column
        prop="environment"
        label="环境"
        width="180"
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
    <create-or-update-Key-dialog
      :create-mode="createMode"
      :visible.sync="showDialog"
      :updating-keys="updatingConfig"
      @created="handleCreated($event)"
      @updated="handleUpdated"
    />
  </div>
</template>
<script>
import CreateOrUpdateKeyDialog from '@/views/grayscaleConfig/components/CreateOrUpdateConfigDialog'
import { remove, list } from '@/api/projectgrayscale'

export default {
  name: 'Account',
  components: { CreateOrUpdateKeyDialog },
  data() {
    return {
      showDialog: false,
      createMode: true,

      /**
       * 正在更新的凭据索引
       */
      updatingIndex: undefined,
      updatingConfig: undefined,
      loading: true,
      projectConfigs: [],
      defaultConfigs: {}
    }
  },
  created() {
    this.loading = true
    this.projectConfigs = []
    list().then(res => {
      this.projectConfigs = this.formatDate(res.data)
      this.defaultConfigs = this.formatDate(res.data)
    })
  },
  methods: {
    handleCreated(projectConfig) {
      console.log(projectConfig)
      this.projectConfigs.splice(this.projectConfigs.length, 0, projectConfig)
    },
    formatDate(res) {
      const target = []
      res.forEach(item => {
        const temp = {
          projectId: Object.keys(item)[0],
          environment: item[Object.keys(item)[0]]
        }
        target.push(temp)
      })
      return target
    },
    handleUpdated() {
      list().then(res => {
        this.projectConfigs = this.formatDate(res.data)
        this.defaultConfigs = this.formatDate(res.data)
        this.$forceUpdate()
      })
    },
    showCreateOrUpdateDialog(create, index, config) {
      this.showDialog = true
      this.createMode = create
      this.updatingIndex = index
      this.updatingConfig = config
    },
    handleDelete(index, config) {
      this.$confirm(`是否确定删除${config.projectId}`, '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        remove(config.projectId).then(_ => {
          this.projectConfigs.splice(index, 1)
          this.$message.success('删除成功')
        }).catch((res) => {
          console.log(res)
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
