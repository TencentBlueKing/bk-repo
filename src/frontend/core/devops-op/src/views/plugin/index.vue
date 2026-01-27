<template>
  <div class="app-container node-container">
    <el-form ref="form" :inline="true" :model="pluginQuery">
      <el-form-item label="生效范围" prop="scope">
        <el-input
          v-model="pluginQuery.scope"
          class="inline-input"
          placeholder="请输入生效范围"
        />
      </el-form-item>
      <el-form-item>
        <el-button size="mini" type="primary" @click="changeRouteQueryParams()">查询</el-button>
        <el-button size="mini" type="primary" @click="showPluginCreate()">新建</el-button>
      </el-form-item>
    </el-form>
    <el-table v-loading="loading" :data="plugins" style="width: 100%">
      <el-table-column prop="id" label="id" min-width="100px" />
      <el-table-column prop="version" label="版本" min-width="80px" />
      <el-table-column prop="scope" label="生效范围" min-width="100px">
        <template slot-scope="scope">
          <el-tag v-for="s in scope.row.scope" :key="s" style="margin-right: 5px">{{ s }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="gitUrl" label="代码库地址" min-width="200px">
        <template slot-scope="scope">
          <el-link :href="scope.row.gitUrl" type="primary" target="blank">{{ scope.row.gitUrl }}</el-link>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="描述" min-width="200px" />

      <el-table-column label="操作" min-width="300px">
        <template slot-scope="scope">
          <el-button size="mini" type="primary" @click="showPluginDetail(scope.row)">修改</el-button>
          <el-button size="mini" type="danger" @click="showPluginDelete(scope.row)">删除</el-button>
          <el-button size="mini" type="primary" @click="showPluginStatus(scope.row)">运行状态</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      style="margin-top: 15px"
      background
      layout="prev, pager, next"
      :current-page.sync="pluginQuery.pageNumber"
      :page-size="pluginQuery.pageSize"
      :hide-on-single-page="true"
      :total="total"
      @current-change="changeRouteQueryParams()"
    />
    <plugin-create-dialog :visible.sync="showPluginCreateDialog" @create-success="onCreateSuccess" />
    <plugin-detail-dialog :visible.sync="showPluginDetailDialog" :plugin="pluginOfDetailDialog" @update-success="onUpdateSuccess" />
    <plugin-delete-dialog :visible.sync="showPluginDeleteDialog" :plugin="pluginToDelete" @delete-success="onDeleteSuccess" />
    <plugin-status-dialog :visible.sync="showPluginStatusDialog" :plugin="pluginOfStatusDialog" />
  </div>
</template>
<script>
import { listPlugin } from '@/api/plugin'
import PluginCreateDialog from '@/views/plugin/components/PluginCreateDialog'
import PluginDetailDialog from '@/views/plugin/components/PluginDetailDialog'
import PluginDeleteDialog from '@/views/plugin/components/PluginDeleteDialog'
import PluginStatusDialog from '@/views/plugin/components/PluginStatusDialog'

export default {
  name: 'Plugin',
  components: { PluginCreateDialog, PluginDetailDialog, PluginDeleteDialog, PluginStatusDialog },
  beforeRouteUpdate(to, from, next) {
    this.onRouteUpdate(to)
    next()
  },
  data() {
    return {
      loading: false,
      pluginQuery: {
        scope: '',
        pageNumber: 1,
        pageSize: 20
      },
      plugins: [],
      total: 0,
      showPluginCreateDialog: false,
      pluginToCreate: {},
      showPluginDetailDialog: false,
      pluginOfDetailDialog: {},
      showPluginDeleteDialog: false,
      pluginToDelete: {},
      showPluginStatusDialog: false,
      pluginOfStatusDialog: {},
      indexOfPluginToDelete: -1
    }
  },
  mounted() {
    this.onRouteUpdate(this.$route)
  },
  methods: {
    queryModeChanged() {
      this.$refs['form'].clearValidate()
    },
    changeRouteQueryParams() {
      this.$router.push({ path: '/system/plugin', query: this.pluginQuery })
    },
    onRouteUpdate(route) {
      this.pluginQuery.scope = route.query.scope
      this.pluginQuery.pageNumber = route.query.pageNumber
      this.pluginQuery.pageSize = route.query.pageSize
      this.$nextTick(() => {
        this.queryPlugin(route.query)
      })
    },
    queryPlugin(pluginQuery) {
      this.loading = true
      const promise = listPlugin(pluginQuery.scope, pluginQuery.pageNumber, pluginQuery.pageSize)
      promise.then(res => {
        this.plugins = res.data.records
        this.total = res.data.totalRecords
      }).catch(_ => {
        this.plugins = []
        this.total = 0
      }).finally(() => {
        this.loading = false
      })
    },
    showPluginCreate() {
      this.showPluginCreateDialog = true
    },
    showPluginDetail(plugin) {
      this.pluginOfDetailDialog = plugin
      this.showPluginDetailDialog = true
    },
    showPluginDelete(plugin) {
      this.pluginToDelete = plugin
      this.showPluginDeleteDialog = true
    },
    onDeleteSuccess() {
      this.queryPlugin(this.pluginQuery)
    },
    onCreateSuccess() {
      this.queryPlugin(this.pluginQuery)
    },
    onUpdateSuccess() {
      this.queryPlugin(this.pluginQuery)
    },
    showPluginStatus(plugin) {
      this.pluginOfStatusDialog = plugin
      this.showPluginStatusDialog = true
    }
  }
}
</script>

<style scoped>

</style>
