<template>
  <div class="app-container">
    <el-table
      :data="credentials"
      style="width: 100%"
    >
      <el-table-column
        prop="key"
        label="Key"
        width="180"
      />
      <el-table-column
        prop="type"
        label="Type"
        width="180"
      />
      <el-table-column
        label="开启缓存"
        width="180"
      >
        <template slot-scope="scope">
          <span>{{ scope.row.cache.enabled ? '是' : '否' }}</span>
        </template>
      </el-table-column>
      <el-table-column
        label="缓存天数"
        width="180"
      >
        <template slot-scope="scope">
          <span>{{ expireDays(scope.row.cache.expireDays) }}</span>
        </template>
      </el-table-column>
      <el-table-column
        label="优先读缓存"
        width="180"
      >
        <template slot-scope="scope">
          <span>{{ scope.row.cache.loadCacheFirst ? '是' : '否' }}</span>
        </template>
      </el-table-column>
      <el-table-column align="right">
        <template slot="header">
          <el-button type="primary" @click="showCreateDialog">创建</el-button>
        </template>
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="primary"
            @click="handleEdit(scope.$index, scope.row)"
          >
            编辑
          </el-button>
          <el-button
            size="mini"
            type="danger"
            @click="handleDelete(scope.$index, scope.row)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <create-credential-dialog :visible.sync="showCreateCredentialDialog" @created="handleCreated($event)" />
  </div>
</template>
<script>
import { credentials, deleteCredential } from '@/api/storage'
import CreateCredentialDialog from '@/views/storage/components/CreateCredentialDialog'

export default {
  name: 'Credential',
  components: { CreateCredentialDialog },
  data() {
    return {
      showCreateCredentialDialog: false,
      loading: true,
      credentials: []
    }
  },
  created() {
    this.loading = true
    credentials().then(res => {
      this.credentials = res.data
    })
  },
  methods: {
    handleCreated(credential) {
      this.credentials.splice(this.credentials.length, 0, credential)
    },
    showCreateDialog() {
      this.showCreateCredentialDialog = true
    },
    handleEdit(index, credential) {
      console.log('edit')
    },
    handleDelete(index, credential) {
      this.$confirm(`是否确定删除${credential.key}`, '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        deleteCredential(credential.key).then(_ => {
          this.credentials.splice(index, 1)
          this.$message.success('删除成功')
        })
      }).catch(() => {
        this.$message({
          type: 'info',
          message: '已取消'
        })
      })
    },
    expireDays(expireDays) {
      return parseInt(expireDays) <= 0 ? '永久' : expireDays
    }
  }
}
</script>

<style scoped>

</style>
