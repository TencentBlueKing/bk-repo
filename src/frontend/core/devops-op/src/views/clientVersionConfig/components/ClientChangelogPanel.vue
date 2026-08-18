<template>
  <div class="changelog-panel">
    <el-tabs v-model="activeProductKey" type="card" class="product-tabs" @tab-click="handleProductTabChange">
      <el-tab-pane
        v-for="p in productPresets"
        :key="p.key"
        :label="p.label"
        :name="p.key"
      />
    </el-tabs>

    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="query" size="small" class="filter-form">
        <el-form-item label="版本号">
          <el-input
            v-model="query.version"
            placeholder="精确匹配"
            clearable
            style="width: 160px"
            @keyup.enter.native="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 120px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已发布" value="PUBLISHED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="resetFilter">重置</el-button>
        </el-form-item>
        <el-form-item style="float:right;">
          <el-button type="primary" icon="el-icon-plus" @click="handleCreate">新增更新日志</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table
        v-loading="loading"
        :data="records"
        border
        size="small"
        :default-sort="tableDefaultSort"
        @sort-change="handleSortChange"
        @row-dblclick="handleEdit"
      >
        <el-table-column label="版本号" prop="version" width="140" align="center" sortable="custom" />
        <el-table-column
          label="发布日期"
          prop="releasedAt"
          width="130"
          align="center"
          sortable="custom"
        />
        <el-table-column label="状态" prop="status" width="90" align="center" sortable="custom">
          <template slot-scope="{ row }">
            <el-tag :type="row.status === 'PUBLISHED' ? 'success' : 'info'" size="mini">
              {{ row.status === 'PUBLISHED' ? '已发布' : '草稿' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="正文摘要" prop="releaseNotes" min-width="240" show-overflow-tooltip>
          <template slot-scope="{ row }">
            <span class="notes-snippet">{{ buildSnippet(row.releaseNotes) }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="更新人"
          prop="lastModifiedBy"
          width="120"
          align="center"
          show-overflow-tooltip
        />
        <el-table-column
          label="更新时间"
          prop="lastModifiedDate"
          min-width="160"
          align="center"
          sortable="custom"
        >
          <template slot-scope="{ row }">
            <span>{{ row.lastModifiedDate | parseTime }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" align="center" fixed="right">
          <template slot-scope="{ row }">
            <div class="row-actions">
              <el-button type="text" size="small" @click="handlePreview(row)">查看</el-button>
              <el-button type="text" size="small" @click="handleEdit(row)">编辑</el-button>
              <el-button
                v-if="row.status === 'DRAFT'"
                type="text"
                size="small"
                class="btn-success-text"
                @click="handleToggleStatus(row, 'PUBLISHED')"
              >
                发布
              </el-button>
              <el-button
                v-else
                type="text"
                size="small"
                class="btn-warning-text"
                @click="handleToggleStatus(row, 'DRAFT')"
              >
                下架
              </el-button>
              <el-button type="text" size="small" class="btn-danger-text" @click="handleDelete(row)">
                删除
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="pagination"
        background
        layout="total, sizes, prev, pager, next, jumper"
        :current-page="query.pageNumber"
        :page-size="query.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </el-card>

    <client-changelog-drawer
      :create-mode="drawerCreateMode"
      :visible.sync="drawerVisible"
      :record="editingRecord"
      :fixed-product-id="currentProductId"
      @created="reload"
      @updated="reload"
    />

    <el-dialog
      :title="`更新日志详情 - ${previewRecord.version || ''}`"
      :visible.sync="previewVisible"
      width="720px"
      append-to-body
    >
      <div v-if="previewRecord.id" class="preview-meta">
        <div><span class="label">产品线：</span>{{ productLabel(previewRecord.productId) }}</div>
        <div><span class="label">版本号：</span>{{ previewRecord.version }}</div>
        <div><span class="label">发布日期：</span>{{ previewRecord.releasedAt }}</div>
        <div>
          <span class="label">状态：</span>
          <el-tag :type="previewRecord.status === 'PUBLISHED' ? 'success' : 'info'" size="mini">
            {{ previewRecord.status === 'PUBLISHED' ? '已发布' : '草稿' }}
          </el-tag>
        </div>
      </div>
      <el-divider />
      <pre class="preview-body">{{ previewRecord.releaseNotes }}</pre>
      <span slot="footer">
        <el-button @click="previewVisible = false">关闭</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import {
  listChangelogs,
  deleteChangelog,
  upsertChangelog,
  getChangelogById
} from '@/api/clientChangelog'
import { formatApiDateTime } from '@/utils/date'
import { PRODUCT_PRESETS, productLabel } from '../productPresets'
import ClientChangelogDrawer from './ClientChangelogDrawer'

export default {
  name: 'ClientChangelogPanel',
  components: { ClientChangelogDrawer },
  filters: {
    parseTime(time) {
      return formatApiDateTime(time)
    }
  },
  data() {
    return {
      productPresets: PRODUCT_PRESETS,
      activeProductKey: PRODUCT_PRESETS[0].key,
      loading: false,
      records: [],
      total: 0,
      reqToken: 0,
      query: {
        version: '',
        status: '',
        pageNumber: 1,
        pageSize: 20,
        sortField: 'releasedAt',
        sortDirection: 'DESC'
      },
      drawerVisible: false,
      drawerCreateMode: true,
      editingRecord: {},
      previewVisible: false,
      previewRecord: {}
    }
  },
  computed: {
    currentProductId() {
      const tab = this.productPresets.find(p => p.key === this.activeProductKey)
      return tab ? tab.productId : PRODUCT_PRESETS[0].productId
    },
    tableDefaultSort() {
      return {
        prop: this.query.sortField,
        order: this.query.sortDirection === 'ASC' ? 'ascending' : 'descending'
      }
    }
  },
  mounted() {
    this.reload()
  },
  methods: {
    productLabel,
    buildSnippet(text) {
      if (!text) return ''
      const oneLine = text.replace(/\s+/g, ' ').trim()
      return oneLine.length > 80 ? `${oneLine.slice(0, 80)}…` : oneLine
    },
    buildPayload() {
      const payload = {
        productId: this.currentProductId,
        pageNumber: this.query.pageNumber,
        pageSize: this.query.pageSize,
        sortField: this.query.sortField,
        sortDirection: this.query.sortDirection
      }
      const v = (this.query.version || '').trim()
      if (v) payload.version = v
      if (this.query.status) payload.status = this.query.status
      return payload
    },
    reload() {
      const token = ++this.reqToken
      this.loading = true
      listChangelogs(this.buildPayload())
        .then(res => {
          if (token !== this.reqToken) return
          const data = res.data || {}
          this.records = data.records || []
          this.total = data.totalRecords || 0
        })
        .finally(() => {
          if (token === this.reqToken) {
            this.loading = false
          }
        })
    },
    handleProductTabChange() {
      this.query.pageNumber = 1
      this.reload()
    },
    handleSearch() {
      this.query.pageNumber = 1
      this.reload()
    },
    resetFilter() {
      this.query.version = ''
      this.query.status = ''
      this.query.pageNumber = 1
      this.query.sortField = 'releasedAt'
      this.query.sortDirection = 'DESC'
      this.reload()
    },
    handleSortChange({ prop, order }) {
      if (!order) {
        this.query.sortField = 'releasedAt'
        this.query.sortDirection = 'DESC'
      } else {
        this.query.sortField = prop
        this.query.sortDirection = order === 'ascending' ? 'ASC' : 'DESC'
      }
      this.query.pageNumber = 1
      this.reload()
    },
    handlePageChange(p) {
      this.query.pageNumber = p
      this.reload()
    },
    handleSizeChange(s) {
      this.query.pageSize = s
      this.query.pageNumber = 1
      this.reload()
    },
    handleCreate() {
      this.drawerCreateMode = true
      this.editingRecord = { productId: this.currentProductId }
      this.drawerVisible = true
    },
    handleEdit(row) {
      this.drawerCreateMode = false
      this.editingRecord = { ...row }
      this.drawerVisible = true
    },
    handlePreview(row) {
      getChangelogById(row.id)
        .then(res => {
          this.previewRecord = res.data || row
          this.previewVisible = true
        })
        .catch(() => {
          this.previewRecord = row
          this.previewVisible = true
        })
    },
    handleToggleStatus(row, target) {
      const action = target === 'PUBLISHED' ? '发布' : '下架'
      const tip = target === 'PUBLISHED'
        ? `确认发布版本 ${row.version} 的更新日志？发布后客户端可见。`
        : `确认下架版本 ${row.version} 的更新日志？下架后客户端不再返回。`
      this.$confirm(tip, `确认${action}`, {
        type: 'warning',
        confirmButtonText: action,
        cancelButtonText: '取消'
      })
        .then(() => upsertChangelog({
          id: row.id,
          productId: row.productId,
          version: row.version,
          releasedAt: row.releasedAt,
          status: target,
          releaseNotes: row.releaseNotes
        }))
        .then(() => {
          this.$message.success(`已${action}`)
          this.reload()
        })
        .catch(() => {})
    },
    handleDelete(row) {
      const msg = [
        '确认删除以下更新日志？删除后无法恢复。',
        `产品线：${productLabel(row.productId)}`,
        `版本号：${row.version}`,
        `发布日期：${row.releasedAt}`
      ].join('\n')
      this.$confirm(msg, '确认删除', {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消'
      })
        .then(() => deleteChangelog(row.id))
        .then(() => {
          this.$message.success('已删除')
          this.reload()
        })
        .catch(() => {})
    }
  }
}
</script>

<style scoped>
.changelog-panel {
  display: flex;
  flex-direction: column;
}
.product-tabs {
  background: #f0f2f5;
  padding: 8px 8px 0;
  border-radius: 4px;
}
.product-tabs >>> .el-tabs__header {
  margin-bottom: 0;
  border-bottom: 1px solid #dcdfe6;
}
.product-tabs >>> .el-tabs__item {
  background: #e4e7ed;
  color: #606266;
  border: 1px solid #dcdfe6;
  border-bottom: none;
  margin-right: 4px;
  border-radius: 4px 4px 0 0;
  height: 36px;
  line-height: 36px;
}
.product-tabs >>> .el-tabs__item.is-active {
  background: #fff;
  color: #409eff;
  font-weight: 600;
  border-color: #409eff;
  border-bottom-color: #fff;
}
.product-tabs >>> .el-tabs__nav-wrap::after {
  display: none;
}
.filter-card {
  margin-bottom: 12px;
}
.filter-form {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
}
.filter-form >>> .el-form-item {
  margin-bottom: 0;
}
.table-card {
  margin-bottom: 16px;
}
.pagination {
  margin-top: 12px;
  text-align: right;
}
.row-actions {
  display: inline-flex;
  align-items: center;
  white-space: nowrap;
}
.row-actions .el-button + .el-button {
  margin-left: 4px;
}
.btn-danger-text {
  color: #f56c6c;
}
.btn-danger-text:hover,
.btn-danger-text:focus {
  color: #f78989;
}
.btn-warning-text {
  color: #e6a23c;
}
.btn-warning-text:hover,
.btn-warning-text:focus {
  color: #ebb563;
}
.btn-success-text {
  color: #67c23a;
}
.btn-success-text:hover,
.btn-success-text:focus {
  color: #85ce61;
}
.notes-snippet {
  color: #606266;
}
.preview-meta div {
  margin-bottom: 6px;
  font-size: 13px;
  color: #303133;
}
.preview-meta .label {
  color: #909399;
  margin-right: 4px;
}
.preview-body {
  max-height: 480px;
  overflow: auto;
  padding: 12px;
  font-family: Menlo, Monaco, Consolas, monospace;
  font-size: 13px;
  line-height: 1.6;
  background: #f5f7fa;
  border-radius: 4px;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
