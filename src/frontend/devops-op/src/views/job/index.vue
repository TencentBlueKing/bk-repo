<template>
  <div class="app-container">
    <el-table
      :data="jobs"
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
          <el-tooltip :disabled="!community" effect="dark" content="容器环境暂不支持动态启用任务" placement="top">
            <el-switch v-model="scope.row.enabled" :disabled="community" @change="changeEnabled(scope.row)" />
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column
        prop="cron"
        label="cron表达式"
        width="150"
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
          {{ scope.row.running ? "是":"否" }}
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
      <el-table-column label="操作" min-width="120px">
        <template slot-scope="scope">
          <template v-if="scope.row.running && scope.row.enabled">
            <el-button size="mini" type="primary" @click="changeRunning(scope.row)">停止</el-button>
          </template>
          <template v-if="!scope.row.running && scope.row.enabled">
            <el-button size="mini" type="primary" @click="changeRunning(scope.row)">启动</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script>

import { jobs, update } from '@/api/job'
import { updateConfig } from '@/api/config'
import { formatNormalDate } from '@/utils/date'

export default {
  name: 'Jobs',
  data() {
    return {
      jobs: []
    }
  },
  computed: {
    community() {
      return process.env.VUE_APP_RELEASE_MODE === 'community'
    }
  },
  created() {
    this.query()
  },
  methods: {
    changeEnabled(job) {
      update(job.name, job.enabled, job.running).then(() => {
        this.query()
      })
      let jobName
      if (job.name.endsWith('Job')) {
        jobName = job.name.substr(0, job.name.length - 3)
      } else {
        jobName = job.name
      }
      const key = 'job.' + jobName + '.enabled'
      const values = [{
        'key': key,
        'value': job.enabled
      }]
      updateConfig(values, 'job')
    },
    changeRunning(job) {
      const message = job.running !== true ? '启动成功' : '停止成功'
      this.$message({
        message: message,
        type: 'success'
      })
      update(job.name, job.enabled, !job.running).then(() => {
        this.query()
      })
    },
    query() {
      jobs().then(res => {
        for (let i = 0; i < res.data.length; i++) {
          res.data[i].lastBeginTime = formatNormalDate(res.data[i].lastBeginTime)
          res.data[i].lastEndTime = formatNormalDate(res.data[i].lastEndTime)
          res.data[i].nextExecuteTime = formatNormalDate(res.data[i].nextExecuteTime)
          res.data[i].lastExecuteTime = res.data[i].lastExecuteTime != null ? res.data[i].lastExecuteTime + '毫秒' : null
        }
        this.jobs = res.data
      })
    }
  }
}
</script>

<style scoped>

</style>
