<template>
  <el-dialog v-loading="loading" title="插件状态" :visible.sync="showDialog" :before-close="close" width="700px">
    <el-table :data="instances" style="width: 100%">
      <el-table-column prop="address" label="服务地址" width="180">
        <template slot-scope="scope">{{ `${scope.row.host}:${scope.row.port}` }}</template>
      </el-table-column>
      <el-table-column label="插件状态">
        <template slot-scope="scope">
          <el-tag v-if="isLoaded(scope.row.detail.loadedPlugins)" type="success">已加载</el-tag>
          <el-tag v-if="!isLoaded(scope.row.detail.loadedPlugins)" type="info">未加载</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作">
        <template slot-scope="scope">
          <el-button v-if="!isLoaded(scope.row.detail.loadedPlugins)" size="mini" type="danger" @click="loadPlugin(scope.row)">加载插件</el-button>
          <el-button v-if="isLoaded(scope.row.detail.loadedPlugins)" size="mini" type="danger" @click="unloadPlugin(scope.row)">卸载插件</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div slot="footer">
      <el-button @click="close">返回</el-button>
    </div>
  </el-dialog>
</template>

<script>
import { loadPlugin, unloadPlugin } from '@/api/plugin'
import { instances } from '@/api/service'

export default {
  name: 'PluginStatusDialog',
  props: {
    visible: Boolean,
    plugin: {
      type: Object,
      required: true
    }
  },
  data() {
    return {
      loading: false,
      showDialog: this.visible,
      resultIcon: 'success',
      resultTitle: '',
      instances: []
    }
  },
  watch: {
    visible: function(newVal) {
      if (newVal) {
        this.getInstances(this.plugin.scope)
        this.showDialog = true
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
    getInstances(scope) {
      this.instances = []
      scope.forEach(element => {
        instances(`bkrepo-${element}`).then(res => {
          this.instances.push(...res.data)
          this.loading = false
        }).catch(() => {
          this.loading = false
        })
      })
    },
    isLoaded(plugins) {
      return plugins.includes(this.plugin.id)
    },
    loadPlugin(instance) {
      this.loading = true
      const host = `${instance.host}:${instance.port}`
      loadPlugin(this.plugin.id, host).then(() => {
        this.$message({
          message: '加载插件成功',
          type: 'success'
        })
        this.getInstances(this.plugin.scope)
        this.loading = false
      }).catch(() => {
        this.loading = false
      })
    },
    unloadPlugin(instance) {
      const id = this.plugin.id
      this.$prompt(`服务运行时卸载插件存在风险,请输入插件id[${id}]确认操作`, '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        inputValidator: function(input) {
          return input === id
        },
        inputErrorMessage: '插件id不正确'
      }).then(() => {
        this.loading = true
        const host = `${instance.host}:${instance.port}`
        unloadPlugin(this.plugin.id, host).then(res => {
          this.$message({
            message: '卸载插件成功',
            type: 'success'
          })
          this.getInstances(this.plugin.scope)
          this.loading = false
        }).catch(() => {
          this.loading = false
        })
      })
    }
  }
}
</script>

<style scoped>

</style>
