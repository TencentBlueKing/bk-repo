<template>
  <div class="app-container">
    <el-table
      v-loading="loading"
      :data="clusters"
      style="width: 100%; margin-top: -20px"
    >
      <el-table-column
        prop="name"
        label="执行集群名"
      />
      <el-table-column
        prop="description"
        label="描述"
      />
      <el-table-column
        prop="type"
        label="类型"
      />
      <el-table-column align="right" width="250">
        <template slot="header">
          <el-button type="primary" @click="showEdit(true)">创建</el-button>
        </template>
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="primary"
            @click="showDetail(scope.row)"
          >
            详情
          </el-button>
          <el-button
            size="mini"
            type="primary"
            @click="showEdit(false, scope.row)"
          >
            编辑
          </el-button>
          <el-button
            size="mini"
            type="danger"
            @click="handleDelete(scope.row)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <edit-cluster-config-dialog :visible.sync="showDialog" :updating-cluster-config="param" :create-mode="createMode" :show-mode="showMode" @updated="updated" />
  </div>
</template>

<script>

import { clusters, remove } from '@/api/executionClusters'
import editClusterConfigDialog from '@/views/execution-clusters/components/EditClusterConfigDialog.vue'

export default {
  name: 'ExecutionClusters',
  components: { editClusterConfigDialog },
  inject: ['reload'],
  data() {
    return {
      loading: false,
      clusters: [],
      param: undefined,
      createMode: false,
      showDialog: false,
      showMode: false
    }
  },
  created() {
    this.query()
  },
  methods: {
    query() {
      clusters().then(res => {
        this.clusters = res.data
      })
    },
    showEdit(mode, row) {
      this.showMode = false
      this.createMode = mode
      this.param = row
      this.showDialog = true
    },
    showDetail(row) {
      this.createMode = false
      this.showMode = true
      this.param = row
      this.showDialog = true
    },
    handleDelete(row) {
      this.$confirm(`是否确定删除当前配置`, '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        let promise = null
        promise = remove(row.name)
        promise.then(() => {
          this.updated()
        })
      }).catch(() => {
        this.$message({
          type: 'info',
          message: '已取消'
        })
      })
    },
    updated() {
      this.reload()
    }
  }
}
</script>

<style scoped>
</style>
