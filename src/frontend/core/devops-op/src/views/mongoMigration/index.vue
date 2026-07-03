<template>
  <div class="app-container mongo-migration-container">
    <el-card shadow="never" class="query-card">
      <div slot="header">
        <span>分库迁移检测</span>
      </div>
      <el-form ref="form" :inline="true" :model="queryForm">
        <el-form-item label="规则名">
          <el-input v-model="queryForm.ruleName" placeholder="如 node" clearable />
        </el-form-item>
        <el-form-item label="项目ID">
          <el-input v-model="queryForm.projectId" placeholder="可选" clearable />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            :loading="statusLoading"
            @click="loadMigrationStatus"
          >
            查询状态
          </el-button>
          <el-button
            :loading="readinessLoading"
            @click="loadRoutingReadiness"
          >
            路由就绪
          </el-button>
          <el-button
            :loading="statsLoading"
            @click="loadCompensationStats"
          >
            补偿统计
          </el-button>
          <el-button
            :loading="healthLoading"
            @click="loadCompensationHealth"
          >
            补偿健康
          </el-button>
          <el-button
            type="warning"
            :loading="verifyAllLoading"
            @click="triggerVerifyAll"
          >
            全量对账
          </el-button>
          <el-button
            type="warning"
            :loading="verifyProjectLoading"
            @click="triggerVerifyProject"
          >
            单项目对账
          </el-button>
          <el-button @click="refreshAll">一键刷新检测</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="query-card">
      <div slot="header">
        <span>迁移编排 API</span>
      </div>
      <div class="api-tip">
        后台同步 Job 仅处理已显式启动的迁移项目；以下操作为页面可直接调用的编排 API。
      </div>
      <el-form :inline="true" :model="bindingForm">
        <el-form-item label="目标实例">
          <el-input
            v-model="bindingForm.targetInstance"
            placeholder="如 heavy1"
            clearable
          />
        </el-form-item>
        <el-form-item label="历史策略">
          <el-select v-model="bindingForm.historicalSyncStrategy" clearable>
            <el-option label="JOB_ONLY" value="JOB_ONLY" />
            <el-option label="DUMP" value="DUMP" />
            <el-option label="DUMP_THEN_JOB" value="DUMP_THEN_JOB" />
            <el-option label="NONE" value="NONE" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务组ID">
          <el-input
            v-model="bindingForm.businessId"
            placeholder="可选"
            clearable
          />
        </el-form-item>
        <el-form-item label="组内项目">
          <el-input
            v-model="bindingForm.groupProjectIds"
            placeholder="多个项目用逗号分隔"
            clearable
          />
        </el-form-item>
      </el-form>
      <div class="action-row">
        <el-button
          type="primary"
          :loading="actionLoading.binding"
          @click="triggerBinding"
        >
          POST /migration/binding
        </el-button>
        <el-button
          type="primary"
          :loading="actionLoading.start"
          @click="triggerAction('start')"
        >
          POST /migration/start
        </el-button>
        <el-button
          :loading="actionLoading.dumpComplete"
          @click="triggerAction('dumpComplete')"
        >
          POST /migration/dump-complete
        </el-button>
        <el-button
          :loading="actionLoading.ready"
          @click="triggerAction('ready')"
        >
          POST /migration/ready
        </el-button>
        <el-button
          type="warning"
          :loading="actionLoading.dualWrite"
          @click="triggerAction('dualWrite')"
        >
          POST /migration/dual-write
        </el-button>
        <el-button
          type="warning"
          :loading="actionLoading.route"
          @click="triggerAction('route')"
        >
          POST /migration/route
        </el-button>
        <el-button
          :loading="actionLoading.cleanup"
          @click="triggerAction('cleanup')"
        >
          POST /migration/cleanup
        </el-button>
        <el-button
          type="danger"
          :loading="actionLoading.rollback"
          @click="triggerAction('rollback')"
        >
          POST /migration/rollback
        </el-button>
      </div>
      <el-descriptions :column="2" border class="api-list">
        <el-descriptions-item label="声明迁移单元">
          POST /migration/binding
        </el-descriptions-item>
        <el-descriptions-item label="启动迁移同步">
          POST /migration/start
        </el-descriptions-item>
        <el-descriptions-item label="确认 dump 完成">
          POST /migration/dump-complete
        </el-descriptions-item>
        <el-descriptions-item label="标记 READY">
          POST /migration/ready
        </el-descriptions-item>
        <el-descriptions-item label="开启双写">
          POST /migration/dual-write
        </el-descriptions-item>
        <el-descriptions-item label="关闭双写并切流">
          POST /migration/route
        </el-descriptions-item>
        <el-descriptions-item label="清理 Default 副本">
          POST /migration/cleanup
        </el-descriptions-item>
        <el-descriptions-item label="回滚迁移">
          POST /migration/rollback
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-row :gutter="20" class="result-row">
      <el-col :span="12">
        <el-card shadow="never">
          <div slot="header">
            <span>路由就绪检查</span>
          </div>
          <pre class="result-block">{{ formatResult(readinessResult) }}</pre>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <div slot="header">
            <span>补偿健康检查</span>
          </div>
          <pre class="result-block">{{ formatResult(healthResult) }}</pre>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="result-row">
      <el-col :span="12">
        <el-card shadow="never">
          <div slot="header">
            <span>补偿统计</span>
          </div>
          <pre class="result-block">{{ formatResult(statsResult) }}</pre>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <div slot="header">
            <span>迁移状态</span>
          </div>
          <el-table :data="statusList" border style="width: 100%">
            <el-table-column prop="projectId" label="项目ID" min-width="160" />
            <el-table-column prop="ruleName" label="规则名" min-width="100" />
            <el-table-column prop="phase" label="阶段" min-width="120" />
            <el-table-column
              prop="targetInstance"
              label="目标实例"
              min-width="120"
            />
            <el-table-column prop="lastError" label="最近错误" min-width="200" />
            <el-table-column prop="updatedAt" label="更新时间" min-width="180" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script>
import {
  cleanupMigration,
  completeMigrationDump,
  createMigrationBinding,
  enableMigrationDualWrite,
  getCompensationHealth,
  getCompensationStats,
  getMigrationStatus,
  getRoutingReadiness,
  markMigrationReady,
  rollbackMigration,
  routeMigration,
  startMigration,
  verifyAll,
  verifyProject
} from '@/api/mongoMigration'

const ACTION_MAP = {
  start: {
    api: startMigration,
    success: '已触发迁移启动'
  },
  dumpComplete: {
    api: completeMigrationDump,
    success: '已确认 dump 完成'
  },
  ready: {
    api: markMigrationReady,
    success: '已标记 READY'
  },
  dualWrite: {
    api: enableMigrationDualWrite,
    success: '已开启双写'
  },
  route: {
    api: routeMigration,
    success: '已执行切流'
  },
  cleanup: {
    api: cleanupMigration,
    success: '已触发清理'
  },
  rollback: {
    api: rollbackMigration,
    success: '已触发回滚'
  }
}

export default {
  name: 'MongoMigration',
  data() {
    return {
      queryForm: {
        ruleName: 'node',
        projectId: ''
      },
      bindingForm: {
        targetInstance: '',
        historicalSyncStrategy: 'JOB_ONLY',
        businessId: '',
        groupProjectIds: ''
      },
      statusLoading: false,
      readinessLoading: false,
      statsLoading: false,
      healthLoading: false,
      verifyAllLoading: false,
      verifyProjectLoading: false,
      actionLoading: {
        binding: false,
        start: false,
        dumpComplete: false,
        ready: false,
        dualWrite: false,
        route: false,
        cleanup: false,
        rollback: false
      },
      readinessResult: null,
      statsResult: null,
      healthResult: null,
      statusList: []
    }
  },
  mounted() {
    this.loadRoutingReadiness()
    this.loadCompensationStats()
    this.loadCompensationHealth()
    this.loadMigrationStatus()
  },
  methods: {
    formatResult(value) {
      if (!value) {
        return '暂无数据'
      }
      return JSON.stringify(value, null, 2)
    },
    buildProjectRequest() {
      return {
        ruleName: this.queryForm.ruleName,
        projectId: this.queryForm.projectId
      }
    },
    ensureProjectParams() {
      if (!this.queryForm.ruleName || !this.queryForm.projectId) {
        this.$message.warning('请先填写规则名和项目ID')
        return false
      }
      return true
    },
    parseGroupProjectIds() {
      return this.bindingForm.groupProjectIds
        .split(',')
        .map(item => item.trim())
        .filter(Boolean)
    },
    loadMigrationStatus() {
      this.statusLoading = true
      const params = { ruleName: this.queryForm.ruleName }
      if (this.queryForm.projectId) {
        params.projectId = this.queryForm.projectId
      }
      getMigrationStatus(params).then(res => {
        this.statusList = res.data.projects || []
      }).finally(() => {
        this.statusLoading = false
      })
    },
    loadRoutingReadiness() {
      this.readinessLoading = true
      getRoutingReadiness().then(res => {
        this.readinessResult = res.data
      }).finally(() => {
        this.readinessLoading = false
      })
    },
    loadCompensationStats() {
      this.statsLoading = true
      getCompensationStats(this.queryForm.ruleName).then(res => {
        this.statsResult = res.data
      }).finally(() => {
        this.statsLoading = false
      })
    },
    loadCompensationHealth() {
      this.healthLoading = true
      getCompensationHealth(this.queryForm.ruleName).then(res => {
        this.healthResult = res.data
      }).finally(() => {
        this.healthLoading = false
      })
    },
    triggerVerifyAll() {
      this.verifyAllLoading = true
      verifyAll().then(() => {
        this.$message.success('已触发全量对账')
      }).finally(() => {
        this.verifyAllLoading = false
      })
    },
    triggerVerifyProject() {
      if (!this.ensureProjectParams()) {
        return
      }
      this.verifyProjectLoading = true
      verifyProject(this.queryForm.ruleName, this.queryForm.projectId).then(() => {
        this.$message.success('已触发单项目对账')
      }).finally(() => {
        this.verifyProjectLoading = false
      })
    },
    triggerBinding() {
      if (!this.ensureProjectParams()) {
        return
      }
      if (!this.bindingForm.targetInstance) {
        this.$message.warning('请先填写目标实例')
        return
      }
      this.actionLoading.binding = true
      const data = {
        ...this.buildProjectRequest(),
        targetInstance: this.bindingForm.targetInstance,
        historicalSyncStrategy: this.bindingForm.historicalSyncStrategy
      }
      if (this.bindingForm.businessId) {
        data.businessId = this.bindingForm.businessId
      }
      const groupProjectIds = this.parseGroupProjectIds()
      if (groupProjectIds.length) {
        data.groupProjectIds = groupProjectIds
      }
      createMigrationBinding(data).then(() => {
        this.$message.success('已声明迁移单元')
        this.refreshAll()
      }).finally(() => {
        this.actionLoading.binding = false
      })
    },
    triggerAction(action) {
      if (!this.ensureProjectParams()) {
        return
      }
      const config = ACTION_MAP[action]
      if (!config) {
        return
      }
      this.actionLoading[action] = true
      config.api(this.buildProjectRequest()).then(() => {
        this.$message.success(config.success)
        this.refreshAll()
      }).finally(() => {
        this.actionLoading[action] = false
      })
    },
    refreshAll() {
      this.loadMigrationStatus()
      this.loadCompensationStats()
      this.loadCompensationHealth()
      this.loadRoutingReadiness()
    }
  }
}
</script>

<style lang="scss" scoped>
.query-card {
  margin-bottom: 20px;
}

.result-row {
  margin-bottom: 20px;
}

.result-block {
  min-height: 220px;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
}

.api-tip {
  margin-bottom: 16px;
  color: #63656e;
}

.action-row {
  margin: 0 0 16px;
}

.action-row .el-button {
  margin-bottom: 12px;
}

.api-list {
  margin-top: 8px;
}
</style>
