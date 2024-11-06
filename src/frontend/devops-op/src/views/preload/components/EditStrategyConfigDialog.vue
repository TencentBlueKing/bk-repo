<template>
  <el-dialog :title="createMode?'创建预加载策略配置':'更新预加载策略配置'" :visible.sync="showDialog" :before-close="close">
    <el-form ref="form" :rules="rules" :model="strategy" status-icon>
      <el-form-item ref="project-form-item" label="项目ID" prop="projectId" :rules="[{ required: true, message: '项目ID不能为空'}]">
        <el-autocomplete
          v-model="strategy.projectId"
          class="inline-input"
          :fetch-suggestions="queryProjects"
          placeholder="请输入项目ID"
          size="mini"
          @select="selectProject"
        >
          <template slot-scope="{ item }">
            <div>{{ item.name }}</div>
          </template>
        </el-autocomplete>
      </el-form-item>
      <el-form-item
        ref="repo-form-item"
        label="仓库名称"
        prop="repoName"
        :rules="[{ required: true, message: '仓库名不能为空'}]"
      >
        <el-autocomplete
          v-model="strategy.repoName"
          class="inline-input"
          :fetch-suggestions="queryRepositories"
          :disabled="!strategy.projectId"
          placeholder="请输入仓库名"
          size="mini"
          @select="selectRepo"
        >
          <template slot-scope="{ item }">
            <div>{{ item.name }}</div>
          </template>
        </el-autocomplete>
      </el-form-item>
      <el-form-item label="策略类型" prop="type">
        <el-select
          v-model="strategy.type"
          placeholder="请选择"
        >
          <el-option
            v-for="item in options"
            :key="item.value"
            :label="item.label"
            :value="item.value"
            :disabled="item.disabled"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="文件路径" prop="fullPathRegex" :rules="[{ required: true, message: '文件路径不能为空'}]">
        <el-input v-model="strategy.fullPathRegex" style="height: 40px ; width: 500px;" />
      </el-form-item>
      <el-form-item label="最小大小" prop="minSize">
        <el-input-number v-model="strategy.minSize" controls-position="right" :min="1" />
      </el-form-item>
      <el-form-item label="最近时间内(秒)" prop="recentSeconds">
        <el-input-number v-model="strategy.recentSeconds" controls-position="right" :min="1" />
      </el-form-item>
      <el-form-item v-if="strategy.type !== 'INTELLIGENT'" label="预加载时间(cron表达式)" prop="preloadCron">
        <el-input v-model="strategy.preloadCron" style="height: 40px ; width: 500px;" />
      </el-form-item>
    </el-form>
    <div slot="footer">
      <el-button @click="close">取 消</el-button>
      <el-button type="primary" @click="handleUpdate()">确 定</el-button>
    </div>
  </el-dialog>
</template>
<script>
import _ from 'lodash'
import { createStrategy, updateStrategy } from '@/api/preload'
import { searchProjects } from '@/api/project'
import { listRepositories } from '@/api/repository'

export default {
  name: 'EditStrategyConfigDialog',
  props: {
    visible: Boolean,
    createMode: Boolean,
    /**
     * 仅在更新模式时有值
     */
    updatingStrategyConfig: {
      type: Object,
      default: undefined
    }
  },
  data() {
    return {
      repoCache: {},
      showDialog: this.visible,
      projects: undefined,
      strategy: this.newStrategy(),
      data: '',
      rules: {},
      options: [
        { value: 'CUSTOM',
          label: '自定义' },
        { value: 'CUSTOM_GENERATED',
          label: '系统自定义类型',
          disabled: true
        },
        { value: 'INTELLIGENT',
          label: '智能预加载策略' }
      ]
    }
  },
  watch: {
    visible: function(newVal) {
      if (newVal) {
        this.resetStrategy()
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
      this.strategy.projectId = project.name
    },
    queryRepositories(queryStr, cb) {
      let repositories = this.repoCache[this.strategy.projectId]
      if (!repositories) {
        listRepositories(this.strategy.projectId).then(res => {
          repositories = res.data
          this.repoCache[this.strategy.projectId] = repositories
          cb(this.doFilter(repositories, queryStr))
        })
      } else {
        cb(this.doFilter(repositories, queryStr))
      }
    },
    selectRepo(repo) {
      this.$refs['repo-form-item'].resetField()
      this.strategy.repoName = repo.name
    },
    doFilter(arr, queryStr) {
      return queryStr ? arr.filter(obj => {
        return obj.name.toLowerCase().indexOf(queryStr.toLowerCase()) !== -1
      }) : arr
    },
    resetStrategy() {
      if (this.createMode) {
        this.strategy = this.newStrategy()
      } else {
        this.strategy = _.cloneDeep(this.updatingStrategyConfig)
      }
      this.$nextTick(() => {
        this.$refs['form'].clearValidate()
      })
    },
    newStrategy() {
      const strategy = {
        projectId: '',
        repoName: '',
        fullPathRegex: '',
        minSize: '',
        recentSeconds: '',
        preloadCron: '',
        type: 'CUSTOM'
      }
      return strategy
    },
    close(changed) {
      this.showDialog = false
      this.$refs['form'].resetFields()
      if (changed === true) {
        this.$emit('updated')
      }
      this.$emit('update:visible', false)
    },
    handleUpdate() {
      this.$refs['form'].validate((valid) => {
        if (valid) {
          if (this.strategy.preloadCron === '') {
            this.strategy.preloadCron = null
          }
          if (this.createMode) {
            createStrategy(this.strategy).then(() => {
              this.$message.success('新建配置成功')
              this.close(true)
            })
          } else {
            updateStrategy(this.strategy).then(() => {
              this.$message.success('更新配置成功')
              this.close(true)
            })
          }
        }
      })
    }
  }
}
</script>

<style scoped>

</style>
