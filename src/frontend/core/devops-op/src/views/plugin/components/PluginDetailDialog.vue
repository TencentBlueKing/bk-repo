<template>
  <el-dialog v-loading="loading" title="修改插件" :visible.sync="showDialog" :before-close="close">
    <template v-if="!updateResult">
      <el-form ref="form" :model="pluginDetail" label-width="150px">
        <el-form-item label="id">
          <el-input v-model="pluginDetail.id" disabled />
        </el-form-item>
        <el-form-item label="版本">
          <el-input v-model="pluginDetail.version" />
        </el-form-item>
        <el-form-item label="生效范围">
          <el-input v-model="pluginDetail.scope" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="pluginDetail.description" />
        </el-form-item>
        <el-form-item label="代码库地址">
          <el-input v-model="pluginDetail.gitUrl" />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button @click="close">取 消</el-button>
        <el-button type="primary" @click="updatePlugin">确定</el-button>
      </div>
    </template>
    <el-result v-else :icon="resultIcon" :title="resultTitle">
      <template slot="extra">
        <el-divider />
        <el-button type="primary" @click="close">返 回</el-button>
      </template>
    </el-result>
  </el-dialog>
</template>

<script>
import { updatePlugin } from '@/api/plugin'

export default {
  name: 'PluginDetailDialog',
  props: {
    visible: Boolean,
    plugin: {
      type: Object,
      required: true
    }
  },
  data() {
    return {
      showDialog: this.visible,
      loading: false,
      pluginDetail: {},
      resultIcon: 'success',
      resultTitle: '',
      updateResult: undefined
    }
  },
  watch: {
    visible: function(newVal) {
      if (newVal) {
        this.showDialog = true
        this.loading = true
        this.pluginDetail = JSON.parse(JSON.stringify(this.plugin))
        this.updateResult = undefined
        this.loading = false
      } else {
        this.close()
      }
    }
  },
  methods: {
    updatePlugin() {
      this.loading = true
      if (typeof (this.pluginDetail.scope) === 'string') {
        this.pluginDetail.scope = this.pluginDetail.scope.split(',')
      }
      const promise = updatePlugin(
        this.pluginDetail.id,
        this.pluginDetail.version,
        this.pluginDetail.scope,
        this.pluginDetail.description,
        this.pluginDetail.gitUrl
      )
      promise.then(() => {
        this.updateResult = true
        this.resultTitle = '更新成功'
        this.resultIcon = 'success'
        this.$emit('update-success')
      }).catch(_ => {
        this.updateResult = {}
        this.resultTitle = '更新失败'
        this.resultIcon = 'error'
      }).finally(() => {
        this.loading = false
      })
    },
    close() {
      this.pluginDetail = {}
      this.showDialog = false
      this.$emit('update:visible', false)
    }
  }
}
</script>

<style scoped>

</style>
