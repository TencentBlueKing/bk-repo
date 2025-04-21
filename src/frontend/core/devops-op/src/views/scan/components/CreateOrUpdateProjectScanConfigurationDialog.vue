<template>
  <el-dialog :title="createMode ? '创建配置' : '更新配置'" :visible.sync="showDialog" :before-close="close">
    <el-form ref="form" :rules="rules" :model="configuration" status-icon>
      <el-form-item label="项目名" prop="projectId" required>
        <el-input v-model="configuration.projectId" :disabled="!createMode" />
      </el-form-item>
      <el-form-item label="子任务数限制" prop="subScanTaskCountLimit">
        <el-input-number v-model="configuration.subScanTaskCountLimit" controls-position="right" :min="0" />
      </el-form-item>
      <el-form-item label="自动扫描" prop="autoScanConfiguration">
        <el-select v-model="selectedScanners" v-loading="loading" multiple placeholder="请选择">
          <el-option v-for="scanner in scanners" :key="scanner" :label="scanner" :value="scanner" />
        </el-select>
      </el-form-item>
      <el-form-item label="子任务分发器" prop="dispatcherConfiguration">
        <br>
        <div
          v-for="(dispatcherConfiguration, index) in configuration.dispatcherConfiguration"
          :key="index"
          style="margin-top: 5px"
        >
          <el-select
            v-model="dispatcherConfiguration.scanner"
            style="margin-right: 10px"
            placeholder="请选择分析工具"
            clearable
            required
            @change="clearDispatcher(dispatcherConfiguration, index)"
          >
            <el-option v-for="scanner in scanners" :key="scanner" :label="scanner" :value="scanner" />
          </el-select>
          <el-select v-model="dispatcherConfiguration.dispatcher" required placeholder="请选择分发的集群" clearable>
            <el-option
              v-for="dispatcher in supportDispatchers(dispatcherConfiguration.scanner)"
              :key="dispatcher"
              :label="dispatcher"
              :value="dispatcher"
            />
          </el-select>
          <el-button
            slot="append"
            style="margin-left: 10px"
            type="danger"
            @click="removeDispatcherConfiguration(index)"
          >删除</el-button>
        </div>
        <el-button
          style="margin-top: 10px"
          type="primary"
          @click="addDispatcherConfiguration()"
        >新增分发器</el-button>
      </el-form-item>
    </el-form>
    <div slot="footer">
      <el-button @click="close">取 消</el-button>
      <el-button type="primary" @click="handleCreateOrUpdate(configuration)">确 定</el-button>
    </div>
  </el-dialog>
</template>

<script>
import _ from 'lodash'
import { createProjectScanConfiguration, scanners, updateProjectScanConfiguration } from '@/api/scan'
export default {
  name: 'CreateOrUpdateProjectScanConfigurationDialog',
  props: {
    visible: Boolean,
    /**
     * 仅在更新模式时有值
     */
    updatingConfiguration: {
      type: Object,
      default: undefined
    },
    /**
     * 是否为创建模式，true时为创建对象，false时为更新对象
     */
    createMode: Boolean
  },
  data() {
    return {
      rules: {
        'dispatcherConfiguration': [
          { validator: this.validateDispatcherConfiguration, trigger: 'change' }
        ]
      },
      showDialog: this.visible,
      configuration: this.newConfiguration(),
      dispatchers: ['k8s', 'docker'],
      scanners: [],
      scannersDetail: new Map(),
      selectedScanners: [],
      loading: false
    }
  },
  watch: {
    selectedScanners: function(newVal) {
      for (const key in this.configuration.autoScanConfiguration) {
        delete this.configuration.autoScanConfiguration[key]
      }
      if (newVal.length > 0) {
        newVal.forEach(s => { this.configuration.autoScanConfiguration[s] = {} })
      }
    },
    visible: function(newVal) {
      if (newVal) {
        this.resetConfiguration()
        this.showDialog = true
        this.loading = true
        this.scanners = scanners().then(res => {
          this.scannersDetail.clear()
          this.scanners = []
          res.data.forEach(s => {
            this.scanners.push(s.name)
            this.scannersDetail.set(s.name, s)
          })
        }).finally(_ => {
          this.loading = false
        })
        this.selectedScanners = Object.keys(this.configuration.autoScanConfiguration)
      } else {
        this.close()
      }
    }
  },
  methods: {
    validateDispatcherConfiguration(rule, value, callback) {
      const keys = new Set()
      value.forEach(dispatcher => {
        if (keys.has(dispatcher.scanner)) {
          callback(new Error(`存在重复的分发器配置[${dispatcher.scanner}]`))
        }
        if (!dispatcher.scanner) {
          callback(new Error(`scanner不能为空`))
        }
        if (!dispatcher.dispatcher) {
          callback(new Error(`dispatcher不能为空`))
        }
        keys.add(dispatcher.scanner)
      })
      callback()
    },
    close() {
      this.showDialog = false
      this.$emit('update:visible', false)
    },
    supportDispatchers(scannerName) {
      const scanner = this.scannersDetail.get(scannerName)
      return scanner ? scanner.supportDispatchers : []
    },
    clearDispatcher(dispatcherConfiguration, index) {
      this.configuration.dispatcherConfiguration.splice(
        index,
        1,
        { scanner: dispatcherConfiguration.scanner }
      )
    },
    handleCreateOrUpdate(configuration) {
      this.$refs['form'].validate((valid) => {
        if (valid) {
          // 根据是否为创建模式发起不同请求
          let reqPromise
          let msg
          let eventName
          if (this.createMode) {
            reqPromise = createProjectScanConfiguration(configuration)
            msg = '创建成功'
            eventName = 'created'
          } else {
            reqPromise = updateProjectScanConfiguration(configuration)
            msg = '更新成功'
            eventName = 'updated'
          }

          // 发起请求
          reqPromise.then(res => {
            this.$message.success(msg)
            this.$emit(eventName, res.data)
            this.close()
          })
        } else {
          return false
        }
      })
    },
    resetConfiguration() {
      if (this.createMode) {
        this.configuration = this.newConfiguration()
      } else {
        this.configuration = _.cloneDeep(this.updatingConfiguration)
      }
      this.$nextTick(() => {
        this.$refs['form'].clearValidate()
      })
    },
    addDispatcherConfiguration() {
      this.configuration.dispatcherConfiguration.push({})
    },
    removeDispatcherConfiguration(index) {
      this.configuration.dispatcherConfiguration.splice(index, 1)
    },
    newConfiguration() {
      const configuration = {}
      configuration.subScanTaskCountLimit = 20
      configuration.autoScanConfiguration = {}
      configuration.dispatcherConfiguration = []
      return configuration
    }
  }
}
</script>

<style scoped>

</style>
