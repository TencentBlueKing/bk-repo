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
          <el-switch
            v-model="scope.row.enabled"
            @change="changeEnabled(scope.row.enabled, scope.row.name)"
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
        <template v-if="scope.row.runnig === true" slot-scope="scope">
          <label>是</label>
        </template>
        <template v-if="scope.row.runnig !== true" slot-scope="scope">
          <label>否</label>
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

import { jobs, update } from '@/api/job'
import { formatNormalDate } from '@/utils/date'

export default {
  name: 'Jobs',
  data() {
    return {
      jobs: []
    }
  },
  created() {
    this.query()
  },
  methods: {
    changeEnabled(enabled, name) {
      update(name, enabled)
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
