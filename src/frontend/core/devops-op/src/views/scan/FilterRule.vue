<template>
  <div class="app-container">
    <el-table :data="items" style="width: 100%">
      <el-table-column prop="name" label="规则名" width="180" />
      <el-table-column prop="type" label="类型" width="180">
        <template slot-scope="scope">
          {{ scope.row.type === RULE_TYPE_IGNORE ? '忽略' : '保留' }}
        </template>
      </el-table-column>
      <el-table-column prop="description" label="描述" width="300" />
      <el-table-column prop="projectIds" label="生效项目" width="300">
        <template slot-scope="scope">
          {{ scope.row.projectIds && scope.row.projectIds.length === 0 ? '全部' : scope.row.projectIds }}
        </template>
      </el-table-column>
      <el-table-column align="right">
        <template slot="header">
          <el-button type="primary" @click="showCreateOrUpdateDialog(true)">创建</el-button>
        </template>
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="primary"
            @click="showCreateOrUpdateDialog(false, scope.$index, scope.row)"
          >
            编辑
          </el-button>
          <el-button
            v-if="!scope.row.default"
            size="mini"
            type="danger"
            @click="handleDelete(scope.$index, scope.row)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      style="margin-top: 15px"
      background
      layout="prev, pager, next"
      :current-page.sync="pageParam.pageNumber"
      :page-size="pageParam.pageSize"
      :hide-on-single-page="true"
      :total="pageParam.total"
      @current-change="changeRouteQueryParams(false)"
    />
    <create-or-update-filter-rule-dialog
      :create-mode="createMode"
      :updating-rule="updatingItem"
      :visible.sync="showDialog"
      @created="refresh()"
      @updated="handleUpdated($event)"
    />
  </div>
</template>

<script>
import { deleteFilterRule, getFilterRules, RULE_TYPE_IGNORE } from '@/api/scan'
import CreateOrUpdateFilterRuleDialog from '@/views/scan/components/CreateOrUpdateFilterRuleDialog.vue'

export default {
  name: 'FilterRule',
  components: { CreateOrUpdateFilterRuleDialog },
  data() {
    return {
      RULE_TYPE_IGNORE: RULE_TYPE_IGNORE,
      pageParam: {
        total: 0,
        pageNumber: 1,
        pageSize: 20
      },
      loading: false,
      showDialog: false,
      createMode: true,
      updatingIndex: undefined,
      updatingItem: undefined,
      items: []
    }
  },
  created() {
    this.loading = true
    this.items = []
    this.refresh()
  },
  methods: {
    handleUpdated(item) {
      this.items.splice(this.updatingIndex, 1, item)
    },
    showCreateOrUpdateDialog(create, index, item) {
      this.showDialog = true
      this.createMode = create
      this.updatingIndex = index
      this.updatingItem = item
    },
    handleDelete(index, item) {
      this.$confirm(`是否确定删除${item.name}`, '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        deleteFilterRule(item.id).then(_ => {
          this.pageParam.pageNumber = 1
          this.refresh()
          this.$message.success('删除成功')
        })
      }).catch(() => {
        this.$message({
          type: 'info',
          message: '已取消'
        })
      })
    },
    changeRouteQueryParams(resetPage = false) {
      const query = {
        page: resetPage ? '1' : String(this.pageParam.pageNumber),
        size: String(this.pageParam.pageSize)
      }
      this.$router.push({ path: '/rules', query: query })
    },
    refresh() {
      getFilterRules(this.pageParam.pageNumber, this.pageParam.pageSize).then(res => {
        this.items = res.data.records
        this.pageParam.total = res.data.totalRecords
      })
    }
  }
}
</script>

<style scoped>

</style>
