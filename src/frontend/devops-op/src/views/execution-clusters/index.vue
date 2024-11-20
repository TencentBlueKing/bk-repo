<template>
  <div class="app-container">
    <el-table
      v-loading="loading"
      :data="clusters"
      border
      style="width: 100%; margin-top: -20px"
    >
      <el-table-column
        prop="name"
        fixed
        label="执行集群名"
        width="180"
      />
      <el-table-column
        prop="description"
        fixed
        label="描述"
        width="100"
      />
      <el-table-column prop="k8s_job" label="k8s_job" align="center">
        <el-table-column prop="jobTtlSecondsAfterFinished" label="最大保留时间" align="center">
          <template slot-scope="scope">
            <span v-if="scope.row.type === 'k8s_job'">
              {{ roughCalculateTime(scope.row.jobTtlSecondsAfterFinished * 1000) }}
            </span>
            <span v-else>
              --
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="cleanJobAfterSuccess" label="执行完是否删除" align="center">
          <template slot-scope="scope">
            <span v-if="scope.row.type === 'k8s_job'">
              {{ scope.row.cleanJobAfterSuccess }}
            </span>
            <span v-else>
              --
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="kubernetesProperties" label="k8s配置" align="center">
          <el-table-column prop="namespace" label="命名空间" align="center">
            <template slot-scope="scope">
              <span v-if="scope.row.type === 'k8s_job'">
                {{ scope.row.kubernetesProperties.namespace }}
              </span>
              <span v-else>
                --
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="apiServer" label="apiServer" align="center" width="150">
            <template slot-scope="scope">
              <span v-if="scope.row.type === 'k8s_job' && scope.row.kubernetesProperties.apiServer">
                {{ scope.row.kubernetesProperties.apiServer }}
              </span>
              <span v-else>
                --
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="certificateAuthorityData" label="certificateAuthorityData" align="center">
            <template slot-scope="scope">
              <span v-if="scope.row.type === 'k8s_job' && scope.row.kubernetesProperties.certificateAuthorityData">
                {{ scope.row.kubernetesProperties.certificateAuthorityData }}
              </span>
              <span v-else>
                --
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="token" label="token" align="center">
            <template slot-scope="scope">
              <span v-if="scope.row.type === 'k8s_job' && scope.row.kubernetesProperties.token">
                {{ scope.row.kubernetesProperties.token }}
              </span>
              <span v-else>
                --
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="clientCertificateData" label="clientCertificateData" align="center">
            <template slot-scope="scope">
              <span v-if="scope.row.type === 'k8s_job' && scope.row.kubernetesProperties.clientCertificateData">
                {{ scope.row.kubernetesProperties.clientCertificateData }}
              </span>
              <span v-else>
                --
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="clientKeyData" label="clientKeyData" align="center">
            <template slot-scope="scope">
              <span v-if="scope.row.type === 'k8s_job' && scope.row.kubernetesProperties.clientKeyData">
                {{ scope.row.kubernetesProperties.clientKeyData }}
              </span>
              <span v-else>
                --
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="limitMem" label="最大使用内存" align="center" width="100">
            <template slot-scope="scope">
              <span v-if="scope.row.type === 'k8s_job'">
                {{ convertFileSize(scope.row.kubernetesProperties.limitMem) }}
              </span>
              <span v-else>
                --
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="limitStorage" label="最大临时内存" align="center" width="100">
            <template slot-scope="scope">
              <span v-if="scope.row.type === 'k8s_job'">
                {{ convertFileSize(scope.row.kubernetesProperties.limitStorage) }}
              </span>
              <span v-else>
                --
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="limitCpu" label="最大cpu" align="center">
            <template slot-scope="scope">
              <span v-if="scope.row.type === 'k8s_job'">
                {{ scope.row.kubernetesProperties.limitCpu }}
              </span>
              <span v-else>
                --
              </span>
            </template>
          </el-table-column>
        </el-table-column>
      </el-table-column>
      <el-table-column prop="k8s_deployment" label="k8s_deployment" align="center">
        <el-table-column prop="kubernetesProperties" label="k8s配置" align="center">
          <el-table-column prop="namespace" label="命名空间" align="center">
            <template slot-scope="scope">
              <span v-if="scope.row.type === 'k8s_deployment'">
                {{ scope.row.kubernetesProperties.namespace }}
              </span>
              <span v-else>
                --
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="apiServer" label="apiServer" align="center" width="150">
            <template slot-scope="scope">
              <span v-if="scope.row.type === 'k8s_deployment' && scope.row.kubernetesProperties.apiServer">
                {{ scope.row.kubernetesProperties.apiServer }}
              </span>
              <span v-else>
                --
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="certificateAuthorityData" label="certificateAuthorityData" align="center">
            <template slot-scope="scope">
              <span v-if="scope.row.type === 'k8s_deployment' && scope.row.kubernetesProperties.certificateAuthorityData">
                {{ scope.row.kubernetesProperties.certificateAuthorityData }}
              </span>
              <span v-else>
                --
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="token" label="token" align="center">
            <template slot-scope="scope">
              <span v-if="scope.row.type === 'k8s_deployment' && scope.row.kubernetesProperties.token">
                {{ scope.row.kubernetesProperties.token }}
              </span>
              <span v-else>
                --
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="clientCertificateData" label="clientCertificateData" align="center">
            <template slot-scope="scope">
              <span v-if="scope.row.type === 'k8s_deployment' && scope.row.kubernetesProperties.clientCertificateData">
                {{ scope.row.kubernetesProperties.clientCertificateData }}
              </span>
              <span v-else>
                --
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="clientKeyData" label="clientKeyData" align="center">
            <template slot-scope="scope">
              <span v-if="scope.row.type === 'k8s_deployment' && scope.row.kubernetesProperties.clientKeyData">
                {{ scope.row.kubernetesProperties.clientKeyData }}
              </span>
              <span v-else>
                --
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="limitMem" label="最大使用内存" align="center" width="100">
            <template slot-scope="scope">
              <span v-if="scope.row.type === 'k8s_deployment'">
                {{ convertFileSize(scope.row.kubernetesProperties.limitMem) }}
              </span>
              <span v-else>
                --
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="limitStorage" label="最大临时内存" align="center" width="100">
            <template slot-scope="scope">
              <span v-if="scope.row.type === 'k8s_deployment'">
                {{ convertFileSize(scope.row.kubernetesProperties.limitStorage) }}
              </span>
              <span v-else>
                --
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="limitCpu" label="最大cpu" align="center">
            <template slot-scope="scope">
              <span v-if="scope.row.type === 'k8s_deployment'">
                {{ scope.row.kubernetesProperties.limitCpu }}
              </span>
              <span v-else>
                --
              </span>
            </template>
          </el-table-column>
        </el-table-column>
        <el-table-column prop="scanner" label="扫描器" align="center">
          <template slot-scope="scope">
            <span v-if="scope.row.type === 'k8s_deployment'">
              {{ scope.row.scanner }}
            </span>
            <span v-else>
              --
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="maxReplicas" label="最大副本数" align="center">
          <template slot-scope="scope">
            <span v-if="scope.row.type === 'k8s_deployment'">
              {{ scope.row.maxReplicas }}
            </span>
            <span v-else>
              --
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="minReplicas" label="最小副本数" align="center">
          <template slot-scope="scope">
            <span v-if="scope.row.type === 'k8s_deployment'">
              {{ scope.row.minReplicas }}
            </span>
            <span v-else>
              --
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="scaleThreshold" label="扩容阈值" align="center">
          <template slot-scope="scope">
            <span v-if="scope.row.type === 'k8s_deployment'">
              {{ scope.row.scaleThreshold }}
            </span>
            <span v-else>
              --
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="pullRetry" label="扫描器重试次数" align="center">
          <template slot-scope="scope">
            <span v-if="scope.row.type === 'k8s_deployment'">
              {{ scope.row.pullRetry }}
            </span>
            <span v-else>
              --
            </span>
          </template>
        </el-table-column>
      </el-table-column>
      <el-table-column prop="docker" label="docker" align="center">
        <el-table-column prop="host" label="host" align="center">
          <template slot-scope="scope">
            <span v-if="scope.row.type === 'docker'">
              {{ scope.row.host }}
            </span>
            <span v-else>
              --
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="version" label="版本" align="center">
          <template slot-scope="scope">
            <span v-if="scope.row.type === 'docker'">
              {{ scope.row.version }}
            </span>
            <span v-else>
              --
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="connectTimeout" label="连接超时时长" align="center">
          <template slot-scope="scope">
            <span v-if="scope.row.type === 'docker'">
              {{ roughCalculateTime(scope.row.connectTimeout) }}
            </span>
            <span v-else>
              --
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="readTimeout" label="读取超时时长" align="center">
          <template slot-scope="scope">
            <span v-if="scope.row.type === 'docker'">
              {{ roughCalculateTime(scope.row.readTimeout) }}
            </span>
            <span v-else>
              --
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="maxTaskCount" label="最大任务数" align="center">
          <template slot-scope="scope">
            <span v-if="scope.row.type === 'docker'">
              {{ scope.row.maxTaskCount }}
            </span>
            <span v-else>
              --
            </span>
          </template>
        </el-table-column>
      </el-table-column>
      <el-table-column align="right" width="150">
        <template slot="header">
          <el-button type="primary" @click="showEdit(true)">创建</el-button>
        </template>
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="primary"
            @click="showEdit(false, scope.row)"
          >
            编辑
          </el-button>
          <el-button
            size="mini"
            type="danger"
            @click="handleDelete(scope.row)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <edit-cluster-config-dialog :visible.sync="showDialog" :updating-cluster-config="param" :create-mode="createMode" @updated="updated" />
  </div>
</template>

<script>

import { clusters, remove } from '@/api/executionClusters'
import { convertFileSize } from 'devops-op/src/utils/file'
import { roughCalculateTime } from '@/utils/date'
import editClusterConfigDialog from '@/views/execution-clusters/components/EditClusterConfigDialog.vue'

export default {
  name: 'ExecutionClusters',
  components: { editClusterConfigDialog },
  inject: ['reload'],
  data() {
    return {
      loading: false,
      clusters: [],
      param: undefined,
      createMode: false,
      showDialog: false
    }
  },
  created() {
    this.query()
  },
  methods: {
    roughCalculateTime,
    convertFileSize,
    query() {
      clusters().then(res => {
        this.clusters = res.data
      })
    },
    showEdit(mode, row) {
      this.createMode = mode
      this.param = row
      this.showDialog = true
    },
    handleDelete(row) {
      this.$confirm(`是否确定删除当前配置`, '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        let promise = null
        promise = remove(row.name)
        promise.then(() => {
          this.updated()
        })
      }).catch(() => {
        this.$message({
          type: 'info',
          message: '已取消'
        })
      })
    },
    updated() {
      this.reload()
    }
  }
}
</script>

<style scoped>
::v-deep .el-table__header thead.is-group th.el-table__cell {
  background: transparent !important;
  border: 1px solid black !important;
}

</style>
