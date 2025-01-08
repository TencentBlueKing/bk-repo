<template>
  <div class="app-container node-container">
    <el-form ref="form" :inline="true" :model="clientQuery">
      <el-form-item label="任务状态" style="margin-left: 15px">
        <el-select v-model="clientQuery.state" clearable placeholder="请选择">
          <el-option
            v-for="item in options"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="任务类型" style="margin-left: 15px">
        <el-select v-model="clientQuery.taskType" clearable placeholder="请选择">
          <el-option
            v-for="item in taskOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
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
        label="仓库名称"
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
      <el-form-item>
        <el-button
          size="mini"
          type="primary"
          @click="changeRouteQueryParams(1)"
        >查询</el-button>
      </el-form-item>
    </el-form>
    <el-table v-loading="loading" :data="preloadData" style="width: 100%">
      <el-table-column type="expand">
        <template slot-scope="props">
          <el-table
            :data="props.row.content.packages"
            style="width: 100%"
          >
            <el-table-column
              label="包KEY"
              prop="packageKey"
            />
            <el-table-column
              label="包kEY正则"
              prop="packageKeyRegex"
            />
            <el-table-column
              label="包版本"
              prop="versions"
            >
              <template slot-scope="scope">
                {{ scope.row.versions }}
              </template>
            </el-table-column>
            <el-table-column
              label="包版本正则"
              prop="versionRegex"
            />
            <el-table-column
              label="排除包版本"
              prop="excludeVersions"
            >
              <template slot-scope="scope">
                {{ scope.row.excludeVersions }}
              </template>
            </el-table-column>
          </el-table>
          <el-divider />
          <el-table
            :data="props.row.content.paths"
            style="width: 100%"
          >
            <el-table-column
              label="路径"
              prop="path"
            />
            <el-table-column
              label="路径正则"
              prop="pathRegex"
            />
            <el-table-column
              label="排除路径"
              prop="excludePath"
            >
              <template slot-scope="scope">
                <span>
                  {{ scope.row.excludePath }}
                </span>
              </template>
            </el-table-column>
          </el-table>
        </template>
      </el-table-column>
      <el-table-column prop="projectId" label="项目ID" align="center" />
      <el-table-column prop="repoName" label="仓库名称" align="center" />
      <el-table-column prop="createdDate" label="创建时间" align="center">
        <template slot-scope="scope">
          <span>
            {{ formatNormalDate(scope.row.createdDate) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column key="state" prop="state" label="状态" align="center">
        <template slot-scope="scope">
          <span>
            {{ formatState(scope.row.state) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column key="createdBy" prop="createdBy" label="创建人" align="center" />
      <el-table-column prop="separationDate" label="降冷临界时间" align="center">
        <template slot-scope="scope">
          <span>
            {{ formatNormalDate(scope.row.separationDate) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="separationDate" label="任务开始时间" align="center">
        <template slot-scope="scope">
          <span>
            {{ formatNormalDate(scope.row.separationDate) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="separationDate" label="任务结束时间" align="center">
        <template slot-scope="scope">
          <span>
            {{ formatNormalDate(scope.row.separationDate) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column key="successCount" prop="successCount" label="成功数" align="center" />
      <el-table-column key="failedCount" prop="failedCount" label="失败数" align="center" />
      <el-table-column key="skippedCount" prop="skippedCount" label="忽略数" align="center" />
      <el-table-column key="lastModifiedBy" prop="lastModifiedBy" label="修改人" align="center" />
      <el-table-column prop="lastModifiedDate" label="修改时间" align="center">
        <template slot-scope="scope">
          <span>
            {{ formatNormalDate(scope.row.lastModifiedDate) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column align="right">
        <template slot="header">
          <el-button type="primary" @click="showEdit(true)">创建</el-button>
        </template>
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="primary"
            @click="refreshState(scope.row)"
          >
            重置
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
    <create-or-update-separation-task-dialog :visible.sync="showEditDialog" :updating-task-config="param" :create-mode="createMode" @updated="updated" />
  </div>
</template>
<script>
import { formatNormalDate } from '@/utils/date'
import { convertFileSize } from '@/utils/file'
import { querySeparateTask, updateSeparateTask } from '@/api/separate'
import CreateOrUpdateSeparationTaskDialog from '@/views/separation/components/CreateOrUpdateSeparationTaskDialog'
import { searchProjects } from '@/api/project'
import { listRepositories } from '@/api/repository'
export default {
  name: 'SeparationTaskConfig',
  components: { CreateOrUpdateSeparationTaskDialog },
  inject: ['reload'],
  beforeRouteUpdate(to, from, next) {
    this.onRouteUpdate(to)
    next()
  },
  data() {
    return {
      loading: false,
      repoCache: {},
      total: 0,
      clientQuery: {
        pageNumber: 1,
        pageSize: 20,
        currentPage: 1,
        state: '',
        projectId: '',
        repoName: '',
        taskType: ''
      },
      options: [
        { label: '待执行', value: 'PENDING' },
        { label: '执行中', value: 'RUNNING' },
        { label: '执行完成', value: 'FINISHED' }
      ],
      taskOptions: [
        { label: '降冷', value: 'SEPARATE' },
        { label: '恢复', value: 'RESTORE' }
      ],
      preloadData: [],
      showEditDialog: false,
      param: undefined,
      createMode: false,
      projects: undefined
    }
  },
  mounted() {
    this.onRouteUpdate(this.$route)
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
      this.clientQuery.currentPage = val
      this.changeRouteQueryParams(val)
    },
    changeRouteQueryParams(pageNum) {
      const query = {
        page: String(pageNum)
      }
      query.state = this.clientQuery.state
      query.projectId = this.clientQuery.projectId
      query.repoName = this.clientQuery.repoName
      query.taskType = this.clientQuery.taskType
      this.$router.push({ path: '/separation-config', query: query })
    },
    onRouteUpdate(route) {
      const query = route.query
      const clientQuery = this.clientQuery
      clientQuery.state = query.state ? query.state : null
      clientQuery.pageNumber = query.page ? Number(query.page) : 1
      clientQuery.projectId = query.projectId ? query.projectId : null
      clientQuery.repoName = query.repoName ? query.repoName : null
      clientQuery.taskType = query.taskType ? query.taskType : null
      this.$nextTick(() => {
        this.queryClients(clientQuery)
      })
    },
    queryClients(clientQuery) {
      if (this.$refs['form']) {
        this.$refs['form'].validate((valid) => {
          if (valid) {
            this.doQueryClients(clientQuery)
          } else {
            return false
          }
        })
      }
    },
    doQueryClients(clientQuery) {
      let promise = null
      promise = querySeparateTask(clientQuery)
      promise.then(res => {
        this.preloadData = res.data.records ? res.data.records : res.data
        this.total = res.data.totalRecords ? res.data.totalRecords : res.data.length
      })
    },
    formatNormalDate(data) {
      return formatNormalDate(data)
    },
    formatState(data) {
      for (let i = 0; i < this.options.length; i++) {
        if (data === this.options[i].value) {
          return this.options[i].label
        }
      }
      return '请求中'
    },
    convertFileSize(size) {
      return convertFileSize(size)
    },
    showEdit(mode, row) {
      this.createMode = mode
      this.param = row
      this.showEditDialog = true
    },
    refreshState(row) {
      updateSeparateTask(row.id).then(() => {
        this.updated()
      })
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
