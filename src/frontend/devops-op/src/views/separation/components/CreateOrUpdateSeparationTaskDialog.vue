<template>
  <el-dialog title="创建降冷任务" :visible.sync="showDialog" :before-close="close">
    <el-form ref="form" :rules="rules" :model="task" status-icon>
      <el-form-item label="任务类型" prop="type">
        <el-select
          v-model="task.type"
          placeholder="请选择"
        >
          <el-option
            v-for="item in options"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
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
      <el-form-item v-if="task.type === 'SEPARATE'" label="降冷时间" prop="separateAt" :rules="[{ required: task.type === 'SEPARATE', message: '降冷时间不能为空'}]">
        <el-date-picker
          v-model="task.separateAt"
          type="datetime"
          placeholder="选择日期时间"
          default-time="12:00:00"
        />
      </el-form-item>
      <el-form-item v-if="task.type === 'RESTORE'" label="恢复是否覆盖" prop="type">
        <el-select
          v-model="task.overwrite"
          placeholder="请选择"
        >
          <el-option
            v-for="item in overwriteOption"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <div v-if="showType !== 'GENERIC'&& showType !== 'DDC'">
        <el-form-item
          v-for="(item,index) in task.content.packages"
          :key="index"
          prop="packages"
        >
          <span>包kEY：</span>
          <el-input
            v-model="item.packageKey"
            placeholder="请输入"
            style="height: 40px; width: 120px"
            min="0"
          />
          <span style="margin-left: 10px">包kEY正则：</span>
          <el-input
            v-model="item.packageKeyRegex"
            placeholder="请输入"
            style="height: 40px; width: 120px"
            min="0"
          />
          <span style="margin-left: 10px">包版本：</span>
          <el-input
            v-model="item.versionList"
            placeholder="请输入数据，按逗号分隔"
            style="height: 40px; width: 200px"
            min="0"
            @change="versionChange(index)"
          />
          <span style="margin-left: 10px">包版本正则：</span>
          <el-input
            v-model="item.versionRegex"
            placeholder="请输入"
            style="height: 40px; width: 120px"
            min="0"
          />
          <span style="margin-left: 10px">排除包版本：</span>
          <el-input
            v-model="item.excludeVersionList"
            placeholder="请输入数据，按逗号分隔"
            style="height: 40px; width: 200px"
            @change="excludeVersionChange(index)"
          />
          <i
            class="el-icon-circle-close"
            style="color: red"
            @click.prevent="removeDomain(item)"
          />
          <i
            v-if="index == task.content.packages.length - 1"
            class="el-icon-circle-plus-outline"
            style="margin: 0px 20px"
            @click.prevent="addDomain()"
          />
        </el-form-item>
      </div>
      <div v-else>
        <el-form-item
          v-for="(item,index) in task.content.paths"
          :key="'path'+ index"
          prop="paths"
        >
          <span>路径：</span>
          <el-input
            v-model="item.path"
            placeholder="请输入"
            style="height: 40px; width: 120px"
            min="0"
          />
          <span style="margin-left: 10px">路径正则：</span>
          <el-input
            v-model="item.pathRegex"
            placeholder="请输入"
            style="height: 40px; width: 120px"
            min="0"
          />
          <span style="margin-left: 10px">排除路径：</span>
          <el-input
            v-model="item.excludePathList"
            placeholder="请输入数据，按逗号分隔"
            style="height: 40px; width: 200px"
            min="0"
            @change="excludePathChange(index)"
          />
          <i
            class="el-icon-circle-close"
            style="color: red"
            @click.prevent="removeNodeDomain(item)"
          />
          <i
            v-if="index === task.content.paths.length - 1"
            class="el-icon-circle-plus-outline"
            style="margin: 0px 20px"
            @click.prevent="addNodeDomain()"
          />
        </el-form-item>
      </div>
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
import { createSeparateTask, updateSeparateTask } from '@/api/separate'
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
      rules: {},
      options: [
        { label: '降冷', value: 'SEPARATE' },
        { label: '恢复', value: 'RESTORE' }
      ],
      overwriteOption: [
        { label: 'true', value: true },
        { label: 'false', value: false }
      ],
      showType: 'GENERIC'
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
      const task = {
        projectId: '',
        repoName: '',
        content: {
          packages: [{
            packageKey: '',
            packageKeyRegex: '',
            versions: [''],
            versionList: '',
            versionRegex: '',
            excludeVersions: [''],
            excludeVersionList: ''
          }],
          paths: [{
            path: '',
            pathRegex: '',
            excludePath: [''],
            excludePathList: ''
          }]
        },
        type: 'SEPARATE',
        separateAt: '',
        overwrite: false
      }
      return task
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
          console.log(this.task)
          const task = this.dataFormat(this.task)
          if (task.content === null) {
            task.content = {
              paths: null,
              packages: null
            }
          }
          task.separateAt = this.task.separateAt !== '' ? this.task.separateAt.toISOString() : null
          console.log(task)
          if (this.createMode) {
            createSeparateTask(task).then(() => {
              this.$message.success('新建配置成功')
              this.close(true)
            })
          } else {
            updateSeparateTask(task).then(() => {
              this.$message.success('更新配置成功')
              this.close(true)
            })
          }
        }
      })
    },
    addDomain() {
      this.task.content.packages.push({
        packageKey: '',
        packageKeyRegex: '',
        versions: [''],
        versionList: '',
        versionRegex: '',
        excludeVersions: [''],
        excludeVersionList: ''
      })
    },
    removeDomain(item) {
      const index = this.task.content.packages.indexOf(item)
      if (index !== -1 && this.task.content.packages.length !== 1) {
        this.task.content.packages.splice(index, 1)
      }
    },
    addNodeDomain() {
      this.task.content.paths.push({
        path: '',
        pathRegex: '',
        excludePath: [''],
        excludePathList: ''
      })
    },
    removeNodeDomain(item) {
      const index = this.task.content.paths.indexOf(item)
      if (index !== -1 && this.task.content.paths.length !== 1) {
        this.task.content.paths.splice(index, 1)
      }
    },
    versionChange(index) {
      this.task.content.packages[index].versions = this.task.content.packages[index].versionList.split(',')
    },
    excludeVersionChange(index) {
      this.task.content.packages[index].excludeVersions = this.task.content.packages[index].excludeVersionList.split(',')
    },
    excludePathChange(index) {
      this.task.content.paths[index].excludePath = this.task.content.paths[index].excludePathList.split(',')
    },
    dataFormat(obj) {
      function recursiveTransform(item) {
        if (Array.isArray(item)) {
          const transformedArray = item.map(recursiveTransform)
          return transformedArray.every(el => el === null) ? null : transformedArray
        } else if (typeof item === 'object' && item !== null) {
          const newObj = {}
          for (const key of Object.keys(item)) {
            newObj[key] = recursiveTransform(item[key])
          }
          return Object.values(newObj).every(value => value === null) ? null : newObj
        } else {
          return item === '' ? null : item
        }
      }
      return recursiveTransform(obj)
    }
  }
}
</script>

<style scoped>
</style>
