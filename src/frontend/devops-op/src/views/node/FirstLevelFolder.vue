<template>
  <div class="app-container">
    <el-form ref="form" :inline="true">
      <el-form-item label="项目id">
        <el-select v-model="projectSelect" placeholder="请选择" @change="queryRepo()">
          <el-option
            v-for="item in projectOptions"
            :key="item.name"
            :label="item.name"
            :value="item.name"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="仓库">
        <el-select v-model="repoSelect" placeholder="请选择" :disabled="!projectSelect">
          <el-option
            v-for="item in repoOptions"
            :key="item.name"
            :label="item.name"
            :value="item.name"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button size="mini" type="primary" :disabled="!repoSelect" @click="queryPage(1)">查询</el-button>
      </el-form-item>
    </el-form>
    <div class="app">
      <el-table :data="tableData" style="width: 100%">
        <el-table-column prop="path" label="路径" width="600" />
        <el-table-column prop="nodeNum" label="节点数" />
        <el-table-column prop="capSize" label="容量大小" />
      </el-table>
    </div>
    <div style="margin-top:20px">
      <el-pagination
        :current-page="folderQuery.pageNumber"
        :page-size="folderQuery.pageSize"
        layout="total, prev, pager, next, jumper"
        :total="total"
        @current-change="handleCurrentChange"
      />
    </div>
  </div>
</template>

<script>
import { DEFAULT_PAGE_SIZE } from '@/api/metrics'
import { listProjects } from '@/api/project'
import { listRepositories } from '@/api/repository'
import { statisticalFirstLevelFolder } from '@/api/node'
import { convertFileSize } from '@/utils/file'

export default {
  data() {
    return {
      projectSelect: '',
      repoSelect: '',
      tableData: [],
      projectOptions: [],
      repoOptions: [],
      total: 0,
      folderQuery: {
        path: '',
        totalSize: '',
        pageNumber: 1,
        pageSize: DEFAULT_PAGE_SIZE
      }
    }
  },
  created() {
    this.queryOption()
  },
  methods: {
    queryOption() {
      listProjects().then(res => {
        this.projectOptions = res.data
      })
    },
    queryRepo() {
      const projectId = this.projectSelect
      listRepositories(projectId).then(res => {
        this.repoOptions = res.data
      })
    },
    handleCurrentChange(val) {
      this.currentPage = val
      this.queryPage(val)
    },
    queryPage(pageNum) {
      const projectId = this.projectSelect
      const repoId = this.repoSelect
      statisticalFirstLevelFolder(projectId, repoId, pageNum).then(res => {
        for (let i = 0; i < res.data.records.length; i++) {
          res.data.records[i].capSize = convertFileSize(res.data.records[i].capSize)
        }
        this.tableData = res.data.records
        this.total = res.data.totalRecords
      })
    }
  }
}

</script>

<style scoped>

</style>
