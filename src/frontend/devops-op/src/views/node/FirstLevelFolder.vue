<template>
  <div class="app-container">
    <el-form ref="form" :inline="true">
      <el-form-item ref="project-form-item" label="项目ID">
        <el-autocomplete
          v-model="folderQuery.projectId"
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
          v-model="folderQuery.repoName"
          class="inline-input"
          :fetch-suggestions="queryRepositories"
          :disabled="!folderQuery.projectId"
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
        <el-button size="mini" type="primary" :disabled="!folderQuery.repoName" @click="queryPage(1)">查询</el-button>
      </el-form-item>
    </el-form>
    <div class="app">
      <el-table v-loading="loading" :data="tableData" style="width: 100%">
        <el-table-column prop="path" label="路径" width="600" />
        <el-table-column prop="nodeNum" label="节点数" />
        <el-table-column prop="capSize" label="容量大小" />
      </el-table>
    </div>
    <div style="margin-top:20px">
      <el-pagination
        :current-page="folderQuery.pageNumber"
        :page-size="folderQuery.pageSize"
        layout="total, prev, pager, next, jumper"
        :total="total"
        @current-change="handleCurrentChange"
      />
    </div>
  </div>
</template>

<script>
import { DEFAULT_PAGE_SIZE } from '@/api/metrics'
import { searchProjects } from '@/api/project'
import { listRepositories } from '@/api/repository'
import { statisticalFirstLevelFolder } from '@/api/node'
import { convertFileSize } from '@/utils/file'

export default {
  beforeRouteUpdate(to, from, next) {
    this.onRouteUpdate(to)
    next()
  },
  data() {
    return {
      projectSelect: '',
      loading: false,
      repoSelect: '',
      tableData: [],
      projectOptions: [],
      repoOptions: [],
      total: 0,
      folderQuery: {
        path: '',
        totalSize: '',
        pageNumber: 1,
        pageSize: DEFAULT_PAGE_SIZE,
        projectId: '',
        repoName: ''
      }
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
      this.folderQuery.projectId = project.name
    },
    queryRepositories(queryStr, cb) {
      listRepositories(this.folderQuery.projectId).then(res => {
        cb(this.doFilter(res.data, queryStr))
      })
    },
    doFilter(arr, queryStr) {
      return queryStr ? arr.filter(obj => {
        return obj.name.toLowerCase().indexOf(queryStr.toLowerCase()) !== -1
      }) : arr
    },
    selectRepo(repo) {
      this.folderQuery.repoName = repo.name
    },
    handleCurrentChange(val) {
      this.currentPage = val
      this.queryPage(val)
    },
    queryPage(pageNum) {
      const query = {
        page: String(pageNum)
      }
      query.projectId = this.folderQuery.projectId
      query.repoName = this.folderQuery.repoName
      this.$router.push({ path: '/nodes/firstLevelFolder', query: query })
    },
    onRouteUpdate(route) {
      const query = route.query
      const folderQuery = this.folderQuery
      folderQuery.projectId = query.projectId ? query.projectId : ''
      folderQuery.repoName = query.repoName ? query.repoName : ''
      folderQuery.pageNumber = query.page ? Number(query.page) : 1
      if (folderQuery.repoName) {
        this.$nextTick(() => {
          this.queryFolder(folderQuery)
        })
      }
    },
    queryFolder(folderQuery) {
      this.loading = true
      let promise = null
      promise = statisticalFirstLevelFolder(folderQuery.projectId, folderQuery.repoName, folderQuery.pageNumber)
      promise.then(res => {
        for (let i = 0; i < res.data.records.length; i++) {
          res.data.records[i].capSize = convertFileSize(res.data.records[i].capSize)
        }
        this.tableData = res.data.records
        this.total = res.data.totalRecords
      }).catch(_ => {
        this.tableData = []
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
