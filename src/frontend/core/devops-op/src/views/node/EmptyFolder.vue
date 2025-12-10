<template>
  <div class="app-container node-container">
    <el-form ref="form" :rules="rules" :inline="true" :model="nodeQuery">
      <el-form-item ref="project-form-item" label="项目ID" prop="projectId">
        <el-autocomplete
          v-model="nodeQuery.projectId"
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
          v-model="nodeQuery.repoName"
          class="inline-input"
          :fetch-suggestions="queryRepositories"
          :disabled="!nodeQuery.projectId"
          placeholder="请输入仓库名"
          size="mini"
          @select="selectRepo"
        >
          <template slot-scope="{ item }">
            <div>{{ item.name }}</div>
          </template>
        </el-autocomplete>
      </el-form-item>
      <el-form-item style="margin-left: 15px" label="路径" prop="path">
        <el-input
          v-model="nodeQuery.path"
          style="width: 500px;"
          :disabled="!nodeQuery.repoName"
          size="mini"
          placeholder="请输入文件或目录路径"
          @keyup.enter.native="changeRouteQueryParams(true)"
        />
      </el-form-item>
      <el-form-item>
        <el-button
          size="mini"
          type="primary"
          :disabled="!nodeQuery.path"
          @click="changeRouteQueryParams(true)"
        >查询</el-button>
        <el-button
          size="mini"
          type="primary"
          :disabled="!nodeQuery.path"
          @click="changeRouteDeleteParams()"
        >删除</el-button>
      </el-form-item>
    </el-form>
    <el-table v-loading="loading" :data="nodes" style="width: 100%">
      <el-table-column prop="fullPath" label="空目录路径" />
    </el-table>
  </div>
</template>
<script>
import { queryEmptyFolder, deleteEmptyFolder } from '@/api/node'
import { searchProjects } from '@/api/project'
import { listRepositories } from '@/api/repository'

export default {
  name: 'EmptyFolder',
  beforeRouteUpdate(to, from, next) {
    this.onRouteUpdate(to)
    next()
  },
  data() {
    return {
      rules: {
        path: [{ validator: this.validatePath, trigger: 'blur' }]
      },
      loading: false,
      projects: undefined,
      repoCache: {},
      preProjectId: '',
      preRepoName: '',
      prePath: '',
      nodeQuery: {
        projectId: '',
        repoName: '',
        path: ''
      },
      nodes: []
    }
  },
  mounted() {
    this.onRouteUpdate(this.$route)
  },
  methods: {
    validateName(rule, value, callback) {
      this.regexValidate(value, /^[\w-]+$/, callback)
    },
    validatePath(rule, value, callback) {
      if (value) {
        this.regexValidate(value, /^(\/|(\/[^\/]+)+\/?)$/, callback)
      }
    },
    regexValidate(value, regex, callback) {
      if (regex.test(value)) {
        callback()
      } else {
        callback(new Error('格式错误'))
      }
    },
    queryProjects(queryStr, cb) {
      searchProjects(queryStr).then(res => {
        this.projects = res.data.records
        cb(this.projects)
      })
    },
    selectProject(project) {
      this.$refs['project-form-item'].resetField()
      this.nodeQuery.projectId = project.name
    },
    queryRepositories(queryStr, cb) {
      let repositories = this.repoCache[this.nodeQuery.projectId]
      if (!repositories) {
        listRepositories(this.nodeQuery.projectId).then(res => {
          repositories = res.data
          this.repoCache[this.nodeQuery.projectId] = repositories
          cb(this.doFilter(repositories, queryStr))
        })
      } else {
        cb(this.doFilter(repositories, queryStr))
      }
    },
    selectRepo(repo) {
      this.$refs['repo-form-item'].resetField()
      this.nodeQuery.repoName = repo.name
    },
    doFilter(arr, queryStr) {
      return queryStr ? arr.filter(obj => {
        return obj.name.toLowerCase().indexOf(queryStr.toLowerCase()) !== -1
      }) : arr
    },
    changeRouteDeleteParams() {
      const projectId = this.nodeQuery.projectId
      const repoName = this.nodeQuery.repoName
      const parentPath = this.nodeQuery.path
      deleteEmptyFolder(projectId, repoName, parentPath).then(() => {
        if (this.preProjectId === projectId && this.prePath === parentPath && this.preRepoName === repoName) {
          queryEmptyFolder(projectId, repoName, parentPath).then(res => {
            this.nodes = res.data
          })
        } else {
          this.changeRouteQueryParams()
        }
      })
    },
    changeRouteQueryParams(resetPage = false) {
      const query = {}
      query.projectId = this.nodeQuery.projectId
      query.repoName = this.nodeQuery.repoName
      query.path = this.nodeQuery.path
      this.$router.push({ path: '/ops/nodes/emptyFolder', query: query })
    },
    onRouteUpdate(route) {
      const query = route.query
      const nodeQuery = this.nodeQuery
      nodeQuery.projectId = query.projectId ? query.projectId : ''
      nodeQuery.repoName = query.repoName ? query.repoName : ''
      nodeQuery.path = query.path ? query.path : ''
      this.prePath = nodeQuery.path
      this.preProjectId = nodeQuery.projectId
      this.preRepoName = nodeQuery.repoName
      this.$nextTick(() => {
        this.queryNodes(nodeQuery)
      })
    },
    queryNodes(nodeQuery) {
      this.$refs['form'].validate((valid) => {
        if (valid) {
          this.doQueryNodes(nodeQuery)
        } else {
          return false
        }
      })
    },
    doQueryNodes(nodeQuery) {
      this.loading = true
      let promise = null
      promise = queryEmptyFolder(nodeQuery.projectId, nodeQuery.repoName, nodeQuery.path)
      promise.then(res => {
        this.nodes = res.data
      }).catch(_ => {
        this.nodes = []
        this.total = 0
      }).finally(() => {
        this.loading = false
      })
    }
  }
}
</script>

<style scoped>

</style>

<style>
</style>
