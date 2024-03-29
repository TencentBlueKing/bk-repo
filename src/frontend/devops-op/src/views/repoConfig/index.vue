<template>
  <div class="app-container node-container">
    <el-form ref="form" :inline="true" :model="clientQuery">
      <el-form-item ref="project-form-item" label="项目ID" prop="projectId">
        <el-autocomplete
          v-model="clientQuery.projectId"
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
          v-model="clientQuery.repoName"
          class="inline-input"
          :fetch-suggestions="queryRepositories"
          :disabled="!clientQuery.projectId"
          placeholder="请输入仓库名"
          size="mini"
          @select="selectRepo"
        >
          <template slot-scope="{ item }">
            <div>{{ item.name }}</div>
          </template>
        </el-autocomplete>
      </el-form-item>
      <el-form-item label="项目类型" style="margin-left: 15px">
        <el-select v-model="clientQuery.type" placeholder="请选择" clearable>
          <el-option
            v-for="item in options"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button
          size="mini"
          type="primary"
          :disabled="!clientQuery.projectId"
          @click="changeRouteQueryParams(1)"
        >查询</el-button>
      </el-form-item>
    </el-form>
    <el-table v-loading="loading" :data="repos" style="width: 100%">
      <el-table-column prop="projectId" label="项目ID" align="center" />
      <el-table-column prop="name" label="仓库名称" align="center" />
      <el-table-column prop="type" label="仓库类型" align="center" />
      <el-table-column prop="quota" label="仓库配额" align="center">
        <template slot-scope="scope">
          <span v-if="scope.row.quota">
            {{ convertFileSize(scope.row.quota) }}
          </span>
          <span v-else>
            --
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="configuration" label="仓库清理配置" align="center">
        <el-table-column prop="retentionDays" label="保存天数" align="center">
          <template slot-scope="scope">
            <span
              v-if="scope.row.configuration.settings.cleanupStrategy
                && scope.row.configuration.settings.cleanupStrategy.cleanupType
                && scope.row.configuration.settings.cleanupStrategy.cleanupType === 'retentionDays'"
            >
              {{ scope.row.configuration.settings.cleanupStrategy.cleanupValue }}
            </span>
            <span v-else>
              --
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="retentionDate" label="保存日期" align="center">
          <template slot-scope="scope">
            <span
              v-if="scope.row.configuration.settings.cleanupStrategy
                && scope.row.configuration.settings.cleanupStrategy.cleanupType
                && scope.row.configuration.settings.cleanupStrategy.cleanupType === 'retentionDate'"
            >
              {{ formatNormalDate(scope.row.configuration.settings.cleanupStrategy.cleanupValue) }}
            </span>
            <span v-else>
              --
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="retentionNums" label="保存数目" align="center">
          <template slot-scope="scope">
            <span
              v-if="scope.row.configuration.settings.cleanupStrategy
                && scope.row.configuration.settings.cleanupStrategy.cleanupType
                && scope.row.configuration.settings.cleanupStrategy.cleanupType === 'retentionNums'"
            >
              {{ scope.row.configuration.settings.cleanupStrategy.cleanupValue }}
            </span>
            <span v-else>
              --
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="cleanTargets" label="清理目标" align="center">
          <template slot-scope="scope">
            <div
              v-if="scope.row.configuration.settings.cleanupStrategy
                && scope.row.configuration.settings.cleanupStrategy.cleanTargets
                && scope.row.configuration.settings.cleanupStrategy.cleanTargets.length !== 0"
            >
              <div
                v-for="(target, index) in scope.row.configuration.settings.cleanupStrategy.cleanTargets"
                :key="index"
              >
                {{ target }}
              </div>
            </div>
            <div v-else>
              --
            </div>
          </template>
        </el-table-column>
      </el-table-column>
      <el-table-column prop="record" label="操作" align="center">
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="primary"
            @click="showEdit(scope.row)"
          >
            编辑
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <div style="margin-top:20px">
      <el-pagination
        v-if="total>0"
        :current-page="clientQuery.pageNumber"
        :page-size="clientQuery.pageSize"
        layout="total, prev, pager, next, jumper"
        :total="total"
        @current-change="handleCurrentChange"
      />
    </div>
    <edit-repo-config-dialog :visible.sync="showDialog" :updating-repo-config="param" @updated="updated" />
  </div>
</template>
<script>
import { searchProjects } from '@/api/project'
import { listRepositories, pageRepositories } from '@/api/repository'
import { formatNormalDate } from '@/utils/date'
import { convertFileSize } from '@/utils/file'
import EditRepoConfigDialog from '@/views/repoConfig/components/editRepoConfigDialog'

export default {
  inject: ['reload'],
  name: 'RepoConfig',
  components: { EditRepoConfigDialog },
  data() {
    return {
      loading: false,
      projects: undefined,
      repoCache: {},
      total: 0,
      clientQuery: {
        projectId: '',
        repoName: '',
        pageNumber: 1,
        type: ''
      },
      repos: [],
      options: [
        { label: 'Generic', value: 'generic' },
        { label: 'DDC', value: 'ddc' },
        { label: 'Docker', value: 'docker' },
        { label: 'Maven', value: 'maven' },
        { label: 'Pypi', value: 'pypi' },
        { label: 'Npm', value: 'npm' },
        { label: 'Helm', value: 'helm' },
        { label: 'Composer', value: 'composer' },
        { label: 'Rpm', value: 'rpm' },
        { label: 'Git', value: 'git' },
        { label: 'Nuget', value: 'nuget' }
      ],
      showDialog: false,
      param: undefined
    }
  },
  mounted() {
    this.onRouteUpdate(this.$route)
  },
  beforeRouteUpdate(to, from, next) {
    this.onRouteUpdate(to)
    next()
  },
  methods: {
    queryProjects(queryStr, cb) {
      searchProjects(queryStr).then(res => {
        this.projects = res.data.records
        cb(this.projects)
      })
    },
    selectProject(project) {
      this.$refs['project-form-item'].resetField()
      this.clientQuery.projectId = project.name
    },
    queryRepositories(queryStr, cb) {
      let repositories = this.repoCache[this.clientQuery.projectId]
      if (!repositories) {
        listRepositories(this.clientQuery.projectId).then(res => {
          repositories = res.data
          this.repoCache[this.clientQuery.projectId] = repositories
          cb(this.doFilter(repositories, queryStr))
        })
      } else {
        cb(this.doFilter(repositories, queryStr))
      }
    },
    selectRepo(repo) {
      this.$refs['repo-form-item'].resetField()
      this.clientQuery.repoName = repo.name
    },
    doFilter(arr, queryStr) {
      return queryStr ? arr.filter(obj => {
        return obj.name.toLowerCase().indexOf(queryStr.toLowerCase()) !== -1
      }) : arr
    },
    handleCurrentChange(val) {
      this.currentPage = val
      this.changeRouteQueryParams(val)
    },
    changeRouteQueryParams(pageNum) {
      const query = {
        page: String(pageNum)
      }
      query.projectId = this.clientQuery.projectId
      query.repoName = this.clientQuery.repoName
      query.type = this.clientQuery.type
      this.$router.push({ path: '/repo-config', query: query })
    },
    onRouteUpdate(route) {
      const query = route.query
      const clientQuery = this.clientQuery
      if (this.clientQuery.projectId.trim() === '' && (query.projectId === '' || query.projectId === undefined)) {
        return
      }
      clientQuery.projectId = query.projectId ? query.projectId : ''
      clientQuery.type = query.type ? query.type : ''
      clientQuery.repoName = query.repoName ? query.repoName : ''
      clientQuery.pageNumber = query.page ? Number(query.page) : 1
      this.$nextTick(() => {
        this.queryClients(clientQuery)
      })
    },
    queryClients(clientQuery) {
      this.$refs['form'].validate((valid) => {
        if (valid) {
          this.doQueryClients(clientQuery)
        } else {
          return false
        }
      })
    },
    doQueryClients(clientQuery) {
      this.loading = true
      let promise = null
      promise = pageRepositories(clientQuery)
      promise.then(res => {
        this.repos = res.data.records
        this.total = res.data.totalRecords
      }).catch(_ => {
        this.repos = []
        this.total = 0
      }).finally(() => {
        this.loading = false
      })
    },
    formatNormalDate(data) {
      return formatNormalDate(data)
    },
    convertFileSize(size) {
      return convertFileSize(size)
    },
    showEdit(row) {
      this.param = row
      this.showDialog = true
    },
    updated() {
      this.reload()
    }
  }
}
</script>

<style scoped>

</style>

<style>
</style>
