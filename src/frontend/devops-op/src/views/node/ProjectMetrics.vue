<template>
  <div class="app-container">
    <el-form ref="form" :inline="true">
      <el-form-item ref="project-form-item" label="项目ID">
        <el-autocomplete
          v-model="projectId"
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
      <el-form-item>
        <el-button size="mini" type="primary" @click="queryPage(1)">查询</el-button>
      </el-form-item>
    </el-form>
    <el-table
      :data="projectData"
      style="width: 100%"
    >
      <el-table-column type="expand">
        <template slot-scope="props">
          <el-table
            :data="props.row.repoMetrics"
          >
            <el-table-column
              label="仓库名称"
              prop="repoName"
            />
            <el-table-column
              label="数量"
              prop="num"
            />
            <el-table-column
              label="大小"
              prop="size"
            />
            <el-table-column
              label="存储凭据"
              prop="credentialsKey"
            />
          </el-table>
        </template>
      </el-table-column>
      <el-table-column
        prop="projectId"
        label="项目ID"
      />
      <el-table-column
        label="创建时间"
        prop="createdDate"
      >
        <template slot-scope="scope">
          <span>{{ formatNormalDate(scope.row.createdDate) }}</span>
        </template>
      </el-table-column>
      <el-table-column
        label="节点数"
        prop="nodeNum"
      />
      <el-table-column
        label="容量大小"
        prop="capSize"
      />
    </el-table>
    <div v-if="total>0" style="margin-top:20px">
      <el-pagination
        :current-page="query.pageNumber"
        :page-size="query.pageSize"
        layout="total, prev, pager, next, jumper"
        :total="total"
        @current-change="handleCurrentChange"
      />
    </div>
  </div>
</template>
<script>

import { queryProjectMetrics } from '@/api/projectMetrics'
import { formatNormalDate } from '@/utils/date'
import { searchProjects } from '@/api/project'

export default {
  name: 'ProjectMetrics',
  data() {
    return {
      loading: true,
      projectData: [],
      projectId: this.$route.query.projectId ? this.$route.query.projectId : '',
      total: 0,
      query: {
        pageNumber: 1,
        pageSize: 10
      }
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
      this.projectId = project.name
    },
    handleCurrentChange(val) {
      this.currentPage = val
      this.queryPage(val)
    },
    queryPage(pageNum) {
      const query = {
        pageNumber: String(pageNum)
      }
      query.projectId = this.projectId
      this.$router.push({ path: '/nodes/ProjectMetrics', query: query })
    },
    onRouteUpdate(route) {
      const query = route.query
      const projectMetricsQuery = {
        pageNumber: String(query.pageNumber ? Number(query.pageNumber) : 1),
        projectId: query.projectId ? query.projectId : ''
      }
      this.$nextTick(() => {
        this.queryMetrics(projectMetricsQuery)
      })
    },
    queryMetrics(projectMetricsQuery) {
      this.loading = true
      let promise = null
      promise = queryProjectMetrics(projectMetricsQuery.projectId, projectMetricsQuery.pageNumber)
      promise.then(res => {
        this.projectData = res.data.records
        this.total = res.data.totalRecords
      }).catch(_ => {
        this.projectData = []
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
