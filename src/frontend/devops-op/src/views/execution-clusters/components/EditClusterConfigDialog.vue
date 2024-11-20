<template>
  <el-dialog :title="createMode?'创建扫描执行集群配置':'更新扫描执行集群配置'" :visible.sync="showDialog" :before-close="close">
    <el-form ref="form" :rules="rules" :model="executionCluster" status-icon>
      <el-form-item ref="project-form-item" label="执行集群名" prop="name" :rules="[{ required: true, message: '执行集群名不能为空'}]">
        <el-input v-model="executionCluster.name" style="height: 40px ; width: 500px;" :disabled="!createMode" />
      </el-form-item>
      <el-form-item ref="project-form-item" label="描述" prop="description">
        <el-input v-model="executionCluster.description" style="height: 40px ; width: 500px;" />
      </el-form-item>
      <el-form-item label="集群类型" prop="type">
        <el-select
          v-model="executionCluster.type"
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
      <el-form-item v-if="executionCluster.type !== 'docker'" label="命名空间" prop="kubernetesProperties.namespace" :rules="[{ required: true, message: '命名空间不能为空'}]">
        <el-input v-model="executionCluster.kubernetesProperties.namespace" style="height: 40px ; width: 500px;" />
      </el-form-item>
      <el-form-item v-if="executionCluster.type !== 'docker'" label="apiServer" prop="apiServer">
        <el-input v-model="executionCluster.kubernetesProperties.apiServer" style="height: 40px ; width: 500px;" />
      </el-form-item>
      <el-form-item v-if="executionCluster.type !== 'docker'" label="certificateAuthorityData" prop="certificateAuthorityData">
        <el-input v-model="executionCluster.kubernetesProperties.certificateAuthorityData" style="height: 40px ; width: 500px;" />
      </el-form-item>
      <el-form-item v-if="executionCluster.type !== 'docker'" label="token" prop="token">
        <el-input v-model="executionCluster.kubernetesProperties.token" style="height: 40px ; width: 500px;" />
      </el-form-item>
      <el-form-item v-if="executionCluster.type !== 'docker'" label="clientCertificateData" prop="clientCertificateData">
        <el-input v-model="executionCluster.kubernetesProperties.clientCertificateData" style="height: 40px ; width: 500px;" />
      </el-form-item>
      <el-form-item v-if="executionCluster.type !== 'docker'" label="clientKeyData" prop="clientCertificateData">
        <el-input v-model="executionCluster.kubernetesProperties.clientKeyData" style="height: 40px ; width: 500px;" />
      </el-form-item>
      <el-form-item v-if="executionCluster.type !== 'docker'" label="最大使用内存(B)" prop="kubernetesProperties.limitMem" :rules="[{ required: true, message: '最大使用内存不能为空'}]">
        <el-input-number v-model="executionCluster.kubernetesProperties.limitMem" controls-position="right" :min="1" />
      </el-form-item>
      <el-form-item v-if="executionCluster.type !== 'docker'" label="最大临时内存(B)" prop="kubernetesProperties.limitStorage" :rules="[{ required: true, message: '最大临时内存不能为空'}]">
        <el-input-number v-model="executionCluster.kubernetesProperties.limitStorage" controls-position="right" :min="1" />
      </el-form-item>
      <el-form-item v-if="executionCluster.type !== 'docker'" label="最大cpu" prop="kubernetesProperties.limitCpu" :rules="[{ required: true, message: '最大cpu不能为空'}]">
        <el-input-number v-model="executionCluster.kubernetesProperties.limitCpu" controls-position="right" :min="1" />
      </el-form-item>
      <el-form-item v-if="executionCluster.type === 'k8s_job'" label="最大保留时间(秒)" prop="jobTtlSecondsAfterFinished">
        <el-input-number v-model="executionCluster.jobTtlSecondsAfterFinished" controls-position="right" :min="1" />
      </el-form-item>
      <el-form-item v-if="executionCluster.type === 'k8s_job'" label="执行完是否删除" prop="cleanJobAfterSuccess">
        <el-switch
          v-model="executionCluster.cleanJobAfterSuccess"
          active-color="#13ce66"
          inactive-color="#ff4949"
        />
      </el-form-item>
      <el-form-item v-if="executionCluster.type === 'k8s_deployment'" label="扫描器" prop="scanner" :rules="[{ required: true, message: '扫描器不能为空'}]">
        <el-select
          v-model="executionCluster.scanner"
          placeholder="请选择"
        >
          <el-option
            v-for="item in scannerOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item v-if="executionCluster.type === 'k8s_deployment'" label="最大副本数" prop="maxReplicas" :rules="[{ required: true, message: '最大副本数不能为空'}]">
        <el-input-number v-model="executionCluster.maxReplicas" controls-position="right" :min="1" />
      </el-form-item>
      <el-form-item v-if="executionCluster.type === 'k8s_deployment'" label="最小副本数" prop="minReplicas" :rules="[{ required: true, message: '最小副本数不能为空'}]">
        <el-input-number v-model="executionCluster.minReplicas" controls-position="right" :min="1" />
      </el-form-item>
      <el-form-item v-if="executionCluster.type === 'k8s_deployment'" label="扩容阈值" prop="scaleThreshold" :rules="[{ required: true, message: '扩容阈值不能为空'}]">
        <el-input-number v-model="executionCluster.scaleThreshold" controls-position="right" :min="1" />
      </el-form-item>
      <el-form-item v-if="executionCluster.type === 'k8s_deployment'" label="扫描器重试次数" prop="pullRetry" :rules="[{ required: true, message: '扫描器重试次数不能为空'}]">
        <el-input-number v-model="executionCluster.pullRetry" controls-position="right" :min="1" />
      </el-form-item>
      <el-form-item v-if="executionCluster.type === 'docker'" label="host" prop="host" :rules="[{ required: true, message: 'host不能为空'}]">
        <el-input v-model="executionCluster.host" style="height: 40px ; width: 500px;" />
      </el-form-item>
      <el-form-item v-if="executionCluster.type === 'docker'" label="版本" prop="version" :rules="[{ required: true, message: '版本不能为空'}]">
        <el-input v-model="executionCluster.version" style="height: 40px ; width: 500px;" />
      </el-form-item>
      <el-form-item v-if="executionCluster.type === 'docker'" label="连接超时时长(毫秒)" prop="connectTimeout" :rules="[{ required: true, message: '连接超时时长不能为空'}]">
        <el-input-number v-model="executionCluster.connectTimeout" controls-position="right" :min="0" />
      </el-form-item>
      <el-form-item v-if="executionCluster.type === 'docker'" label="读取超时时长(毫秒)" prop="readTimeout" :rules="[{ required: true, message: '读取超时时长不能为空'}]">
        <el-input-number v-model="executionCluster.readTimeout" controls-position="right" :min="0" />
      </el-form-item>
      <el-form-item v-if="executionCluster.type === 'docker'" label="最大任务数" prop="maxTaskCount" :rules="[{ required: true, message: '最大任务数不能为空'}]">
        <el-input-number v-model="executionCluster.maxTaskCount" controls-position="right" :min="0" />
      </el-form-item>
    </el-form>
    <div slot="footer">
      <el-button @click="close">取 消</el-button>
      <el-button type="primary" @click="handleUpdate()">确 定</el-button>
    </div>
  </el-dialog>
</template>
<script>
import _ from 'lodash'
import { scanners } from '@/api/scan'
import { create, update } from '@/api/executionClusters'

export default {
  name: 'EditClusterConfigDialog',
  props: {
    visible: Boolean,
    createMode: Boolean,
    /**
     * 仅在更新模式时有值
     */
    updatingClusterConfig: {
      type: Object,
      default: undefined
    }
  },
  data() {
    return {
      repoCache: {},
      showDialog: this.visible,
      executionCluster: this.newCluster(),
      data: '',
      rules: {},
      options: [
        { value: 'k8s_job',
          label: 'k8s_job' },
        { value: 'k8s_deployment',
          label: 'k8s_deployment'
        },
        { value: 'docker',
          label: 'docker' }
      ],
      scannerOptions: []
    }
  },
  watch: {
    visible: function(newVal) {
      if (newVal) {
        this.resetStrategy()
        this.showDialog = true
      } else {
        this.close()
      }
    }
  },
  created() {
    scanners().then(res => {
      res.data.forEach(item => {
        const scanner = {
          label: item.name,
          value: item.name
        }
        this.scannerOptions.push(scanner)
      })
    })
  },
  methods: {
    resetStrategy() {
      if (this.createMode) {
        this.executionCluster = this.newCluster()
      } else {
        this.executionCluster = _.cloneDeep(this.updatingClusterConfig)
        if (!this.executionCluster.kubernetesProperties) {
          this.executionCluster.kubernetesProperties = {
            namespace: '',
            apiServer: '',
            certificateAuthorityData: '',
            token: '',
            clientCertificateData: '',
            clientKeyData: '',
            limitMem: 1,
            limitStorage: 1,
            limitCpu: 1
          }
        }
      }
      this.$nextTick(() => {
        this.$refs['form'].clearValidate()
      })
    },
    newCluster() {
      const Cluster = {
        name: '',
        type: 'k8s_job',
        description: '',
        kubernetesProperties: {
          namespace: '',
          apiServer: '',
          certificateAuthorityData: '',
          token: '',
          clientCertificateData: '',
          clientKeyData: '',
          limitMem: 1,
          limitStorage: 1,
          limitCpu: 1
        },
        jobTtlSecondsAfterFinished: 1,
        cleanJobAfterSuccess: true,
        scanner: '',
        maxReplicas: 1,
        minReplicas: 1,
        scaleThreshold: 1,
        pullRetry: 1,
        host: 'unix://var/run/docker.sock',
        version: '1.23',
        connectTimeout: 5000,
        readTimeout: 0,
        maxTaskCount: 1
      }
      return Cluster
    },
    close(changed) {
      this.showDialog = false
      this.$refs['form'].resetFields()
      if (changed === true) {
        this.$emit('updated')
      }
      this.$emit('update:visible', false)
    },
    handleUpdate() {
      this.$refs['form'].validate((valid) => {
        if (valid) {
          if (this.executionCluster.kubernetesProperties.apiServer === '') {
            this.executionCluster.kubernetesProperties.apiServer === null
          }
          if (this.executionCluster.kubernetesProperties.certificateAuthorityData === '') {
            this.executionCluster.kubernetesProperties.certificateAuthorityData === null
          }
          if (this.executionCluster.kubernetesProperties.token === '') {
            this.executionCluster.kubernetesProperties.token === null
          }
          if (this.executionCluster.kubernetesProperties.clientCertificateData === '') {
            this.executionCluster.kubernetesProperties.clientCertificateData === null
          }
          if (this.executionCluster.kubernetesProperties.clientKeyData === '') {
            this.executionCluster.kubernetesProperties.clientKeyData === null
          }
          if (this.createMode) {
            create(this.executionCluster).then(() => {
              this.$message.success('新建配置成功')
              this.close(true)
            })
          } else {
            update(this.executionCluster).then(() => {
              this.$message.success('更新配置成功')
              this.close(true)
            })
          }
        }
      })
    }
  }
}
</script>

<style scoped>

</style>
