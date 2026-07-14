<template>
  <div class="app-container">
    <el-alert
      title="Package 修复工具（Admin 专用）"
      type="warning"
      :closable="false"
      style="margin-bottom: 15px;"
    >
      <div style="font-size: 12px; line-height: 1.6;">
        本页面提供 3 类 Package/Version 数据修复能力，均为 <b>Admin 专属操作</b>。
        全库级修复不可逆且耗时较长，请在业务低峰期执行并做好数据备份。
      </div>
    </el-alert>

    <el-tabs v-model="activeTab" type="border-card">
      <!-- Tab 1：元数据字段修复 -->
      <el-tab-pane label="元数据修复（latest / historyVersion）" name="metadata">
        <el-alert
          title="按项目 + 仓库（可选包）范围重算 latest、historyVersion 字段"
          type="info"
          :closable="false"
          style="margin-bottom: 15px;"
        >
          <div style="font-size: 12px;">
            以 <b>package_version</b> 集合为权威数据源：
            <ul style="margin: 4px 0 4px 20px; padding: 0;">
              <li>latest = ordinal DESC 排序取第一个版本</li>
              <li>historyVersion 全量覆盖为当前所有版本名集合，清理脏数据</li>
              <li>元数据已一致的 package 不会写库（记入 skipped）</li>
            </ul>
          </div>
        </el-alert>

        <el-form ref="form" :inline="true" :model="repairQuery" :rules="rules">
          <el-form-item ref="project-form-item" label="项目ID" prop="projectId">
            <el-autocomplete
              v-model="repairQuery.projectId"
              class="inline-input"
              :fetch-suggestions="queryProjects"
              placeholder="请输入项目ID"
              size="mini"
              @select="selectProject"
            >
              <template slot-scope="{ item }">
                <div>{{ item.name }}</div>
              </template>
            </el-autocomplete>
          </el-form-item>
          <el-form-item
            ref="repo-form-item"
            style="margin-left: 15px"
            label="仓库"
            prop="repoName"
          >
            <el-autocomplete
              v-model="repairQuery.repoName"
              class="inline-input"
              :fetch-suggestions="queryRepositories"
              :disabled="!repairQuery.projectId"
              placeholder="请输入仓库名"
              size="mini"
              @select="selectRepo"
            >
              <template slot-scope="{ item }">
                <div>{{ item.name }}</div>
              </template>
            </el-autocomplete>
          </el-form-item>
          <el-form-item style="margin-left: 15px" label="修复范围">
            <el-radio-group v-model="repairScope" size="mini">
              <el-radio-button label="single">单个 package</el-radio-button>
              <el-radio-button label="all">仓库全部</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item
            v-if="repairScope === 'single'"
            style="margin-left: 15px"
            label="包唯一标识"
            prop="packageKey"
          >
            <el-input
              v-model="repairQuery.packageKey"
              style="width: 320px;"
              :disabled="!repairQuery.repoName"
              size="mini"
              placeholder="例如: helm://mysql 或 npm://xxx"
            />
          </el-form-item>
          <el-form-item style="margin-left: 15px">
            <el-button
              size="mini"
              type="primary"
              :loading="loading"
              :disabled="!canSubmit"
              @click="handleRepair"
            >开始修复</el-button>
          </el-form-item>
        </el-form>

        <div v-if="result" v-loading="loading">
          <el-descriptions
            title="修复结果"
            :column="4"
            border
            style="margin-bottom: 15px;"
          >
            <el-descriptions-item label="总数">
              <el-tag type="info">{{ result.total }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="已更新">
              <el-tag type="success">{{ result.updated }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="已跳过">
              <el-tag type="warning">{{ result.skipped }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="失败">
              <el-tag :type="result.failed > 0 ? 'danger' : 'info'">{{ result.failed }}</el-tag>
            </el-descriptions-item>
          </el-descriptions>

          <el-table
            v-if="result.failedItems && result.failedItems.length > 0"
            :data="result.failedItems"
            border
            size="mini"
            style="width: 100%;"
          >
            <el-table-column type="index" label="#" width="60" />
            <el-table-column prop="packageKey" label="包唯一标识" />
            <el-table-column prop="reason" label="失败原因" show-overflow-tooltip />
          </el-table>
        </div>
      </el-tab-pane>

      <!-- Tab 2：npm 历史版本迁移 -->
      <el-tab-pane label="历史版本迁移（npm packageKey）" name="historyVersion">
        <el-alert
          title="修复 npm 历史版本数据 —— 全库操作"
          type="error"
          :closable="false"
          style="margin-bottom: 15px;"
        >
          <div style="font-size: 12px; line-height: 1.6;">
            将扫描 <b>全部 npm 仓库</b>，把历史 <code>package_version.packageId</code> 字段规整为新版 packageKey 格式。<br>
            ⚠️ 此接口为<b>异步任务</b>，点击后立即返回，实际执行由后端后台进程处理，可通过 repository 服务日志跟踪进度。<br>
            ⚠️ 操作不可回滚，建议在业务低峰期执行。
          </div>
        </el-alert>

        <el-descriptions :column="1" border style="margin-bottom: 15px;">
          <el-descriptions-item label="接口">
            <code>GET /repository/api/version/history/repair</code>
          </el-descriptions-item>
          <el-descriptions-item label="执行模式">
            <el-tag size="mini" type="warning">异步（fire-and-forget）</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="影响范围">
            <el-tag size="mini" type="danger">全库所有 npm 包</el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <el-button
          type="danger"
          size="mini"
          :loading="historyLoading"
          @click="handleRepairHistoryVersion"
        >触发历史版本迁移</el-button>
        <span v-if="historyTriggeredAt" style="margin-left: 15px; color: #67C23A; font-size: 12px;">
          ✅ 已于 {{ historyTriggeredAt }} 触发，请查看后端日志跟踪进度。
        </span>
      </el-tab-pane>

      <!-- Tab 3：版本数重算 -->
      <el-tab-pane label="版本数重算（packages.versions）" name="versionCount">
        <el-alert
          title="修正包的版本数 —— 全库操作"
          type="error"
          :closable="false"
          style="margin-bottom: 15px;"
        >
          <div style="font-size: 12px; line-height: 1.6;">
            将扫描 <b>全部 package</b>，以 <b>package_version</b> 集合的真实计数覆盖 <code>packages.versions</code> 字段，
            修正因异常删除/并发写导致的版本计数偏差。<br>
            ⚠️ 此接口为<b>异步任务</b>，点击后立即返回，实际执行由后端后台进程处理。<br>
            ⚠️ 会遍历全部 package，耗时随规模增加。
          </div>
        </el-alert>

        <el-descriptions :column="1" border style="margin-bottom: 15px;">
          <el-descriptions-item label="接口">
            <code>PUT /repository/api/package/version/recount</code>
          </el-descriptions-item>
          <el-descriptions-item label="执行模式">
            <el-tag size="mini" type="warning">异步（fire-and-forget）</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="影响范围">
            <el-tag size="mini" type="danger">全库所有 package</el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <el-button
          type="danger"
          size="mini"
          :loading="recountLoading"
          @click="handleRepairVersionCount"
        >触发版本数重算</el-button>
        <span v-if="recountTriggeredAt" style="margin-left: 15px; color: #67C23A; font-size: 12px;">
          ✅ 已于 {{ recountTriggeredAt }} 触发，请查看后端日志跟踪进度。
        </span>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script>
import { searchProjects } from '@/api/project'
import { listRepositories } from '@/api/repository'
import {
  repairPackageMetadata,
  repairHistoryVersion,
  repairVersionCount
} from '@/api/packageRepair'

export default {
  name: 'PackageRepair',
  data() {
    return {
      activeTab: 'metadata',
      // Tab1: 元数据修复
      loading: false,
      repoCache: {},
      repairScope: 'single',
      repairQuery: {
        projectId: '',
        repoName: '',
        packageKey: ''
      },
      result: null,
      // Tab2: npm 历史版本迁移
      historyLoading: false,
      historyTriggeredAt: '',
      // Tab3: 版本数重算
      recountLoading: false,
      recountTriggeredAt: ''
    }
  },
  computed: {
    canSubmit() {
      if (!this.repairQuery.projectId || !this.repairQuery.repoName) return false
      if (this.repairScope === 'single' && !this.repairQuery.packageKey) return false
      return true
    },
    // 注意：rules 必须放在 computed 里，data() 执行阶段 methods 还未挂载，
    // 直接 this.validatePackageKey 会取到 undefined，导致 async-validator 校验失败，
    // 表现为点击"开始修复"没有任何反应（既不弹确认框也不发请求）。
    rules() {
      return {
        projectId: [{ required: true, message: '请输入项目ID', trigger: 'blur' }],
        repoName: [{ required: true, message: '请输入仓库名', trigger: 'blur' }],
        packageKey: [
          {
            validator: this.validatePackageKey,
            trigger: 'blur'
          }
        ]
      }
    }
  },
  methods: {
    validatePackageKey(rule, value, callback) {
      if (this.repairScope === 'single' && !value) {
        callback(new Error('请输入包唯一标识'))
      } else {
        callback()
      }
    },
    queryProjects(queryStr, cb) {
      searchProjects(queryStr).then(res => {
        cb(res.data.records)
      })
    },
    selectProject(project) {
      this.$refs['project-form-item'].resetField()
      this.repairQuery.projectId = project.name
      this.repairQuery.repoName = ''
    },
    queryRepositories(queryStr, cb) {
      const projectId = this.repairQuery.projectId
      let repositories = this.repoCache[projectId]
      if (!repositories) {
        listRepositories(projectId).then(res => {
          repositories = res.data
          this.repoCache[projectId] = repositories
          cb(this.doFilter(repositories, queryStr))
        })
      } else {
        cb(this.doFilter(repositories, queryStr))
      }
    },
    selectRepo(repo) {
      this.$refs['repo-form-item'].resetField()
      this.repairQuery.repoName = repo.name
    },
    doFilter(arr, queryStr) {
      return queryStr
        ? arr.filter(obj => obj.name.toLowerCase().indexOf(queryStr.toLowerCase()) !== -1)
        : arr
    },
    handleRepair() {
      this.$refs['form'].validate((valid) => {
        if (!valid) return
        const scopeText = this.repairScope === 'single'
          ? `package [${this.repairQuery.packageKey}]`
          : `仓库 [${this.repairQuery.projectId}/${this.repairQuery.repoName}] 下所有 package`
        this.$confirm(
          `将对 ${scopeText} 执行 latest / historyVersion 元数据修复，是否继续？`,
          '二次确认',
          {
            confirmButtonText: '确定修复',
            cancelButtonText: '取消',
            type: 'warning'
          }
        ).then(() => {
          this.doRepair()
        }).catch(() => {})
      })
    },
    doRepair() {
      this.loading = true
      const body = {
        projectId: this.repairQuery.projectId,
        repoName: this.repairQuery.repoName
      }
      if (this.repairScope === 'single') {
        body.packageKey = this.repairQuery.packageKey
      }
      repairPackageMetadata(body).then(res => {
        this.result = res.data
        this.$message({
          type: this.result.failed > 0 ? 'warning' : 'success',
          message: `修复完成：共 ${this.result.total} 个，更新 ${this.result.updated}，跳过 ${this.result.skipped}，失败 ${this.result.failed}`
        })
      }).catch(_ => {
        this.result = null
      }).finally(() => {
        this.loading = false
      })
    },
    // ===== 全库级危险操作：双重确认 =====
    async confirmDangerAction(title, message) {
      try {
        await this.$confirm(message, title, {
          confirmButtonText: '下一步',
          cancelButtonText: '取消',
          type: 'warning'
        })
        // 第二重：需要输入关键字才能触发
        const { value } = await this.$prompt(
          '请输入 "CONFIRM" 完成二次确认（区分大小写）',
          title,
          {
            confirmButtonText: '确定执行',
            cancelButtonText: '取消',
            inputPattern: /^CONFIRM$/,
            inputErrorMessage: '输入不匹配，请输入 CONFIRM'
          }
        )
        return value === 'CONFIRM'
      } catch (_) {
        return false
      }
    },
    formatNow() {
      const d = new Date()
      const pad = n => String(n).padStart(2, '0')
      return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
    },
    async handleRepairHistoryVersion() {
      const confirmed = await this.confirmDangerAction(
        '⚠️ 全库操作确认',
        '即将对【全部 npm 包】执行历史版本 packageKey 迁移，操作不可回滚，是否继续？'
      )
      if (!confirmed) return
      this.historyLoading = true
      try {
        await repairHistoryVersion()
        this.historyTriggeredAt = this.formatNow()
        this.$message.success('已触发历史版本迁移任务，后端异步执行中')
      } finally {
        this.historyLoading = false
      }
    },
    async handleRepairVersionCount() {
      const confirmed = await this.confirmDangerAction(
        '⚠️ 全库操作确认',
        '即将对【全部 package】重算 versions 字段，操作耗时较长，是否继续？'
      )
      if (!confirmed) return
      this.recountLoading = true
      try {
        await repairVersionCount()
        this.recountTriggeredAt = this.formatNow()
        this.$message.success('已触发版本数重算任务，后端异步执行中')
      } finally {
        this.recountLoading = false
      }
    }
  }
}
</script>

<style scoped>
.app-container {
  padding: 20px;
}
.app-container code {
  background: #f5f7fa;
  color: #e6a23c;
  padding: 2px 6px;
  border-radius: 3px;
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
}
</style>
