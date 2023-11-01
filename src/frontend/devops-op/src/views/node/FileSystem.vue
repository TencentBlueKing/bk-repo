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
      <el-form-item>
        <el-button
          size="mini"
          type="primary"
          @click="changeRouteQueryParams(1)"
        >查询</el-button>
      </el-form-item>
    </el-form>
    <el-table v-loading="loading" :data="clients" style="width: 100%">
      <el-table-column prop="projectId" label="项目ID" />
      <el-table-column prop="repoName" label="仓库名称" />
      <el-table-column prop="mountPoint" label="挂载点" />
      <el-table-column prop="userId" label="用户ID" />
      <el-table-column prop="ip" label="IP" />
      <el-table-column prop="version" label="版本" />
      <el-table-column prop="os" label="操作系统" />
      <el-table-column prop="arch" label="架构" />
      <el-table-column prop="online" label="是否在线" >
        <template slot-scope="scope">
          {{ scope.row.online ? "是":"否" }}
        </template>
      </el-table-column>
      <el-table-column prop="heartbeatTime" label="心跳时间">
        <template slot-scope="scope">
          <span>{{ formatNormalDate(scope.row.heartbeatTime) }}</span>
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
  </div>
</template>
<script>
import { queryFileSystemClient } from '@/api/fileSystem'
import { searchProjects } from '@/api/project'
import { listRepositories } from '@/api/repository'
import { formatNormalDate } from '@/utils/date'

export default {
  name: 'FileSystem',
  data() {
    return {
      loading: false,
      projects: undefined,
      repoCache: {},
      total: 0,
      clientQuery: {
        projectId: '',
        repoName: '',
        pageNumber: 1
      },
      clients: []
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
      this.$router.push({ path: '/nodes/FileSystem', query: query })
    },
    onRouteUpdate(route) {
      const query = route.query
      const clientQuery = this.clientQuery
      clientQuery.projectId = query.projectId ? query.projectId : ''
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
      promise = queryFileSystemClient(clientQuery.projectId, clientQuery.repoName, clientQuery.pageNumber)
      promise.then(res => {
        this.clients = res.data.records
        this.total = res.data.totalRecords
      }).catch(_ => {
        this.clients = []
        this.total = 0
      }).finally(() => {
        this.loading = false
      })
    },
    formatNormalDate(data) {
      return formatNormalDate(data)
    }
  }
}
</script>

<style scoped>

</style>

<style>
</style>
