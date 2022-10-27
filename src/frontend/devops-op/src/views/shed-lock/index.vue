<template>
  <div class="app-container">
    <el-table
      :data="shedlocks"
      style="width: 100%"
    >
      <el-table-column
        prop="id"
        label="名称"
      />
      <el-table-column
        prop="lockUntil"
        label="释放时间"
        sortable
      >
        <template slot-scope="scope">
          <span>{{ formatNormalDate(scope.row.lockUntil) }}</span>
        </template>
      </el-table-column>
      <el-table-column
        prop="lockedAt"
        label="锁定时间"
        sortable
      >
        <template slot-scope="scope">
          <span>{{ formatNormalDate(scope.row.lockedAt) }}</span>
        </template>
      </el-table-column>
      <el-table-column
        prop="lockedBy"
        label="锁定者"
      />
    </el-table>
  </div>
</template>

<script>

import { listShedlock } from '@/api/shed-lock'
import { formatNormalDate } from '@/utils/date'
export default {
  name: 'Service',
  data() {
    return {
      shedlocks: []
    }
  },
  created() {
    listShedlock().then(res => {
      this.shedlocks = res.data
    })
  },
  methods: {
    formatNormalDate(data) {
      return formatNormalDate(data)
    }
  }
}
</script>

<style scoped>

</style>
