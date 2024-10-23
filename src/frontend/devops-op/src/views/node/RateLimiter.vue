<template>
  <div class="app-container node-container">
    <el-form ref="form" :inline="true" :model="clientQuery">
      <el-form-item ref="project-form-item" label="资源标识">
        <el-input v-model="clientQuery.resource" type="text" size="small" width="50" placeholder="请输入资源标识" />
      </el-form-item>
      <el-form-item
        ref="repo-form-item"
        style="margin-left: 15px"
        label="资源维度"
      >
        <el-select v-model="clientQuery.limitDimension" clearable placeholder="请选择">
          <el-option
            v-for="item in options"
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
          @click="queryRateLimit()"
        >查询</el-button>
      </el-form-item>
    </el-form>
    <el-table v-loading="loading" :data="rateLimits" style="width: 100%">
      <el-table-column prop="resource" label="资源标识" />
      <el-table-column prop="limitDimension" label="资源维度">
        <template slot-scope="scope">
          <span>{{ formatLimitDimension(scope.row.limitDimension) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="algo" label="算法选择">
        <template slot-scope="scope">
          <span>{{ formatAlgo(scope.row.algo) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="targets" label="指定机器">
        <template slot-scope="scope">
          <div
            v-for="(item,index) in scope.row.targets"
            :key="index"
          >
            <span> {{ item }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="limit" label="限流值" />
      <el-table-column prop="duration" label="限流周期(秒)" />
      <el-table-column prop="capacity" label="桶容量" />
      <el-table-column prop="scope" label="生效范围" />
      <el-table-column prop="moduleName" label="作用模块">
        <template slot-scope="scope">
          <span> {{ scope.row.moduleName }}</span>
        </template>
      </el-table-column>
      <el-table-column align="right">
        <template slot="header">
          <el-button type="primary" @click="showCreateOrUpdateDialog(true)">创建</el-button>
        </template>
        <template slot-scope="scope">
          <el-button
            v-if="scope.row.id"
            size="mini"
            type="primary"
            @click="showCreateOrUpdateDialog(false, scope.$index, scope.row)"
          >
            编辑
          </el-button>
          <el-button
            v-if="scope.row.id"
            size="mini"
            type="danger"
            @click="handleDelete(scope.$index, scope.row)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <create-or-update-rate-limit-dialog
      :create-mode="createMode"
      :visible.sync="showDialog"
      :updating-rate-limit="updatingRateLimit"
      :rate-limit-config="rateLimitConf"
      @created="getDates"
      @updated="getDates"
    />
  </div>
</template>
<script>

import CreateOrUpdateRateLimitDialog from '@/views/node/components/CreateOrUpdateRateLimitDialog'
import { getRateLimitConfig, queryRateLimits, deleteRateLimit } from '@/api/rateLimit'

export default {
  name: 'RateLimit',
  components: { CreateOrUpdateRateLimitDialog },
  data() {
    return {
      loading: false,
      showDialog: false,
      createMode: true,
      updatingIndex: undefined,
      updatingRateLimit: undefined,
      rateLimits: [],
      originRateLimits: [],
      rateLimitConf: [],
      clientQuery: {
        resource: '',
        limitDimension: ''
      },
      options: [{
        label: '指定URL限流',
        value: 'URL'
      }, {
        label: '指定项目/仓库',
        value: 'URL_REPO'
      }, {
        label: '仓库上传总大小',
        value: 'UPLOAD_USAGE'
      }, {
        label: '仓库下载总大小',
        value: 'DOWNLOAD_USAGE'
      }, {
        label: '指定用户指定请求',
        value: 'USER_URL'
      }, {
        label: '指定用户访问指定项目/仓库',
        value: 'USER_URL_REPO'
      }, {
        label: '指定用户上传总大小',
        value: 'USER_UPLOAD_USAGE'
      }, {
        value: 'USER_DOWNLOAD_USAGE',
        label: '指定用户下载总大小'
      }, {
        value: 'UPLOAD_BANDWIDTH',
        label: '项目维度上传带宽'
      }, {
        value: 'DOWNLOAD_BANDWIDTH',
        label: '项目维度下载带宽'
      }],
      algoOptions: [
        {
          value: 'FIXED_WINDOW',
          label: '固定窗口'
        },
        {
          value: 'SLIDING_WINDOW',
          label: '滑动窗口'
        },
        {
          value: 'TOKEN_BUCKET',
          label: '令牌桶'
        },
        {
          value: 'LEAKY_BUCKET',
          label: '漏桶'
        }
      ]
    }
  },
  async created() {
    await this.getDates()
  },
  methods: {
    showCreateOrUpdateDialog(create, index, rateLimit) {
      this.showDialog = true
      this.createMode = create
      this.updatingIndex = index
      this.updatingRateLimit = rateLimit
    },
    handleDelete(index, rateLimit) {
      this.$confirm('是否确定删除此限流配置吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        if (rateLimit.id) {
          deleteRateLimit(rateLimit.id).then(_ => {
            this.rateLimits.splice(index, 1)
            this.originRateLimits = this.originRateLimits.filter(rate => {
              return rate.id !== rateLimit.id
            })
            this.$message.success('删除成功')
          })
        }
      }).catch(() => {
        this.$message({
          type: 'info',
          message: '已取消'
        })
      })
    },
    async getDates() {
      this.rateLimits = []
      this.originRateLimits = []
      await queryRateLimits().then(res => {
        if (res.data && res.data.length !== 0) {
          this.rateLimits.push.apply(this.rateLimits, res.data)
          this.originRateLimits.push.apply(this.originRateLimits, res.data)
        }
      })
      await getRateLimitConfig().then(res => {
        this.handleDateFromConfig(res)
      })
    },
    handleDateFromConfig(res) {
      if (res.data) {
        const obj = JSON.parse(res.data)
        this.rateLimitConf = obj
        console.log(this.rateLimitConf)
        this.rateLimits.push.apply(this.rateLimits, obj)
        this.originRateLimits.push.apply(this.originRateLimits, obj)
      }
    },
    queryRateLimit() {
      this.rateLimits = this.originRateLimits
      if (this.clientQuery.resource === '' && this.clientQuery.limitDimension === '') {
        this.rateLimits = this.originRateLimits
      } else {
        this.rateLimits = this.rateLimits.filter(rateLimit => {
          const rateLimitFilter = rateLimit.resource.toString().includes(this.clientQuery.resource)
          const limitDimensionFilter = rateLimit.limitDimension.toString() === (this.clientQuery.limitDimension)
          return rateLimitFilter && limitDimensionFilter
        })
      }
    },
    formatAlgo(value) {
      return this.algoOptions.find(option => option.value === value).label
    },
    formatLimitDimension(value) {
      return this.options.find(option => option.value === value).label
    }
  }
}
</script>

<style scoped>

</style>

