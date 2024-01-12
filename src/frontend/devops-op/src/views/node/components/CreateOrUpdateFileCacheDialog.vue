<template>
  <el-dialog :title="createMode ? '创建缓存配置' : '更新缓存配置'" :visible.sync="showDialog" :before-close="close">
    <el-form ref="form" :rules="rules" :model="fileCache" status-icon>
      <el-form-item ref="project-form-item" label="项目ID" prop="projectId" :rules="[{ required: true, message: '应用ID不能为空'}]">
        <el-autocomplete
          v-model="fileCache.projectId"
          class="inline-input"
          :fetch-suggestions="queryProjects"
          placeholder="请输入项目ID"
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
        :rules="[{ required: true, message: '仓库不能为空'}]"
      >
        <el-autocomplete
          v-model="fileCache.repoName"
          class="inline-input"
          :fetch-suggestions="queryRepositories"
          :disabled="!fileCache.projectId"
          placeholder="请输入仓库名"
          @select="selectRepo"
        >
          <template slot-scope="{ item }">
            <div>{{ item.name }}</div>
          </template>
        </el-autocomplete>
      </el-form-item>
      <el-form-item label="大小(GB单位)" prop="size" :rules="[{ required: true, message: '大小不能为空'}]">
        <el-input v-model="fileCache.size" type="number" />
      </el-form-item>
      <el-form-item label="保存时间(天)" prop="days" :rules="[{ required: true, message: '保存天数不能为空'}]">
        <el-input v-model="fileCache.days" type="number" />
      </el-form-item>
      <el-form-item label="路径前缀" prop="paths">
        <el-input
          v-model="fileCache.paths"
          placeholder="请输入数据按逗号分割（如/1,/2,/3）"
          min="0"
          type="text"
          @input="updateInput()"
        />
      </el-form-item>
    </el-form>
    <div slot="footer">
      <el-button @click="close">取 消</el-button>
      <el-button type="primary" @click="handleCreateOrUpdate()">确 定</el-button>
    </div>
  </el-dialog>
</template>

<script>
import { createFileCache, updateFileCache } from '@/api/fileCache'
import _ from 'lodash'
import { searchProjects } from '@/api/project'
import { listRepositories } from '@/api/repository'
import { updateConfig } from '@/api/config'
import { formatFileSize } from '@/utils/file'
export default {
  name: 'CreateOrUpdateFileCacheDialog',
  props: {
    visible: Boolean,
    /**
     * 仅在更新模式时有值
     */
    updatingFileCaches: {
      type: Object,
      default: undefined
    },
    /**
     * 是否为创建模式，true时为创建对象，false时为更新对象
     */
    createMode: Boolean,
    repoConfig: {
      type: Object,
      default: undefined
    }
  },
  data() {
    return {
      repoCache: {},
      showDialog: this.visible,
      fileCache: this.newFileCache(),
      rules: {}
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
    queryProjects(queryStr, cb) {
      searchProjects(queryStr).then(res => {
        this.projects = res.data.records
        cb(this.projects)
      })
    },
    selectProject(project) {
      this.$refs['project-form-item'].resetField()
      this.fileCache.projectId = project.name
    },
    queryRepositories(queryStr, cb) {
      let repositories = this.repoCache[this.fileCache.projectId]
      if (!repositories) {
        listRepositories(this.fileCache.projectId).then(res => {
          repositories = res.data
          this.repoCache[this.fileCache.projectId] = repositories
          cb(this.doFilter(repositories, queryStr))
        })
      } else {
        cb(this.doFilter(repositories, queryStr))
      }
    },
    selectRepo(repo) {
      this.$refs['repo-form-item'].resetField()
      this.fileCache.repoName = repo.name
    },
    doFilter(arr, queryStr) {
      return queryStr ? arr.filter(obj => {
        return obj.name.toLowerCase().indexOf(queryStr.toLowerCase()) !== -1
      }) : arr
    },
    updateInput() {
      this.$forceUpdate()
    },
    close() {
      this.showDialog = false
      this.$refs['form'].resetFields()
      this.$emit('update:visible', false)
    },
    handleCreateOrUpdate() {
      if (this.createMode || (!this.createMode && this.fileCache.id !== undefined)) {
        if (this.checkExist()) {
          this.$message.error('已有此类配置')
          return
        }
      }
      this.$refs['form'].validate((valid) => {
        if (valid) {
          const fileCache = this.fileCache
          fileCache.pathPrefix = fileCache.paths.split(',')
          // 根据是否为创建模式发起不同请求
          let reqPromise
          let msg
          let eventName
          if (this.createMode) {
            reqPromise = createFileCache(fileCache)
            msg = '创建缓存配置成功'
            eventName = 'created'
          } else {
            msg = '更新缓存配置成功'
            eventName = 'updated'
            if (fileCache.id !== undefined) {
              reqPromise = updateFileCache(fileCache)
            } else {
              const target = []
              for (let i = 0; i < this.repoConfig.repoConfig.repos.length; i++) {
                if (!(fileCache.projectId === this.repoConfig.repoConfig.repos[i].projectId &&
                  fileCache.repoName === this.repoConfig.repoConfig.repos[i].repoName)) {
                  target.push(this.repoConfig.repoConfig.repos[i])
                }
              }
              target.push(this.fileCache)
              const values = [{
                'key': 'job.expired-cache-file-cleanup.repoConfig.repos',
                'value': target
              }]
              reqPromise = updateConfig(values, 'job')
            }
          }
          // 发起请求
          reqPromise.then(_ => {
            this.$message.success(msg)
            this.$emit(eventName)
            this.close()
          }).catch(_ => {
            this.$message.error('请求异常')
          })
        } else {
          return false
        }
      })
    },
    resetFileCache() {
      if (this.createMode) {
        this.fileCache = this.newFileCache()
      } else {
        this.fileCache = _.cloneDeep(this.updatingFileCaches)
      }
      this.$nextTick(() => {
        this.$refs['form'].clearValidate()
      })
    },
    newFileCache() {
      const fileCache = {
        projectId: '',
        repoName: '',
        paths: '',
        pathPrefix: [],
        days: '',
        size: ''
      }
      return fileCache
    },
    checkExist() {
      if (this.repoConfig === undefined) {
        return false
      }
      for (let i = 0; i < this.repoConfig.repoConfig.repos.length; i++) {
        const repo = this.repoConfig.repoConfig.repos[i]
        if (repo.projectId === this.fileCache.projectId &&
          repo.repoName === this.fileCache.repoName &&
          repo.days === Number(this.fileCache.days) &&
            formatFileSize(this.repoConfig.repoConfig.size) === Number(this.fileCache.size)
        ) { return true }
      }
      return false
    }
  }
}</script>

<style scoped>

</style>

