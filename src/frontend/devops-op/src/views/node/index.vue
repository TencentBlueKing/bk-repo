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
          @keyup.enter.native="changeRouteQueryParams(true)"
        />
      </el-form-item>
      <el-form-item v-if="nodeQuery.useSha256" label="SHA256" prop="sha256">
        <el-input
          v-model="nodeQuery.sha256"
          style="width: 500px"
          size="mini"
          placeholder="请输入所查文件的SHA256"
          @keyup.enter.native="changeRouteQueryParams(true)"
        />
      </el-form-item>
      <el-form-item>
        <el-button
          size="mini"
          :disabled="!nodeQuery.useSha256 && !nodeQuery.path || nodeQuery.useSha256 && !nodeQuery.sha256"
          type="primary"
          @click="changeRouteQueryParams(true)"
        >查询</el-button>
      </el-form-item>
    </el-form>
    <el-table v-loading="loading" :data="nodes" style="width: 100%" :row-class-name="tableRowClassName">
      <el-table-column prop="name" label="文件名" width="430px">
        <template slot-scope="scope">
          <svg-icon :icon-class="fileIcon(scope.row)" style="margin-right: 6px" />
          <el-link v-if="scope.row.folder" @click="showNodesOfFolder(scope.row)">{{ scope.row.name }}</el-link>
          <span v-else>{{ scope.row.name }}</span>
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
          <el-dropdown size="mini" split-button type="primary" @click="showNodeDetail(scope.row)">
            详情
            <el-dropdown-menu slot="dropdown">
              <el-dropdown-item v-if="!scope.row.folder" @click.native="showFileReferenceDetail(scope.row)">引用详情</el-dropdown-item>
              <el-dropdown-item v-if="!scope.row.folder" @click.native="showNodesOfSha256(scope.row.sha256)">同引用文件</el-dropdown-item>
              <el-dropdown-item v-if="!scope.row.folder" @click.native="showScanDialog(scope.row)">扫描</el-dropdown-item>
              <el-dropdown-item @click.native="fileOperation('copy', scope.row)">复制</el-dropdown-item>
              <el-dropdown-item @click.native="fileOperation('move', scope.row)">移动</el-dropdown-item>
              <el-dropdown-item @click.native="fileOperation('rename', scope.row)">重命名</el-dropdown-item>
              <el-dropdown-item @click.native="showShare(scope.row)">分享</el-dropdown-item>
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
          <el-link v-if="!scope.row.folder" :underline="false" :href="downloadUrl(scope.row)" target="_blank">
            <el-button style="margin-left: 10px" size="mini" type="primary">下载</el-button>
          </el-link>
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
      @current-change="changeRouteQueryParams(false)"
    />
    <file-reference-dialog :visible.sync="showFileReferenceDialog" :node="nodeOfFileReference" />
    <file-detail-dialog :visible.sync="showNodeDetailDialog" :node="nodeOfDetailDialog" />
    <file-restore-dialog :visible.sync="showNodeRestoreDialog" :node="nodeToRestore" @restore-success="onRestoreSuccess" />
    <file-delete-dialog :visible.sync="showNodeDeleteDialog" :node="nodeToDelete" @delete-success="onDeleteSuccess" />
    <file-scan-dialog :visible.sync="showFileScanDialog" :node="nodeToScan" />
    <share-dialog :visible.sync="showShareDialog" :updating-keys="nodeToShare" />
    <file-operation-dialog
      :visible.sync="showFileOperationDialog"
      :create-mode="createMode"
      :updating-keys="nodeToOperate"
      @complete="queryNodes(nodeQuery)"
    />
  </div>
</template>
<script>
import { searchNodes, pageNodesBySha256, DEFAULT_PAGE_SIZE } from '@/api/node'
import { convertFileSize, formatDate } from '@/utils/file'
import FileReferenceDialog from '@/views/node/components/FileReferenceDialog'
import FileDetailDialog from '@/views/node/components/FileDetailDialog'
import FileRestoreDialog from '@/views/node/components/FileRestoreDialog'
import FileDeleteDialog from '@/views/node/components/FileDeleteDialog'
import { searchProjects } from '@/api/project'
import { listRepositories } from '@/api/repository'
import FileScanDialog from '@/views/node/components/FileScanDialog'
import ShareDialog from '@/views/node/components/ShareDialog'
import FileOperationDialog from '@/views/node/components/FileOperationDialog'

export default {
  name: 'Node',
  components: { FileScanDialog, FileDeleteDialog, FileRestoreDialog, FileDetailDialog, FileReferenceDialog, ShareDialog, FileOperationDialog },
  data() {
    return {
      rules: {
        sha256: [{ validator: this.validateSha256, trigger: 'blur' }],
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
        pageSize: DEFAULT_PAGE_SIZE
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
      indexOfNodeToDelete: -1,
      showFileScanDialog: false,
      nodeToScan: {},
      showShareDialog: false,
      showFileOperationDialog: false,
      createMode: '',
      nodeToShare: {},
      nodeToOperate: {}
    }
  },
  mounted() {
    this.onRouteUpdate(this.$route)
  },
  beforeRouteUpdate(to, from, next) {
    this.onRouteUpdate(to)
    next()
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
        this.regexValidate(value, /^(\/|(\/[^\/]+)+\/?)$/, callback)
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
      searchProjects(queryStr).then(res => {
        this.projects = res.data.records
        cb(this.projects)
      })
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
    changeRouteQueryParams(resetPage = false) {
      const query = {
        page: resetPage ? '1' : String(this.nodeQuery.pageNumber),
        size: String(this.nodeQuery.pageSize)
      }
      if (this.nodeQuery.useSha256) {
        query.sha256 = this.nodeQuery.sha256
        this.$router.push({ path: '/nodes', query: query })
      } else {
        query.projectId = this.nodeQuery.projectId
        query.repoName = this.nodeQuery.repoName
        query.path = this.nodeQuery.path
        this.$router.push({ path: '/nodes', query: query })
      }
    },
    onRouteUpdate(route) {
      const query = route.query
      const nodeQuery = this.nodeQuery
      nodeQuery.useSha256 = Boolean(query.sha256)
      nodeQuery.sha256 = query.sha256 ? query.sha256 : ''
      nodeQuery.projectId = query.projectId ? query.projectId : ''
      nodeQuery.repoName = query.repoName ? query.repoName : ''
      nodeQuery.path = query.path ? query.path : ''
      nodeQuery.pageNumber = query.page ? Number(query.page) : 1
      nodeQuery.pageSize = query.size ? Number(query.size) : DEFAULT_PAGE_SIZE
      if (nodeQuery.sha256 || (nodeQuery.projectId && nodeQuery.repoName && nodeQuery.path)) {
        this.$nextTick(() => {
          this.queryNodes(nodeQuery)
        })
      }
    },
    queryNodes(nodeQuery) {
      this.$refs['form'].validate((valid) => {
        if (valid) {
          this.doQueryNodes(nodeQuery)
        } else {
          return false
        }
      })
    },
    doQueryNodes(nodeQuery) {
      this.loading = true
      let promise = null
      if (nodeQuery.useSha256) {
        promise = pageNodesBySha256(nodeQuery.sha256, nodeQuery.pageNumber, nodeQuery.pageSize)
      } else {
        promise = searchNodes(nodeQuery.projectId, nodeQuery.repoName, nodeQuery.path, nodeQuery.pageNumber, nodeQuery.pageSize)
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
    downloadUrl(node) {
      const fullPath = node.fullPath.substring(1)
      return `/web/generic/${node.projectId}/${node.repoName}/${encodeURIComponent(fullPath)}`
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
      this.changeRouteQueryParams(true)
    },
    showScanDialog(node) {
      this.nodeToScan = node
      this.showFileScanDialog = true
    },
    showNodesOfFolder(node) {
      const nodeQuery = this.nodeQuery
      nodeQuery.useSha256 = false
      nodeQuery.projectId = node.projectId
      nodeQuery.repoName = node.repoName
      nodeQuery.path = node.fullPath + '/'
      this.changeRouteQueryParams(true)
    },
    showNodeRestore(index, node) {
      this.nodeToRestore = node
      this.showNodeRestoreDialog = true
    },
    onRestoreSuccess(node) {
      this.queryNodes(this.nodeQuery)
    },
    showNodeDelete(index, node) {
      this.nodeToDelete = node
      this.showNodeDeleteDialog = true
      this.indexOfNodeToDelete = index
    },
    onDeleteSuccess() {
      this.queryNodes(this.nodeQuery)
    },
    showShare(node) {
      this.showShareDialog = true
      this.nodeToShare = node
    },
    fileOperation(key, node) {
      this.createMode = key
      this.showFileOperationDialog = true
      this.nodeToOperate = node
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
