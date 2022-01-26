<template>
  <div class="app-container node-container">
    <el-form ref="form" :rules="rules" :inline="true" :model="nodeQuery">
      <el-form-item label="查询模式">
        <el-select
          v-model="nodeQuery.useSha256"
          size="mini"
          style="width: 110px"
          placeholder="请选择查询模式"
          @change="queryModeChanged"
        >
          <el-option :key="true" :value="true" label="SHA256" />
          <el-option :key="false" :value="false" label="文件路径" />
        </el-select>
        <el-divider direction="vertical" />
      </el-form-item>
      <el-form-item v-if="!nodeQuery.useSha256" ref="project-form-item" label="项目ID" prop="projectId">
        <el-autocomplete
          v-model="nodeQuery.projectId"
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
        v-if="!nodeQuery.useSha256"
        ref="repo-form-item"
        style="margin-left: 15px"
        label="仓库"
        prop="repoName"
      >
        <el-autocomplete
          v-model="nodeQuery.repoName"
          :disabled="!nodeQuery.projectId"
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
      <el-form-item v-if="!nodeQuery.useSha256" style="margin-left: 15px" label="路径" prop="path">
        <el-input
          v-model="nodeQuery.path"
          :disabled="!nodeQuery.repoName"
          style="width: 500px;"
          size="mini"
          placeholder="请输入文件或目录路径"
          @keyup.enter.native="queryNodes(nodeQuery, true)"
        />
      </el-form-item>
      <el-form-item v-if="nodeQuery.useSha256" label="SHA256" prop="sha256">
        <el-input
          v-model="nodeQuery.sha256"
          style="width: 500px"
          size="mini"
          placeholder="请输入所查文件的SHA256"
          @keyup.enter.native="queryNodes(nodeQuery, true)"
        />
      </el-form-item>
      <el-form-item>
        <el-button
          size="mini"
          :disabled="!nodeQuery.useSha256 && !nodeQuery.path || nodeQuery.useSha256 && !nodeQuery.sha256"
          type="primary"
          @click="queryNodes(nodeQuery, true)"
        >查询</el-button>
      </el-form-item>
    </el-form>
    <el-table v-loading="loading" :data="nodes" style="width: 100%" :row-class-name="tableRowClassName">
      <el-table-column prop="name" label="文件名" width="200px">
        <template slot-scope="scope">
          <svg-icon :icon-class="fileIcon(scope.row)" style="margin-right: 6px" /><span>{{ scope.row.name }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="size" label="大小" width="120px">
        <template slot-scope="scope">
          {{ scope.row.folder ? '--' : fileSize(scope.row.size) }}
        </template>
      </el-table-column>
      <el-table-column prop="lastModifiedBy" label="修改人" width="120px" />
      <el-table-column prop="lastModifiedDate" label="修改时间" width="200px">
        <template slot-scope="scope">{{ formatDate(scope.row.lastModifiedDate) }}</template>
      </el-table-column>
      <el-table-column prop="deleted" label="删除时间" width="200px">
        <template slot-scope="scope">{{ formatDate(scope.row.deleted) }}</template>
      </el-table-column>
      <el-table-column label="操作">
        <template slot-scope="scope">
          <el-button v-if="scope.row.folder" size="mini" type="primary" @click="showNodeDetail(scope.row)">
            详情
          </el-button>
          <el-dropdown v-else size="mini" split-button type="primary" @click="showNodeDetail(scope.row)">
            详情
            <el-dropdown-menu slot="dropdown">
              <el-dropdown-item @click.native="showFileReferenceDetail(scope.row)">引用详情</el-dropdown-item>
              <el-dropdown-item @click.native="showNodesOfSha256(scope.row.sha256)">同引用文件</el-dropdown-item>
            </el-dropdown-menu>
          </el-dropdown>
          <el-button
            v-if="scope.row.deleted"
            style="margin-left: 10px"
            size="mini"
            type="primary"
            @click="showNodeRestore(scope.$index, scope.row)"
          >恢复</el-button>
          <el-button
            v-if="!scope.row.deleted"
            style="margin-left: 10px"
            size="mini"
            type="danger"
            @click="showNodeDelete(scope.$index, scope.row)"
          >删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      style="margin-top: 15px"
      background
      layout="prev, pager, next"
      :current-page.sync="nodeQuery.pageNumber"
      :page-size="nodeQuery.pageSize"
      :hide-on-single-page="true"
      :total="total"
      @current-change="queryNodes(nodeQuery)"
    />
    <file-reference-dialog :visible.sync="showFileReferenceDialog" :node="nodeOfFileReference" />
    <file-detail-dialog :visible.sync="showNodeDetailDialog" :node="nodeOfDetailDialog" />
    <file-restore-dialog :visible.sync="showNodeRestoreDialog" :node="nodeToRestore" @restore-success="onRestoreSuccess" />
    <file-delete-dialog :visible.sync="showNodeDeleteDialog" :node="nodeToDelete" @delete-success="onDeleteSuccess" />
  </div>
</template>
<script>
import { searchNodes, pageNodesBySha256 } from '@/api/node'
import { convertFileSize, formatDate } from '@/utils/file'
import FileReferenceDialog from '@/views/node/components/FileReferenceDialog'
import FileDetailDialog from '@/views/node/components/FileDetailDialog'
import FileRestoreDialog from '@/views/node/components/FileRestoreDialog'
import FileDeleteDialog from '@/views/node/components/FileDeleteDialog'
import { listProjects } from '@/api/project'
import { listRepositories } from '@/api/repository'

export default {
  name: 'Node',
  components: { FileDeleteDialog, FileRestoreDialog, FileDetailDialog, FileReferenceDialog },
  data() {
    return {
      rules: {
        sha256: [{ validator: this.validateSha256, trigger: 'blur' }],
        projectId: [{ validator: this.validateName, trigger: 'blur' }],
        repoName: [{ validator: this.validateName, trigger: 'blur' }],
        path: [{ validator: this.validatePath, trigger: 'blur' }]
      },
      loading: false,
      projects: undefined,
      repoCache: {},
      nodeQuery: {
        useSha256: false,
        projectId: '',
        repoName: '',
        path: '',
        sha256: '',
        pageNumber: 1,
        pageSize: 20
      },
      nodes: [],
      total: 0,
      showFileReferenceDialog: false,
      nodeOfFileReference: {},
      showNodeDetailDialog: false,
      nodeOfDetailDialog: {},
      showNodeRestoreDialog: false,
      nodeToRestore: {},
      showNodeDeleteDialog: false,
      nodeToDelete: {},
      indexOfNodeToDelete: -1
    }
  },
  methods: {
    validateSha256(rule, value, callback) {
      if (this.nodeQuery.useSha256) {
        this.regexValidate(value, /^\w{64}$/, callback)
      }
      callback()
    },
    validateName(rule, value, callback) {
      if (!this.nodeQuery.useSha256) {
        this.regexValidate(value, /^[\w-]+$/, callback)
      }
      callback()
    },
    validatePath(rule, value, callback) {
      if (!this.nodes.useSha256) {
        this.regexValidate(value, /^(\/|(\/[\w-~#\\.]+)+\/?)$/, callback)
      }
      callback()
    },
    regexValidate(value, regex, callback) {
      if (regex.test(value)) {
        callback()
      } else {
        callback(new Error('格式错误'))
      }
    },
    fileSize(size) {
      return convertFileSize(size)
    },
    formatDate(date) {
      return formatDate(date)
    },
    fileIcon(node) {
      if (node.folder) {
        return 'folder-yellow'
      } else {
        return 'file-blue'
      }
    },
    queryModeChanged() {
      this.$refs['form'].clearValidate()
    },
    queryProjects(queryStr, cb) {
      if (!this.projects) {
        listProjects().then(res => {
          this.projects = res.data
          cb(this.doFilter(this.projects, queryStr))
        })
      } else {
        cb(this.doFilter(this.projects, queryStr))
      }
    },
    selectProject(project) {
      this.$refs['project-form-item'].resetField()
      this.nodeQuery.projectId = project.name
    },
    queryRepositories(queryStr, cb) {
      let repositories = this.repoCache[this.nodeQuery.projectId]
      if (!repositories) {
        listRepositories(this.nodeQuery.projectId).then(res => {
          repositories = res.data
          this.repoCache[this.nodeQuery.projectId] = repositories
          cb(this.doFilter(repositories, queryStr))
        })
      } else {
        cb(this.doFilter(repositories, queryStr))
      }
    },
    selectRepo(repo) {
      this.$refs['repo-form-item'].resetField()
      this.nodeQuery.repoName = repo.name
    },
    doFilter(arr, queryStr) {
      return queryStr ? arr.filter(obj => {
        return obj.name.toLowerCase().indexOf(queryStr.toLowerCase()) !== -1
      }) : arr
    },
    queryNodes(nodeQuery, resetPage = false) {
      this.$refs['form'].validate((valid) => {
        if (valid) {
          this.doQueryNodes(nodeQuery, resetPage)
        } else {
          return false
        }
      })
    },
    doQueryNodes(nodeQuery, resetPage = false) {
      this.loading = true
      let promise = null
      if (nodeQuery.useSha256) {
        promise = pageNodesBySha256(nodeQuery.sha256, nodeQuery.pageNumber, nodeQuery.pageSize)
      } else {
        promise = searchNodes(nodeQuery.projectId, nodeQuery.repoName, nodeQuery.path, nodeQuery.pageNumber, nodeQuery.pageSize)
      }
      if (resetPage) {
        nodeQuery.pageNumber = 1
      }
      promise.then(res => {
        this.nodes = res.data.records
        this.total = res.data.totalRecords
      }).catch(_ => {
        this.nodes = []
        this.total = 0
      }).finally(() => {
        this.loading = false
      })
    },
    tableRowClassName({ row }) {
      if (row.deleted) {
        return 'deleted-row'
      }
      return ''
    },
    showNodeDetail(node) {
      this.nodeOfDetailDialog = node
      this.showNodeDetailDialog = true
    },
    showFileReferenceDetail(node) {
      this.nodeOfFileReference = node
      this.showFileReferenceDialog = true
    },
    showNodesOfSha256(sha256) {
      this.nodeQuery.useSha256 = true
      this.nodeQuery.sha256 = sha256
      this.queryNodes(this.nodeQuery)
    },
    showNodeRestore(index, node) {
      this.nodeToRestore = node
      this.showNodeRestoreDialog = true
    },
    onRestoreSuccess(node) {
      node.deleted = undefined
    },
    showNodeDelete(index, node) {
      this.nodeToDelete = node
      this.showNodeDeleteDialog = true
      this.indexOfNodeToDelete = index
    },
    onDeleteSuccess() {
      const projectId = this.nodeToDelete.projectId
      const repoName = this.nodeToDelete.repoName
      const fullPath = this.nodeToDelete.fullPath
      searchNodes(projectId, repoName, fullPath, 1, 1).then(res => {
        this.nodes.splice(this.indexOfNodeToDelete, 1, res.data.records[0])
      })
    }
  }
}
</script>

<style scoped>

</style>

<style>
.node-container .el-table .deleted-row {
  background: #e9e9e9;
}
</style>
