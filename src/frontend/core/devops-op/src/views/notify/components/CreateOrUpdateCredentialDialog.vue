<template>
  <el-dialog :title="createMode ? '创建凭据' : '更新凭据'" :visible.sync="showDialog" :before-close="close">
    <el-form ref="form" :rules="rules" :model="credential" status-icon>
      <el-form-item label="名称" prop="name" required>
        <el-input v-model="credential.name" :disabled="!createMode" />
      </el-form-item>
      <el-form-item label="类型" prop="type" required>
        <el-select v-model="credential.type" placeholder="通知渠道类型" :disabled="!createMode">
          <el-option v-for="type in types" :key="type.value" :label="type.label" :value="type.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="是否默认" prop="default" required>
        <el-switch v-model="credential.default" />
      </el-form-item>
      <!-- wework-bot -->
      <el-form-item v-if="credential.type === CREDENTIAL_TYPE_WEWORK_BOT" label="key" prop="key" required>
        <el-input v-model="credential.key" placeholder="请输入Key，可从企业微信机器人Webhook中获取" />
      </el-form-item>
    </el-form>
    <div slot="footer">
      <el-button @click="close">取 消</el-button>
      <el-button type="primary" @click="handleCreateOrUpdate(credential)">确 定</el-button>
    </div>
  </el-dialog>
</template>

<script>
import { CREDENTIAL_TYPE_WEWORK_BOT, notifyCredentialTypes } from '@/views/notify/components/credential-type'
import { UUID_REGEX } from '@/utils/validate'
import _ from 'lodash'
import { createCredential, updateCredential } from '@/api/notify'
export default {
  name: 'CreateOrUpdateCredentialDialog',
  props: {
    visible: Boolean,
    /**
     * 仅在更新模式时有值
     */
    updatingCredentials: {
      type: Object,
      default: undefined
    },
    /**
     * 是否为创建模式，true时为创建对象，false时为更新对象
     */
    createMode: Boolean
  },
  data() {
    return {
      types: notifyCredentialTypes,
      CREDENTIAL_TYPE_WEWORK_BOT: CREDENTIAL_TYPE_WEWORK_BOT,
      rules: {
        'key': [
          { validator: this.validateUuid, trigger: 'change' }
        ]
      },
      showDialog: this.visible,
      credential: this.newCredential()
    }
  },
  watch: {
    visible: function(newVal) {
      if (newVal) {
        this.resetCredential()
        this.showDialog = true
      } else {
        this.close()
      }
    },
    'credential.type': function(newVal) {
      this.resetCredential(newVal)
    }
  },
  methods: {
    validateUuid(rule, value, callback) {
      if (UUID_REGEX.test(value)) {
        callback()
      } else {
        callback(new Error('UUID格式错误'))
      }
    },
    close() {
      this.showDialog = false
      this.$emit('update:visible', false)
    },
    handleCreateOrUpdate(credential) {
      this.$refs['form'].validate((valid) => {
        if (valid) {
          // 根据是否为创建模式发起不同请求
          let reqPromise
          let msg
          let eventName
          if (this.createMode) {
            reqPromise = createCredential(credential)
            msg = '创建凭据成功'
            eventName = 'created'
          } else {
            reqPromise = updateCredential(credential.name, credential)
            msg = '更新凭据成功'
            eventName = 'updated'
          }

          // 发起请求
          reqPromise.then(res => {
            this.$message.success(msg)
            this.$emit(eventName, res.data)
            this.close()
          })
        } else {
          return false
        }
      })
    },
    resetCredential(type) {
      if (this.createMode) {
        this.credential = this.newCredential(type)
      } else {
        this.credential = _.cloneDeep(this.updatingCredentials)
      }
      this.$nextTick(() => {
        this.$refs['form'].clearValidate()
      })
    },
    newCredential(type = CREDENTIAL_TYPE_WEWORK_BOT) {
      const credential = {
        type: type,
        name: '',
        default: false
      }
      if (type === CREDENTIAL_TYPE_WEWORK_BOT) {
        credential.key = ''
      }
      return credential
    }
  }
}
</script>

<style scoped>

</style>
