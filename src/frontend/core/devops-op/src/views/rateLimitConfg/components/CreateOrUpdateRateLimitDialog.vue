<template>
  <el-dialog :title="createMode ? '创建限流配置' : '更新限流配置'" :visible.sync="showDialog" :before-close="close">
    <el-form ref="form" :rules="rules" :model="rateLimit" status-icon>
      <el-form-item ref="project-form-item" label="资源标识" prop="resource" :rules="[{ required: true, message: '资源标识不能为空'}]">
        <el-input v-model="rateLimit.resource" type="text" size="small" width="50" :placeholder="resourceTip" @change="resourceChange()" />
      </el-form-item>
      <el-form-item
        ref="repo-form-item"
        label="限流维度"
        prop="limitDimension"
        :rules="[{ required: true, message: '限流维度不能为空'}]"
      >
        <el-select v-model="rateLimit.limitDimension" placeholder="请选择" @change="changeLimitDimension()">
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
        <el-select v-model="rateLimit.moduleName" multiple filterable collapse-tags placeholder="请选择" @change="changeModule()">
          <el-option
            v-for="item in moduleNameOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
            :disabled="item.disabled"
            :loading="loading"
          />
        </el-select>
        <el-checkbox v-model="selectDockerAndOci" style="margin-left: 10px">添加docker和oci</el-checkbox>
        <el-checkbox v-model="moduleSelectAll" style="margin-left: 10px">全选</el-checkbox>
      </el-form-item>
      <el-form-item label="是否保持连接" prop="keepConnection">
        <el-switch v-model="rateLimit.keepConnection" />
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
import { createRateLimit, updateRateLimit, getExistModule } from '@/api/rateLimit'
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
      resourceTip: '请输入资源标识,例子如下：/{projectId}/{repoName}/**',
      rules: {},
      options: [{
        label: 'url维度请求频率',
        value: 'URL'
      }, {
        label: '项目/仓库维度请求频率',
        value: 'URL_REPO'
      }, {
        label: '项目/仓库维度上传总量',
        value: 'UPLOAD_USAGE'
      }, {
        label: '项目/仓库维度下载总量',
        value: 'DOWNLOAD_USAGE'
      }, {
        label: '用户+url维度请求频率',
        value: 'USER_URL'
      }, {
        label: '用户+项目/仓库维度请求频率',
        value: 'USER_URL_REPO'
      }, {
        label: '用户+项目/仓库维度上传总量',
        value: 'USER_UPLOAD_USAGE'
      }, {
        value: 'USER_DOWNLOAD_USAGE',
        label: '用户+项目/仓库维度下载总量'
      }, {
        value: 'UPLOAD_BANDWIDTH',
        label: '项目/仓库维度上传带宽'
      }, {
        value: 'DOWNLOAD_BANDWIDTH',
        label: '项目/仓库维度下载带宽'
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
      moduleNameOptions: this.newModuleName(),
      moduleSelectAll: false,
      selectDockerAndOci: false,
      loading: false
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
          if (!moduleName.disabled) {
            this.rateLimit.moduleName.push(moduleName.value)
          }
        })
      } else {
        this.rateLimit.moduleName = []
      }
    },
    selectDockerAndOci: function(newVal) {
      if (newVal) {
        if (this.rateLimit.moduleName.indexOf('docker') < 0 && !this.moduleNameOptions[28].disabled) {
          this.rateLimit.moduleName.push('docker')
          this.rateLimit.moduleName.push('oci')
        }
      } else {
        let has = false
        for (let i = 0; i < this.rateLimit.moduleName.length; i++) {
          if (this.rateLimit.moduleName[i] === 'docker') {
            has = true
            break
          }
        }
        if (has) {
          this.rateLimit.moduleName.splice(this.rateLimit.moduleName.indexOf('docker'), 1)
          this.rateLimit.moduleName.splice(this.rateLimit.moduleName.indexOf('oci'), 1)
        }
      }
    }
  },
  methods: {
    newModuleName() {
      const name = [
        {
          value: 'auth',
          label: 'auth',
          disabled: false
        },
        {
          value: 'composer',
          label: 'composer',
          disabled: false
        },
        {
          value: 'generic',
          label: 'generic',
          disabled: false
        },
        {
          value: 'helm',
          label: 'helm',
          disabled: false
        },
        {
          value: 'maven',
          label: 'maven',
          disabled: false
        },
        {
          value: 'npm',
          label: 'npm',
          disabled: false
        },
        {
          value: 'nuget',
          label: 'nuget',
          disabled: false
        },
        {
          value: 'opdata',
          label: 'opdata',
          disabled: false
        },
        {
          value: 'pypi',
          label: 'pypi',
          disabled: false
        },
        {
          value: 'replication',
          label: 'replication',
          disabled: false
        },
        {
          value: 'repository',
          label: 'repository',
          disabled: false
        },
        {
          value: 'rpm',
          label: 'rpm',
          disabled: false
        },
        {
          value: 'git',
          label: 'git',
          disabled: false
        },
        {
          value: 'oci',
          label: 'oci',
          disabled: false
        },
        {
          value: 'webhook',
          label: 'webhook',
          disabled: false
        },
        {
          value: 'job',
          label: 'job',
          disabled: false
        },
        {
          value: 'analyst',
          label: 'analyst',
          disabled: false
        },
        {
          value: 'analysis-executor',
          label: 'analysis-executor',
          disabled: false
        },
        {
          value: 'conan',
          label: 'conan',
          disabled: false
        },
        {
          value: 'fs',
          label: 'fs',
          disabled: false
        },
        {
          value: 'config',
          label: 'config',
          disabled: false
        },
        {
          value: 'lfs',
          label: 'lfs',
          disabled: false
        },
        {
          value: 'ddc',
          label: 'ddc',
          disabled: false
        },
        {
          value: 'svn',
          label: 'svn',
          disabled: false
        },
        {
          value: 'archive',
          label: 'archive',
          disabled: false
        },
        {
          value: 's3',
          label: 's3',
          disabled: false
        },
        {
          value: 'router-controller',
          label: 'router-controller',
          disabled: false
        },
        {
          value: 'media',
          label: 'media',
          disabled: false
        },
        {
          value: 'docker',
          label: 'docker',
          disabled: false
        }
      ]
      return name
    },
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
    resourceChange() {
      if (this.rateLimit.resource !== '' && !this.loading) {
        this.loading = true
        getExistModule(this.rateLimit.resource, this.rateLimit.limitDimension).then((res) => {
          for (let i = 0; i < this.moduleNameOptions.length; i++) {
            const module = this.moduleNameOptions[i]
            if (res.data.indexOf(this.moduleNameOptions[i].value) > -1) {
              this.$set(module, 'disabled', true)
            } else {
              this.$set(module, 'disabled', false)
            }
          }
          this.loading = false
        })
      }
    },
    changeLimitDimension() {
      const msg = '请输入资源标识，例子如下：'
      switch (this.rateLimit.limitDimension) {
        case 'URL':
          this.resourceTip = msg + '/{projectId}/{repoName}/**'
          break
        case 'URL_REPO':
          this.resourceTip = msg + '/blueking/generic-local/ 或者/blueking/'
          break
        case 'UPLOAD_USAGE':
          this.resourceTip = msg + '/blueking/generic-local/ 或者/blueking/'
          break
        case 'DOWNLOAD_USAGE':
          this.resourceTip = msg + '/blueking/generic-local/ 或者/blueking/'
          break
        case 'USER_URL':
          this.resourceTip = msg + 'user1:/{projectId}/{repoName}/**'
          break
        case 'USER_URL_REPO':
          this.resourceTip = msg + 'user1:/blueking/generic-local/ 或者user1:/blueking/'
          break
        case 'USER_UPLOAD_USAGE':
          this.resourceTip = msg + 'user1:/blueking/generic-local/ 或者user1:/blueking/'
          break
        case 'USER_DOWNLOAD_USAGE':
          this.resourceTip = msg + 'user1:/blueking/generic-local/ 或者user1:/blueking/'
          break
        case 'UPLOAD_BANDWIDTH':
          this.resourceTip = msg + '/blueking/generic-local/ 或者/blueking/'
          break
        case 'DOWNLOAD_BANDWIDTH':
          this.resourceTip = msg + '/blueking/generic-local/ 或者/blueking/'
          break
      }
      if (this.rateLimit.resource !== '' && !this.loading) {
        this.loading = true
        getExistModule(this.rateLimit.resource, this.rateLimit.limitDimension).then((res) => {
          for (let i = 0; i < this.moduleNameOptions.length; i++) {
            const module = this.moduleNameOptions[i]
            if (res.data.indexOf(this.moduleNameOptions[i].value) > -1) {
              this.$set(module, 'disabled', true)
            } else {
              this.$set(module, 'disabled', false)
            }
          }
          this.loading = false
        })
      }
    },
    close() {
      this.showDialog = false
      this.moduleSelectAll = false
      this.selectDockerAndOci = false
      this.rateLimit = this.newRateLimit()
      this.moduleNameOptions = this.newModuleName()
      this.$refs['form'].resetFields()
      this.$emit('update:visible', false)
    },
    handleCreateOrUpdate() {
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
      this.$nextTick(() => {
        this.$refs['form'].clearValidate()
      })
      if (this.createMode) {
        this.rateLimit = this.newRateLimit()
      } else {
        this.rateLimit = _.cloneDeep(this.updatingRateLimit)
        if (this.rateLimit.moduleName && this.rateLimit.moduleName.some(moduleName => moduleName === 'docker')) {
          this.selectDockerAndOci = true
        }
        if (this.rateLimit.targets.length === 0) {
          this.rateLimit.targets = ['']
        }
      }
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
        moduleName: [],
        keepConnection: true,
        targets: ['']
      }
      return rateLimit
    },
    changeModule() {
      const module = this.rateLimit.moduleName
      if (module === null || (module.some(mod => mod === 'oci') && module.some(mod => mod === 'docker'))) {
        return
      }
      if (module.some(mod => mod === 'docker') && !module.some(mod => mod === 'oci')) {
        this.rateLimit.moduleName.splice(this.rateLimit.moduleName.indexOf('docker'), 1)
        this.selectDockerAndOci = false
      }
      if (module.some(mod => mod === 'oci') && !module.some(mod => mod === 'docker')) {
        this.rateLimit.moduleName.splice(this.rateLimit.moduleName.indexOf('oci'), 1)
        this.selectDockerAndOci = false
      }
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

