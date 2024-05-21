<template>
  <el-dialog title="查询记录" :visible.sync="showDialog" :before-close="close" width="1000px">
    <template v-loading="loading">
      <el-form ref="form" :inline="true" :model="clientQuery">
        <el-form-item style="margin-left: 15px" label="状态" prop="actions">
          <el-select v-model="clientQuery.actions" placeholder="请选择">
            <el-option
              v-for="item in options"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item style="margin-left: 15px" label="起始日期" prop="startTime">
          <el-date-picker v-model="clientQuery.startTime" value-format="yyyy-MM-dd" type="date" placeholder="选择日期" />
        </el-form-item>
        <el-form-item style="margin-left: 15px" label="结束日期" prop="endTime">
          <el-date-picker v-model="clientQuery.endTime" value-format="yyyy-MM-dd" type="date" placeholder="选择日期" />
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
        <el-table-column prop="userId" label="用户ID" />
        <el-table-column prop="action" label="状态">
          <template slot-scope="scope">
            {{ scope.row.action === 'start' ? "上线":"下线" }}
          </template>
        </el-table-column>
        <el-table-column prop="time" label="时间" width="160" align="center">
          <template slot-scope="scope">
            <span>{{ formatNormalDate(scope.row.time) }}</span>
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
    </template>
  </el-dialog>
</template>
<script>
import { queryDailyFileSystemClient } from '@/api/fileSystem'
import { formatNormalDate } from '@/utils/date'

export default {
  name: 'FileSystemStatusRecordDialog',
  props: {
    visible: Boolean,
    param: {
      type: Object,
      default: undefined
    }
  },
  data() {
    return {
      loading: false,
      showDialog: this.visible,
      projects: undefined,
      repoCache: {},
      total: 0,
      clientQuery: {
        projectId: '',
        repoName: '',
        pageNumber: 1,
        pageSize: 10,
        ip: '',
        version: '',
        startTime: '',
        endTime: '',
        actions: 'start,finish'
      },
      clients: [],
      options: [{
        value: 'start',
        label: '上线'
      }, {
        value: 'finish',
        label: '下线'
      }, {
        value: 'start,finish',
        label: '全部'
      }]
    }
  },
  watch: {
    visible: function(newVal) {
      if (newVal) {
        this.changeRouteQueryParams(1)
        this.showDialog = true
      } else {
        this.close()
      }
    }
  },
  methods: {
    handleCurrentChange(val) {
      this.currentPage = val
      this.changeRouteQueryParams(val)
    },
    changeRouteQueryParams(pageNum) {
      let url = '?'
      url = url + 'pageNumber=' + pageNum + '&pageSize=' + this.clientQuery.pageSize +
        '&projectId=' + this.param.projectId +
        '&repoName=' + this.param.repoName +
        '&ip=' + this.param.ip +
        '&mountPoint=' + this.param.mountPoint +
        '&actions=' + this.buildActions()
      if (this.clientQuery.startTime !== '' && this.clientQuery.startTime !== null) {
        url = url + '&startTime=' + this.clientQuery.startTime
      }
      if (this.clientQuery.endTime !== '' && this.clientQuery.endTime !== null) {
        url = url + '&endTime=' + this.clientQuery.endTime
      }
      const query = {}
      this.doQueryClients(query, url)
    },
    doQueryClients(clientQuery, url) {
      this.loading = true
      let promise = null
      promise = queryDailyFileSystemClient(clientQuery, url)
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
    },
    close() {
      this.showDialog = false
      this.$emit('update:visible', false)
    },
    buildActions() {
      if (this.clientQuery.actions.includes(',')) {
        return 'start&actions=finish'
      } else {
        return this.clientQuery.actions
      }
    }
  }
}
</script>

<style scoped>

</style>

<style>
</style>
