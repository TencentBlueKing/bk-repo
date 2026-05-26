<template>
  <div class="app-container client-version-page">
    <el-tabs v-model="activeProductKey" type="card" class="product-tabs" @tab-click="handlePageContextChange">
      <el-tab-pane
        v-for="p in productPresets"
        :key="p.key"
        :label="p.label"
        :name="p.key"
      />
    </el-tabs>

    <el-tabs v-model="activePlatformKey" type="card" class="platform-tabs" @tab-click="handlePageContextChange">
      <el-tab-pane
        v-for="p in platformPresets"
        :key="p.key"
        :label="p.label"
        :name="p.key"
      />
    </el-tabs>

    <el-card shadow="never" class="arch-card">
      <span class="arch-label">架构</span>
      <el-checkbox
        v-model="isArchCheckAll"
        :indeterminate="archIndeterminate"
        class="arch-check-all"
        @change="handleArchCheckAllChange"
      >
        全选
      </el-checkbox>
      <el-checkbox-group v-model="selectedArchs" class="arch-group" @change="handleArchChange">
        <el-checkbox v-for="a in archPresets" :key="a.value" :label="a.value">
          {{ a.label }}
        </el-checkbox>
      </el-checkbox-group>
      <span class="page-context-hint">{{ pageContextHint }}</span>
    </el-card>

    <!-- 全员默认配置 -->
    <el-card shadow="never" class="section-card">
      <div slot="header" class="section-header">
        <span>全员默认配置</span>
        <template v-if="operationArch">
          <el-button
            v-if="!globalConfig"
            type="primary"
            size="mini"
            icon="el-icon-plus"
            @click="handleCreateGlobal"
          >
            新增全员
          </el-button>
          <div v-else>
            <el-button type="primary" size="mini" @click="handleUpdate(globalConfig)">修改</el-button>
            <el-button type="danger" size="mini" @click="handleDelete(globalConfig)">删除</el-button>
          </div>
        </template>
      </div>
      <div v-loading="globalLoading" class="global-summary">
        <template v-if="operationArch && globalConfig">
          <div class="global-item">
            <span class="label">最新版本</span>
            <span class="value">{{ globalConfig.latestVersion }}</span>
          </div>
          <div class="global-item">
            <span class="label">最低版本(强升)</span>
            <span class="value">{{ globalConfig.minVersion || '—' }}</span>
          </div>
          <div class="global-item">
            <span class="label">启用</span>
            <el-tag :type="globalConfig.enabled ? 'success' : 'info'" size="mini">
              {{ globalConfig.enabled ? '是' : '否' }}
            </el-tag>
          </div>
          <div class="global-item wide">
            <span class="label">下载地址</span>
            <span class="value url">{{ globalConfig.downloadUrl }}</span>
          </div>
        </template>
        <el-table
          v-else-if="globalConfigList.length"
          :data="globalConfigList"
          border
          size="small"
          :default-sort="tableDefaultSort"
          @sort-change="handleTableSortChange"
        >
          <el-table-column label="架构" prop="arch" width="90" align="center" sortable="custom" />
          <el-table-column
            label="最新版本"
            prop="latestVersion"
            width="120"
            align="center"
            sortable="custom"
          />
          <el-table-column label="最低版本" prop="minVersion" width="110" align="center" />
          <el-table-column label="启用" prop="enabled" width="72" align="center" sortable="custom">
            <template slot-scope="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'info'" size="mini">
                {{ row.enabled ? '是' : '否' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="下载地址" prop="downloadUrl" min-width="160" show-overflow-tooltip />
          <el-table-column label="操作" width="110" align="center">
            <template slot-scope="{ row }">
              <div class="row-actions">
                <el-button type="text" size="small" @click="handleUpdate(row)">编辑</el-button>
                <el-button type="text" size="small" class="btn-danger-text" @click="handleDelete(row)">
                  删除
                </el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else :image-size="64" description="暂无全员配置" />
        <div v-if="!operationArch" class="arch-tip">勾选单个架构后可新增/修改全员配置</div>
      </div>
    </el-card>

    <!-- 按用户灰度 -->
    <el-card shadow="never" class="section-card">
      <div slot="header" class="section-header">
        <span>按用户灰度</span>
        <div>
          <el-button
            type="primary"
            size="mini"
            icon="el-icon-plus"
            :disabled="!operationArch"
            @click="handleCreateUser"
          >
            按用户灰度
          </el-button>
          <el-button
            type="success"
            size="mini"
            icon="el-icon-document-copy"
            :disabled="!operationArch"
            @click="userBatchDialogVisible = true"
          >
            批量新增
          </el-button>
          <el-button
            type="success"
            size="mini"
            plain
            :disabled="!operationArch"
            @click="openCopyUserDialog"
          >
            复制到用户
          </el-button>
        </div>
      </div>

      <el-form :inline="true" :model="userQuery" size="small" class="user-filter">
        <el-form-item label="用户">
          <el-input
            v-model="userQuery.targetUserId"
            placeholder="精确匹配"
            clearable
            style="width: 160px"
            @keyup.enter.native="handleUserFilter"
          />
        </el-form-item>
        <el-form-item label="版本号">
          <el-input
            v-model="userQuery.latestVersion"
            placeholder="精确匹配"
            clearable
            style="width: 140px"
            @keyup.enter.native="handleUserFilter"
          />
        </el-form-item>
        <el-form-item label="启用">
          <el-select v-model="userQuery.enabled" clearable placeholder="全部" style="width: 100px">
            <el-option label="启用" :value="true" />
            <el-option label="禁用" :value="false" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :disabled="selectedArchs.length === 0" @click="handleUserFilter">
            查询
          </el-button>
          <el-button :disabled="selectedArchs.length === 0" @click="resetUserFilter">重置</el-button>
          <el-button
            type="danger"
            :disabled="userSelectedIds.length === 0"
            @click="handleBatchDeleteUser"
          >
            批量删除{{ userSelectedIds.length ? `（${userSelectedIds.length}）` : '' }}
          </el-button>
        </el-form-item>
      </el-form>

      <el-table
        v-loading="userLoading"
        :data="userList"
        border
        size="small"
        :default-sort="tableDefaultSort"
        @sort-change="handleTableSortChange"
        @selection-change="handleUserSelectionChange"
        @row-dblclick="handleUpdateUser"
      >
        <el-table-column type="selection" width="45" align="center" />
        <el-table-column
          v-if="!operationArch"
          label="架构"
          prop="arch"
          width="90"
          align="center"
          sortable="custom"
        />
        <el-table-column
          label="用户"
          prop="targetUserId"
          min-width="140"
          show-overflow-tooltip
          sortable="custom"
        />
        <el-table-column
          label="版本号"
          prop="latestVersion"
          width="120"
          align="center"
          sortable="custom"
        />
        <el-table-column label="最低版本" prop="minVersion" width="110" align="center" show-overflow-tooltip />
        <el-table-column label="启用" prop="enabled" width="72" align="center" sortable="custom">
          <template slot-scope="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="mini">
              {{ row.enabled ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="下载地址" prop="downloadUrl" min-width="160" show-overflow-tooltip />
        <el-table-column
          label="更新时间"
          prop="lastModifiedDate"
          min-width="150"
          align="center"
          sortable="custom"
        >
          <template slot-scope="{ row }">
            <span>{{ row.lastModifiedDate | parseTime }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" align="center" fixed="right">
          <template slot-scope="{ row }">
            <div class="row-actions">
              <el-button type="text" size="small" @click="handleUpdateUser(row)">编辑</el-button>
              <el-button type="text" size="small" class="btn-danger-text" @click="handleDelete(row)">
                删除
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="user-pagination"
        background
        layout="total, sizes, prev, pager, next, jumper"
        :disabled="selectedArchs.length === 0"
        :current-page="userQuery.pageNumber"
        :page-size="userQuery.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="userTotal"
        @current-change="handleUserPageChange"
        @size-change="handleUserSizeChange"
      />
    </el-card>

    <client-version-config-dialog
      :create-mode="dialogCreateMode"
      :visible.sync="dialogVisible"
      :record="editingRecord"
      :fixed-product-id="currentProductId"
      :fixed-platform="currentPlatform"
      :fixed-arch="operationArch"
      :user-only="dialogUserOnly"
      @created="reloadPage"
      @updated="reloadPage"
    />

    <client-version-config-user-batch-dialog
      :visible.sync="userBatchDialogVisible"
      :fixed-product-id="currentProductId"
      :fixed-platform="currentPlatform"
      :fixed-arch="operationArch"
      @created="reloadPage"
    />

    <client-version-config-copy-user-dialog
      :visible.sync="copyUserDialogVisible"
      :tab-context="pageTabContext"
      :source-user-options="copySourceUserOptions"
      :default-source-user-id="copyDefaultSourceUserId"
      @copied="reloadPage"
    />
  </div>
</template>

<script>
import {
  listClientVersionConfigs,
  deleteClientVersionConfig,
  batchDeleteClientVersionConfig,
  BATCH_LIMIT
} from '@/api/clientVersionConfig'
import { formatApiDateTime } from '@/utils/date'
import {
  PRODUCT_PRESETS,
  PLATFORM_PRESETS,
  ARCH_PRESETS,
  productLabel,
  platformLabel
} from './productPresets'
import ClientVersionConfigDialog from './components/ClientVersionConfigDialog'
import ClientVersionConfigUserBatchDialog from './components/ClientVersionConfigUserBatchDialog'
import ClientVersionConfigCopyUserDialog from './components/ClientVersionConfigCopyUserDialog'

export default {
  name: 'ClientVersionConfig',
  components: {
    ClientVersionConfigDialog,
    ClientVersionConfigUserBatchDialog,
    ClientVersionConfigCopyUserDialog
  },
  filters: {
    parseTime(time) {
      return formatApiDateTime(time)
    }
  },
  data() {
    return {
      productPresets: PRODUCT_PRESETS,
      platformPresets: PLATFORM_PRESETS,
      archPresets: ARCH_PRESETS,
      activeProductKey: PRODUCT_PRESETS[0].key,
      activePlatformKey: PLATFORM_PRESETS[0].key,
      selectedArchs: ARCH_PRESETS.map(a => a.value),
      isArchCheckAll: true,
      globalLoading: false,
      globalConfig: null,
      globalConfigList: [],
      globalReqToken: 0,
      userLoading: false,
      userList: [],
      userTotal: 0,
      userReqToken: 0,
      userQuery: {
        targetUserId: '',
        latestVersion: '',
        enabled: null,
        pageNumber: 1,
        pageSize: 20,
        sortField: 'lastModifiedDate',
        sortDirection: 'DESC'
      },
      userSelectedIds: [],
      userSelectedRows: [],
      dialogVisible: false,
      dialogCreateMode: true,
      dialogUserOnly: false,
      editingRecord: {},
      userBatchDialogVisible: false,
      copyUserDialogVisible: false,
      copySourceUserOptions: [],
      copyDefaultSourceUserId: ''
    }
  },
  computed: {
    currentProductId() {
      const tab = this.productPresets.find(p => p.key === this.activeProductKey)
      return tab ? tab.productId : PRODUCT_PRESETS[0].productId
    },
    currentPlatform() {
      const tab = this.platformPresets.find(p => p.key === this.activePlatformKey)
      return tab ? tab.value : PLATFORM_PRESETS[0].value
    },
    archIndeterminate() {
      const n = this.selectedArchs.length
      return n > 0 && n < this.archPresets.length
    },
    operationArch() {
      return this.selectedArchs.length === 1 ? this.selectedArchs[0] : ''
    },
    pageContextHint() {
      const archText = this.selectedArchs.length === this.archPresets.length
        ? '全部架构'
        : (this.selectedArchs.length ? this.selectedArchs.join(', ') : '未选架构')
      return `${productLabel(this.currentProductId)} / ${platformLabel(this.currentPlatform)} / ${archText}`
    },
    pageTabContext() {
      return {
        productId: this.currentProductId,
        platform: this.currentPlatform,
        arch: this.operationArch
      }
    },
    tableDefaultSort() {
      return {
        prop: this.userQuery.sortField,
        order: this.userQuery.sortDirection === 'ASC' ? 'ascending' : 'descending'
      }
    }
  },
  mounted() {
    this.reloadPage()
  },
  methods: {
    buildListQuery(extra) {
      const base = {
        productId: this.currentProductId,
        platform: this.currentPlatform
      }
      if (this.selectedArchs.length === 1) {
        base.arch = this.selectedArchs[0]
      } else if (
        this.selectedArchs.length > 1 &&
        this.selectedArchs.length < this.archPresets.length
      ) {
        base.archs = [...this.selectedArchs]
      }
      return { ...base, ...extra }
    },
    reloadPage() {
      if (this.selectedArchs.length === 0) {
        this.globalConfig = null
        this.globalConfigList = []
        this.userList = []
        this.userTotal = 0
        return
      }
      this.loadGlobal()
      this.loadUserList()
    },
    handleArchCheckAllChange(checked) {
      this.selectedArchs = checked ? this.archPresets.map(a => a.value) : []
      this.handlePageContextChange()
    },
    handleArchChange() {
      this.isArchCheckAll = this.selectedArchs.length === this.archPresets.length
      this.handlePageContextChange()
    },
    handlePageContextChange() {
      this.dialogVisible = false
      this.userBatchDialogVisible = false
      this.copyUserDialogVisible = false
      this.userQuery.pageNumber = 1
      this.userSelectedIds = []
      this.userSelectedRows = []
      this.reloadPage()
    },
    loadGlobal() {
      if (this.selectedArchs.length === 0) {
        this.globalConfig = null
        this.globalConfigList = []
        return
      }
      const token = ++this.globalReqToken
      this.globalLoading = true
      const query = this.buildListQuery({
        scope: 'global',
        pageNumber: 1,
        pageSize: 20,
        sortField: this.userQuery.sortField,
        sortDirection: this.userQuery.sortDirection
      })
      listClientVersionConfigs(query)
        .then(res => {
          if (token !== this.globalReqToken) return
          const records = (res.data && res.data.records) || []
          if (this.operationArch) {
            this.globalConfig = records.find(
              r => (r.arch || '').toLowerCase() === this.operationArch
            ) || null
            this.globalConfigList = []
          } else {
            this.globalConfig = null
            this.globalConfigList = records
          }
        })
        .finally(() => {
          if (token === this.globalReqToken) {
            this.globalLoading = false
          }
        })
    },
    loadUserList() {
      if (this.selectedArchs.length === 0) {
        this.userList = []
        this.userTotal = 0
        return
      }
      const token = ++this.userReqToken
      this.userLoading = true
      const query = this.buildListQuery({
        scope: 'user',
        pageNumber: this.userQuery.pageNumber,
        pageSize: this.userQuery.pageSize,
        sortField: this.userQuery.sortField,
        sortDirection: this.userQuery.sortDirection
      })
      const uid = (this.userQuery.targetUserId || '').trim()
      if (uid) {
        query.targetUserId = uid
      }
      const ver = (this.userQuery.latestVersion || '').trim()
      if (ver) {
        query.latestVersion = ver
      }
      if (this.userQuery.enabled === true || this.userQuery.enabled === false) {
        query.enabled = this.userQuery.enabled
      }
      listClientVersionConfigs(query)
        .then(res => {
          if (token !== this.userReqToken) return
          const data = res.data || {}
          this.userList = data.records || []
          this.userTotal = data.totalRecords || 0
        })
        .finally(() => {
          if (token === this.userReqToken) {
            this.userLoading = false
          }
        })
    },
    handleUserFilter() {
      if (this.selectedArchs.length === 0) {
        this.$message.warning('请先选择架构')
        return
      }
      this.userQuery.pageNumber = 1
      this.loadUserList()
    },
    resetUserFilter() {
      this.userQuery.targetUserId = ''
      this.userQuery.latestVersion = ''
      this.userQuery.enabled = null
      this.userQuery.pageNumber = 1
      this.userQuery.sortField = 'lastModifiedDate'
      this.userQuery.sortDirection = 'DESC'
      this.loadUserList()
    },
    handleTableSortChange({ prop, order }) {
      if (!order) {
        this.userQuery.sortField = 'lastModifiedDate'
        this.userQuery.sortDirection = 'DESC'
      } else {
        this.userQuery.sortField = prop
        this.userQuery.sortDirection = order === 'ascending' ? 'ASC' : 'DESC'
      }
      this.userQuery.pageNumber = 1
      this.reloadPage()
    },
    handleUserPageChange(pageNumber) {
      this.userQuery.pageNumber = pageNumber
      this.loadUserList()
    },
    handleUserSizeChange(pageSize) {
      this.userQuery.pageSize = pageSize
      this.userQuery.pageNumber = 1
      this.loadUserList()
    },
    handleCreateGlobal() {
      if (!this.operationArch) {
        this.$message.warning('请只勾选一个架构后再操作')
        return
      }
      this.dialogCreateMode = true
      this.dialogUserOnly = false
      this.editingRecord = {
        productId: this.currentProductId,
        platform: this.currentPlatform,
        arch: this.operationArch,
        targetUserId: ''
      }
      this.dialogVisible = true
    },
    handleCreateUser() {
      if (!this.operationArch) {
        this.$message.warning('请只勾选一个架构后再操作')
        return
      }
      this.dialogCreateMode = true
      this.dialogUserOnly = true
      this.editingRecord = {
        productId: this.currentProductId,
        platform: this.currentPlatform,
        arch: this.operationArch
      }
      this.dialogVisible = true
    },
    handleUpdate(row) {
      this.dialogCreateMode = false
      this.dialogUserOnly = !!(row.targetUserId && row.targetUserId.trim())
      this.editingRecord = { ...row }
      this.dialogVisible = true
    },
    handleUpdateUser(row) {
      this.dialogCreateMode = false
      this.dialogUserOnly = true
      this.editingRecord = { ...row }
      this.dialogVisible = true
    },
    handleUserSelectionChange(rows) {
      this.userSelectedRows = rows.filter(r => r.id)
      this.userSelectedIds = this.userSelectedRows.map(r => r.id)
    },
    buildConfigScopeLabel(row) {
      const uid = (row.targetUserId || '').trim()
      return uid ? `用户 ${uid}` : '全员默认'
    },
    buildDeleteConfirmMessage(row) {
      return [
        '确认删除以下配置？',
        `产品线：${productLabel(row.productId)}`,
        `平台：${platformLabel(row.platform)}`,
        `架构：${row.arch}`,
        `范围：${this.buildConfigScopeLabel(row)}`,
        `版本：${row.latestVersion}`
      ].join('\n')
    },
    buildBatchDeleteConfirmMessage(rows) {
      const lines = rows.slice(0, 5).map(r => {
        return `- ${this.buildConfigScopeLabel(r)} / ${r.arch} / ${r.latestVersion}`
      })
      if (rows.length > 5) {
        lines.push(`... 等共 ${rows.length} 条`)
      }
      return ['确认删除以下配置？', ...lines].join('\n')
    },
    openCopyUserDialog() {
      if (!this.operationArch) {
        this.$message.warning('请只勾选一个架构后再操作')
        return
      }
      const users = [
        ...new Set(
          this.userList.map(r => (r.targetUserId || '').trim()).filter(Boolean)
        )
      ].sort()
      this.copySourceUserOptions = users
      const selected = this.userSelectedRows.filter(r => (r.targetUserId || '').trim())
      this.copyDefaultSourceUserId = selected.length
        ? selected[0].targetUserId.trim()
        : (users[0] || '')
      this.copyUserDialogVisible = true
    },
    handleDelete(row) {
      if (!row.id) return
      this.$confirm(this.buildDeleteConfirmMessage(row), '确认删除', {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消'
      })
        .then(() => deleteClientVersionConfig(row.id))
        .then(() => {
          this.$message.success('已删除')
          this.reloadPage()
        })
        .catch(() => {})
    },
    handleBatchDeleteUser() {
      if (this.userSelectedIds.length === 0) return
      if (this.userSelectedIds.length > BATCH_LIMIT) {
        this.$message.warning(`批量删除最多支持 ${BATCH_LIMIT} 条`)
        return
      }
      this.$confirm(this.buildBatchDeleteConfirmMessage(this.userSelectedRows), '确认批量删除', {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消'
      })
        .then(() => batchDeleteClientVersionConfig(this.userSelectedIds))
        .then(() => {
          this.$message.success(`已删除 ${this.userSelectedIds.length} 条`)
          this.userSelectedIds = []
          this.userSelectedRows = []
          this.reloadPage()
        })
        .catch(() => {})
    }
  }
}
</script>

<style scoped>
.product-tabs,
.platform-tabs {
  background: #f0f2f5;
  padding: 8px 8px 0;
  border-radius: 4px;
}
.product-tabs {
  margin-bottom: 4px;
}
.platform-tabs {
  margin-top: 0;
}
.product-tabs >>> .el-tabs__header,
.platform-tabs >>> .el-tabs__header {
  margin-bottom: 0;
  border-bottom: 1px solid #dcdfe6;
}
.product-tabs >>> .el-tabs__item,
.platform-tabs >>> .el-tabs__item {
  background: #e4e7ed;
  color: #606266;
  border: 1px solid #dcdfe6;
  border-bottom: none;
  margin-right: 4px;
  border-radius: 4px 4px 0 0;
  height: 36px;
  line-height: 36px;
}
.product-tabs >>> .el-tabs__item.is-active,
.platform-tabs >>> .el-tabs__item.is-active {
  background: #fff;
  color: #409eff;
  font-weight: 600;
  border-color: #409eff;
  border-bottom-color: #fff;
}
.product-tabs >>> .el-tabs__nav-wrap::after,
.platform-tabs >>> .el-tabs__nav-wrap::after {
  display: none;
}
.arch-card {
  margin-bottom: 12px;
}
.arch-card >>> .el-card__body {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 16px;
}
.arch-card .arch-label {
  color: #606266;
  font-size: 14px;
  font-weight: 500;
}
.arch-check-all {
  margin-right: 4px;
}
.arch-group {
  display: inline-flex;
  align-items: center;
}
.arch-card .page-context-hint {
  margin-left: 16px;
  color: #909399;
  font-size: 13px;
}
.section-card {
  margin-bottom: 16px;
}
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.global-summary {
  min-height: 80px;
}
.global-item {
  display: inline-block;
  margin-right: 24px;
  margin-bottom: 8px;
  font-size: 14px;
}
.global-item.wide {
  display: block;
  margin-right: 0;
}
.global-item .label {
  color: #909399;
  margin-right: 8px;
}
.global-item .value.url {
  word-break: break-all;
}
.row-actions {
  display: inline-flex;
  align-items: center;
  justify-content: center;
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
.arch-tip {
  margin-top: 8px;
  font-size: 12px;
  color: #e6a23c;
}
.user-filter {
  margin-bottom: 12px;
}
.user-pagination {
  margin-top: 12px;
}
</style>
