<template>
  <el-dialog v-loading="loading" title="新建插件" :visible.sync="showDialog" :before-close="close">
    <template v-if="!createResult">
      <el-form ref="form" :model="plugin" :rules="rules" label-width="150px">
        <el-form-item>
          <span>请与插件gradle.properties中配置保持一致</span>
        </el-form-item>
        <el-form-item label="id" prop="id">
          <el-input v-model="plugin.id" />
        </el-form-item>
        <el-form-item label="版本" prop="version">
          <el-input v-model="plugin.version" />
        </el-form-item>
        <el-form-item label="生效范围" prop="scope">
          <el-input v-model="plugin.scope" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="plugin.description" />
        </el-form-item>
        <el-form-item label="代码库地址" prop="gitUrl">
          <el-input v-model="plugin.gitUrl" />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button @click="close">取 消</el-button>
        <el-button type="primary" @click="createPlugin">确定</el-button>
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
import { createPlugin } from '@/api/plugin'

export default {
  name: 'PluginCreateDialog',
  props: {
    visible: Boolean
  },
  data() {
    return {
      rules: {
        id: [{ required: true, message: '请输入插件id', trigger: 'blur' }],
        version: [{ required: true, message: '请输入插件版本', trigger: 'blur' }],
        scope: [{ required: true, message: '请输入生效范围', trigger: 'blur' }],
        description: [{ required: true, message: '请输入插件描述', trigger: 'blur' }],
        gitUrl: [{ required: true, message: '请输入代码库地址', trigger: 'blur' }]
      },
      showDialog: this.visible,
      loading: false,
      plugin: {},
      resultIcon: 'success',
      resultTitle: '',
      createResult: undefined
    }
  },
  watch: {
    visible: function(newVal) {
      if (newVal) {
        this.showDialog = true
        this.loading = true
        this.createResult = undefined
        this.loading = false
      } else {
        this.close()
      }
    }
  },
  methods: {
    createPlugin() {
      this.$refs['form'].validate((valid) => {
        if (!valid) {
          return
        }
        this.loading = true
        const promise = createPlugin(
          this.plugin.id,
          this.plugin.version,
          this.plugin.scope.split(','),
          this.plugin.description,
          this.plugin.gitUrl
        )
        promise.then(() => {
          this.createResult = true
          this.resultTitle = '创建成功'
          this.resultIcon = 'success'
          this.$emit('create-success')
        }).catch(_ => {
          this.createResult = {}
          this.resultTitle = '创建失败'
          this.resultIcon = 'error'
        }).finally(() => {
          this.loading = false
        })
      })
    },
    close() {
      this.plugin = {}
      this.showDialog = false
      this.$emit('update:visible', false)
    }
  }
}
</script>

<style scoped>

</style>
