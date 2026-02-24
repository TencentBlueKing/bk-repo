<template>
  <div class="app-container">
    <el-form ref="form" :inline="true" :model="clientQuery">
      <el-form-item ref="project-form-item" label="任务Key">
        <el-input v-model="clientQuery.taskKey" size="small" />
      </el-form-item>
      <el-form-item ref="project-form-item" label="事件类型" style="margin-left: 10px">
        <el-select
          v-model="clientQuery.eventType"
          clearable
          style="margin-left: 10px;"
          placeholder="请选择"
        >
          <el-option
            v-for="item in options"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item ref="project-form-item" label="是否完成" style="margin-left: 10px">
        <el-select
          v-model="clientQuery.taskCompleted"
          clearable
          style="margin-left: 10px;"
          placeholder="请选择"
        >
          <el-option
            v-for="item in completeOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item ref="project-form-item" label="是否成功" style="margin-left: 10px">
        <el-select
          v-model="clientQuery.taskSucceeded"
          clearable
          style="margin-left: 10px;"
          placeholder="请选择"
        >
          <el-option
            v-for="item in successOptions"
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
      :data="events"
      style="width: 100%; margin-top: 20px"
      :max-height="825"
    >
      <el-table-column
        prop="messageId"
        fixed
        label="消息ID"
      />
      <el-table-column prop="event" label="事件对象" align="center">
        <el-table-column prop="event.type" label="事件类型" align="center" />
        <el-table-column prop="event.projectId" label="项目ID" align="center" />
        <el-table-column prop="event.repoName" label="仓库名称" align="center" />
        <el-table-column prop="event.resourceKey" label="事件资源KEY" align="center" />
        <el-table-column prop="event.userId" label="操作用户" align="center" />
        <el-table-column prop="event.eventId" label="事件ID" align="center" />
      </el-table-column>
      <el-table-column
        prop="taskKey"
        label="关联任务KEY"
        width="150"
      />
      <el-table-column
        label="是否完成"
        width="130"
      >
        <template slot-scope="props">
          {{ props.row.taskCompleted ? '是': '否' }}
        </template>
      </el-table-column>
      <el-table-column
        label="是否成功"
        width="130"
      >
        <template slot-scope="props">
          {{ props.row.taskSucceeded ? '是': '否' }}
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
        prop="createdDate"
        label="创建时间"
        width="160"
      >
        <template slot-scope="scope">
          <span>{{ formatNormalDate(scope.row.createdDate) }}</span>
        </template>
      </el-table-column>
      <el-table-column
        prop="lastModifiedDate"
        label="上一次更新时间"
        width="160"
      >
        <template slot-scope="scope">
          <span>{{ formatNormalDate(scope.row.lastModifiedDate) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作">
        <template slot-scope="scope">
          <el-button type="primary" size="mini" @click="doRetry(scope.row)">重试</el-button>
          <el-button type="danger" size="mini" @click="doDelete(scope.row, scope.index)">删除</el-button>
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

import { deleteEvent, events, retryEvent } from '@/api/federatedEvent'
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
      events: [],
      clientQuery: {
        taskKey: '',
        eventType: '',
        taskCompleted: '',
        taskSucceeded: '',
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
      completeOptions: [
        {
          value: 'true',
          label: '是'
        }, {
          value: 'false',
          label: '否'
        }
      ],
      successOptions: [
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
      ]
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
      query.eventType = this.clientQuery.eventType
      query.taskCompleted = this.clientQuery.taskCompleted
      query.taskSucceeded = this.clientQuery.taskSucceeded
      query.sortField = this.clientQuery.sortField
      query.sortDirection = this.clientQuery.sortDirection
      this.$router.push({ path: '/federated/event', query: query })
    },
    onRouteUpdate(route) {
      const query = route.query
      this.clientQuery.taskKey = query.taskKey ? query.taskKey : ''
      this.clientQuery.eventType = query.eventType ? query.eventType : ''
      this.clientQuery.taskCompleted = query.taskCompleted ? query.taskCompleted : ''
      this.clientQuery.taskSucceeded = query.taskSucceeded ? query.taskSucceeded : ''
      this.clientQuery.sortField = query.sortField ? query.sortField : ''
      this.clientQuery.sortDirection = query.sortDirection ? query.sortDirection : ''
      this.pageOption.pageNumber = query.page ? Number(query.page) : 1
      const clientQuery = {
        taskKey: this.clientQuery.taskKey === '' ? null : this.clientQuery.taskKey,
        eventType: this.clientQuery.eventType === '' ? null : this.clientQuery.eventType,
        taskCompleted: this.clientQuery.taskCompleted === '' ? null : this.clientQuery.taskCompleted,
        taskSucceeded: this.clientQuery.taskSucceeded === '' ? null : this.clientQuery.taskSucceeded,
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
      promise = events(clientQuery)
      promise.then(res => {
        this.events = res.data.records ? res.data.records : res.data
        this.pageOption.total = res.data.totalRecords ? res.data.totalRecords : res.data.length
      })
    },
    doDelete(data, index) {
      this.$confirm(`是否确定删除`, '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        const params = {
          taskKey: data.taskKey,
          eventId: data.event.eventId
        }
        deleteEvent(params).then(() => {
          this.$message.success('删除成功')
          this.events.splice(index, 1)
        })
      }).catch(() => {
        this.$message({
          type: 'info',
          message: '已取消'
        })
      })
    },
    doRetry(data) {
      const params = {
        taskKey: data.taskKey,
        eventId: data.event.eventId
      }
      retryEvent(params).then(response => {
        this.$message.success('重试成功')
        this.reload()
      })
    }
  }
}
</script>

<style scoped>

</style>
