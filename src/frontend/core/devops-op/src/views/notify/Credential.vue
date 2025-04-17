<template>
  <div class="app-container">

    <el-table
      :data="credentials"
      style="width: 100%"
    >
      <el-table-column
        prop="name"
        label="名称"
        width="180"
      />
      <el-table-column
        prop="type"
        label="类型"
        width="180"
      />
      <el-table-column
        prop="default"
        label="默认通道"
        width="180"
      >
        <template slot-scope="scope">
          <span>{{ scope.row.default ? '是' : '否' }}</span>
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
      :visible.sync="showDialog"
      @created="handleCreated($event)"
      @updated="handleUpdated($event)"
    />
  </div>

</template>

<script>
import CreateOrUpdateCredentialDialog from '@/views/notify/components/CreateOrUpdateCredentialDialog'
import { credentials, deleteCredential } from '@/api/notify'

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
      credentials: []
    }
  },
  created() {
    this.loading = true
    this.credentials = []
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
      this.$confirm(`是否确定删除${credential.name}`, '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        deleteCredential(credential.name).then(_ => {
          this.credentials.splice(index, 1)
          this.$message.success('删除成功')
        })
      }).catch(() => {
        this.$message({
          type: 'info',
          message: '已取消'
        })
      })
    }
  }
}
</script>

<style scoped>

</style>
