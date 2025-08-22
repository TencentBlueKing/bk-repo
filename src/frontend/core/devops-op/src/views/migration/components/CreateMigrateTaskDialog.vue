<template>
  <el-dialog title="创建迁移任务" :visible.sync="showDialog" :before-close="close">
    <el-form ref="form" :rules="rules" :model="task" status-icon label-position="left" label-width="100px">
      <el-form-item ref="project-form-item" label="项目ID" prop="projectId" :rules="[{ required: true, message: '项目ID不能为空'}]">
        <el-autocomplete
          v-model="task.projectId"
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
          v-model="task.repoName"
          class="inline-input"
          :fetch-suggestions="queryRepositories"
          :disabled="!task.projectId"
          placeholder="请输入仓库名"
          size="mini"
          @select="selectRepo"
        >
          <template slot-scope="{ item }">
            <div>{{ item.name }}</div>
          </template>
        </el-autocomplete>
      </el-form-item>
      <el-form-item label="目标存储KEY" prop="dstCredentialsKey">
        <el-input
          v-model="task.dstCredentialsKey"
          placeholder="请输入"
          style="width: 250px"
        />
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
import { searchProjects } from '@/api/project'
import { listRepositories } from '@/api/repository'
import { createMigrateTask } from '@/api/migrate'

export default {
  name: 'EditSeparatorTaskConfigDialog',
  props: {
    visible: Boolean,
    createMode: Boolean,
    /**
     * 仅在更新模式时有值
     */
    updatingTaskConfig: {
      type: Object,
      default: undefined
    }
  },
  data() {
    return {
      repoCache: {},
      showDialog: this.visible,
      projects: undefined,
      task: this.newTask(),
      rules: {}
    }
  },
  watch: {
    visible: function(newVal) {
      if (newVal) {
        this.resetTask()
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
      this.task.projectId = project.name
    },
    queryRepositories(queryStr, cb) {
      let repositories = this.repoCache[this.task.projectId]
      if (!repositories) {
        listRepositories(this.task.projectId).then(res => {
          repositories = res.data
          this.repoCache[this.task.projectId] = repositories
          cb(this.doFilter(repositories, queryStr))
        })
      } else {
        cb(this.doFilter(repositories, queryStr))
      }
    },
    selectRepo(repo) {
      this.$refs['repo-form-item'].resetField()
      this.showType = repo.type
      this.task.repoName = repo.name
    },
    doFilter(arr, queryStr) {
      return queryStr ? arr.filter(obj => {
        return obj.name.toLowerCase().indexOf(queryStr.toLowerCase()) !== -1
      }) : arr
    },
    resetTask() {
      if (this.createMode) {
        this.task = this.newTask()
      } else {
        this.task = _.cloneDeep(this.updatingTaskConfig)
      }
      this.$nextTick(() => {
        this.$refs['form'].clearValidate()
      })
    },
    newTask() {
      return {
        projectId: '',
        repoName: '',
        dstCredentialsKey: ''
      }
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
          const task = this.task
          task.dstCredentialsKey = this.task.dstCredentialsKey !== '' ? this.task.dstCredentialsKey : null
          createMigrateTask(this.task).then(() => {
            this.$message.success('新建配置成功')
            this.close(true)
          })
        }
      })
    }
  }
}
</script>

<style scoped>
</style>
