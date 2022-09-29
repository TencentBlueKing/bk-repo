<template>
  <div class="app-container">
    <el-table
      :data="accounts"
      style="width: 100%"
    >
      <el-table-column
        label="创建时间"
        width="180"
      >
        <template slot-scope="scope">
          <span>{{ formatNormalDate(scope.row.credentials[0].createdAt) }}</span>
        </template>
      </el-table-column>
      <el-table-column
        prop="appId"
        label="应用ID"
        width="180"
      />
      <el-table-column
        label="locked状态"
        width="180"
      >
        <template slot-scope="scope">
          <span>{{ scope.row.locked ? '是' : '否' }}</span>
        </template>
      </el-table-column>
      <el-table-column
        label="accessKey"
        width="350"
      >
        <template slot-scope="scope">
          <span>{{ scope.row.credentials[0].accessKey }}</span>
        </template>
      </el-table-column>
      <el-table-column
        label="secretKey"
        width="180"
      >
        <template slot-scope="scope">
          <span>{{ scope.row.credentials[0].secretKey }}</span>
        </template>
      </el-table-column>
      <el-table-column
        label="是否启用"
        width="180"
      >
        <template slot-scope="scope">
          <span>{{ scope.row.credentials.status === 'ENABLE' ? '是' : '否' }}</span>
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
    <create-or-update-account-dialog
      :create-mode="createMode"
      :updating-credentials="updatingAccount"
      :visible.sync="showDialog"
      @created="handleCreated($event)"
      @updated="handleUpdated($event)"
    />
  </div>
</template>
<script>
import CreateOrUpdateAccountDialog from '@/views/account/components/CreateOrUpdateAccountDialog'
import { deleteAccount, list } from '@/api/account'
import { formatNormalDate } from '@/utils/date'

export default {
  name: 'Account',
  components: { CreateOrUpdateAccountDialog },
  data() {
    return {
      showDialog: false,
      createMode: true,
      /**
       * 正在更新的凭据索引
       */
      updatingIndex: undefined,
      updatingAccount: undefined,
      loading: true,
      accounts: [],
      defaultAccount: {}
    }
  },
  computed: {
    defaultCredentialType() {
      return this.defaultAccount
    }
  },
  created() {
    this.loading = true
    this.accounts = []
    list().then(res => {
      this.accounts = res.data
      this.defaultAccount = res.data
    })
  },
  methods: {
    handleCreated(credential) {
      this.accounts.splice(this.accounts.length, 0, credential)
    },
    handleUpdated(credential) {
      this.accounts.splice(this.updatingIndex, 1, credential)
    },
    showCreateOrUpdateDialog(create, index, account) {
      this.showDialog = true
      this.createMode = create
      this.updatingIndex = index
      this.updatingAccount = account
    },
    handleDelete(index, account) {
      this.$confirm(`是否确定删除应用${account.appId}`, '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        deleteAccount(account.appId).then(_ => {
          this.accounts.splice(index, 1)
          this.$message.success('删除成功')
        })
      }).catch(() => {
        this.$message({
          type: 'info',
          message: '已取消'
        })
      })
    },
    formatNormalDate(data) {
      return formatNormalDate(data)
    }
  }
}
</script>

<style scoped>

</style>
