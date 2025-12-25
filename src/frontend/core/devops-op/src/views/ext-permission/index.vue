<template>
  <div class="app-container node-container">
    <el-form ref="form" :inline="true" :model="permissionQuery">
      <el-form-item label="项目ID" prop="projectId">
        <el-input
          v-model="permissionQuery.projectId"
          class="inline-input"
          placeholder="请输入项目ID"
        />
      </el-form-item>
      <el-form-item style="margin-left: 15px" label="仓库" prop="repoName">
        <el-input
          v-model="permissionQuery.repoName"
          class="inline-input"
          placeholder="请输入仓库名"
        />
      </el-form-item>
      <el-form-item label="url" prop="url">
        <el-input
          v-model="permissionQuery.url"
          class="inline-input"
          style="width: 300px"
          placeholder="请输入外部权限url"
        />
      </el-form-item>
      <el-form-item style="margin-left: 15px" label="适用接口范围" prop="scope">
        <el-input v-model="permissionQuery.scope" style="width: 200px;" placeholder="请输入适用范围" />
      </el-form-item>
      <el-form-item label="启用" prop="enabled">
        <el-select v-model="permissionQuery.enabled" clearable placeholder="请选择其否启用">
          <el-option label="启用" :value="true" />
          <el-option label="未启用" :value="false" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button size="mini" type="primary" @click="changeRouteQueryParams()">查询</el-button>
        <el-button size="mini" type="primary" @click="showPermissionCreate()">新建</el-button>
      </el-form-item>
    </el-form>
    <el-table v-loading="loading" :data="permissions" style="width: 100%">
      <el-table-column prop="projectId" label="项目Id" min-width="100px" />
      <el-table-column prop="repoName" label="仓库名" min-width="120px" />
      <el-table-column prop="url" label="url" min-width="200px" />
      <el-table-column prop="headers" label="请求头" min-width="200px">
        <template slot-scope="scope">{{ JSON.stringify(scope.row.headers) }}</template>
      </el-table-column>
      <el-table-column prop="scope" label="适用接口范围" min-width="200px" />
      <el-table-column prop="enabled" label="平台账号白名单" min-width="100px">
        <template slot-scope="scope">{{ scope.row.platformWhiteList.join(',') }}</template>
      </el-table-column>
      <el-table-column prop="enabled" label="是否启用" min-width="100px">
        <template slot-scope="scope">{{ String(scope.row.enabled) }}</template>
      </el-table-column>
      <el-table-column label="操作" min-width="120px">
        <template slot-scope="scope">
          <el-button size="mini" type="primary" @click="showPermissionDetail(scope.row)">修改</el-button>
          <el-button size="mini" type="danger" @click="showPermissionDelete(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      style="margin-top: 15px"
      background
      layout="prev, pager, next"
      :current-page.sync="permissionQuery.pageNumber"
      :page-size="permissionQuery.pageSize"
      :hide-on-single-page="true"
      :total="total"
      @current-change="changeRouteQueryParams()"
    />
    <permission-create-dialog :visible.sync="showPermissionCreateDialog" @create-success="onCreateSuccess" />
    <permission-detail-dialog :visible.sync="showPermissionDetailDialog" :permission="permissionOfDetailDialog" @update-success="onUpdateSuccess" />
    <permission-delete-dialog :visible.sync="showPermissionDeleteDialog" :permission="permissionToDelete" @delete-success="onDeleteSuccess" />
  </div>
</template>
<script>
import { listExtPermission } from '@/api/ext-permission'
import { formatDate } from '@/utils/file'
import PermissionCreateDialog from '@/views/ext-permission/components/PermissionCreateDialog'
import PermissionDetailDialog from '@/views/ext-permission/components/PermissionDetailDialog'
import PermissionDeleteDialog from '@/views/ext-permission/components/PermissionDeleteDialog'

export default {
  name: 'ExtPermission',
  components: { PermissionCreateDialog, PermissionDetailDialog, PermissionDeleteDialog },
  beforeRouteUpdate(to, from, next) {
    this.onRouteUpdate(to)
    next()
  },
  data() {
    return {
      loading: false,
      permissionQuery: {
        url: '',
        projectId: '',
        repoName: '',
        scope: '',
        enabled: '',
        pageNumber: 1,
        pageSize: 20
      },
      permissions: [],
      total: 0,
      showFileReferenceDialog: false,
      nodeOfFileReference: {},
      showPermissionCreateDialog: false,
      permissionToCreate: {},
      showPermissionDetailDialog: false,
      permissionOfDetailDialog: {},
      showNodeRestoreDialog: false,
      nodeToRestore: {},
      showPermissionDeleteDialog: false,
      permissionToDelete: {},
      indexOfPermissionToDelete: -1
    }
  },
  mounted() {
    this.onRouteUpdate(this.$route)
  },
  methods: {
    formatDate(date) {
      return formatDate(date)
    },
    queryModeChanged() {
      this.$refs['form'].clearValidate()
    },
    changeRouteQueryParams() {
      this.$router.push({ path: '/system/ext-permission', query: this.permissionQuery })
    },
    onRouteUpdate(route) {
      this.$nextTick(() => {
        this.queryPermission(route.query)
      })
    },
    queryPermission(permissionQuery) {
      this.loading = true
      const promise = listExtPermission(
        permissionQuery.projectId,
        permissionQuery.repoName,
        permissionQuery.url,
        permissionQuery.scope,
        permissionQuery.enabled
      )
      promise.then(res => {
        this.permissions = res.data.records
        this.total = res.data.totalRecords
      }).catch(_ => {
        this.permissions = []
        this.total = 0
      }).finally(() => {
        this.loading = false
      })
    },
    showPermissionCreate() {
      this.showPermissionCreateDialog = true
    },
    showPermissionDetail(permission) {
      this.permissionOfDetailDialog = permission
      this.showPermissionDetailDialog = true
    },
    showPermissionDelete(permission) {
      this.permissionToDelete = permission
      this.showPermissionDeleteDialog = true
    },
    onDeleteSuccess() {
      this.queryPermission(this.permissionQuery)
    },
    onCreateSuccess() {
      this.queryPermission(this.permissionQuery)
    },
    onUpdateSuccess() {
      this.queryPermission(this.permissionQuery)
    }
  }
}
</script>

<style scoped>

</style>
