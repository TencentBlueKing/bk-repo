<template>
  <el-dialog title="创建AK/SK" :visible.sync="showKeyDialog" :before-close="close">
    <el-form ref="form" :model="key" status-icon>
      <el-form-item
        label="应用ID"
        prop="appId"
      >
        <el-input v-model="appId" disabled />
      </el-form-item>
      <el-form-item label="认证授权方式" prop="authorizationGrantTypes">
        <el-select v-model="authType" placeholder="请选择" style="width: 350px" :change="key.authorizationGrantTypes = authType">
          <el-option
            v-for="item in authOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
    </el-form>
    <div slot="footer">
      <el-button @click="close">取 消</el-button>
      <el-button type="primary" @click="handleCreateOrUpdate(key)">确 定</el-button>
    </div>
  </el-dialog>
</template>

<script>
import { createKey } from '@/api/account'
export default {
  name: 'CreateOrUpdateKeyDialog',
  props: {
    visible: Boolean,
    /**
     * 仅在更新模式时有值
     */
    updatingKeys: {
      type: Object,
      default: undefined
    },
    /**
     * 是否为创建模式，true时为创建对象，false时为更新对象
     */
    createKeyMode: Boolean
  },
  data() {
    return {
      showKeyDialog: this.visible,
      key: this.newCredential(),
      authType: '',
      appId: '',
      authOptions: [{
        value: 'AUTHORIZATION_CODE',
        label: 'AUTHORIZATION_CODE'
      }, {
        value: 'PLATFORM',
        label: 'PLATFORM'
      }]
    }
  },
  watch: {
    visible: function(newVal) {
      if (newVal) {
        this.resetCredential()
        this.showKeyDialog = true
      } else {
        this.close()
      }
    },
    updatingKeys: function(newVal) {
      if (newVal) {
        this.appId = this.updatingKeys.appId
      }
    }
  },
  methods: {
    close() {
      this.showKeyDialog = false
      this.authType = ''
      this.$emit('update:visible', false)
    },
    handleCreateOrUpdate(credential) {
      const reqPromise = createKey(this.updatingKeys.appId, this.authType)
      const msg = '创建AK/SK成功'
      reqPromise.then(() => {
        this.$message.success(msg)
        this.$emit('created')
        this.close()
      })
    },
    resetCredential() {
      this.credential = _.cloneDeep(this.updatingKeys)
    },
    newCredential() {
      const credential = {
        appId: '',
        authorizationGrantTypes: []
      }
      return credential
    }
  }
}

import _ from 'lodash'
</script>

<style scoped>

</style>
