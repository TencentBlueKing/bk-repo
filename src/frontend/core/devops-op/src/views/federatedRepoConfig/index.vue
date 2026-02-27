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
      <el-form-item label="联邦仓库ID" style="margin-left: 15px">
        <el-input v-model="clientQuery.federationId" />
      </el-form-item>
      <el-form-item>
        <el-button
          size="mini"
          type="primary"
          :disabled="!clientQuery.repoName || !clientQuery.projectId"
          @click="changeRouteQueryParams()"
        >查询</el-button>
      </el-form-item>
    </el-form>
    <el-table v-loading="loading" :data="repos" style="width: 100%">
      <el-table-column type="expand">
        <template slot-scope="props">
          <el-table
            :data="props.row.federatedClusters"
            @selection-change="handleSelectionChange"
          >
            <el-table-column
              type="selection"
              width="55"
            />
            <el-table-column
              label="项目ID"
              width="250"
              prop="projectId"
            />
            <el-table-column
              label="仓库名"
              width="250"
              prop="repoName"
            />
            <el-table-column
              label="集群ID"
              width="350"
              prop="clusterId"
            />
            <el-table-column
              label="任务ID"
              width="250"
              prop="taskId"
            />
            <el-table-column
              label="记录ID"
              width="250"
              prop="recordId"
            />
            <el-table-column
              label="启用状态"
              width="180"
            >
              <template slot-scope="props">
                {{ props.row.enabled ? '是': '否'}}
              </template>
            </el-table-column>
            <el-table-column align="right">
              <template slot="header">
                <el-button type="danger" @click="deleteCluster(props.row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="名称" align="center" />
      <el-table-column prop="federationId" label="联邦仓库ID" align="center" />
      <el-table-column prop="projectId" label="项目ID" align="center" />
      <el-table-column prop="repoName" label="仓库名称" align="center" />
      <el-table-column prop="clusterId" label="集群ID" align="center" />
      <el-table-column prop="isFullSyncing" label="是否在全量同步" align="center">
        <template slot-scope="scope">
          {{ scope.row.isFullSyncing ? '是': '否'}}
        </template>
      </el-table-column>
      <el-table-column prop="lastFullSyncStartTime" label="上次全量同步开始时间" align="center">
        <template slot-scope="scope">
          {{ formatNormalDate(scope.row.lastFullSyncStartTime) }}
        </template>
      </el-table-column>
      <el-table-column prop="lastFullSyncEndTime" label="上次全量同步结束时间" align="center">
        <template slot-scope="scope">
          {{ formatNormalDate(scope.row.lastFullSyncStartTime) }}
        </template>
      </el-table-column>
      <el-table-column width="450" prop="record" align="center">
        <template slot="header">
          <el-button type="primary" @click="showCreateOrUpdateDialog(true)">创建</el-button>
        </template>
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="primary"
            @click="showCreateOrUpdateDialog(false,scope.$index,scope.row)"
          >
            编辑
          </el-button>
          <el-button
            size="mini"
            type="danger"
            @click="deleteFederation(scope.row, scope.$index)"
          >
            删除
          </el-button>
          <el-button
            size="mini"
            type="primary"
            @click="startSync(scope.row)"
          >
            开始全量同步
          </el-button>
          <el-button
            size="mini"
            type="primary"
            @click="endSync(scope.row)"
          >
            结束全量同步
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <edit-repo-config-dialog :create-mode="createMode" :visible.sync="showDialog" :updating-repo-config="updatingFederation" @updated="updated" />
  </div>
</template>
<script>
import { searchProjects } from '@/api/project'
import { listRepositories } from '@/api/repository'
import { formatNormalDate } from '@/utils/date'
import EditRepoConfigDialog from '@/views/federatedRepoConfig/components/EditRepoConfigDialog'
import {
  deleteFederation,
  federations,
  removeCluster,
  startFullSync,
  stopFullSync
} from '@/api/federatedRepository'

export default {
  name: 'FederationRepoConfig',
  components: { EditRepoConfigDialog },
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
        federationId: ''
      },
      repos: [],
      showDialog: false,
      updatingFederation: undefined,
      createMode: true,
      updatingIndex: undefined,
      multipleSelection: []
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
      this.currentPage = val
      this.changeRouteQueryParams(val)
    },
    changeRouteQueryParams() {
      const query = {}
      query.projectId = this.clientQuery.projectId
      query.repoName = this.clientQuery.repoName
      query.federationId = this.clientQuery.federationId
      this.$router.push({ path: '/federated/federated-repository', query: query })
    },
    onRouteUpdate(route) {
      const query = route.query
      const clientQuery = this.clientQuery
      if (this.clientQuery.projectId.trim() === '' && (query.projectId === '' || query.projectId === undefined)) {
        return
      }
      clientQuery.projectId = query.projectId ? query.projectId : ''
      clientQuery.federationId = query.federationId ? query.federationId : ''
      clientQuery.repoName = query.repoName ? query.repoName : ''
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
      promise = federations(clientQuery)
      promise.then(res => {
        this.repos = res.data
      }).catch(_ => {
        this.repos = []
      }).finally(() => {
        this.loading = false
      })
    },
    handleSelectionChange(val) {
      this.multipleSelection = val
    },
    showCreateOrUpdateDialog(create, index, federation) {
      this.showDialog = true
      this.createMode = create
      this.updatingIndex = index
      this.updatingFederation = federation
    },
    formatNormalDate(data) {
      return formatNormalDate(data)
    },
    deleteFederation(data, index) {
      this.$confirm(`是否确定删除`, '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        deleteFederation(data).then(() => {
          this.$message.success('删除成功')
          this.repos.splice(index, 1)
        })
      }).catch(() => {
        this.$message({
          type: 'info',
          message: '已取消'
        })
      })
    },
    startSync(data) {
      startFullSync(data).then(() => {
        this.$message.success('开始同步成功')
        this.reload()
      })
    },
    endSync(data) {
      stopFullSync(data).then(() => {
        this.$message.success('结束同步成功')
        this.reload()
      })
    },
    updated() {
      this.reload()
    },
    deleteCluster(baseInfo) {
      const selectRows = this.multipleSelection
      this.$confirm(`是否确定删除`, '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        removeCluster(selectRows, baseInfo).then(() => {
          this.$message.success('删除成功')
          this.reload()
        })
      }).catch(() => {
        this.$message({
          type: 'info',
          message: '已取消'
        })
      })
    }
  }
}
</script>

<style scoped>

</style>

<style>
</style>
