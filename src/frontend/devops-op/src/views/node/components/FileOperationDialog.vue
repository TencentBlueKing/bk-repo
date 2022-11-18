<template>

  <el-dialog ref="dialog" :title="mode" :visible.sync="showFileOperationDialog" :before-close="close">
    <el-form ref="form" :model="key" status-icon>
      <el-form-item
        label="源项目名称"
        prop="srcProjectId"
      >
        <el-input v-model="key.srcProjectId" disabled />
      </el-form-item>
      <el-form-item
        label="源仓库名称"
        prop="srcRepoName"
      >
        <el-input v-model="key.srcRepoName" disabled />
      </el-form-item>
      <el-form-item
        label="源完整路径	"
        prop="srcFullPath"
      >
        <el-input v-model="key.srcFullPath" disabled />
      </el-form-item>
      <el-form-item v-if="createMode !== 'rename'" ref="project-form-item" label="目的项目" prop="projectId">
        <el-autocomplete
          v-model="key.destProjectId"
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
        v-if="createMode !== 'rename'"
        ref="repo-form-item"
        label="目的仓库"
        prop="destRepoName"
      >
        <el-autocomplete
          v-model="key.destRepoName"
          class="inline-input"
          :fetch-suggestions="queryRepositories"
          placeholder="请输入仓库名"
          size="mini"
          @select="selectRepo"
        >
          <template slot-scope="{ item }">
            <div>{{ item.name }}</div>
          </template>
        </el-autocomplete>
      </el-form-item>
      <el-form-item
        label="目的路径"
        prop="destFullPath"
        :rules="[{ required: true, message: '路径不能为空'},
                 { validator: validatePath, message: '请输入正确的路径', trigger: 'blur' }]"
      >
        <el-input
          v-model="key.destFullPath"
          style="width: 500px;"
          size="mini"
          placeholder="请输入文件或目录路径"
        />
      </el-form-item>
      <el-form-item
        v-if="createMode !== 'rename'"
        label="同名文件是否覆盖"
      >
        <el-switch v-model="key.overwrite" />
      </el-form-item>
    </el-form>
    <div slot="footer">
      <el-button @click="close">取 消</el-button>
      <el-button type="primary" @click="fileOperation(key)">确 定</el-button>
    </div>
  </el-dialog>
</template>

<script>
import { searchProjects } from '@/api/project'
import { listRepositories } from '@/api/repository'
import { copyNode, renameNode, moveNode } from '@/api/node'

export default {
  name: 'FileOperationDialog',
  props: {
    visible: Boolean,
    /**
     * 仅在更新模式时有值
     */
    updatingKeys: {
      type: Object,
      default: undefined
    },
    createMode: {
      type: String,
      default: undefined
    }
  },
  data() {
    return {
      mode: '',
      showFileOperationDialog: this.visible,
      key: this.newNode(),
      projects: undefined,
      repoCache: {}
    }
  },
  watch: {
    visible: function(newVal) {
      if (newVal) {
        this.showFileOperationDialog = true
        this.key.srcProjectId = this.updatingKeys.projectId
        this.key.srcRepoName = this.updatingKeys.repoName
        this.key.srcFullPath = this.updatingKeys.fullPath
      } else {
        this.close()
      }
    },
    createMode: function(newVal) {
      if (newVal) {
        switch (newVal) {
          case 'rename' :
            this.mode = '重命名节点'
            break
          case 'move':
            this.mode = '移动节点'
            break
          case 'copy':
            this.mode = '拷贝节点'
            break
        }
      }
    }
  },
  methods: {
    close() {
      this.showFileOperationDialog = false
      this.$refs['form'].resetFields()
      this.key.destProjectId = ''
      this.key.destRepoName = ''
      this.key.destFullPath = ''
      this.$emit('update:visible', false)
    },
    validatePath(rule, value, callback) {
      if (value) {
        this.regexValidate(value, /^(\/|(\/[^\/]+)+\/?)$/, callback)
      }
    },
    regexValidate(value, regex, callback) {
      if (regex.test(value)) {
        callback()
      } else {
        callback(new Error('格式错误'))
      }
    },
    queryProjects(queryStr, cb) {
      searchProjects(queryStr).then(res => {
        this.projects = res.data.records
        cb(this.projects)
      })
    },
    selectProject(project) {
      this.$refs['project-form-item'].resetField()
      this.key.destProjectId = project.name
    },
    queryRepositories(queryStr, cb) {
      let repositories = this.repoCache[this.key.destProjectId]
      if (this.key.destProjectId !== null && this.key.destProjectId !== '') {
        if (!repositories) {
          listRepositories(this.key.destProjectId).then(res => {
            repositories = res.data
            this.repoCache[this.key.destProjectId] = repositories
            cb(this.doFilter(repositories, queryStr))
          })
        } else {
          cb(this.doFilter(repositories, queryStr))
        }
      } else {
        cb([])
      }
    },
    selectRepo(repo) {
      this.$refs['repo-form-item'].resetField()
      this.key.destRepoName = repo.name
    },
    doFilter(arr, queryStr) {
      return queryStr ? arr.filter(obj => {
        return obj.name.toLowerCase().indexOf(queryStr.toLowerCase()) !== -1
      }) : arr
    },
    newNode() {
      const node = {
        srcProjectId: '',
        srcRepoName: '',
        srcFullPath: '',
        destProjectId: '',
        destRepoName: '',
        destFullPath: '',
        overwrite: false
      }
      return node
    },
    fileOperation(key) {
      const node = {}
      this.$refs['form'].validate((valid) => {
        if (valid) {
          if (key.destProjectId === '') {
            key.destProjectId = null
          }
          if (key.destRepoName === '') {
            key.destRepoName = null
          }
          switch (this.createMode) {
            case 'rename' :
              node.projectId = key.srcProjectId
              node.repoName = key.srcRepoName
              node.fullPath = key.srcFullPath
              node.newFullPath = key.destFullPath
              renameNode(node).then(() => {
                this.$emit('complete')
                this.close()
              })
              break
            case 'move':
              moveNode(key).then(() => {
                this.$emit('complete')
                this.close()
              })
              break
            case 'copy':
              copyNode(key).then(() => {
                this.$emit('complete')
                this.close()
              })
              break
          }
        } else {
          return false
        }
      })
    }
  }
}

</script>

<style scoped>

</style>
