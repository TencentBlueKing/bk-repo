<template>
  <el-dialog :title="createMode ? '创建配置' : '更新配置'" :visible.sync="showDialog" :before-close="close">
    <el-form ref="form" :model="configuration" status-icon>
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
      showDialog: this.visible,
      configuration: this.newConfiguration(),
      scanners: [],
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
          this.scanners = res.data.map(s => s.name)
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
    close() {
      this.showDialog = false
      this.$emit('update:visible', false)
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
    newConfiguration() {
      const configuration = {}
      configuration.subScanTaskCountLimit = 20
      configuration.autoScanConfiguration = {}
      return configuration
    }
  }
}
</script>

<style scoped>

</style>
