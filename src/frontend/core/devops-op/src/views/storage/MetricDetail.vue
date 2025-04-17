<template>
  <div class="app-container">
    <el-form ref="form" :inline="true">
      <el-form-item label="父级目录">
        <el-select v-model="metricSelect" placeholder="请选择" @change="queryDetail(1)">
          <el-option
            v-for="item in options"
            :key="item"
            :label="item"
            :value="item"
          />
        </el-select>
      </el-form-item>
    </el-form>
    <div class="app">
      <el-table :data="tableData" style="width: 100%">
        <el-table-column prop="path" label="路径" width="600" />
        <el-table-column prop="totalSize" label="大小" />
      </el-table>
    </div>
    <div style="margin-top:20px">
      <el-pagination
        :current-page="metricQuery.pageNumber"
        :page-size="metricQuery.pageSize"
        layout="total, prev, pager, next, jumper"
        :total="total"
        @current-change="handleCurrentChange"
      />
    </div>
  </div>
</template>

<script>
import { querySelectOption, queryDetail, DEFAULT_PAGE_SIZE } from '@/api/metrics'
import { convertFileSize } from '@/utils/file'

export default {
  data() {
    return {
      metricSelect: '',
      tableData: [],
      options: [],
      total: 0,
      metricQuery: {
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
      querySelectOption().then(res => {
        this.options = res.data
      })
    },
    queryDetail(pageNum) {
      const path = this.metricSelect
      this.queryPage(path, pageNum)
    },
    handleCurrentChange(val) {
      this.currentPage = val
      const path = this.metricSelect
      this.queryPage(path, val)
    },
    queryPage(path, pageNum) {
      queryDetail(path, pageNum).then(res => {
        for (let i = 0; i < res.data.records.length; i++) {
          res.data.records[i].totalSize = convertFileSize(res.data.records[i].totalSize)
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
