<template>
  <div>
    <el-button type="primary" @click="toInstance">toInstance</el-button>
  </div>
</template>

<script>

export default {
  name: 'ServiceManager',
  methods: {
    toInstance() {
      this.$router.push({ name: 'Instance', params: { serviceName: 'repo-auth' }})
    }
  }
}
</script>

<style scoped>

</style>
