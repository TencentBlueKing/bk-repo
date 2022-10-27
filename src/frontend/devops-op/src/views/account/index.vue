<template>
  <div class="app-container">
    <el-table
      :data="accounts"
      style="width: 100%"
    >
      <el-table-column type="expand">
        <template slot-scope="props">
          <el-table
            :data="props.row.credentials"
          >
            <el-table-column
              label="AK/SK创建时间"
              width="250"
              prop="createdAt"
            >
              <template slot-scope="scope">
                <span>{{ formatNormalDate(scope.row.createdAt) }}</span>
              </template>
            </el-table-column>
            <el-table-column
              label="认证授权方式"
              width="250"
              prop="authorizationGrantType"
            />
            <el-table-column
              label="accessKey"
              width="350"
              prop="accessKey"
            />
            <el-table-column
              label="secretKey"
              width="250"
              prop="secretKey"
            />
            <el-table-column
              label="启用状态"
              width="180"
            >
              <template slot-scope="scope">
                <el-switch
                  :value="scope.row.status === 'ENABLE'"
                  @change="changeStatus(props.$index, props.row, scope.$index, scope.row)"
                />
              </template>
            </el-table-column>
            <el-table-column align="right">
              <template slot="header">
                <el-button type="primary" @click="showCreateKeyDialog(props.$index, props.row)">创建</el-button>
              </template>
              <template slot-scope="scope">
                <el-button
                  v-if="!scope.row.default"
                  size="mini"
                  type="danger"
                  @click="keyHandleDelete(props.row, props.$index, scope.$index, scope.row)"
                >
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </template>
      </el-table-column>
      <el-table-column
        prop="appId"
        label="应用ID"
        width="180"
      />
      <el-table-column
        label="创建时间"
        width="250"
        prop="createdDate"
      >
        <template slot-scope="scope">
          <span>{{ formatNormalDate(scope.row.createdDate) }}</span>
        </template>
      </el-table-column>
      <el-table-column
        label="locked状态"
        width="180"
      >
        <template slot-scope="scope">
          <span>{{ scope.row.locked ? '是' : '否' }}</span>
        </template>
      </el-table-column>
      <el-table-column
        label="最后修改时间"
        width="250"
        prop="lastModifiedDate"
      >
        <template slot-scope="scope">
          <span>{{ formatNormalDate(scope.row.lastModifiedDate) }}</span>
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
    <create-or-update-Key-dialog
      :create-mode="createKeyMode"
      :visible.sync="showKeyDialog"
      :updating-keys="updatingAccount"
      @created="handleKeyCreated($event)"
    />
  </div>
</template>
<script>
import CreateOrUpdateAccountDialog from '@/views/account/components/CreateOrUpdateAccountDialog'
import CreateOrUpdateKeyDialog from '@/views/account/components/CreateOrUpdateKeyDialog'
import { deleteAccount, list, deleteKey, updateKey, keyLists } from '@/api/account'
import { formatNormalDate } from '@/utils/date'

export default {
  name: 'Account',
  components: { CreateOrUpdateAccountDialog, CreateOrUpdateKeyDialog },
  data() {
    return {
      showDialog: false,
      showKeyDialog: false,
      createMode: true,
      createKeyMode: true,

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
      list().then(res => {
        this.accounts = res.data
        this.defaultAccount = res.data
        this.$forceUpdate()
      })
    },
    handleKeyCreated() {
      keyLists(this.updatingAccount.appId).then(res => {
        this.accounts[this.updatingIndex].credentials = res.data
        this.updatingAccount = undefined
        this.updatingIndex = undefined
      })
    },
    changeStatus(accountIndex, account, index, key) {
      const resStatus = key.status === 'ENABLE' ? 'DISABLE' : 'ENABLE'
      updateKey(account.appId, key.accessKey, resStatus).then(() => {
        this.accounts[accountIndex].credentials[index].status = resStatus
      })
    },
    showCreateKeyDialog(index, account) {
      this.showKeyDialog = true
      this.createKeyMode = true
      this.updatingIndex = index
      this.updatingAccount = account
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
        }).catch((res) => {
          console.log(res)
        })
      }).catch(() => {
        this.$message({
          type: 'info',
          message: '已取消'
        })
      })
    },
    keyHandleDelete(account, accountIndex, index, key) {
      this.$confirm(`是否确定删除`, '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        deleteKey(account.appId, key.accessKey).then(_ => {
          this.accounts[accountIndex].credentials.splice(index, 1)
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
