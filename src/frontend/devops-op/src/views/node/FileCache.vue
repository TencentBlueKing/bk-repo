<template>
  <div class="app-container node-container">
    <el-form ref="form" :inline="true" :model="clientQuery">
      <el-form-item ref="project-form-item" label="项目ID" prop="projectId">
        <el-autocomplete
          v-model="clientQuery.projectId"
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
        style="margin-left: 15px"
        label="仓库"
        prop="repoName"
      >
        <el-autocomplete
          v-model="clientQuery.repoName"
          class="inline-input"
          :fetch-suggestions="queryRepositories"
          :disabled="!clientQuery.projectId"
          placeholder="请输入仓库名"
          size="mini"
          @select="selectRepo"
        >
          <template slot-scope="{ item }">
            <div>{{ item.name }}</div>
          </template>
        </el-autocomplete>
      </el-form-item>
      <el-form-item>
        <el-button
          size="mini"
          type="primary"
          @click="queryFileCache()"
        >查询</el-button>
      </el-form-item>
    </el-form>
    <el-table v-loading="loading" :data="fileCaches" style="width: 100%">
      <el-table-column prop="projectId" label="项目Id" />
      <el-table-column prop="repoName" label="仓库名称" />
      <el-table-column prop="paths" label="路径前缀匹配" width="780" />
      <el-table-column prop="size" label="大小(MB)" />
      <el-table-column prop="days" label="保存时间（天）" />
      <el-table-column align="right">
        <template slot="header">
          <el-button type="primary" @click="showCreateOrUpdateDialog(true)">创建</el-button>
        </template>
        <template slot-scope="scope">
          <el-button
            v-if="consulType || (!consulType && scope.row.id)"
            size="mini"
            type="primary"
            @click="showCreateOrUpdateDialog(false, scope.$index, scope.row)"
          >
            编辑
          </el-button>
          <el-button
            v-if="consulType || (!consulType && scope.row.id)"
            size="mini"
            type="danger"
            @click="handleDelete(scope.$index, scope.row)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <create-or-update-file-cache-dialog
      :create-mode="createMode"
      :visible.sync="showDialog"
      :updating-file-caches="updatingFileCache"
      :repo-config="fileCacheConf"
      @created="getDates"
      @updated="getDates"
    />
  </div>
</template>
<script>

import { deleteFileCache, queryFileCache, getNodeConfig } from '@/api/fileCache'
import { getConfig, updateConfig } from '@/api/config'
import CreateOrUpdateFileCacheDialog from '@/views/node/components/CreateOrUpdateFileCacheDialog'
import { formatFileSize } from '@/utils/file'
import { searchProjects } from '@/api/project'
import { listRepositories } from '@/api/repository'

export default {
  name: 'FileCache',
  components: { CreateOrUpdateFileCacheDialog },
  data() {
    return {
      loading: false,
      showDialog: false,
      createMode: true,
      updatingIndex: undefined,
      updatingFileCache: undefined,
      fileCaches: [],
      originFileCache: [],
      keyName: 'job.expired-cache-file-cleanup',
      defaultConf: {
        projectId: '',
        repoName: '',
        pathPrefix: [],
        days: 30
      },
      fileCacheConf: undefined,
      consulType: true,
      repoCache: {},
      clientQuery: {
        projectId: '',
        repoName: ''
      }
    }
  },
  async created() {
    await this.getDates()
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
      this.clientQuery.projectId = project.name
    },
    queryRepositories(queryStr, cb) {
      let repositories = this.repoCache[this.clientQuery.projectId]
      if (!repositories) {
        listRepositories(this.clientQuery.projectId).then(res => {
          repositories = res.data
          this.repoCache[this.clientQuery.projectId] = repositories
          cb(this.doFilter(repositories, queryStr))
        })
      } else {
        cb(this.doFilter(repositories, queryStr))
      }
    },
    selectRepo(repo) {
      this.$refs['repo-form-item'].resetField()
      this.clientQuery.repoName = repo.name
    },
    doFilter(arr, queryStr) {
      return queryStr ? arr.filter(obj => {
        return obj.name.toLowerCase().indexOf(queryStr.toLowerCase()) !== -1
      }) : arr
    },
    showCreateOrUpdateDialog(create, index, fileCache) {
      this.showDialog = true
      this.createMode = create
      this.updatingIndex = index
      this.updatingFileCache = fileCache
    },
    handleDelete(index, fileCache) {
      this.$confirm('是否确定删除此缓存配置吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        if (fileCache.id) {
          deleteFileCache(fileCache.id).then(_ => {
            this.fileCaches.splice(index, 1)
            this.$message.success('删除成功')
          })
        } else {
          const target = []
          for (let i = 0; i < this.fileCacheConf.repoConfig.repos.length; i++) {
            if (!(fileCache.projectId === this.fileCacheConf.repoConfig.repos[i].projectId &&
              fileCache.repoName === this.fileCacheConf.repoConfig.repos[i].repoName)) {
              target.push(this.fileCacheConf.repoConfig.repos[i])
            }
          }
          this.fileCacheConf.repoConfig.repos = target
          const values = [{
            'key': this.keyName + '.repoConfig.repos',
            'value': this.fileCacheConf.repoConfig.repos
          }]
          updateConfig(values, 'job').then(_ => {
            this.fileCaches.splice(index, 1)
            this.$message.success('删除成功')
          })
        }
      }).catch(() => {
        this.$message({
          type: 'info',
          message: '已取消'
        })
      })
    },
    async getDates() {
      this.fileCaches = []
      await queryFileCache().then(res => {
        if (res.data && res.data.length !== 0) {
          for (let i = 0; i < res.data.length; i++) {
            for (let j = 0; j < res.data[i].pathPrefix.length; j++) {
              if (j === 0) {
                res.data[i].paths = res.data[i].pathPrefix[0]
              } else {
                res.data[i].paths = res.data[i].paths + ',' + res.data[i].pathPrefix[j]
              }
            }
          }
          this.fileCaches.push.apply(this.fileCaches, res.data)
          this.originFileCache.push.apply(this.originFileCache, res.data)
        }
      })
      await getConfig(this.keyName, 'job').then(res => {
        this.handleDateFromConfig(res)
      }).catch(_ => {
        this.consulType = false
        getNodeConfig().then(res => {
          this.handleDateFromConfig(res)
        })
      })
    },
    handleDateFromConfig(res) {
      if (res.data) {
        const obj = JSON.parse(res.data)
        this.fileCacheConf = obj
        if (obj.repoConfig) {
          if (obj.repoConfig.repos) {
            let size = ''
            if (obj.repoConfig.size) {
              size = formatFileSize(obj.repoConfig.size, 'MB')
            }
            obj.repoConfig.repos.forEach(repo => {
              if (repo.pathPrefix.length === 0) {
                repo.paths = []
              } else {
                for (let j = 0; j < repo.pathPrefix.length; j++) {
                  if (j === 0) {
                    repo.paths = repo.pathPrefix[0]
                  } else {
                    repo.paths = repo.paths + ',' + repo.pathPrefix[j]
                  }
                }
              }
            })
            const temp = obj.repoConfig.repos.map(v => ({ ...v, size: size }))
            this.fileCaches.push.apply(this.fileCaches, temp)
            this.originFileCache.push.apply(this.fileCaches, temp)
          }
        }
      }
    },
    queryFileCache() {
      this.fileCaches = this.originFileCache
      if (this.clientQuery.projectId === '' && this.clientQuery.repoName === '') {
        this.fileCaches = this.originFileCache
      } else {
        this.fileCaches = this.fileCaches.filter(fileCache => {
          const projectFilter = fileCache.projectId.toString().includes(this.clientQuery.projectId)
          const repoFilter = fileCache.repoName.toString().includes(this.clientQuery.repoName)
          return projectFilter && repoFilter
        })
      }
    }
  }
}
</script>

<style scoped>

</style>

