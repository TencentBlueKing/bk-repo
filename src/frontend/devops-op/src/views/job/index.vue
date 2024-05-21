<template>
  <div class="app-container">
    <el-form ref="form" :inline="true">
      <el-form-item ref="project-form-item" label="任务名称">
        <el-input v-model="search" size="small" />
      </el-form-item>
      <el-form-item ref="project-form-item" label="更多显示" style="margin-left: 50px">
        <el-select
          v-model="displayColumns"
          multiple
          collapse-tags
          style="margin-left: 20px;"
          placeholder="请选择"
          :change="filterDisplayFunction(displayColumns)"
        >
          <el-option
            v-for="item in options"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
    </el-form>
    <el-table
      ref="jobTable"
      v-loading="loading"
      :data="jobs.filter(data => !search || data.name.toLowerCase().includes(search.toLowerCase()))"
      style="width: 100%; margin-top: -20px"
      :max-height="825"
    >
      <el-table-column
        prop="name"
        fixed
        label="任务名称"
        width="280"
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
        :filters="[{ text: '是', value: true }, { text: '否', value: false }]"
        :filter-method="filterFunction"
      >
        <template slot-scope="scope">
          {{ scope.row.running ? "是":"否" }}
        </template>
      </el-table-column>
      <el-table-column
        prop="lastBeginTime"
        label="上一次开始时间"
        width="160"
      />
      <el-table-column
        prop="lastEndTime"
        label="上一次结束时间"
        width="160"
      />
      <el-table-column
        v-if="hiddenColumn.nextExecuteTime"
        prop="nextExecuteTime"
        label="下一次执行时间"
        width="160"
      />
      <el-table-column
        v-if="hiddenColumn.lastExecuteTime"
        prop="lastExecuteTime"
        label="上一次执行耗时"
        align="center"
        width="170"
      />
      <el-table-column
        label="操作"
        align="center"
        fixed="right"
      >
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
import { formatNormalDate, roughCalculateTime } from '@/utils/date'

export default {
  name: 'Jobs',
  data() {
    return {
      loading: false,
      jobs: [],
      search: '',
      hiddenColumn: {
        nextExecuteTime: false,
        lastExecuteTime: false
      },
      displayColumns: [],
      options: [
        {
          value: 'nextExecuteTime',
          label: '下一次执行时间'
        },
        {
          value: 'lastExecuteTime',
          label: '上一次执行耗时'
        }
      ]
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
      this.loading = true
      update(job.name, job.enabled, job.running).then(() => {
        let jobName = 'job.'
        if (job.name.endsWith('Job')) {
          jobName = jobName + job.name.substr(0, job.name.length - 3)
        } else {
          jobName = jobName + job.name
        }
        const key = (job.jobConfigName !== '' ? job.jobConfigName : jobName) + '.enabled'
        const values = [{
          'key': key,
          'value': job.enabled
        }]
        updateConfig(values, 'job').finally(() => {
          this.loading = false
          this.query()
        })
      })
    },
    changeRunning(job) {
      this.loading = true
      update(job.name, job.enabled, !job.running).then(() => {
        const message = job.running !== true ? '启动成功' : '停止成功'
        this.$message({
          message: message,
          type: 'success'
        })
      }).finally(() => {
        this.loading = false
        this.query()
      })
    },
    query() {
      jobs().then(res => {
        for (let i = 0; i < res.data.length; i++) {
          res.data[i].lastBeginTime = formatNormalDate(res.data[i].lastBeginTime)
          res.data[i].lastEndTime = formatNormalDate(res.data[i].lastEndTime)
          res.data[i].nextExecuteTime = formatNormalDate(res.data[i].nextExecuteTime)
          res.data[i].lastExecuteTime = res.data[i].lastExecuteTime != null ? roughCalculateTime(res.data[i].lastExecuteTime) : null
        }
        this.jobs = res.data
      })
    },
    filterFunction(value, row) {
      return row.running === value
    },
    filterDisplayFunction(value) {
      if (value.length !== 0) {
        for (let i = 0; i < value.length; i++) {
          if (Object.keys(this.hiddenColumn).some(name => name === value[i])) {
            this.hiddenColumn[value[i]] = true
          }
        }
        for (let i = 0; i < Object.keys(this.hiddenColumn).length; i++) {
          if (!value.some(name => name === Object.keys(this.hiddenColumn)[i])) {
            this.hiddenColumn[Object.keys(this.hiddenColumn)[i]] = false
          }
        }
      } else {
        for (let i = 0; i < Object.keys(this.hiddenColumn).length; i++) {
          this.hiddenColumn[Object.keys(this.hiddenColumn)[i]] = false
        }
      }
    }
  }
}
</script>

<style scoped>

</style>
