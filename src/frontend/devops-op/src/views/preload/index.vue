<template>
  <div class="app-container node-container">
    <el-form ref="form" :inline="true" :model="clientQuery">
      <el-form-item label="配置类型" style="margin-left: 15px">
        <el-select v-model="clientQuery.type" placeholder="请选择">
          <el-option
            v-for="item in options"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item ref="project-form-item" label="项目ID" prop="projectId" :rules="[{ required: true, message: '项目ID不能为空'}]">
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
        :rules="[{ required: true, message: '仓库名称不能为空'}]"
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
          :disabled="!clientQuery.projectId"
          @click="changeRouteQueryParams(1)"
        >查询</el-button>
      </el-form-item>
      <el-form-item v-if="clientQuery.type === 'plan'">
        <el-button
          size="mini"
          type="danger"
          :disabled="!clientQuery.projectId"
          @click="handleDeleteAll()"
        >删除全部</el-button>
      </el-form-item>
    </el-form>
    <el-table v-loading="loading" :data="preloadData" style="width: 100%">
      <el-table-column prop="projectId" label="项目ID" align="center" />
      <el-table-column prop="repoName" label="仓库名称" align="center" />
      <el-table-column v-if="clientQuery.type!=='strategy'" key="fullPath" prop="fullPath" label="文件路径" align="center" />
      <el-table-column v-if="clientQuery.type!=='strategy'" key="sha256" prop="sha256" label="SHA256" align="center" />
      <el-table-column v-if="clientQuery.type!=='strategy'" key="size" prop="size" label="文件大小" align="center">
        <template slot-scope="scope">
          <span>
            {{ convertFileSize(scope.row.size) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column v-if="clientQuery.type!=='strategy'" key="credentialsKey" prop="credentialsKey" label="CredentialsKey" align="center" />
      <el-table-column v-if="clientQuery.type!=='strategy'" key="executeTime" prop="executeTime" label="预加载计划时间" align="center">
        <template slot-scope="scope">
          <span>
            {{ formatNormalDate(new Date(scope.row.executeTime)) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column v-if="clientQuery.type==='strategy'" key="fullPathRegex" prop="fullPathRegex" label="文件路径" align="center" />
      <el-table-column v-if="clientQuery.type==='strategy'" key="minSize" prop="minSize" label="最小大小" align="center" />
      <el-table-column v-if="clientQuery.type==='strategy'" key="recentSeconds" prop="recentSeconds" label="最近时间内(秒)" align="center" />
      <el-table-column v-if="clientQuery.type==='strategy'" key="preloadCron" prop="preloadCron" label="预加载时间" align="center" />
      <el-table-column v-if="clientQuery.type==='strategy'" key="type" prop="type" label="策略类型" align="center">
        <template slot-scope="scope">
          <span>
            {{ formatType(scope.row.type) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column align="right">
        <template slot="header">
          <el-button type="primary" @click="showEdit(true)">创建</el-button>
        </template>
        <template slot-scope="scope">
          <el-button
            v-if="clientQuery.type === 'strategy'"
            size="mini"
            type="primary"
            @click="showEdit(false, scope.row)"
          >
            编辑
          </el-button>
          <el-button
            v-if="!scope.row.default"
            size="mini"
            type="danger"
            @click="handleDelete(scope.row)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <div style="margin-top:20px">
      <el-pagination
        v-if="total>0 && clientQuery.type === 'plan'"
        :current-page="clientQuery.pageNumber"
        :page-size="clientQuery.pageSize"
        layout="total, prev, pager, next, jumper"
        :total="total"
        @current-change="handleCurrentChange"
      />
    </div>
    <edit-plan-config-dialog :visible.sync="showPlanDialog" :updating-plan-config="param" :create-mode="createMode" @updated="updated" />
    <edit-strategy-config-dialog :visible.sync="showStrategyDialog" :updating-strategy-config="param" :create-mode="createMode" @updated="updated" />
  </div>
</template>
<script>
import { searchProjects } from '@/api/project'
import { listRepositories } from '@/api/repository'
import { formatNormalDate } from '@/utils/date'
import { convertFileSize } from '@/utils/file'
import editPlanConfigDialog from '@/views/preload/components/EditPlanConfigDialog'
import editStrategyConfigDialog from '@/views/preload/components/EditStrategyConfigDialog'
import { deletePlan, deletePlans, deleteStrategy, queryPlans, queryStrategies } from '@/api/preload'

export default {
  name: 'PreloadConfig',
  components: { editPlanConfigDialog, editStrategyConfigDialog },
  inject: ['reload'],
  beforeRouteUpdate(to, from, next) {
    this.onRouteUpdate(to)
    next()
  },
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
        pageSize: 20,
        currentPage: 1,
        type: 'strategy'
      },
      options: [
        { label: '预加载策略', value: 'strategy' },
        { label: '预加载计划', value: 'plan' }
      ],
      preloadData: [],
      showPlanDialog: false,
      showStrategyDialog: false,
      param: undefined,
      createMode: false
    }
  },
  watch: {
    'clientQuery.type'(newType) {
      this.preloadData = []
      this.total = 0
      this.clientQuery.pageNumber = 1
      this.clientQuery.currentPage = 1
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
      query.projectId = this.clientQuery.projectId
      query.repoName = this.clientQuery.repoName
      query.type = this.clientQuery.type
      this.$router.push({ path: '/nodes/preloadConfig', query: query })
    },
    onRouteUpdate(route) {
      const query = route.query
      const clientQuery = this.clientQuery
      this.clientQuery.type = query.type ? query.type : this.clientQuery.type
      if (clientQuery.projectId.trim() === '' && (!query.projectId || query.projectId === '' || query.projectId === undefined)) {
        return
      }
      clientQuery.projectId = query.projectId ? query.projectId : ''
      clientQuery.repoName = query.repoName ? query.repoName : ''
      clientQuery.pageNumber = query.page ? Number(query.page) : 1
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
      if (clientQuery.type === 'plan') {
        promise = queryPlans(clientQuery)
      } else {
        promise = queryStrategies(clientQuery.projectId, clientQuery.repoName)
      }
      promise.then(res => {
        this.preloadData = res.data.records ? res.data.records : res.data
        this.total = res.data.totalRecords ? res.data.totalRecords : res.data.length
      })
    },
    formatNormalDate(data) {
      return formatNormalDate(data)
    },
    formatType(data) {
      if (data === 'CUSTOM') {
        return '自定义'
      } else if (data === 'CUSTOM_GENERATED') {
        return '系统自定义类型'
      } else {
        return '智能预加载策略'
      }
    },
    convertFileSize(size) {
      return convertFileSize(size)
    },
    showEdit(mode, row) {
      this.createMode = mode
      this.param = row
      if (this.clientQuery.type === 'plan') {
        this.showPlanDialog = true
      } else {
        this.showStrategyDialog = true
      }
    },
    handleDelete(row) {
      this.$confirm(`是否确定删除当前配置`, '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        let promise = null
        if (this.clientQuery.type === 'plan') {
          promise = deletePlan(row.projectId, row.repoName, row.id)
        } else {
          promise = deleteStrategy(row.projectId, row.repoName, row.id)
        }
        promise.then(() => {
          this.updated()
        })
      }).catch(() => {
        this.$message({
          type: 'info',
          message: '已取消'
        })
      })
    },
    handleDeleteAll() {
      this.$confirm(`是否确定删除当前条件下所有配置`, '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        const promise = deletePlans(this.clientQuery.projectId, this.clientQuery.repoName)
        promise.then(() => {
          this.updated()
        })
      }).catch(() => {
        this.$message({
          type: 'info',
          message: '已取消'
        })
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
