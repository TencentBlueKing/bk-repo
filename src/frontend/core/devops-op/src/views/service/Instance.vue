<template>
  <div class="app-container">
    <el-table
      :data="instances.filter(data => !search || (data.host+':'+data.port).includes(search))"
      style="width: 100%"
    >
      <el-table-column
        prop="id"
        label="ID"
      />
      <el-table-column
        prop="address"
        label="地址"
        width="180"
      >
        <template slot-scope="scope">
          {{ `${scope.row.host}:${scope.row.port}` }}
        </template>
      </el-table-column>
      <el-table-column
        prop="status"
        label="状态"
        width="180"
      >
        <template slot-scope="scope">
          <el-tag :type="instanceStatusTagType(scope.row.status)">{{ scope.row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column
        label="正在传输"
        width="180"
      >
        <template
          v-if="scope.row.detail.uploadingCount >= 0 && scope.row.detail.downloadingCount >= 0"
          slot-scope="scope"
        >
          <svg-icon icon-class="arrow-up" />{{ scope.row.detail.uploadingCount }}
          <svg-icon icon-class="arrow-down" /> {{ scope.row.detail.downloadingCount }}
        </template>
      </el-table-column>
      <el-table-column
        label="正在异步上传"
        width="180"
      >
        <template v-if="scope.row.detail.asyncTaskActiveCount >= 0" slot-scope="scope">
          <svg-icon icon-class="arrow-up" />{{ scope.row.detail.asyncTaskActiveCount }}
        </template>
      </el-table-column>
      <el-table-column
        label="上传带宽"
        prop="serviceUploadBandwidth"
      >
        <template slot-scope="scope">
          {{ convertBpsToMbps(scope.row.serviceUploadBandwidth) }}
        </template>
      </el-table-column>
      <el-table-column
        label="下载带宽"
        prop="serviceDownloadBandwidth"
      >
        <template slot-scope="scope">
          {{ convertBpsToMbps(scope.row.serviceDownloadBandwidth) }}
        </template>
      </el-table-column>
      <el-table-column
        label="异步上传带宽"
        prop="serviceCosAsyncUploadBandwidth"
      >
        <template slot-scope="scope">
          {{ convertBpsToMbps(scope.row.serviceCosAsyncUploadBandwidth) }}
        </template>
      </el-table-column>
      <el-table-column
        label="实例总带宽"
        prop="hostBandwidth"
      >
        <template slot-scope="scope">
          {{ convertBpsToMbps(scope.row.hostBandwidth) }}
        </template>
      </el-table-column>
      <el-table-column label="已加载插件">
        <template slot-scope="scope">
          <el-tag v-for="plugin in scope.row.detail.loadedPlugins" :key="plugin" style="margin-right:5px;">{{ plugin }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作">
        <template slot="header" slot-scope="{}">
          <el-input
            v-model="search"
            placeholder="输入关键字搜索地址"
          />
        </template>
        <template slot-scope="scope">
          <el-button
            :disabled="disableChangeInstanceStatusBtn(scope.row.status)"
            type="primary"
            size="mini"
            style="width: 75px"
            @click="changeInstanceStatus(serviceName, scope.row, scope.$index)"
          >
            {{ changeInstanceStatusBtnName(scope.row.status) }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>
<script>
import { bandwidths, down, INSTANCE_STATUS_DEREGISTER, INSTANCE_STATUS_RUNNING, instances, up } from '@/api/service'

export default {
  name: 'Instance',
  props: {
    serviceName: {
      type: String,
      required: true
    }
  },
  data() {
    return {
      loading: true,
      instances: [],
      search: ''
    }
  },
  created() {
    instances(this.serviceName).then(res => {
      this.instances = res.data
      this.loading = false
      bandwidths(this.serviceName).then(bandwidths => {
        if (this.instances.length > 0 && bandwidths.data.length > 0) {
          this.instances = this.instances.map(instance => {
            const match = bandwidths.data.find(bandwidth => bandwidth.host === instance.host)
            return match ? { ...instance, ...match } : instance
          })
        }
      })
    }).catch(() => {
      this.loading = false
    })
  },
  methods: {
    changeInstanceStatus(serviceName, instance, index) {
      const instanceStatus = instance.status
      const instanceId = instance.id

      const operation = instanceStatus === INSTANCE_STATUS_RUNNING ? '下线' : '上线'
      this.$confirm(`是否确定${operation}实例${instanceId}`, '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        const instancePromise = instanceStatus === INSTANCE_STATUS_RUNNING
          ? this.down(serviceName, instanceId) : this.up(serviceName, instanceId)
        instancePromise.then(updatedInstance => {
          this.instances.splice(index, 1, updatedInstance)
        })
      }).catch(() => {
        this.$message({
          type: 'info',
          message: '已取消'
        })
      })
    },
    convertBpsToMbps(data) {
      if (data) {
        return (data / 1000000) + '/Mbps'
      } else {
        return '0/Mbps'
      }
    },
    changeInstanceStatusBtnName(instanceStatus) {
      switch (instanceStatus) {
        case INSTANCE_STATUS_RUNNING:
          return '下线'
        default:
          return '上线'
      }
    },
    disableChangeInstanceStatusBtn(instanceStatus) {
      switch (instanceStatus) {
        case INSTANCE_STATUS_RUNNING:
        case INSTANCE_STATUS_DEREGISTER:
          return false
        default:
          return true
      }
    },
    instanceStatusTagType(status) {
      switch (status) {
        case INSTANCE_STATUS_RUNNING:
          return 'success'
        case INSTANCE_STATUS_DEREGISTER:
          return 'info'
        default:
          return 'danger'
      }
    },
    up(serviceName, instanceId) {
      return up(serviceName, instanceId).then(res => {
        this.$message.success(`上线成功`)
        return res.data
      })
    },
    down(serviceName, instanceId) {
      return down(serviceName, instanceId).then(res => {
        this.$message.success(`下线成功`)
        return res.data
      })
    }
  }
}
</script>

<style scoped>

</style>
