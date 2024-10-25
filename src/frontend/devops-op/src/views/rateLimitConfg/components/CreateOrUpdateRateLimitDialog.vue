<template>
  <el-dialog :title="createMode ? '创建限流配置' : '更新限流配置'" :visible.sync="showDialog" :before-close="close">
    <el-form ref="form" :rules="rules" :model="rateLimit" status-icon>
      <el-form-item ref="project-form-item" label="资源标识" prop="resource" :rules="[{ required: true, message: '资源标识不能为空'}]">
        <el-input v-model="rateLimit.resource" type="text" size="small" width="50" placeholder="请输入资源标识" />
      </el-form-item>
      <el-form-item
        ref="repo-form-item"
        label="限流维度"
        prop="limitDimension"
        :rules="[{ required: true, message: '限流维度不能为空'}]"
      >
        <el-select v-model="rateLimit.limitDimension" placeholder="请选择">
          <el-option
            v-for="item in options"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="限流值" prop="limit" :rules="[{ required: true, message: '限流值不能为空'}]">
        <el-input-number v-model="rateLimit.limit" controls-position="right" :min="0" />
      </el-form-item>
      <el-form-item label="算法" prop="algo" :rules="[{ required: true, message: '算法不能为空'}]">
        <el-select v-model="rateLimit.algo" placeholder="请选择">
          <el-option
            v-for="item in algoOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="限流周期(秒)" prop="duration" :rules="[{ required: true, message: '限流值不能为空'}]">
        <el-input-number v-model="rateLimit.duration" controls-position="right" :min="0" />
      </el-form-item>
      <el-form-item
        v-if="rateLimit.algo === 'TOKEN_BUCKET' || rateLimit.algo === 'LEAKY_BUCKET'"
        label="桶容量"
        prop="capacity"
        :rules="[{ required: rateLimit.algo === 'TOKEN_BUCKET' || rateLimit.algo === 'LEAKY_BUCKET', message: '桶容量不能为空'},
                 { validator: validateNum, message: '请输入正确容量', trigger: 'blur' }]"
      >
        <el-input v-model="rateLimit.capacity" type="text" size="small" width="50" placeholder="请输入桶容量" />
      </el-form-item>
      <el-form-item label="生效范围" prop="scope">
        <el-select v-model="rateLimit.scope" placeholder="请选择">
          <el-option
            v-for="item in scopeOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="作用模块" prop="moduleName" :rules="[{ required: true, message: '作用模块不能为空'}]">
        <el-select v-model="rateLimit.moduleName" multiple filterable collapse-tags placeholder="请选择">
          <el-option
            v-for="item in moduleNameOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-checkbox v-model="moduleSelectAll" style="margin-left: 10px">全选</el-checkbox>
      </el-form-item>
      <el-form-item
        v-for="(item,index) in rateLimit.targets"
        :key="index"
        label="指定机器"
        prop="targets"
      >
        <el-input
          v-model="rateLimit.targets[index]"
          style="height: 40px ; width: 500px;"
          placeholder="请输入数据"
          min="0"
          @input="updateInput()"
        />
        <i
          class="el-icon-circle-close"
          style="color: red"
          @click.prevent="removeDomain(item)"
        />
        <i
          v-if="index === rateLimit.targets.length - 1"
          class="el-icon-circle-plus-outline"
          style="margin: 0px 20px"
          @click.prevent="addDomain()"
        />
      </el-form-item>
    </el-form>
    <div slot="footer">
      <el-button @click="close">取 消</el-button>
      <el-button type="primary" @click="handleCreateOrUpdate()">确 定</el-button>
    </div>
  </el-dialog>
</template>

<script>
import _ from 'lodash'
import { createRateLimit, updateRateLimit } from '@/api/rateLimit'
export default {
  name: 'CreateOrUpdateRateLimitDialog',
  props: {
    visible: Boolean,
    /**
     * 仅在更新模式时有值
     */
    updatingRateLimit: {
      type: Object,
      default: undefined
    },
    /**
     * 是否为创建模式，true时为创建对象，false时为更新对象
     */
    createMode: Boolean,
    rateLimitConfig: {
      type: Array,
      default: () => []
    }
  },
  data() {
    return {
      showDialog: this.visible,
      rateLimit: this.newRateLimit(),
      rules: {},
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
      ],
      scopeOptions: [
        {
          value: 'LOCAL',
          label: 'LOCAL'
        },
        {
          value: 'GLOBAL',
          label: 'GLOBAL'
        }
      ],
      moduleNameOptions: [
        {
          value: 'auth',
          label: 'auth'
        },
        {
          value: 'composer',
          label: 'composer'
        },
        {
          value: 'generic',
          label: 'generic'
        },
        {
          value: 'helm',
          label: 'helm'
        },
        {
          value: 'maven',
          label: 'maven'
        },
        {
          value: 'npm',
          label: 'npm'
        },
        {
          value: 'nuget',
          label: 'nuget'
        },
        {
          value: 'opdata',
          label: 'opdata'
        },
        {
          value: 'pypi',
          label: 'pypi'
        },
        {
          value: 'replication',
          label: 'replication'
        },
        {
          value: 'repository',
          label: 'repository'
        },
        {
          value: 'rpm',
          label: 'rpm'
        },
        {
          value: 'git',
          label: 'git'
        },
        {
          value: 'oci',
          label: 'oci'
        },
        {
          value: 'webhook',
          label: 'webhook'
        },
        {
          value: 'job',
          label: 'job'
        },
        {
          value: 'analyst',
          label: 'analyst'
        },
        {
          value: 'analysis-executor',
          label: 'analysis-executor'
        },
        {
          value: 'conan',
          label: 'conan'
        },
        {
          value: 'fs',
          label: 'fs'
        },
        {
          value: 'config',
          label: 'config'
        },
        {
          value: 'lfs',
          label: 'lfs'
        },
        {
          value: 'ddc',
          label: 'ddc'
        },
        {
          value: 'svn',
          label: 'svn'
        },
        {
          value: 'archive',
          label: 'archive'
        },
        {
          value: 's3',
          label: 's3'
        },
        {
          value: 'router-controller',
          label: 'router-controller'
        },
        {
          value: 'media',
          label: 'media'
        }
      ],
      moduleSelectAll: false
    }
  },
  watch: {
    visible: function(newVal) {
      if (newVal) {
        this.resetRateLimit()
        this.showDialog = true
      } else {
        this.close()
      }
    },
    moduleSelectAll: function(newVal) {
      if (newVal) {
        this.rateLimit.moduleName = []
        this.moduleNameOptions.forEach(moduleName => {
          this.rateLimit.moduleName.push(moduleName.value)
        })
      } else {
        this.rateLimit.moduleName = []
      }
    }
  },
  methods: {
    validateNum(rule, value, callback) {
      if (!value) {
        callback()
      } else {
        if (Number(value) && !isNaN(value)) {
          callback()
        } else {
          callback(new Error('数字格式错误'))
        }
      }
    },
    updateInput() {
      this.$forceUpdate()
    },
    close() {
      this.showDialog = false
      this.moduleSelectAll = false
      this.rateLimit = this.newRateLimit()
      this.$refs['form'].resetFields()
      this.$emit('update:visible', false)
    },
    handleCreateOrUpdate() {
      if (this.createMode || (!this.createMode && this.rateLimit.id !== undefined)) {
        if (this.checkExist()) {
          this.$message.error('已有此类配置')
          return
        }
      }
      this.$refs['form'].validate((valid) => {
        if (valid) {
          const rateLimit = this.rateLimit
          for (let target in rateLimit.targets) {
            target = target.trim()
          }
          rateLimit.targets = Array.from(new Set(rateLimit.targets))
          if (rateLimit.targets.length === 1 && rateLimit.targets[0].trim() === '') {
            rateLimit.targets = null
          }
          if (rateLimit.moduleName.length < 1) {
            rateLimit.moduleName = null
          }
          // 根据是否为创建模式发起不同请求
          let reqPromise
          let msg
          let eventName
          if (this.createMode) {
            reqPromise = createRateLimit(rateLimit)
            msg = '创建限流配置成功'
            eventName = 'created'
          } else {
            msg = '更新限流配置成功'
            eventName = 'updated'
            reqPromise = updateRateLimit(rateLimit)
          }
          // 发起请求
          reqPromise.then(_ => {
            this.$message.success(msg)
            this.$emit(eventName)
            this.close()
          }).catch(_ => {
            this.$message.error('请求异常')
          })
        } else {
          return false
        }
      })
    },
    resetRateLimit() {
      if (this.createMode) {
        this.rateLimit = this.newRateLimit()
      } else {
        this.rateLimit = _.cloneDeep(this.updatingRateLimit)
        if (this.rateLimit.targets.length === 0) {
          this.rateLimit.targets = ['']
        }
      }
      this.$nextTick(() => {
        this.$refs['form'].clearValidate()
      })
    },
    newRateLimit() {
      const rateLimit = {
        algo: 'FIXED_WINDOW',
        resource: '',
        limitDimension: 'URL',
        duration: '',
        limit: '',
        capacity: '',
        scope: 'LOCAL',
        moduleName: ['repository'],
        targets: ['']
      }
      return rateLimit
    },
    checkExist() {
      if (this.rateLimitConfig.length === 0) {
        return false
      }
      if (this.rateLimitConfig.find(item => item.resource === this.rateLimit.resource && item.limitDimension === this.rateLimit.limitDimension)) {
        return true
      }
      return false
    },
    removeDomain(item) {
      const index = this.rateLimit.targets.indexOf(item)
      if (index !== -1 && this.rateLimit.targets.length !== 1) {
        this.rateLimit.targets.splice(index, 1)
      }
    },
    addDomain() {
      this.rateLimit.targets.push('')
    }
  }
}</script>

<style scoped>

</style>

