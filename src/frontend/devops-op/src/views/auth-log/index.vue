<template>
  <div class="app-container">
    <el-form ref="form" :inline="true">
      <el-form-item style="margin-left: 15px" label="日期" prop="associationId">
        <el-date-picker
          v-model="authLogQuery.startTime"
          type="datetime"
          placeholder="选择起始日期时间"
          default-time="00:00:00"
        />
        <span> - </span>
        <el-date-picker
          v-model="authLogQuery.endTime"
          type="datetime"
          placeholder="选择截止日期时间"
          default-time="23:59:59"
        />
      </el-form-item>
      <el-form-item ref="project-form-item" label="操作用户">
        <el-input
          v-model="authLogQuery.userId"
          class="inline-input"
          placeholder="请输入用户ID"
        />
      </el-form-item>
      <el-form-item>
        <el-button size="mini" type="primary" @click="queryPage(1)">查询</el-button>
      </el-form-item>
    </el-form>
    <div class="app">
      <el-table v-loading="loading" :data="tableData" style="width: 100%;">
        <el-table-column prop="nodeNum" label="操作时间" width="250" header-align="center" align="center">
          <template slot-scope="scope">
            <span>{{ formatNormalDate(scope.row.createdDate) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="userId" label="操作用户" width="100" header-align="center" align="center" />
        <el-table-column prop="operate" label="操作事件" width="600" header-align="center" align="center" />
        <el-table-column prop="description" label="操作内容" width="600" header-align="center" align="center" />
        <el-table-column prop="clientAddress" label="客户端IP" header-align="center" align="center" />
      </el-table>
    </div>
    <div v-if="total > 0" style="margin-top:20px">
      <el-pagination
        :current-page="authLogQuery.pageNumber"
        :page-size="authLogQuery.pageSize"
        layout="total, prev, pager, next, jumper"
        :total="total"
        @current-change="handleCurrentChange"
      />
    </div>
  </div>
</template>

<script>
import { DEFAULT_PAGE_SIZE } from '@/api/auth-log'
import { searchProjects } from '@/api/project'
import { page } from '@/api/auth-log'
import { formatNormalDate } from '@/utils/date'

export default {
  data() {
    return {
      projectSelect: '',
      loading: false,
      repoSelect: '',
      tableData: [],
      projectOptions: [],
      repoOptions: [],
      total: 0,
      authLogQuery: {
        path: '',
        userId: '',
        pageNumber: 1,
        pageSize: DEFAULT_PAGE_SIZE,
        projectId: '',
        startTime: '',
        endTime: ''
      }
    }
  },
  mounted() {
    this.onRouteUpdate(this.$route)
  },
  beforeRouteUpdate(to, from, next) {
    this.onRouteUpdate(to)
    next()
  },
  methods: {
    handleCurrentChange(val) {
      this.currentPage = val
      this.queryPage(val)
    },
    queryPage(pageNum) {
      const query = {
        page: String(pageNum)
      }
      query.userId = this.authLogQuery.userId
      query.startTime = this.authLogQuery.startTime
      query.endTime = this.authLogQuery.endTime
      this.$router.push({ path: '/auth-log', query: query })
    },
    onRouteUpdate(route) {
      const query = route.query
      const authLogQuery = this.authLogQuery
      authLogQuery.projectId = query.projectId ? query.projectId : ''
      authLogQuery.pageNumber = query.page ? Number(query.page) : 1
      if (authLogQuery.startTime === '') authLogQuery.startTime = null
      if (authLogQuery.endTime === '') authLogQuery.endTime = null
      if (authLogQuery.projectId === '') authLogQuery.projectId = null
      if (authLogQuery.userId === '') authLogQuery.userId = null
      this.$nextTick(() => {
        this.queryAuthLog(authLogQuery)
      })
    },
    queryAuthLog(authLogQuery) {
      this.loading = true
      let promise = null
      promise = page(authLogQuery.pageNumber, authLogQuery.pageSize, authLogQuery.projectId, authLogQuery.startTime, authLogQuery.endTime, authLogQuery.userId)
      promise.then(res => {
        for (let i = 0; i < res.data.records.length; i++) {
          const des = JSON.stringify(res.data.records[i].content.des).replace(/\\"/g, '"').replace(/(?:\\[rn])+/g, '').replaceAll('\\\\','')
          res.data.records[i].description = des
        }
        this.tableData = res.data.records
        this.total = res.data.totalRecords
      }).catch(_ => {
        this.tableData = []
        this.total = 0
      }).finally(() => {
        this.loading = false
      })
    },
    formatNormalDate(data) {
      return formatNormalDate(data)
    }
  }
}

</script>

<style scoped>

</style>
