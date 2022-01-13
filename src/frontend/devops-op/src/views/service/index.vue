<template>
  <div class="app-container">
    <el-table
      :data="services"
      style="width: 100%"
    >
      <el-table-column
        prop="name"
        label="服务名"
        width="180"
      />
      <el-table-column label="操作">
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
      services: []
    }
  },
  created() {
    services().then(res => {
      this.services = res.data
    })
  },
  methods: {
    toInstance(serviceName) {
      this.$router.push({ name: ROUTER_NAME_INSTANCE, params: { serviceName: serviceName }})
    }
  }
}
</script>

<style scoped>

</style>
