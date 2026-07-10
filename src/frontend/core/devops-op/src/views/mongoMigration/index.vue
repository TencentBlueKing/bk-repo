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
            :loading="allHealthLoading"
            @click="loadAllCompensationHealth"
          >
            全部补偿健康
          </el-button>
          <el-button
            type="info"
            :loading="triggerCompensationLoading"
            @click="triggerCompensationAction"
          >
            补偿触发
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
        API 只写 DB 编排状态（phase / Job 断点），<strong>不驱动路由</strong>。
        路由配置（project-routing、routing-state）由运维在本区域 Consul 手动维护。
      </div>
      <el-form :inline="true" :model="bindingForm">
        <el-form-item label="目标实例">
          <el-input
            v-model="bindingForm.targetInstance"
            placeholder="如 heavy1"
            clearable
          />
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
        <el-button
          type="info"
          :loading="rollbackVerifyLoading"
          @click="triggerRollbackVerify"
        >
          POST /migration/rollback-verify
        </el-button>
      </div>
      <el-descriptions :column="2" border class="api-list">
        <el-descriptions-item label="声明迁移单元">
          POST /migration/binding
        </el-descriptions-item>
        <el-descriptions-item label="启动迁移同步">
          POST /migration/start → 随后 Consul 加 project-routing + routing-state=DUAL_WRITE
        </el-descriptions-item>
        <el-descriptions-item label="关闭双写并切流">
          POST /migration/route → Consul routing-state=ROUTED + routing-effective-at（延迟生效）
        </el-descriptions-item>
        <el-descriptions-item label="清理 Default 副本">
          POST /migration/cleanup
        </el-descriptions-item>
        <el-descriptions-item label="回滚迁移">
          POST /migration/rollback
        </el-descriptions-item>
        <el-descriptions-item label="回滚后验证">
          POST /migration/rollback-verify/{projectId}
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="never" class="query-card">
      <div slot="header">
        <span>Consul 路由配置（运维手动）</span>
      </div>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="本区域 Consul 为路由唯一写入源，代码/API 不会自动修改。"
      />
      <ol class="consul-sop">
        <li>binding / start 后：在 Consul 添加 <code>project-routing</code> 条目，设 <code>routing-state=DUAL_WRITE</code></li>
        <li>route 后：按 API 返回写入 Consul（routing-state=ROUTED、config-version++、routing-effective-at）；到点后自动切 Heavy 读</li>
        <li>cleanup 后：删除 <code>project-routing</code> 中该项目；散发查询参数改 Consul <code>scatter-query.*</code></li>
        <li>回滚：Consul <code>routing-state=OFF</code> 并清理 <code>project-routing</code></li>
      </ol>
    </el-card>

    <el-card shadow="never" class="query-card">
      <div slot="header">
        <span>Gate 参数（DB）</span>
        <span class="card-subtitle">（mongo_routing_config，仅 MigrationGate 低频读取）</span>
      </div>
      <el-form :inline="true" :model="configForm">
        <el-form-item label="最大并发双写">
          <el-input-number
            v-model="configForm.maxConcurrentDualWrite"
            :min="0"
            :max="50"
            controls-position="right"
          />
        </el-form-item>
        <el-form-item label="冻结 DDL">
          <el-switch v-model="configForm.freezeDdl" active-text="是" inactive-text="否" />
        </el-form-item>
      </el-form>
      <div class="action-row">
        <el-button
          type="primary"
          :loading="configLoading"
          @click="loadRoutingConfig"
        >
          刷新配置
        </el-button>
        <el-button
          type="success"
          :loading="configSaveLoading"
          @click="saveRoutingConfig"
        >
          保存配置
        </el-button>
      </div>
      <el-descriptions :column="2" border size="small" class="config-desc">
        <el-descriptions-item label="DB config-version">
          {{ configVersion || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="最大并发双写">
          {{ configForm.maxConcurrentDualWrite }}
        </el-descriptions-item>
        <el-descriptions-item label="冻结 DDL">
          {{ configForm.freezeDdl ? '是' : '否' }}
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="never" class="query-card">
      <div slot="header">
        <span>历史数据同步</span>
      </div>
      <div class="action-row">
        <el-button
          type="primary"
          :loading="historicalSyncAllLoading"
          @click="triggerHistoricalSyncAll"
        >
          同步全部规则
        </el-button>
        <el-button
          type="primary"
          :loading="historicalSyncRuleLoading"
          @click="triggerHistoricalSyncRule"
        >
          同步指定规则
        </el-button>
      </div>
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

    <el-row :gutter="20" class="result-row">
      <el-col :span="24">
        <el-card shadow="never">
          <div slot="header">
            <span>全部补偿健康</span>
          </div>
          <pre class="result-block">{{ formatResult(allHealthResult) }}</pre>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script>
import {
  cleanupMigration,
  createMigrationBinding,
  getCompensationHealth,
  getAllCompensationHealth,
  getCompensationStats,
  getMigrationStatus,
  getRoutingConfig,
  getRoutingReadiness,
  rollbackMigration,
  rollbackVerify,
  routeMigration,
  startMigration,
  syncHistoricalData,
  syncHistoricalDataByRule,
  triggerCompensation,
  updateRoutingConfig,
  verifyAll,
  verifyProject
} from '@/api/mongoMigration'

const ACTION_MAP = {
  start: {
    api: startMigration,
    success: '已触发迁移启动；请在 Consul 添加 project-routing 并设 routing-state=DUAL_WRITE'
  },
  route: {
    api: routeMigration,
    success: '已执行切流；请按返回的 consulHint 写入 Consul（含 routing-effective-at）'
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
        businessId: '',
        groupProjectIds: ''
      },
      configForm: {
        maxConcurrentDualWrite: 1,
        freezeDdl: false
      },
      configVersion: null,
      configLoading: false,
      configSaveLoading: false,
      historicalSyncAllLoading: false,
      historicalSyncRuleLoading: false,
      statusLoading: false,
      readinessLoading: false,
      statsLoading: false,
      healthLoading: false,
      allHealthLoading: false,
      triggerCompensationLoading: false,
      verifyAllLoading: false,
      verifyProjectLoading: false,
      rollbackVerifyLoading: false,
      actionLoading: {
        binding: false,
        start: false,
        route: false,
        cleanup: false,
        rollback: false
      },
      readinessResult: null,
      statsResult: null,
      healthResult: null,
      allHealthResult: null,
      statusList: []
    }
  },
  mounted() {
    this.loadRoutingConfig()
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
    triggerRollbackVerify() {
      if (!this.queryForm.projectId) {
        this.$message.warning('请先填写项目ID')
        return
      }
      this.rollbackVerifyLoading = true
      rollbackVerify(this.queryForm.projectId).then(res => {
        this.$message.success(`回滚验证完成：${JSON.stringify(res.data)}`)
      }).finally(() => {
        this.rollbackVerifyLoading = false
      })
    },
    loadRoutingConfig() {
      this.configLoading = true
      getRoutingConfig().then(res => {
        const d = res.data
        this.configForm.maxConcurrentDualWrite = d.maxConcurrentDualWrite
        this.configForm.freezeDdl = d.freezeDdl
        this.configVersion = d.configVersion
      }).finally(() => {
        this.configLoading = false
      })
    },
    saveRoutingConfig() {
      this.configSaveLoading = true
      updateRoutingConfig(this.configForm).then(res => {
        this.configVersion = res.data.configVersion
        this.$message.success(`Gate 参数已保存（DB config-version=${this.configVersion}）`)
      }).finally(() => {
        this.configSaveLoading = false
      })
    },
    triggerHistoricalSyncAll() {
      this.historicalSyncAllLoading = true
      syncHistoricalData().then(() => {
        this.$message.success('已触发全部规则历史数据同步')
      }).finally(() => {
        this.historicalSyncAllLoading = false
      })
    },
    triggerHistoricalSyncRule() {
      if (!this.queryForm.ruleName) {
        this.$message.warning('请先填写规则名')
        return
      }
      this.historicalSyncRuleLoading = true
      syncHistoricalDataByRule(this.queryForm.ruleName).then(() => {
        this.$message.success(`已触发规则[${this.queryForm.ruleName}]历史数据同步`)
      }).finally(() => {
        this.historicalSyncRuleLoading = false
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
        historicalSyncStrategy: 'JOB_ONLY'
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
      config.api(this.buildProjectRequest()).then((res) => {
        const hint = action === 'route' && res?.data?.consulHint
        this.$message.success(hint ? `${config.success}\n${hint}` : config.success)
        this.refreshAll()
      }).finally(() => {
        this.actionLoading[action] = false
      })
    },
    refreshAll() {
      this.loadRoutingConfig()
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

.card-subtitle {
  font-size: 12px;
  color: #979ba5;
  margin-left: 12px;
  font-weight: normal;
}

.consul-sop {
  margin: 12px 0 0;
  padding-left: 20px;
  color: #63656e;
  line-height: 1.8;
}

.config-desc {
  margin-top: 16px;
}
</style>
