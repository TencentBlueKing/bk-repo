<template>
  <div class="app-container">
    <el-row :gutter="12" class="summary-row">
      <el-col :span="8">
        <el-card shadow="never">
          <div class="summary-label">REMOTE 节点总数</div>
          <div class="summary-value">{{ summary.totalNodes }}</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <div class="summary-label">活跃任务总数</div>
          <div class="summary-value">{{ summary.activeTaskCount }}</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <div class="summary-label">已完成任务数</div>
          <div class="summary-value">{{ summary.completedTaskCount }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-form :inline="true" size="small" class="filter-form">
      <el-form-item label="关键字">
        <el-input v-model="query.keyword" placeholder="按节点名/URL 模糊搜索" clearable style="width: 240px" @keyup.enter.native="reload" />
      </el-form-item>
      <el-form-item label="排序">
        <el-select v-model="query.sortBy" style="width: 160px">
          <el-option label="最近使用时间" value="LAST_USED_TIME" />
          <el-option label="关联任务数" value="TASK_COUNT" />
          <el-option label="节点名称" value="NAME" />
        </el-select>
        <el-select v-model="query.sortOrder" style="width: 90px; margin-left: 6px">
          <el-option label="降序" value="desc" />
          <el-option label="升序" value="asc" />
        </el-select>
      </el-form-item>
      <el-form-item label="时间范围">
        <el-select v-model="timeFilter" style="width: 180px" @change="onTimeFilterChange">
          <el-option label="不限" value="" />
          <el-option label="最近 7 天使用过" value="7d_used" />
          <el-option label="最近 30 天使用过" value="30d_used" />
          <el-option label="超过 90 天未使用" value="90d_unused" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" @click="reload">查询</el-button>
        <el-button icon="el-icon-refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="page.records" border stripe @row-click="onRowClick">
      <el-table-column prop="name" label="节点名称" min-width="200" />
      <el-table-column prop="url" label="URL" min-width="240" show-overflow-tooltip />
      <el-table-column prop="taskCount" label="关联任务数" width="120" align="center" />
      <el-table-column prop="activeTaskCount" label="活跃任务" width="100" align="center" />
      <el-table-column prop="lastUsedTime" label="最近使用时间" width="180">
        <template slot-scope="{ row }">{{ row.lastUsedTime || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="120">
        <template slot-scope="{ row }">
          <el-button size="mini" type="text" @click.stop="openTasks(row)">查看任务</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="pagination"
      :current-page.sync="query.pageNumber"
      :page-size.sync="query.pageSize"
      :total="page.total"
      :page-sizes="[20, 50, 100]"
      layout="total, sizes, prev, pager, next, jumper"
      @size-change="reload"
      @current-change="reload"
    />

    <el-dialog :visible.sync="taskDialogVisible" :title="`节点关联任务：${currentNode}`" width="80%" top="6vh">
      <el-table v-loading="taskLoading" :data="taskList" border stripe>
        <el-table-column prop="key" label="任务 Key" min-width="180" show-overflow-tooltip />
        <el-table-column prop="name" label="任务名" min-width="160" show-overflow-tooltip />
        <el-table-column prop="projectId" label="项目" width="160" />
        <el-table-column prop="replicaType" label="同步类型" width="120" />
        <el-table-column prop="enabled" label="启用" width="80" align="center">
          <template slot-scope="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="mini">{{ row.enabled ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastExecutionStatus" label="上次状态" width="120" />
        <el-table-column prop="createdDate" label="创建时间" width="180" />
        <el-table-column prop="lastExecutionTime" label="最近执行" width="180" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script>
import { pageRemoteNodes, getRemoteSummary, getRemoteNodeTasks } from '@/api/topology'

export default {
  name: 'RemoteNodes',
  data() {
    return {
      loading: false,
      summary: { totalNodes: 0, activeTaskCount: 0, completedTaskCount: 0 },
      page: { pageNumber: 1, pageSize: 20, total: 0, records: [] },
      query: {
        keyword: '',
        sortBy: 'LAST_USED_TIME',
        sortOrder: 'desc',
        pageNumber: 1,
        pageSize: 20,
        lastUsedAfter: null,
        lastUsedBefore: null
      },
      timeFilter: '',
      taskDialogVisible: false,
      taskLoading: false,
      currentNode: '',
      taskList: []
    }
  },
  mounted() {
    this.loadSummary()
    this.reload()
  },
  methods: {
    async loadSummary() {
      try {
        const resp = await getRemoteSummary()
        this.summary = resp.data || resp || this.summary
      } catch (e) { /* ignore */ }
    },
    async reload() {
      this.loading = true
      try {
        const resp = await pageRemoteNodes(this.query)
        const data = resp.data || resp
        this.page = data || this.page
      } catch (e) {
        this.$message.error('查询失败')
      } finally {
        this.loading = false
      }
    },
    resetQuery() {
      this.query = {
        keyword: '',
        sortBy: 'LAST_USED_TIME',
        sortOrder: 'desc',
        pageNumber: 1,
        pageSize: 20,
        lastUsedAfter: null,
        lastUsedBefore: null
      }
      this.timeFilter = ''
      this.reload()
    },
    onTimeFilterChange(val) {
      const now = new Date()
      const fmt = (d) => {
        const pad = (n) => String(n).padStart(2, '0')
        return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
      }
      if (val === '7d_used') {
        this.query.lastUsedAfter = fmt(new Date(now.getTime() - 7 * 24 * 3600 * 1000))
        this.query.lastUsedBefore = null
      } else if (val === '30d_used') {
        this.query.lastUsedAfter = fmt(new Date(now.getTime() - 30 * 24 * 3600 * 1000))
        this.query.lastUsedBefore = null
      } else if (val === '90d_unused') {
        this.query.lastUsedAfter = null
        this.query.lastUsedBefore = fmt(new Date(now.getTime() - 90 * 24 * 3600 * 1000))
      } else {
        this.query.lastUsedAfter = null
        this.query.lastUsedBefore = null
      }
      this.reload()
    },
    onRowClick(row) {
      this.openTasks(row)
    },
    async openTasks(row) {
      this.currentNode = row.name
      this.taskDialogVisible = true
      this.taskLoading = true
      this.taskList = []
      try {
        const resp = await getRemoteNodeTasks(row.name)
        this.taskList = resp.data || resp || []
      } catch (e) {
        this.$message.error('查询任务列表失败')
      } finally {
        this.taskLoading = false
      }
    }
  }
}
</script>

<style scoped>
.summary-row {
  margin-bottom: 12px;
}
.summary-label {
  color: #909399;
  font-size: 13px;
}
.summary-value {
  font-size: 22px;
  font-weight: 600;
  margin-top: 4px;
  color: #303133;
}
.filter-form {
  margin-bottom: 12px;
}
.pagination {
  margin-top: 12px;
  text-align: right;
}
</style>
