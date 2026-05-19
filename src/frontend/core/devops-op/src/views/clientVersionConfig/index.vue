<template>
  <div class="app-container">
    <div class="filter-container">
      <el-input
        v-model="listQuery.productId"
        placeholder="制品产品线 productId，留空查全部"
        style="width: 280px;"
        class="filter-item"
        clearable
        @keyup.enter.native="handleFilter"
      />
      <el-button class="filter-item" type="primary" icon="el-icon-search" @click="handleFilter">
        查询
      </el-button>
      <el-button class="filter-item" type="primary" icon="el-icon-plus" @click="handleCreate">
        新增
      </el-button>
      <el-button class="filter-item" type="success" icon="el-icon-document-copy" @click="batchDialogVisible = true">
        批量新增
      </el-button>
      <el-button
        class="filter-item"
        type="danger"
        icon="el-icon-delete"
        :disabled="selectedIds.length === 0"
        @click="handleBatchDelete"
      >
        批量删除{{ selectedIds.length ? `（${selectedIds.length}）` : '' }}
      </el-button>
    </div>

    <el-table
      v-loading="listLoading"
      :data="list"
      border
      fit
      highlight-current-row
      style="width: 100%;"
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="45" align="center" />
      <el-table-column label="产品线" prop="productId" min-width="120" align="center" />
      <el-table-column label="平台" prop="platform" width="100" align="center" />
      <el-table-column label="架构" prop="arch" width="90" align="center">
        <template slot-scope="{ row }">
          <span>{{ row.arch || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="目标用户" min-width="120" align="center" show-overflow-tooltip>
        <template slot-scope="{ row }">
          <span>{{ row.targetUserId || '全员' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="最低版本" prop="minVersion" width="110" align="center" show-overflow-tooltip />
      <el-table-column label="最新版本" prop="latestVersion" width="110" align="center" />
      <el-table-column label="下载地址" prop="downloadUrl" min-width="160" align="center" show-overflow-tooltip />
      <el-table-column label="启用" width="72" align="center">
        <template slot-scope="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" size="mini">{{ row.enabled ? '是' : '否' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="更新人" prop="lastModifiedBy" width="100" align="center" />
      <el-table-column label="更新时间" min-width="150" align="center">
        <template slot-scope="{ row }">
          <span>{{ row.lastModifiedDate | parseTime }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" align="center" fixed="right">
        <template slot-scope="{ row }">
          <el-button type="primary" size="mini" @click="handleUpdate(row)">编辑</el-button>
          <el-button type="danger" size="mini" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      style="margin-top: 15px"
      background
      layout="total, sizes, prev, pager, next, jumper"
      :current-page="listQuery.pageNumber"
      :page-size="listQuery.pageSize"
      :page-sizes="[10, 20, 50, 100]"
      :hide-on-single-page="true"
      :total="total"
      @current-change="handlePageChange"
      @size-change="handleSizeChange"
    />

    <client-version-config-dialog
      :create-mode="dialogCreateMode"
      :visible.sync="dialogVisible"
      :record="editingRecord"
      @created="getList"
      @updated="getList"
    />

    <client-version-config-batch-dialog
      :visible.sync="batchDialogVisible"
      @created="getList"
    />
  </div>
</template>

<script>
import {
  listClientVersionConfigs,
  deleteClientVersionConfig,
  batchDeleteClientVersionConfig
} from '@/api/clientVersionConfig'
import { formatApiDateTime } from '@/utils/date'
import ClientVersionConfigDialog from './components/ClientVersionConfigDialog'
import ClientVersionConfigBatchDialog from './components/ClientVersionConfigBatchDialog'

const BATCH_LIMIT = 50

export default {
  name: 'ClientVersionConfig',
  components: { ClientVersionConfigDialog, ClientVersionConfigBatchDialog },
  filters: {
    parseTime(time) {
      return formatApiDateTime(time)
    }
  },
  data() {
    return {
      list: [],
      total: 0,
      listLoading: false,
      listQuery: {
        productId: '',
        pageNumber: 1,
        pageSize: 20
      },
      dialogVisible: false,
      dialogCreateMode: true,
      editingRecord: {},
      batchDialogVisible: false,
      selectedIds: []
    }
  },
  mounted() {
    this.getList()
  },
  methods: {
    getList() {
      this.listLoading = true
      const pid = (this.listQuery.productId || '').trim()
      listClientVersionConfigs(pid || undefined, this.listQuery.pageNumber, this.listQuery.pageSize)
        .then(res => {
          const data = res.data || {}
          this.list = data.records || []
          this.total = data.totalRecords || 0
        })
        .finally(() => {
          this.listLoading = false
        })
    },
    handleFilter() {
      this.listQuery.pageNumber = 1
      this.getList()
    },
    handlePageChange(pageNumber) {
      this.listQuery.pageNumber = pageNumber
      this.getList()
    },
    handleSizeChange(pageSize) {
      this.listQuery.pageSize = pageSize
      this.listQuery.pageNumber = 1
      this.getList()
    },
    handleCreate() {
      this.dialogCreateMode = true
      this.editingRecord = {}
      this.dialogVisible = true
    },
    handleUpdate(row) {
      this.dialogCreateMode = false
      this.editingRecord = { ...row }
      this.dialogVisible = true
    },
    handleSelectionChange(rows) {
      this.selectedIds = rows.filter(r => r.id).map(r => r.id)
    },
    handleDelete(row) {
      if (!row.id) {
        return
      }
      this.$confirm('确认删除该版本配置？', '提示', { type: 'warning' })
        .then(() => deleteClientVersionConfig(row.id))
        .then(() => {
          this.$message.success('已删除')
          this.getList()
        })
        .catch(() => {})
    },
    handleBatchDelete() {
      if (this.selectedIds.length === 0) return
      if (this.selectedIds.length > BATCH_LIMIT) {
        this.$message.warning(`批量删除最多支持 ${BATCH_LIMIT} 条`)
        return
      }
      this.$confirm(
        `确认删除选中的 ${this.selectedIds.length} 条版本配置？`,
        '提示',
        { type: 'warning' }
      )
        .then(() => batchDeleteClientVersionConfig(this.selectedIds))
        .then(() => {
          this.$message.success(`已删除 ${this.selectedIds.length} 条`)
          this.selectedIds = []
          this.getList()
        })
        .catch(() => {})
    }
  }
}
</script>
