<template>
  <el-dialog title="更新REPO配置" :visible.sync="showDialog" :before-close="close">
    <el-form ref="form" :rules="rules" :model="repoConfig" status-icon>
      <el-form-item label="项目名" prop="projectId">
        <el-input v-model="repoConfig.projectId" disabled style="height: 40px ; width: 500px;" />
      </el-form-item>
      <el-form-item label="仓库名" prop="repoName">
        <el-input v-model="repoConfig.name" disabled style="height: 40px ; width: 500px;" />
      </el-form-item>
      <el-form-item label="仓库配额(单位B)" prop="size">
        <el-input-number v-model="repoConfig.quota" controls-position="right" :min="0" />
        <el-button type="primary" style="margin-left: 10px" @click="handleUpdateQuota()">更 改</el-button>
      </el-form-item>
      <div v-if="repoConfig.type === 'GENERIC' || repoConfig.type === 'HELM' || repoConfig.type === 'DOCKER'">
        <el-form-item label="自动清理" prop="enable">
          <el-switch v-model="repoConfig.configuration.settings.cleanupStrategy.enable" @change="changeEnable" />
        </el-form-item>
        <el-form-item v-if="repoConfig.configuration.settings.cleanupStrategy.enable" label="清理策略" prop="cleanupType">
          <el-select
            v-if="repoConfig.type === 'HELM' || repoConfig.type === 'DOCKER'"
            v-model="repoConfig.configuration.settings.cleanupStrategy.cleanupType"
            placeholder="请选择"
            @change="changeType"
          >
            <el-option
              v-for="item in options"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
          <el-select v-else v-model="repoConfig.configuration.settings.cleanupStrategy.cleanupType" placeholder="请选择" @change="changeType">
            <el-option
              v-for="item in genericOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          v-if="repoConfig.configuration.settings.cleanupStrategy.enable && repoConfig.configuration.settings.cleanupStrategy.cleanupType === 'retentionDays'"
          label="保存时间(天)"
          prop="cleanupValue"
        >
          <el-input-number v-model="cleanValue" controls-position="right" :min="0" @change="changeEnable" />
        </el-form-item>
        <el-form-item
          v-if="repoConfig.configuration.settings.cleanupStrategy.enable && repoConfig.configuration.settings.cleanupStrategy.cleanupType === 'retentionNums'"
          label="保存数目"
          prop="cleanupValue"
        >
          <el-input-number v-model="cleanValue" controls-position="right" :min="0" @change="changeEnable" />
        </el-form-item>
        <el-form-item
          v-if="repoConfig.configuration.settings.cleanupStrategy.enable && repoConfig.configuration.settings.cleanupStrategy.cleanupType === 'retentionDate'"
          label="保存时间"
          prop="cleanupValue"
        >
          <el-date-picker
            v-model="data"
            type="datetime"
            placeholder="选择日期时间"
          />
        </el-form-item>
        <div v-if="repoConfig.configuration.settings.cleanupStrategy.enable">
          <el-form-item
            v-for="(item,index) in repoConfig.configuration.settings.cleanupStrategy.cleanTargets"
            :key="index"
            label="目标"
            prop="target"
          >
            <el-input
              v-model="repoConfig.configuration.settings.cleanupStrategy.cleanTargets[index]"
              style="height: 40px ; width: 500px;"
              placeholder="请输入数据"
              min="0"
              @input="changeEnable"
            />
            <i
              class="el-icon-circle-close"
              style="color: red"
              @click.prevent="removeDomain(item)"
            />
            <i
              v-if="index === repoConfig.configuration.settings.cleanupStrategy.cleanTargets.length - 1"
              class="el-icon-circle-plus-outline"
              style="margin: 0px 20px"
              @click.prevent="addDomain()"
            />
          </el-form-item>
        </div>
      </div>
    </el-form>
    <div v-if="repoConfig.type === 'GENERIC' || repoConfig.type === 'HELM' || repoConfig.type === 'DOCKER'" slot="footer">
      <el-button @click="close">取 消</el-button>
      <el-button type="primary" @click="handleUpdate()">确 定</el-button>
    </div>
  </el-dialog>
</template>
<script>
import _ from 'lodash'
import { updateRepoCleanConfig, updateRepoQuota } from '@/api/repository'

export default {
  name: 'EditRepoConfigDialog',
  props: {
    visible: Boolean,
    /**
     * 仅在更新模式时有值
     */
    updatingRepoConfig: {
      type: Object,
      default: undefined
    }
  },
  data() {
    return {
      repoCache: {},
      showDialog: false,
      cleanValue: '',
      repoConfig: {
        projectId: '',
        name: '',
        quota: '',
        type: 'GENERIC',
        configuration: {
          settings: {
            cleanupStrategy: {
              enable: false,
              cleanupType: '',
              cleanTargets: [],
              cleanupValue: ''
            }
          }
        }
      },
      data: '',
      rules: {},
      options: [
        { value: 'retentionNums',
          label: '保存数目' },
        { value: 'retentionDate',
          label: '保存日期' },
        { value: 'retentionDays',
          label: '保存天数' }
      ],
      genericOptions: [
        { value: 'retentionDate',
          label: '保存日期' },
        { value: 'retentionDays',
          label: '保存天数' }
      ]
    }
  },
  watch: {
    visible: function(newVal) {
      if (newVal) {
        this.resetFileCache()
        this.showDialog = true
      } else {
        this.close()
      }
    }
  },
  methods: {
    resetFileCache() {
      this.repoConfig = _.cloneDeep(this.updatingRepoConfig)
      if (!this.repoConfig.configuration.settings.cleanupStrategy || !this.repoConfig.configuration.settings.cleanupStrategy.enable) {
        this.repoConfig.configuration.settings.cleanupStrategy = {
          enable: false,
          cleanupType: 'retentionDate',
          cleanTargets: [''],
          cleanupValue: new Date()
        }
      } else {
        this.cleanValue = this.repoConfig.configuration.settings.cleanupStrategy.cleanupValue
      }
      if (this.repoConfig.configuration.settings.cleanupStrategy.cleanTargets.length === 0) {
        this.repoConfig.configuration.settings.cleanupStrategy.cleanTargets = ['']
      }
      this.data = this.repoConfig.configuration.settings.cleanupStrategy.cleanupValue
      this.$nextTick(() => {
        this.$refs['form'].clearValidate()
      })
    },
    removeDomain(item) {
      this.$forceUpdate()
      const index = this.repoConfig.configuration.settings.cleanupStrategy.cleanTargets.indexOf(item)
      if (index !== -1 && this.repoConfig.configuration.settings.cleanupStrategy.cleanTargets.length !== 1) {
        this.repoConfig.configuration.settings.cleanupStrategy.cleanTargets.splice(index, 1)
      }
    },
    addDomain() {
      this.$forceUpdate()
      this.repoConfig.configuration.settings.cleanupStrategy.cleanTargets.push('')
    },
    close() {
      this.showDialog = false
      this.$refs['form'].resetFields()
      this.$emit('updated')
      this.$emit('update:visible', false)
    },
    handleUpdate() {
      if (this.repoConfig.configuration.settings.cleanupStrategy.cleanTargets.length === 1 && this.repoConfig.configuration.settings.cleanupStrategy.cleanTargets[0].trim() === '') {
        this.repoConfig.configuration.settings.cleanupStrategy.cleanTargets = []
      }
      if (this.repoConfig.configuration.settings.cleanupStrategy.cleanTargets.type === 'retentionDate') {
        this.repoConfig.configuration.settings.cleanupStrategy.cleanupValue = this.data
      }
      if (!this.repoConfig.configuration.settings.cleanupStrategy.enable) {
        this.repoConfig.configuration.settings.cleanupStrategy = {
          enable: false
        }
      }
      updateRepoCleanConfig(this.repoConfig).then(() => {
        this.$message.success('更新配置成功')
        this.close()
      })
    },
    handleUpdateQuota() {
      this.$confirm('是否确定更改此仓库配额吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        updateRepoQuota(this.repoConfig)
      })
    },
    changeEnable() {
      this.repoConfig.configuration.settings.cleanupStrategy.cleanupValue = this.cleanValue.toString()
      this.$forceUpdate()
    },
    changeType() {
      this.$forceUpdate()
      this.repoConfig.configuration.settings.cleanupStrategy.cleanupValue = ''
    }
  }
}
</script>

<style scoped>

</style>
