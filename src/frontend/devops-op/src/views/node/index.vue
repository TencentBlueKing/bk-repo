<template>
  <div class="app-container">
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
      <el-form-item v-if="!nodeQuery.useSha256" label="项目ID" prop="projectId">
        <el-input v-model="nodeQuery.projectId" size="mini" placeholder="请输入项目ID" />
      </el-form-item>
      <el-form-item v-if="!nodeQuery.useSha256" style="margin-left: 15px" label="仓库" prop="repoName">
        <el-input v-model="nodeQuery.repoName" :disabled="!nodeQuery.projectId" size="mini" placeholder="请输入仓库名" />
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
          <el-dropdown size="mini" split-button type="primary" @click="showNodeDetail(scope.row)">
            详情
            <el-dropdown-menu v-if="!scope.row.folder" slot="dropdown">
              <el-dropdown-item @click.native="showFileReferenceDetail(scope.row)">引用详情</el-dropdown-item>
              <el-dropdown-item @click.native="showNodesOfSha256(scope.row.sha256)">同引用文件</el-dropdown-item>
            </el-dropdown-menu>
          </el-dropdown>
          <el-button
            v-if="scope.row.deleted"
            style="margin-left: 10px"
            size="mini"
            type="primary"
            @click="showNodeRestore(scope.row)"
          >恢复</el-button>
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
    <file-restore-dialog :visible.sync="showNodeRestoreDialog" :node="nodeOfRestoreDialog" @restore-success="onRestoreSuccess" />
  </div>
</template>
<script>
import { searchNodes, pageNodesBySha256 } from '@/api/node'
import { convertFileSize, formatDate } from '@/utils/file'
import FileReferenceDialog from '@/views/node/components/FileReferenceDialog'
import FileDetailDialog from '@/views/node/components/FileDetailDialog'
import FileRestoreDialog from '@/views/node/components/FileRestoreDialog'

export default {
  name: 'Node',
  components: { FileRestoreDialog, FileDetailDialog, FileReferenceDialog },
  data() {
    return {
      rules: {
        sha256: [{ validator: this.validateSha256, trigger: 'blur' }],
        projectId: [{ validator: this.validateName, trigger: 'blur' }],
        repoName: [{ validator: this.validateName, trigger: 'blur' }],
        path: [{ validator: this.validatePath, trigger: 'blur' }]
      },
      loading: false,
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
      nodeOfRestoreDialog: {}
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
    showNodeRestore(node) {
      this.nodeOfRestoreDialog = node
      this.showNodeRestoreDialog = true
    },
    onRestoreSuccess(node) {
      node.deleted = undefined
    },
    deleteNode(node) {
      console.log('delete')
    }
  }
}
</script>

<style scoped>

</style>

<style>
.el-table .deleted-row {
  background: #e9e9e9;
}
</style>
