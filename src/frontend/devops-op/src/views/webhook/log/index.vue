<template>
  <div class="app-container node-container">
    <el-form ref="form" :inline="true" :model="webhookLogQuery" :rules="queryRule">
      <el-form-item label="webhook id" prop="id">
        <el-input v-model="webhookLogQuery.id" placeholder="请输入webhook id" />
      </el-form-item>
      <el-form-item style="margin-left: 15px" label="日期" prop="associationId">
        <el-date-picker
          v-model="webhookLogQuery.dateTimeRange"
          type="datetimerange"
          :picker-options="pickerOptions"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="yyyy-MM-ddTHH:mm:ss"
          default
          align="right"
        />
      </el-form-item>
      <el-form-item>
        <el-button size="mini" type="primary" @click="changeRouteQueryParams()">查询</el-button>
      </el-form-item>
    </el-form>
    <el-table v-loading="loading" :data="logs" style="width: 100%">
      <el-table-column prop="webHookUrl" label="url" min-width="200px" />
      <el-table-column prop="requestHeaders" label="请求头" min-width="200px" />
      <el-table-column prop="triggeredEvent" label="触发事件" min-width="200px" />
      <el-table-column prop="requestPayload" label="请求体" min-width="200px" />
      <el-table-column prop="status" label="状态" min-width="100px" />
      <el-table-column prop="responseHeaders" label="响应头" />
      <el-table-column prop="responseBody" label="响应体" />
      <el-table-column prop="requestDuration" label="请求耗时" />
      <el-table-column prop="requestTime" label="请求时间" />
      <el-table-column prop="errorMsg" label="错误信息" />
    </el-table>
    <el-pagination
      style="margin-top: 15px"
      background
      layout="prev, pager, next"
      :current-page.sync="webhookLogQuery.pageNumber"
      :page-size="webhookLogQuery.pageSize"
      :hide-on-single-page="true"
      :total="total"
      @current-change="changeRouteQueryParams()"
    />
  </div>
</template>
<script>
import { listWebhookLog } from '@/api/webhook'
import { formatDate } from '@/utils/file'

export default {
  name: 'WebHookLog',
  data() {
    return {
      queryRule: {
        id: [{ required: true, message: '请输入webhook id', trigger: 'blur' }]
      },
      pickerOptions: {
        shortcuts: [{
          text: '最近一周',
          onClick(picker) {
            const end = new Date()
            const start = new Date()
            start.setTime(start.getTime() - 3600 * 1000 * 24 * 7)
            picker.$emit('pick', [start, end])
          }
        }, {
          text: '最近一个月',
          onClick(picker) {
            const end = new Date()
            const start = new Date()
            start.setTime(start.getTime() - 3600 * 1000 * 24 * 30)
            picker.$emit('pick', [start, end])
          }
        }]
      },
      loading: false,
      webhookLogQuery: {
        id: '',
        dateTimeRange: []
      },
      logs: [],
      total: 0
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
    formatDate(date) {
      return formatDate(date)
    },
    queryModeChanged() {
      this.$refs['form'].clearValidate()
    },
    changeRouteQueryParams() {
      this.$refs['form'].validate((valid) => {
        if (valid) {
          this.$router.push({ path: '/webhook/log', query: this.webhookLogQuery })
        }
      })
    },
    onRouteUpdate(route) {
      this.$nextTick(() => {
        this.queryWebHookLog(route.query)
      })
    },
    queryWebHookLog(webhookLogQuery) {
      console.log('query', webhookLogQuery)
      if (!webhookLogQuery.id) {
        return
      }
      this.loading = true
      const promise = listWebhookLog(
        webhookLogQuery.id,
        webhookLogQuery.dateTimeRange ? webhookLogQuery.dateTimeRange[0] : new Date(new Date().getTime() - 3600 * 1000 * 24 * 7),
        webhookLogQuery.dateTimeRange ? webhookLogQuery.dateTimeRange[1] : new Date()
      )
      promise.then(res => {
        console.log(res)
        this.logs = res.data.records
        this.total = res.data.records.length
      }).catch(_ => {
        this.logs = []
        this.total = 0
      }).finally(() => {
        this.loading = false
      })
    }
  }
}
</script>

<style scoped>

</style>
