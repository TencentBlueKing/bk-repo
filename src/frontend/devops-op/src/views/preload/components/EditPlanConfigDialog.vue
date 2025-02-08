<template>
  <el-dialog title="创建预加载计划配置" :visible.sync="showDialog" :before-close="close">
    <el-form ref="form" :rules="rules" :model="plan" status-icon>
      <el-form-item ref="project-form-item" label="项目ID" prop="projectId" :rules="[{ required: true, message: '项目ID不能为空'}]">
        <el-autocomplete
          v-model="plan.projectId"
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
          v-model="plan.repoName"
          class="inline-input"
          :fetch-suggestions="queryRepositories"
          :disabled="!plan.projectId"
          placeholder="请输入仓库名"
          size="mini"
          @select="selectRepo"
        >
          <template slot-scope="{ item }">
            <div>{{ item.name }}</div>
          </template>
        </el-autocomplete>
      </el-form-item>
      <el-form-item label="文件路径" prop="fullPath" :rules="[{ required: true, message: '文件路径不能为空'}]">
        <el-input v-model="plan.fullPath" style="height: 40px ; width: 500px;" />
      </el-form-item>
      <el-form-item label="预加载计划执行毫秒时间戳" prop="executeTime">
        <el-date-picker
          v-model="plan.executeTime"
          type="datetime"
          placeholder="选择日期时间"
          default-time="12:00:00"
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
import { createPlan } from '@/api/preload'
import { searchProjects } from '@/api/project'
import { listRepositories } from '@/api/repository'

export default {
  name: 'EditPlanConfigDialog',
  props: {
    visible: Boolean,
    createMode: Boolean,
    /**
     * 仅在更新模式时有值
     */
    updatingPlanConfig: {
      type: Object,
      default: undefined
    }
  },
  data() {
    return {
      repoCache: {},
      projects: undefined,
      showDialog: this.visible,
      plan: this.newPlan(),
      rules: {}
    }
  },
  watch: {
    visible: function(newVal) {
      if (newVal) {
        this.resetPlan()
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
      this.plan.projectId = project.name
    },
    queryRepositories(queryStr, cb) {
      let repositories = this.repoCache[this.plan.projectId]
      if (!repositories) {
        listRepositories(this.plan.projectId).then(res => {
          repositories = res.data
          this.repoCache[this.plan.projectId] = repositories
          cb(this.doFilter(repositories, queryStr))
        })
      } else {
        cb(this.doFilter(repositories, queryStr))
      }
    },
    selectRepo(repo) {
      this.$refs['repo-form-item'].resetField()
      this.plan.repoName = repo.name
    },
    doFilter(arr, queryStr) {
      return queryStr ? arr.filter(obj => {
        return obj.name.toLowerCase().indexOf(queryStr.toLowerCase()) !== -1
      }) : arr
    },
    resetPlan() {
      this.plan = this.newPlan()
      this.$nextTick(() => {
        this.$refs['form'].clearValidate()
      })
    },
    newPlan() {
      const plan = {
        projectId: '',
        repoName: '',
        executeTime: '',
        fullPath: ''
      }
      return plan
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
          this.plan.executeTime = this.plan.executeTime.getTime()
          createPlan(this.plan).then(() => {
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
