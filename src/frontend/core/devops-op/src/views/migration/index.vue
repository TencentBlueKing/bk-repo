<template>
  <div class="app-container node-container">
    <el-form ref="form" :inline="true" :model="clientQuery">
      <el-form-item label="任务状态" style="margin-left: 15px">
        <el-select v-model="clientQuery.state" clearable placeholder="请选择" @change="changeRouteQueryParams(1)">
          <el-option
            v-for="item in options"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
    </el-form>
    <el-table v-loading="loading" :data="preloadData" style="width: 100%">
      <el-table-column prop="projectId" label="项目ID" align="center" />
      <el-table-column prop="repoName" label="仓库名称" align="center" />
      <el-table-column prop="srcStorageKey" label="源存储" align="center" />
      <el-table-column prop="dstStorageKey" label="目标存储" align="center" />
      <el-table-column key="state" prop="state" label="状态" align="center">
        <template slot-scope="scope">
          <span>
            {{ formatState(scope.row.state) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column key="executingOn" prop="executingOn" label="运行实例ID" align="center" />
      <el-table-column prop="startDate" label="任务开始时间" width="160" align="center">
        <template slot-scope="scope">
          <span>
            {{ formatNormalDate(scope.row.startDate) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column key="progress" label="进度" align="center">
        <template slot-scope="scope">
          <span>
            {{ formatProgress(scope.row) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column key="lastMigratedNodeId" prop="lastMigratedNodeId" label="最后的节点ID" align="center" />
      <el-table-column key="createdBy" prop="createdBy" label="创建人" align="center" />
      <el-table-column prop="createdDate" label="创建时间" width="160" align="center">
        <template slot-scope="scope">
          <span>
            {{ formatNormalDate(scope.row.createdDate) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column key="lastModifiedBy" prop="lastModifiedBy" label="修改人" align="center" />
      <el-table-column prop="lastModifiedDate" label="修改时间" width="160" align="center">
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
    <create-migrate-task-dialog :visible.sync="showEditDialog" :updating-task-config="param" :create-mode="createMode" @updated="updated" />
  </div>
</template>
<script>
import { formatNormalDate } from '@/utils/date'
import { convertFileSize } from '@/utils/file'
import CreateMigrateTaskDialog from '@/views/migration/components/CreateMigrateTaskDialog'
import { queryMigrateTask } from '@/api/migrate'
export default {
  name: 'MigrateRepoStorageTaskConfig',
  components: { CreateMigrateTaskDialog },
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
        state: ''
      },
      options: [
        { label: '待执行', value: 'PENDING' },
        { label: '迁移中', value: 'MIGRATING' },
        { label: '迁移完成', value: 'MIGRATE_FINISHED' },
        { label: '矫正数据中', value: 'CORRECTING' },
        { label: '矫正完成', value: 'CORRECT_FINISHED' },
        { label: '失败数据重传', value: 'MIGRATING_FAILED_NODE' },
        { label: '失败数据重传完成', value: 'MIGRATE_FAILED_NODE_FINISHED' },
        { label: '迁移结束', value: 'FINISHED' }
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
    handleCurrentChange(val) {
      this.clientQuery.currentPage = val
      this.changeRouteQueryParams(val)
    },
    changeRouteQueryParams(pageNum) {
      const query = {
        page: String(pageNum)
      }
      query.state = this.clientQuery.state
      this.$router.push({ path: '/migration-config', query: query })
    },
    onRouteUpdate(route) {
      const query = route.query
      const clientQuery = this.clientQuery
      clientQuery.state = query.state ? query.state : null
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
      promise = queryMigrateTask(clientQuery)
      promise.then(res => {
        this.preloadData = res.data.records ? res.data.records : res.data
        this.total = res.data.totalRecords ? res.data.totalRecords : res.data.length
      })
    },
    formatNormalDate(data) {
      return formatNormalDate(data)
    },
    formatProgress(data) {
      if (!data.totalCount || data.totalCount === 0 || !data.migratedCount) {
        return '0%(0/0)'
      }
      return Math.round(data.migratedCount / data.totalCount * 10000) / 100 + '%(' + data.migratedCount + '/' + data.totalCount + ')'
    },
    formatState(data) {
      for (let i = 0; i < this.options.length; i++) {
        if (data === this.options[i].value) {
          return this.options[i].label
        }
      }
      return data
    },
    convertFileSize(size) {
      return convertFileSize(size)
    },
    showEdit(mode, row) {
      this.createMode = mode
      this.param = row
      this.showEditDialog = true
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
