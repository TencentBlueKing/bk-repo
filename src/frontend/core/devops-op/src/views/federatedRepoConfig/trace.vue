<template>
  <div class="app-container">
    <el-form ref="form" :inline="true" :model="clientQuery">
      <el-form-item ref="project-form-item" label="任务Key">
        <el-input v-model="clientQuery.taskKey" size="small" />
      </el-form-item>
      <el-form-item ref="project-form-item" label="远程集群ID">
        <el-input v-model="clientQuery.remoteClusterId" size="small" />
      </el-form-item>
      <el-form-item ref="project-form-item" label="项目ID">
        <el-input v-model="clientQuery.projectId" size="small" />
      </el-form-item>
      <el-form-item ref="project-form-item" label="本地仓库名">
        <el-input v-model="clientQuery.localRepoName" size="small" />
      </el-form-item>
      <el-form-item ref="project-form-item" label="远程项目ID">
        <el-input v-model="clientQuery.remoteProjectId" size="small" />
      </el-form-item>
      <el-form-item ref="project-form-item" label="远程仓库名称">
        <el-input v-model="clientQuery.remoteRepoName" size="small" />
      </el-form-item>
      <el-form-item ref="project-form-item" label="最大重试次数">
        <el-input-number v-model="clientQuery.maxRetryCount" controls-position="right" />
      </el-form-item>
      <el-form-item ref="project-form-item" label="重试中" style="margin-left: 10px">
        <el-select
          v-model="clientQuery.retrying"
          clearable
          style="margin-left: 10px;"
          placeholder="请选择"
        >
          <el-option
            v-for="item in retryOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item ref="project-form-item" label="排序字段" style="margin-left: 10px">
        <el-select
          v-model="clientQuery.sortField"
          clearable
          style="margin-left: 10px;"
          placeholder="请选择"
        >
          <el-option
            v-for="item in sortFieldOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item ref="project-form-item" label="排序方向" style="margin-left: 10px">
        <el-select
          v-model="clientQuery.sortDirection"
          clearable
          style="margin-left: 10px;"
          :disabled="!clientQuery.sortField"
          placeholder="请选择"
        >
          <el-option
            v-for="item in sortDirectionOptions"
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
          @click="handleCurrentChange(1)"
        >查询</el-button>
      </el-form-item>
    </el-form>
    <el-table
      ref="eventTable"
      v-loading="loading"
      :data="trackRecords"
      style="width: 100%; margin-top: 20px"
      :max-height="825"
      @selection-change="handleSelectionChange"
    >
      <el-table-column
        type="selection"
        width="55"
      />
      <el-table-column
        prop="messageId"
        label="消息ID"
        width="150"
      />
      <el-table-column
        prop="taskKey"
        label="关联任务KEY"
        width="150"
      />
      <el-table-column
        prop="remoteClusterId"
        label="远程集群id"
        width="150"
      />
      <el-table-column
        prop="projectId"
        label="项目ID"
        width="150"
      />
      <el-table-column
        prop="localRepoName"
        label="本地仓库名称"
        width="150"
      />
      <el-table-column
        prop="remoteProjectId"
        label="远程项目ID"
        width="150"
      />
      <el-table-column
        prop="remoteRepoName"
        label="远程仓库名称"
        width="150"
      />
      <el-table-column
        prop="nodePath"
        label="节点路径"
        width="150"
      />
      <el-table-column
        prop="createdDate"
        label="开始时间"
        width="160"
      >
        <template slot-scope="scope">
          {{ formatNormalDate(scope.row.createdDate) }}
        </template>
      </el-table-column>
      <el-table-column
        prop="retryCount"
        label="重试次数"
        width="150"
      />
      <el-table-column
        label="是否在重试"
        width="130"
      >
        <template slot-scope="props">
          {{ props.row.retrying ? '是': '否' }}
        </template>
      </el-table-column>
      <el-table-column
        prop="failureReason"
        label="失败原因"
        width="200"
      />
      <el-table-column
        prop="lastModifiedDate"
        label="上一次更新时间"
        width="160"
      >
        <template slot-scope="scope">
          {{ formatNormalDate(scope.row.lastModifiedDate) }}
        </template>
      </el-table-column>
      <el-table-column align="center">
        <template slot="header">
          <el-button type="primary" @click="doDelete()">删除</el-button>
        </template>
        <template slot-scope="scope">
          <el-button type="primary" size="mini" @click="doRetry(scope.row)">重试</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div style="margin-top:20px">
      <el-pagination
        v-if="pageOption.total>0"
        :current-page="pageOption.pageNumber"
        :page-size="pageOption.pageSize"
        layout="total, prev, pager, next, jumper"
        :total="pageOption.total"
        @current-change="handleCurrentChange"
      />
    </div>
  </div>
</template>

<script>

import { deleteTrackRecord, retryTrackRecord, trackRecords } from '@/api/federatedTrack'
import { formatNormalDate } from '@/utils/date'

export default {
  name: 'Event',
  inject: ['reload'],
  beforeRouteUpdate(to, from, next) {
    this.onRouteUpdate(to)
    next()
  },
  data() {
    return {
      loading: false,
      trackRecords: [],
      clientQuery: {
        taskKey: '',
        remoteClusterId: '',
        projectId: '',
        localRepoName: '',
        remoteProjectId: '',
        remoteRepoName: '',
        retrying: '',
        maxRetryCount: '',
        sortField: '',
        sortDirection: ''
      },
      pageOption: {
        pageNumber: 1,
        pageSize: 20,
        total: 0
      },
      options: [
        {
          value: 'NORMAL',
          label: '一般事件'
        },
        {
          value: 'FEDERATION',
          label: '联邦事件'
        }
      ],
      retryOptions: [
        {
          value: 'true',
          label: '是'
        }, {
          value: 'false',
          label: '否'
        }
      ],
      sortFieldOptions: [
        {
          value: 'createdDate',
          label: '创建时间'
        }, {
          value: 'lastModifiedDate',
          label: '最近修改时间'
        }, {
          value: 'retryCount',
          label: '重试次数'
        }
      ],
      sortDirectionOptions: [
        {
          value: 'ASC',
          label: '正序'
        }, {
          value: 'DESC',
          label: '反序'
        }
      ],
      multipleSelection: []
    }
  },
  mounted() {
    this.onRouteUpdate(this.$route)
  },
  created() {
    this.handleCurrentChange(1)
  },
  methods: {
    formatNormalDate,
    handleCurrentChange(val) {
      this.pageOption.pageNumber = val
      this.changeRouteQueryParams(val)
    },
    changeRouteQueryParams(pageNum) {
      const query = {
        page: String(pageNum)
      }
      query.taskKey = this.clientQuery.taskKey
      query.remoteClusterId = this.clientQuery.remoteClusterId
      query.localRepoName = this.clientQuery.localRepoName
      query.remoteProjectId = this.clientQuery.remoteProjectId
      query.remoteRepoName = this.clientQuery.remoteRepoName
      query.retrying = this.clientQuery.retrying
      query.maxRetryCount = this.clientQuery.maxRetryCount
      query.sortField = this.clientQuery.sortField
      query.sortDirection = this.clientQuery.sortDirection
      this.$router.push({ path: '/federated/trace', query: query })
    },
    onRouteUpdate(route) {
      const query = route.query
      this.clientQuery.taskKey = query.taskKey ? query.taskKey : ''
      this.clientQuery.remoteClusterId = query.remoteClusterId ? query.remoteClusterId : ''
      this.clientQuery.localRepoName = query.localRepoName ? query.localRepoName : ''
      this.clientQuery.remoteProjectId = query.remoteProjectId ? query.remoteProjectId : ''
      this.clientQuery.remoteRepoName = query.remoteRepoName ? query.remoteRepoName : ''
      this.clientQuery.retrying = query.retrying ? query.retrying : ''
      this.clientQuery.maxRetryCount = query.maxRetryCount ? query.maxRetryCount : ''
      this.clientQuery.sortField = query.sortField ? query.sortField : ''
      this.clientQuery.sortDirection = query.sortDirection ? query.sortDirection : ''
      this.pageOption.pageNumber = query.page ? Number(query.page) : 1
      const clientQuery = {
        taskKey: this.clientQuery.taskKey === '' ? null : this.clientQuery.taskKey,
        remoteClusterId: this.clientQuery.remoteClusterId === '' ? null : this.clientQuery.remoteClusterId,
        localRepoName: this.clientQuery.localRepoName === '' ? null : this.clientQuery.localRepoName,
        remoteProjectId: this.clientQuery.remoteProjectId === '' ? null : this.clientQuery.remoteProjectId,
        remoteRepoName: this.clientQuery.remoteRepoName === '' ? null : this.clientQuery.remoteRepoName,
        retrying: this.clientQuery.retrying === '' ? null : this.clientQuery.retrying,
        maxRetryCount: this.clientQuery.maxRetryCount === '' ? null : this.clientQuery.maxRetryCount,
        sortField: this.clientQuery.sortField === '' ? null : this.clientQuery.sortField,
        sortDirection: this.clientQuery.sortDirection === '' ? null : this.clientQuery.sortDirection,
        pageNumber: this.pageOption.pageNumber,
        pageSize: this.pageOption.pageSize
      }
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
      promise = trackRecords(clientQuery)
      promise.then(res => {
        this.trackRecords = res.data.records ? res.data.records : res.data
        this.total = res.data.totalRecords ? res.data.totalRecords : res.data.length
      })
    },
    handleSelectionChange(val) {
      this.multipleSelection = val
    },
    doDelete(data, index) {
      const message = this.clientQuery.maxRetryCount ? '是否确定按照重试' + this.clientQuery.maxRetryCount + '次和选择的删除' : '是否确定删除选择的'
      const selectRows = this.multipleSelection.map(row => row.id)
      console.log(selectRows)
      const params = {
        ids: this.multipleSelection.length> 0 ? this.multipleSelection.map(row => row.id) : null,
        maxRetryCount: this.clientQuery.maxRetryCount !== '' ? this.clientQuery.maxRetryCount : null
      }
      this.$confirm(message, '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        deleteTrackRecord(params).then(() => {
          this.$message.success('删除成功')
        })
      }).catch(() => {
        this.$message({
          type: 'info',
          message: '已取消'
        })
      })
    },
    doRetry(data) {
      retryTrackRecord(data).then(response => {
        this.$message.success('重试成功')
        this.reload()
      })
    }
  }
}
</script>

<style scoped>

</style>
