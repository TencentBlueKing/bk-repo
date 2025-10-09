<template>
  <div class="app-container">
    <div class="filter-container">
      <el-input v-model="listQuery.projectId" placeholder="项目ID" style="width: 200px;" class="filter-item" @keyup.enter.native="handleFilter" />
      <el-button class="filter-item" type="primary" icon="el-icon-search" @click="handleFilter">
        搜索
      </el-button>
      <el-button class="filter-item" style="margin-left: 10px;" type="primary" icon="el-icon-plus" @click="handleCreate">
        创建
      </el-button>
    </div>

    <el-table
      v-loading="listLoading"
      :data="list"
      border
      fit
      highlight-current-row
      style="width: 100%;"
    >
      <el-table-column label="项目ID" prop="projectId" align="center" min-width="120">
        <template slot-scope="{row}">
          <span>{{ row.projectId }}</span>
        </template>
      </el-table-column>
      <el-table-column label="扫描器配置" prop="scanner" align="center" min-width="200">
        <template slot-scope="{row}">
          <el-tag v-for="(value, key) in row.scanner" :key="key" style="margin-right: 5px;">
            {{ key }}: {{ value }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="标签" prop="tags" align="center" min-width="150">
        <template slot-scope="{row}">
          <el-tag v-for="tag in row.tags" :key="tag" style="margin-right: 5px;">{{ tag }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建人" prop="createdBy" align="center" min-width="100">
        <template slot-scope="{row}">
          <span>{{ row.createdBy }}</span>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" prop="createdDate" align="center" min-width="180">
        <template slot-scope="{row}">
          <span>{{ row.createdDate | parseTime('{y}-{m}-{d} {h}:{i}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="最后修改人" prop="lastModifiedBy" align="center" min-width="100">
        <template slot-scope="{row}">
          <span>{{ row.lastModifiedBy }}</span>
        </template>
      </el-table-column>
      <el-table-column label="最后修改时间" prop="lastModifiedDate" align="center" min-width="180">
        <template slot-scope="{row}">
          <span>{{ row.lastModifiedDate | parseTime('{y}-{m}-{d} {h}:{i}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="230" class-name="small-padding fixed-width">
        <template slot-scope="{row}">
          <el-button type="primary" size="mini" @click="handleUpdate(row)">
            编辑
          </el-button>
          <el-button size="mini" type="danger" @click="handleDelete(row)">
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      style="margin-top: 15px"
      background
      layout="total, prev, pager, next, jumper"
      :current-page="listQuery.pageNumber"
      :page-size="listQuery.pageSize"
      :hide-on-single-page="true"
      :total="total"
      @current-change="changeRouteQueryParams"
    />

    <create-or-update-sign-config-dialog
      :create-mode="dialogCreateMode"
      :visible.sync="dialogVisible"
      :updating-sign-config="updatingSignConfig"
      @created="handleCreated"
      @updated="handleUpdated"
    />
  </div>
</template>

<script>
import { getSignConfigList, deleteSignConfig } from '@/api/signconfig'
import CreateOrUpdateSignConfigDialog from './components/CreateOrUpdateSignConfigDialog'

export default {
  name: 'SignConfig',
  components: { CreateOrUpdateSignConfigDialog },
  beforeRouteUpdate(to, from, next) {
    this.onRouteUpdate(to)
    next()
  },
  filters: {
    parseTime(time, cFormat) {
      if (!time) return ''
      const format = cFormat || '{y}-{m}-{d} {h}:{i}'
      let date
      if (typeof time === 'object') {
        date = time
      } else {
        if ((typeof time === 'string') && (/^[0-9]+$/.test(time))) {
          time = parseInt(time)
        }
        if ((typeof time === 'number') && (time.toString().length === 10)) {
          time = time * 1000
        }
        date = new Date(time)
      }
      const formatObj = {
        y: date.getFullYear(),
        m: date.getMonth() + 1,
        d: date.getDate(),
        h: date.getHours(),
        i: date.getMinutes(),
        s: date.getSeconds(),
        a: date.getDay()
      }
      const time_str = format.replace(/{([ymdhisa])+}/g, (result, key) => {
        const value = formatObj[key]
        if (key === 'a') { return ['日', '一', '二', '三', '四', '五', '六'][value] }
        return value.toString().padStart(2, '0')
      })
      return time_str
    }
  },
  data() {
    return {
      list: null,
      total: 0,
      listLoading: true,
      listQuery: {
        pageNumber: 1,
        pageSize: 20,
        projectId: undefined
      },
      dialogVisible: false,
      dialogCreateMode: true,
      updatingSignConfig: {}
    }
  },
  mounted() {
    this.onRouteUpdate(this.$route)
  },
  methods: {
    getList() {
      this.listLoading = true
      getSignConfigList(this.listQuery.projectId, this.listQuery.pageNumber, this.listQuery.pageSize).then(response => {
        this.list = response.data.records
        this.total = response.data.totalRecords
        this.listLoading = false
      }).catch(() => {
        this.listLoading = false
      })
    },
    changeRouteQueryParams(pageNumber) {
      this.listQuery.pageNumber = pageNumber
      const query = {
        pageNumber: this.listQuery.pageNumber,
        pageSize: this.listQuery.pageSize,
        projectId: this.listQuery.projectId
      }
      console.log("change router query:", query)
      this.$router.push({ path: '/sign-config', query: query })
    },
    onRouteUpdate(route) {
      console.log("router update", route.query)
      this.listQuery.pageNumber = route.query.pageNumber || 1
      this.listQuery.pageSize = route.query.pageSize || 20
      this.listQuery.projectId = route.query.projectId
      this.$nextTick(() => {
        this.getList()
      })
    },
    handleFilter() {
      this.listQuery.pageNumber = 1
      this.listQuery.pageSize = 20
      this.getList()
    },
    handleCreate() {
      this.dialogCreateMode = true
      this.updatingSignConfig = {}
      this.dialogVisible = true
    },
    handleUpdate(row) {
      this.dialogCreateMode = false
      this.updatingSignConfig = Object.assign({}, row)
      this.dialogVisible = true
    },
    handleDelete(row) {
      this.$confirm('确定要删除该签名配置吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        deleteSignConfig(row.projectId).then(() => {
          this.$message({
            type: 'success',
            message: '删除成功!'
          })
          this.getList()
        })
      }).catch(() => {
        this.$message({
          type: 'info',
          message: '已取消删除'
        })
      })
    },
    handleCreated() {
      this.getList()
    },
    handleUpdated() {
      this.getList()
    }
  }
}
</script>

<style scoped>
.filter-container {
  padding-bottom: 10px;
}
.filter-item {
  display: inline-block;
  vertical-align: middle;
  margin-bottom: 10px;
}
</style>
