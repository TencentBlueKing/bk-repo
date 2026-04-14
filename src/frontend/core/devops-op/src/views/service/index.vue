<template>
  <div class="app-container">
    <el-form ref="form" :inline="true">
      <el-form-item ref="project-form-item" label="实例地址">
        <el-autocomplete
          v-model="ip"
          style="margin-left: 10px;"
          :fetch-suggestions="querySearch"
          placeholder="请输入IP来进行搜索"
          @select="handleSelect"
          @input="handleInput"
        />
      </el-form-item>
    </el-form>
    <el-table
      :data="services"
      style="width: 100%"
    >
      <el-table-column
        prop="name"
        label="服务名"
        width="280"
      />
      <el-table-column
        prop="instances"
        label="实例数"
        width="180"
      >
        <template slot-scope="scope">
          {{ scope.row.instances ? scope.row.instances.length: 0 }}
        </template>
      </el-table-column>
      <el-table-column
        prop="onlineNumber"
        width="180"
      >
        <template slot="header">
          <span>在线实例数 <i class="el-icon-success" style="color: #67C23A;" /></span>
        </template>
        <template slot-scope="scope">
          {{ scope.row.onlineNumber }}
        </template>
      </el-table-column>
      <el-table-column
        prop="offlineNumber"
        width="180"
      >
        <template slot="header">
          <span>离线实例数 <i class="el-icon-error" style="color: #F56C6C;" /></span>
        </template>
        <template slot-scope="scope">
          {{ scope.row.offlineNumber }}
        </template>
      </el-table-column>
      <el-table-column>
        <template slot="header">
          <span>操作</span>
        </template>
        <template slot-scope="scope">
          <el-button type="primary" size="mini" @click="toInstance(scope.row.name)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script>

import { ROUTER_NAME_INSTANCE } from '@/router'
import { services } from '@/api/service'

export default {
  name: 'Service',
  data() {
    return {
      originalServices: [],
      services: [],
      ip: '',
      ips: []
    }
  },
  created() {
    this.ip = localStorage.getItem('instanceHost') || ''
    services().then(res => {
      this.originalServices = res.data
      if (this.ip !== '') {
        this.services = this.filterFunction(this.ip)
      } else {
        this.services = res.data
      }
      this.ips = res.data.reduce((acc, service) => {
        if (service.instances) {
          service.instances.forEach(instance => {
            if (instance.host && instance.host.trim() && !acc.includes(instance.host)) {
              acc.push(instance.host)
            }
          })
        }
        return acc
      }, [])
    })
  },
  methods: {
    querySearch(queryString, cb) {
      if (queryString.trim() === '') {
        this.services = this.originalServices
      }
      var ips = this.ips || []
      var results = queryString ? ips.filter(ip => ip.includes(queryString)) : ips
      // 调用 callback 返回建议列表的数据
      cb(results.map(ip => ({ value: ip })))
    },
    toInstance(serviceName) {
      this.$router.push({ name: ROUTER_NAME_INSTANCE, params: { serviceName: serviceName }})
    },
    handleSelect(selected) {
      const selectedIp = selected.value
      if (selectedIp) {
        localStorage.setItem('instanceHost', selectedIp)
        this.services = this.filterFunction(selectedIp)
      } else {
        localStorage.removeItem('instanceHost')
        this.services = this.originalServices
      }
    },
    handleInput(value) {
      localStorage.setItem('instanceHost', value)
      if (value.trim() === '') {
        localStorage.removeItem('instanceHost')
        this.services = this.originalServices
      } else {
        localStorage.setItem('instanceHost', value)
        this.services = this.filterFunction(value)
      }
    },
    filterFunction(value) {
      return this.originalServices.filter(service => {
        const hasMatchingInstance = service.instances && service.instances.some(instance =>
          instance.host && instance.host.includes(value)
        )
        if (hasMatchingInstance) {
          service.onlineNumber = service.instances.filter(instance =>
            instance.host && instance.host.includes(value) && instance.status === 'RUNNING'
          ).length
          service.offlineNumber = service.instances.filter(instance =>
            instance.host && instance.host.includes(value) && instance.status !== 'RUNNING'
          ).length
        }
        return hasMatchingInstance
      })
    }
  }
}
</script>

<style scoped>

</style>
