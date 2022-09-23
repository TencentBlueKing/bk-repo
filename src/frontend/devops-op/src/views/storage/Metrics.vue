<template>
  <div class="app-container">
    <div class="app">
      <el-table :data="tableData" style="width: 100%">
        <el-table-column prop="path" label="路径" width="600" />
        <el-table-column prop="totalFileCount" label="文件数量" />
        <el-table-column prop="totalFolderCount" label="文件夹数量" />
        <el-table-column prop="totalSpace" label="磁盘总存储" />
        <el-table-column prop="totalSize" label="路径已用存储大小" />
        <el-table-column prop="usedPercent" label="磁盘已用百分比" />
      </el-table>
    </div>
    <div style="margin-top:20px">
      <el-pagination
        :current-page="metricsQuery.pageNumber"
        :page-size="metricsQuery.pageSize"
        layout="total, prev, pager, next, jumper"
        :total="total"
        @current-change="handleCurrentChange"
      />
    </div>
  </div>
</template>

<script>
import { queryMetrics } from '@/api/metrics'
import { DEFAULT_PAGE_SIZE } from '@/api/metrics'
import { convertFileSize } from '@/utils/file'

export default {
  data() {
    return {
      tableData: [],
      total: 0,
      formDate: '',
      metricsQuery: {
        path: '',
        totalSize: '',
        pageNumber: 1,
        pageSize: DEFAULT_PAGE_SIZE
      }
    }
  },
  created() {
    this.queryPage(1)
  },
  methods: {
    handleCurrentChange(val) {
      this.currentPage = val
      this.queryPage(val)
    },
    queryPage(pageNum) {
      queryMetrics(pageNum).then(res => {
        for (let i = 0; i < res.data.records.length; i++) {
          res.data.records[i].totalSize = convertFileSize(res.data.records[i].totalSize)
          res.data.records[i].totalSpace = convertFileSize(res.data.records[i].totalSpace)
          res.data.records[i].usedPercent = (res.data.records[i].usedPercent * 100).toFixed(2) + '%'
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
