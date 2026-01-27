<template>
  <div class="app-container node-container">
    <el-form ref="form" :inline="true" :model="webhookLogQuery" :rules="queryRule">
      <el-form-item label="webhook id" prop="id">
        <el-input v-model="webhookLogQuery.id" placeholder="请输入webhook id" />
      </el-form-item>
      <el-form-item style="margin-left: 15px" label="日期" prop="associationId">
        <el-date-picker
          v-model="webhookLogQuery.startTime"
          value-format="yyyy-MM-ddTHH:mm:ss"
          type="datetime"
          placeholder="选择起始日期时间"
          default-time="00:00:00"
        />
        <span> - </span>
        <el-date-picker
          v-model="webhookLogQuery.endTime"
          value-format="yyyy-MM-ddTHH:mm:ss"
          type="datetime"
          placeholder="选择截止日期时间"
          default-time="23:59:59"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="webhookLogQuery.status" placeholder="请选择关联对象类型">
          <el-option label="ALL" value="" />
          <el-option label="SUCCESS" value="SUCCESS" />
          <el-option label="FAIL" value="FAIL" />
          <el-option label="ERROR" value="ERROR" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button size="mini" type="primary" @click="changeRouteQueryParams()">查询</el-button>
      </el-form-item>
    </el-form>
    <el-table v-loading="loading" :data="logs" style="width: 100%">
      <el-table-column prop="webHookUrl" label="url" min-width="200px" />
      <el-table-column prop="requestHeaders" label="请求头" min-width="200px" />
      <el-table-column prop="triggeredEvent" label="触发事件" min-width="200px" />
      <el-table-column prop="requestPayload" label="请求体" min-width="400px" />
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
import moment from 'moment'

export default {
  name: 'WebHookLog',
  beforeRouteUpdate(to, from, next) {
    this.onRouteUpdate(to)
    next()
  },
  data() {
    return {
      queryRule: {
        id: [{ required: true, message: '请输入webhook id', trigger: 'blur' }]
      },
      loading: false,
      webhookLogQuery: {
        id: '',
        startTime: '',
        endTime: '',
        status: ''
      },
      logs: [],
      total: 0
    }
  },
  mounted() {
    this.onRouteUpdate(this.$route)
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
          this.$router.push({ path: '/system/webhook/log', query: this.webhookLogQuery })
        }
      })
    },
    onRouteUpdate(route) {
      this.webhookLogQuery.id = route.query.id
      this.webhookLogQuery.startTime = route.query.startTime
      this.webhookLogQuery.endTime = route.query.endTime
      this.$nextTick(() => {
        this.queryWebHookLog(route.query)
      })
    },
    queryWebHookLog(webhookLogQuery) {
      if (!webhookLogQuery.id) {
        return
      }
      this.loading = true
      const promise = listWebhookLog(
        webhookLogQuery.id,
        webhookLogQuery.startTime ? webhookLogQuery.startTime : moment(new Date() - 3600 * 1000 * 24 * 7).format('YYYY-MM-DDTHH:mm:ss'),
        webhookLogQuery.endTime ? webhookLogQuery.endTime : moment(new Date()).format('YYYY-MM-DDTHH:mm:ss'),
        webhookLogQuery.status
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
