<template>
  <div class="app-container">
    <el-form ref="form" :rules="rules" :inline="true" :model="nodeQuery">
      <el-form-item label="查询模式">
        <el-select v-model="nodeQuery.useSha256" size="mini" style="width: 110px" placeholder="请选择查询模式">
          <el-option :key="true" :value="true" label="SHA256" />
          <el-option :key="false" :value="false" label="文件路径" />
        </el-select>
        <el-divider direction="vertical" />
      </el-form-item>
      <el-form-item v-if="!nodeQuery.useSha256" label="项目ID" prop="projectId">
        <el-input v-model="nodeQuery.projectId" size="mini" placeholder="请输入项目ID" />
      </el-form-item>
      <el-form-item v-if="!nodeQuery.useSha256" label="仓库" prop="repoName">
        <el-input v-model="nodeQuery.repoName" size="mini" placeholder="请输入仓库名" />
      </el-form-item>
      <el-form-item v-if="!nodeQuery.useSha256" label="路径" prop="path">
        <el-input v-model="nodeQuery.path" style="width: 500px;" size="mini" placeholder="请输入文件或目录路径" />
      </el-form-item>
      <el-form-item v-if="nodeQuery.useSha256" label="SHA256" prop="sha256">
        <el-input v-model="nodeQuery.sha256" style="width: 500px" size="mini" placeholder="请输入所查节点的SHA256" />
      </el-form-item>
      <el-form-item>
        <el-button size="mini" type="primary" @click="queryNodes(nodeQuery, true)">查询</el-button>
      </el-form-item>
    </el-form>
    <el-table v-loading="loading" :data="nodes" style="width: 100%" :row-class-name="tableRowClassName">
      <el-table-column prop="name" label="文件名" width="200px" />
      <el-table-column prop="size" label="大小" width="120px" />
      <el-table-column prop="lastModifiedBy" label="修改人" width="120px" />
      <el-table-column prop="lastModifiedDate" label="修改时间" width="200px" />
      <el-table-column prop="deletedDate" label="删除时间" width="200px" />
      <el-table-column label="操作">
        <template slot-scope="scope">
          <el-dropdown size="mini" split-button type="primary" @click="showNodeDetail(scope.row)">
            详情
            <el-dropdown-menu slot="dropdown">
              <el-dropdown-item @click="showFileReferenceDetail(scope.row)">引用详情</el-dropdown-item>
              <el-dropdown-item @click="showNodesOfFile(scope.row.sha256)">同引用节点</el-dropdown-item>
            </el-dropdown-menu>
          </el-dropdown>
          <el-popconfirm title="确定恢复文件吗" @onConfirm="restore(scope.row)">
            <el-button slot="reference" style="margin-left: 10px" size="mini" type="primary">恢复</el-button>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      background
      layout="prev, pager, next"
      :current-page.sync="nodeQuery.pageNumber"
      :page-size="nodeQuery.pageSize"
      :hide-on-single-page="true"
      :total="total"
      @current-change="queryNodes(nodeQuery)"
    />
  </div>
</template>
<script>
import { pageNodes, pageNodesBySha256 } from '@/api/node'

export default {
  name: 'Node',
  data() {
    return {
      rules: {},
      loading: false,
      nodeQuery: {
        useSha256: true,
        projectId: '',
        repoName: '',
        path: '',
        sha256: '',
        pageNumber: 1,
        pageSize: 2
      },
      nodes: [],
      total: 0
    }
  },
  methods: {
    queryNodes(nodeQuery, resetPage = false) {
      this.loading = true
      let promise = null
      if (nodeQuery.useSha256) {
        if (resetPage) {
          nodeQuery.pageNumber = 1
        }
        promise = pageNodesBySha256(nodeQuery.sha256, nodeQuery.pageNumber, nodeQuery.pageSize)
      } else {
        promise = pageNodes(nodeQuery.projectId, nodeQuery.repoName, nodeQuery.path, nodeQuery.pageNumber, nodeQuery.pageSize)
      }
      promise.then(res => {
        this.nodes = res.data.records
        this.total = res.data.totalRecords
      }).catch(_ => {
        this.nodes = []
        this.total = 0
      }).finally(() => {
        this.loading = false
      })
    },
    tableRowClassName({ row }) {
      if (row.deletedDate) {
        return 'danger-row'
      }
      return ''
    },
    showNodeDetail(node) {
      console.log(node)
    },
    showFileReferenceDetail(node) {
      console.log('showFileReferenceDetail')
    },
    showNodesOfFile(sha256) {
      console.log(sha256)
    },
    restore(node) {
      console.log('restore')
    },
    deleteNode(node) {
      console.log('delete')
    }
  }
}
</script>

<style scoped>

</style>
