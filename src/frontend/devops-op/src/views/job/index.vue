<template>
  <div class="app-container">
    <el-table
      :data="Jobs"
      style="width: 100%"
    >
      <el-table-column
        prop="name"
        label="任务名称"
        width="180"
      />
      <el-table-column
        label="是否启用"
        width="100"
      >
        <template slot-scope="scope">
          <el-switch
            v-model="scope.row.enabled"
            @change="enablechange(scope.row.enabled)"
          />
        </template>
      </el-table-column>
      <el-table-column
        prop="cron"
        label="cron表达式"
        width="180"
      />
      <el-table-column
        prop="fixedDelay"
        label="fixedDelay"
        width="130"
      />
      <el-table-column
        prop="fixedRate"
        label="fixedRate"
        width="130"
      />
      <el-table-column
        prop="initialDelay"
        label="initialDelay"
        width="130"
      />
      <el-table-column
        label="运行中"
        width="100"
      >
        <template slot-scope="scope">
          <el-switch
            v-model="scope.row.running"
            disabled
          />
        </template>
      </el-table-column>
      <el-table-column
        prop="lastBeginTime"
        label="上一次开始时间"
        width="200"
      />
      <el-table-column
        prop="lastEndTime"
        label="上一次结束时间"
        width="200"
      />
      <el-table-column
        prop="nextExecuteTime"
        label="下一次执行时间"
        width="200"
      />
      <el-table-column
        prop="lastExecuteTime"
        label="上一次执行耗时"
        width="120"
      />
    </el-table>
  </div>
</template>

<script>

import { jobs } from '@/api/job'
import moment from 'moment'

export default {
  name: 'Jobs',
  data() {
    return {
      jobs: []
    }
  },
  created() {
    jobs().then(res => {
      for (let i = 0; i < res.data.length; i++) {
        res.data[i].lastBeginTime = res.data[i].lastBeginTime != null ? moment(res.data[i].lastBeginTime).format('YYYY-MM-DD HH:mm:ss') : null
        res.data[i].lastEndTime = res.data[i].lastEndTime != null ? moment(res.data[i].lastEndTime).format('YYYY-MM-DD HH:mm:ss') : null
        res.data[i].nextExecuteTime = res.data[i].nextExecuteTime != null ? moment(res.data[i].nextExecuteTime).format('YYYY-MM-DD HH:mm:ss') : null
      }
      this.jobs = res.data
    })
  },
  methods: {
    enablechange(enabled) {
      console.log(enabled)
    }
  }
}
</script>

<style scoped>

</style>
