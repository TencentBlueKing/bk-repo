<template>
  <div class="app-container">
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

    <el-alert
      v-if="!result"
      title="修复说明"
      type="info"
      :closable="false"
      style="margin-bottom: 15px;"
    >
      <div>
        <p style="margin: 4px 0;">
          以 <b>package_version</b> 集合为权威数据源，重算 package 的
          <b>latest</b> 与 <b>historyVersion</b> 字段：
        </p>
        <ul style="margin: 4px 0 4px 20px; padding: 0;">
          <li>latest = ordinal DESC 排序取第一个版本</li>
          <li>historyVersion 全量覆盖为当前所有版本名集合，清理脏数据</li>
          <li>元数据已一致的 package 不会写库（记入 skipped）</li>
        </ul>
      </div>
    </el-alert>

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
  </div>
</template>

<script>
import { searchProjects } from '@/api/project'
import { listRepositories } from '@/api/repository'
import { repairPackageMetadata } from '@/api/packageRepair'

export default {
  name: 'PackageRepair',
  data() {
    return {
      loading: false,
      repoCache: {},
      repairScope: 'single',
      repairQuery: {
        projectId: '',
        repoName: '',
        packageKey: ''
      },
      result: null
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
    }
  }
}
</script>

<style scoped>
.app-container {
  padding: 20px;
}
</style>
