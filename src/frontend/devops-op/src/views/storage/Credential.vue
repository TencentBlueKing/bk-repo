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
        label="缓存天数(已废弃)"
        width="180"
      >
        <template v-if="scope.row.cache.enabled" slot-scope="scope">
          <span>{{ expireDays(scope.row.cache.expireDays) }}</span>
        </template>
      </el-table-column>
      <el-table-column
        label="缓存时间"
        width="180"
      >
        <template v-if="scope.row.cache.enabled" slot-scope="scope">
          <span>{{ formatSeconds(scope.row.cache.expireDuration) }}</span>
        </template>
      </el-table-column>
      <el-table-column
        label="优先读缓存"
        width="180"
      >
        <template v-if="scope.row.cache.enabled" slot-scope="scope">
          <span>{{ scope.row.cache.loadCacheFirst ? '是' : '否' }}</span>
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
    <create-or-update-credential-dialog
      :create-mode="createMode"
      :updating-credentials="updatingCredential"
      :storage-type="defaultCredential.type"
      :visible.sync="showDialog"
      @created="handleCreated($event)"
      @updated="handleUpdated($event)"
    />
  </div>
</template>
<script>
import { credentials, defaultCredential, deleteCredential } from '@/api/storage'
import CreateOrUpdateCredentialDialog from '@/views/storage/components/CreateOrUpdateCredentialDialog'

export default {
  name: 'Credential',
  components: { CreateOrUpdateCredentialDialog },
  data() {
    return {
      showDialog: false,
      createMode: true,
      /**
       * 正在更新的凭据索引
       */
      updatingIndex: undefined,
      updatingCredential: undefined,
      loading: true,
      credentials: [],
      defaultCredential: {}
    }
  },
  computed: {
    defaultCredentialType() {
      return this.defaultCredential.type
    }
  },
  created() {
    this.loading = true
    this.credentials = []
    defaultCredential().then(res => {
      this.defaultCredential = res.data
      this.defaultCredential.key = 'default'
      this.defaultCredential.default = true
      this.credentials.push(this.defaultCredential)
    })
    credentials().then(res => {
      this.credentials.push(...res.data)
    })
  },
  methods: {
    handleCreated(credential) {
      this.credentials.splice(this.credentials.length, 0, credential)
    },
    handleUpdated(credential) {
      this.credentials.splice(this.updatingIndex, 1, credential)
    },
    showCreateOrUpdateDialog(create, index, credential) {
      this.showDialog = true
      this.createMode = create
      this.updatingIndex = index
      this.updatingCredential = credential
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
    },
    formatSeconds(seconds) {
      if (seconds <= 0) {
        return '永久'
      }
      const hours = Math.floor(seconds / 3600)
      const minutes = Math.floor((seconds % 3600) / 60)
      const remainingSeconds = seconds % 60

      return `${hours}小时 ${minutes}分钟 ${remainingSeconds}秒`
    }
  }
}
</script>

<style scoped>

</style>
