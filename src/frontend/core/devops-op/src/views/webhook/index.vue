<template>
  <div class="app-container node-container">
    <el-form ref="form" :inline="true" :model="webhookQuery" :rules="queryRule">
      <el-form-item label="关联对象类型" prop="associationType">
        <el-select v-model="webhookQuery.associationType" placeholder="请选择关联对象类型">
          <el-option label="系统" value="SYSTEM" />
          <el-option label="项目" value="PROJECT" />
          <el-option label="仓库" value="REPO" />
        </el-select>
      </el-form-item>
      <el-form-item style="margin-left: 15px" label="关联对象id" prop="associationId">
        <el-input
          v-model="webhookQuery.associationId"
          class="inline-input"
          placeholder="请输入关联对象id"
        />
      </el-form-item>
      <el-form-item>
        <el-button size="mini" type="primary" @click="changeRouteQueryParams()">查询</el-button>
        <el-button size="mini" type="primary" @click="showWebhookCreate()">新建</el-button>
      </el-form-item>
    </el-form>
    <el-table v-loading="loading" :data="webhooks" style="width: 100%">
      <el-table-column prop="id" label="id" min-width="200px" />
      <el-table-column prop="url" label="url" min-width="200px" />
      <el-table-column prop="headers" label="请求头" min-width="200px">
        <template slot-scope="scope">{{ JSON.stringify(scope.row.headers) }}</template>
      </el-table-column>
      <el-table-column prop="triggers" label="触发事件" :formatter="triggersFormatter" min-width="120px" />
      <el-table-column prop="associationType" label="关联对象类型" min-width="100px" />
      <el-table-column prop="associationId" label="关联对象Id" min-width="100px" />
      <el-table-column prop="resourceKeyPattern" label="资源key正则" />
      <el-table-column label="操作" min-width="220px">
        <template slot-scope="scope">
          <el-button size="mini" type="primary" @click="showWebhookDetail(scope.row)">修改</el-button>
          <el-button size="mini" type="danger" @click="showWebhookDelete(scope.row)">删除</el-button>
          <el-button size="mini" type="primary" @click="showWebhookLog(scope.row)">查看日志</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      style="margin-top: 15px"
      background
      layout="prev, pager, next"
      :current-page.sync="webhookQuery.pageNumber"
      :page-size="webhookQuery.pageSize"
      :hide-on-single-page="true"
      :total="total"
      @current-change="changeRouteQueryParams()"
    />
    <webhook-create-dialog :visible.sync="showWebhookCreateDialog" @create-success="onCreateSuccess" />
    <webhook-detail-dialog :visible.sync="showWebhookDetailDialog" :webhook="webhookOfDetailDialog" @update-success="onUpdateSuccess" />
    <webhook-delete-dialog :visible.sync="showWebhookDeleteDialog" :webhook="webhookToDelete" @delete-success="onDeleteSuccess" />
  </div>
</template>
<script>
import { listWebHook } from '@/api/webhook'
import { formatDate } from '@/utils/file'
import WebhookCreateDialog from '@/views/webhook/components/WebhookCreateDialog'
import WebhookDetailDialog from '@/views/webhook/components/WebhookDetailDialog'
import WebhookDeleteDialog from '@/views/webhook/components/WebhookDeleteDialog'
import moment from 'moment'

export default {
  name: 'WebHook',
  components: { WebhookCreateDialog, WebhookDetailDialog, WebhookDeleteDialog },
  beforeRouteUpdate(to, from, next) {
    this.onRouteUpdate(to)
    next()
  },
  data() {
    var validateId = (rule, value, callback) => {
      if (this.webhookQuery.associationType === 'PROJECT' && value.length === 0) {
        callback(new Error('请输入项目Id'))
      }
      if (this.webhookQuery.associationType === 'REPO' && value.indexOf(':') === -1) {
        callback(new Error('请输入[项目Id:仓库名]'))
      }
      callback()
    }
    return {
      queryRule: {
        associationType: [{ required: true, message: '请选择关联对象类型', trigger: 'blur' }],
        associationId: [{ validator: validateId, trigger: 'blur' }]
      },
      loading: false,
      webhookQuery: {
        associationType: 'SYSTEM',
        associationId: ''
      },
      webhooks: [],
      total: 0,
      showWebhookCreateDialog: false,
      webhookToCreate: {},
      showWebhookDetailDialog: false,
      webhookOfDetailDialog: {},
      showWebhookDeleteDialog: false,
      webhookToDelete: {},
      indexOfWebhookToDelete: -1
    }
  },
  mounted() {
    this.onRouteUpdate(this.$route)
  },
  methods: {
    triggersFormatter(row, column) {
      return row.triggers.join(',')
    },
    formatDate(date) {
      return formatDate(date)
    },
    queryModeChanged() {
      this.$refs['form'].clearValidate()
    },
    changeRouteQueryParams() {
      this.$refs['form'].validate((valid) => {
        if (valid) {
          this.$router.push({ path: '/webhook', query: this.webhookQuery })
        }
      })
    },
    onRouteUpdate(route) {
      this.$nextTick(() => {
        this.queryWebHook(route.query)
      })
    },
    queryWebHook(webhookQuery) {
      this.loading = true
      const promise = listWebHook(
        webhookQuery.associationType,
        webhookQuery.associationId
      )
      promise.then(res => {
        this.webhooks = res.data
        this.total = res.data.length
      }).catch(_ => {
        this.webhooks = []
        this.total = 0
      }).finally(() => {
        this.loading = false
      })
    },
    showWebhookCreate() {
      this.showWebhookCreateDialog = true
    },
    showWebhookDetail(webhook) {
      this.webhookOfDetailDialog = webhook
      this.showWebhookDetailDialog = true
    },
    showWebhookDelete(webhook) {
      this.webhookToDelete = webhook
      this.showWebhookDeleteDialog = true
    },
    onDeleteSuccess() {
      this.queryWebhook(this.webhookQuery)
    },
    onCreateSuccess() {
      this.queryWebhook(this.webhookQuery)
    },
    onUpdateSuccess() {
      this.queryWebhook(this.webhookQuery)
    },
    showWebhookLog(webhook) {
      const query = {
        id: webhook.id,
        startTime: moment(new Date() - 3600 * 1000 * 24 * 7).format('YYYY-MM-DDTHH:mm:ss'),
        endTime: moment(new Date()).format('YYYY-MM-DDTHH:mm:ss'),
        pageNumber: 1
      }
      this.$router.push({ path: '/webhook/log', query: query })
    }
  }
}
</script>

<style scoped>

</style>
