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
      <el-table-column label="操作">
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="danger"
            @click="keyHandleDelete(scope.row, scope.$index)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script>

import { listShedlock, deleteShelock } from '@/api/shed-lock'
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
    },
    keyHandleDelete(row, index) {
      this.$confirm(`是否确定删除`, '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        deleteShelock(row.id).then(() => {
          this.shedlocks.splice(index, 1)
          this.$message.success('删除成功')
        })
      }).catch(() => {
        this.$message({
          type: 'info',
          message: '已取消'
        })
      })
    }
  }
}
</script>

<style scoped>

</style>
