<template>
  <el-dialog title="创建项目配置" :visible.sync="showDialog" :before-close="close">
    <el-form ref="form" :model="projectConfig" status-icon>
      <el-form-item ref="project-form-item" label="项目ID" prop="projectId" :rules="[{ required: true, message: '项目ID不能为空'}]">
        <el-input :disabled="!createMode" v-model="projectConfig.projectId" />
      </el-form-item>
      <el-form-item
        label="环境"
        prop="environment"
        :rules="[{ required: true, message: '环境不能为空'}]"
      >
        <el-input v-model="projectConfig.environment" />
      </el-form-item>
    </el-form>
    <div slot="footer">
      <el-button @click="close">取 消</el-button>
      <el-button type="primary" @click="handleCreateOrUpdate(projectConfig)">确 定</el-button>
    </div>
  </el-dialog>
</template>

<script>
import _ from 'lodash'
import { searchProjects } from '@/api/project'
import { create, update } from '@/api/projectgrayscale'

export default {
  name: 'CreateOrUpdateConfigDialog',
  props: {
    visible: Boolean,
    /**
     * 仅在更新模式时有值
     */
    updatingKeys: {
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
      projectId: '',
      projectConfig: {
        projectId: '',
        environment: ''
      }
    }
  },
  watch: {
    visible: function(newVal) {
      if (newVal) {
        this.resetConfig()
        this.showDialog = true
      } else {
        this.close()
      }
    }
  },
  methods: {
    queryProjects(queryStr, cb) {
      searchProjects(queryStr).then(res => {
        this.projects = res.data.records
        cb(this.projects)
      })
    },
    selectProject(project) {
      this.$refs['project-form-item'].resetField()
      this.projectConfig.projectId = project.name
    },
    close() {
      this.showDialog = false
      this.$emit('update:visible', false)
    },
    handleCreateOrUpdate(projectConfig) {
      this.$refs['form'].validate((valid) => {
        if (valid) {
          let reqPromise
          if (this.createMode) {
            reqPromise = create(projectConfig)
          } else {
            reqPromise = update(projectConfig)
          }
          const msg = this.createMode ? '创建配置成功' : '更新配置成功'
          reqPromise.then(() => {
            this.$message.success(msg)
            this.createMode ? this.$emit('created', projectConfig) : this.$emit('updated')
            this.close()
          })
        }
      })
    },
    resetConfig() {
      if (!this.createMode) {
        this.projectConfig = _.cloneDeep(this.updatingKeys)
      } else {
        this.projectConfig = {
          projectId: '',
          environment: ''
        }
      }
      this.$nextTick(() => {
        this.$refs['form'].clearValidate()
      })
    }
  }
}

</script>

<style scoped>

</style>
