<template>
  <el-dialog :title="createMode ? '创建账户' : '更新账号'" :visible.sync="showDialog" :before-close="close">
    <el-form ref="form" :rules="rules" :model="credential" status-icon>
      <el-form-item
        label="应用ID"
        prop="appId"
        :rules="[{ required: true, message: '应用ID不能为空'}]"
      >
        <el-input v-model="credential.appId" :disabled="!createMode" />
      </el-form-item>
      <el-form-item label="lock状态" prop="key">
        <el-switch
          v-model="credential.locked"
          active-color="#13ce66"
          inactive-color="#ff4949"
        />
      </el-form-item>
      <el-form-item label="认证授权方式" prop="authorizationGrantTypes">
        <el-select v-model="authType" multiple placeholder="请选择" style="width: 350px" :change="changeAuthType(authType)">
          <el-option
            v-for="item in authOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item
        label="账号主页地址"
        prop="homepageUrl"
        :rules="[{ required: authType.includes('AUTHORIZATION_CODE'), message: '地址不能为空'},
                 { validator: validateUrl, message: '请输入正确的地址', trigger: 'blur' }]"
      >
        <el-input v-model="credential.homepageUrl" />
      </el-form-item>
      <el-form-item
        label="回调地址"
        prop="redirectUri"
        :rules="[{ required: authType.includes('AUTHORIZATION_CODE'), message: '地址不能为空'},
                 { validator: validateUrl, message: '请输入正确的地址', trigger: 'blur' }]"
      >
        <el-input v-model="credential.redirectUri" />
      </el-form-item>
      <el-form-item label="账号图标地址" prop="avatarUrl">
        <el-input v-model="credential.avatarUrl" />
      </el-form-item>
      <el-form-item
        label="权限类型"
        prop="scope"
        :rules="[{ required: authType.includes('AUTHORIZATION_CODE') , message: '请选择权限类型'}]"
      >
        <el-select v-model="scopeType" multiple placeholder="请选择" style="width: 400px" clearable :change="changeScope(scopeType)">
          <el-option
            v-for="item in scopeOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="权限范围" />
      <el-form-item
        v-for="(item,index) in credential.scopeDesc"
        :key="index"
        prop="scopeDesc"
      >
        <span>操作类型：</span>
        <el-select v-model="item.operation" placeholder="请选择" style="width: 130px" clearable>
          <el-option
            v-for="operationType in operationTypeOptions"
            :key="operationType.value"
            :label="operationType.label"
            :value="operationType.value"
          />
        </el-select>
        <span style="margin-left: 10px">字段名：</span>
        <el-input
          v-model="item.field"
          placeholder="请输入"
          style="height: 40px; width: 150px"
          min="0"
        />
        <span v-if="item.operation !== 'NULL'&& item.operation !== 'NOT_NULL'" style="margin-left: 10px">值：</span>
        <el-input
          v-if="item.operation !== 'BEFORE'&& item.operation !== 'AFTER' && item.operation !== 'NULL'&& item.operation !== 'NOT_NULL'"
          v-model="item.jsonObj"
          :placeholder="item.operation === 'IN' || item.operation === 'NIN' ? '请输入数据按逗号分割（如1,2,3）': '请输入'"
          style="height: 40px ; width: 300px;"
          min="0"
          :type="item.operation === 'LTE' || item.operation === 'LT'|| item.operation === 'GTE' || item.operation === 'TE'? 'number' : 'text'"
          @input="updateInput()"
        />
        <el-date-picker
          v-if="item.operation === 'BEFORE'||item.operation === 'AFTER'"
          v-model="item.jsonObj"
          type="datetime"
          style="height: 40px ; width: 300px;"
          placeholder="选择日期时间"
        />
        <i
          class="el-icon-circle-close"
          style="color: red"
          @click.prevent="removeDomain(item)"
        />
        <i
          v-if="index == credential.scopeDesc.length - 1"
          class="el-icon-circle-plus-outline"
          style="margin: 0px 20px"
          @click.prevent="addDomain()"
        />
      </el-form-item>
      <el-form-item label="描述" prop="description">
        <el-input v-model="credential.description" />
      </el-form-item>
    </el-form>
    <div slot="footer">
      <el-button @click="close">取 消</el-button>
      <el-button type="primary" @click="handleCreateOrUpdate()">确 定</el-button>
    </div>
  </el-dialog>
</template>

<script>
import { create, update } from '@/api/account'
import _ from 'lodash'
import { URL_REGEX } from '@/utils/validate'

export default {
  name: 'CreateOrUpdateAccountDialog',
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
      showDialog: this.visible,
      credential: this.newCredential(),
      authType: [],
      scopeType: [],
      authOptions: [{
        value: 'AUTHORIZATION_CODE',
        label: 'AUTHORIZATION_CODE'
      }, {
        value: 'PLATFORM',
        label: 'PLATFORM'
      }],
      scopeOptions: [{
        value: 'SYSTEM',
        label: 'SYSTEM'
      }, {
        value: 'PROJECT',
        label: 'PROJECT'
      }, {
        value: 'REPO',
        label: 'REPO'
      }, {
        value: 'NODE',
        label: 'NODE'
      }],
      operationTypeOptions: [{
        value: 'EQ',
        label: 'EQ'
      }, {
        value: 'NE',
        label: 'NE'
      }, {
        value: 'LTE',
        label: 'LTE'
      }, {
        value: 'LT',
        label: 'LT'
      }, {
        value: 'GTE',
        label: 'GTE'
      }, {
        value: 'GT',
        label: 'GT'
      }, {
        value: 'BEFORE',
        label: 'BEFORE'
      }, {
        value: 'AFTER',
        label: 'AFTER'
      }, {
        value: 'IN',
        label: 'IN'
      }, {
        value: 'NIN',
        label: 'NIN'
      }, {
        value: 'PREFIX',
        label: 'PREFIX'
      }, {
        value: 'SUFFIX',
        label: 'SUFFIX'
      }, {
        value: 'MATCH',
        label: 'MATCH'
      }, {
        value: 'MATCH_I',
        label: 'MATCH_I'
      }, {
        value: 'REGEX',
        label: 'REGEX'
      }, {
        value: 'NULL',
        label: 'NULL'
      }, {
        value: 'NOT_NULL',
        label: 'NOT_NULL'
      }],
      rules: {
        avatarUrl: [
          { validator: this.validateUrl, message: '请输入正确的地址', trigger: 'blur' }
        ]
      }
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
    updatingCredentials: function(newVal) {
      if (newVal) {
        this.authType = newVal.authorizationGrantTypes
        this.scopeType = newVal.scope
      } else {
        this.authType = []
        this.scopeType = []
      }
    }
  },
  methods: {
    changeScope(scopeType) {
      if (scopeType !== null && scopeType.length === 0) {
        this.credential.scope = null
      } else {
        this.credential.scope = scopeType
      }
    },
    changeAuthType(authType) {
      if (authType.length === 0) {
        this.credential.authorizationGrantTypes = ['PLATFORM']
      } else {
        this.credential.authorizationGrantTypes = authType
      }
    },
    buildStr(key, value) {
      let str = value[0]
      for (let i = 1; i < value.length; i++) {
        str = str + ',' + value[i]
      }
      return str
    },
    updateInput() {
      this.$forceUpdate()
    },
    addDomain() {
      this.credential.scopeDesc.push({
        field: '',
        value: '',
        operation: '',
        jsonObj: ''
      })
    },
    removeDomain(item) {
      const index = this.credential.scopeDesc.indexOf(item)
      if (index !== -1 && this.credential.scopeDesc.length !== 1) {
        this.credential.scopeDesc.splice(index, 1)
        this.$refs['form'].validateField(['scopeDesc'], null)
      }
    },
    validateUrl(rule, value, callback) {
      if (value) {
        this.validateUri(rule, value, callback, URL_REGEX)
      } else {
        callback()
      }
    },
    validateUri(rule, value, callback, regex, allowEmpty = false) {
      if (allowEmpty && (!value || value.length === 0)) {
        callback()
      }
      if (regex.test(value)) {
        callback()
      } else {
        callback(new Error('路径格式错误'))
      }
    },
    close() {
      this.showDialog = false
      this.authType = []
      this.scopeType = []
      this.$refs['form'].resetFields()
      this.$emit('update:visible', false)
    },
    handleCreateOrUpdate() {
      this.$refs['form'].validate((valid) => {
        if (valid) {
          const account = this.credential
          if (account.scopeDesc.length === 1 && account.scopeDesc[0].operation === '') {
            account.scopeDesc = null
          } else {
            for (let i = 0; i < account.scopeDesc.length; i++) {
              if (account.scopeDesc[i].operation === 'IN' || account.scopeDesc[i].operation === 'NIN') {
                account.scopeDesc[i].value = account.scopeDesc[i].jsonObj.split(',')
              } else {
                account.scopeDesc[i].value = account.scopeDesc[i].jsonObj
              }
              if (account.scopeDesc[i].operation === 'NULL' || account.scopeDesc[i].operation === 'NOT_NULL') {
                account.scopeDesc[i].value = ''
              }
            }
          }
          // 根据是否为创建模式发起不同请求
          let reqPromise
          let msg
          let eventName
          if (this.createMode) {
            reqPromise = create(account)
            msg = '创建账号成功'
            eventName = 'created'
          } else {
            reqPromise = update(account)
            msg = '更新账号成功'
            eventName = 'updated'
          }
          // 发起请求
          reqPromise.then(res => {
            this.$message.success(msg)
            if (!this.createMode) {
              this.$emit(eventName, account)
            } else {
              for (let i = 0; i < res.data.credentials.length; i++) {
                res.data.credentials[i].secretKey = res.data.credentials[i].secretKey.substring(0, 8) + '*****'
              }
              this.$emit(eventName, res.data)
            }
            this.close()
          })
        } else {
          return false
        }
      })
    },
    resetCredential() {
      if (this.createMode) {
        this.credential = this.newCredential()
      } else {
        this.credential = _.cloneDeep(this.updatingCredentials)
        if (this.credential.scopeDesc !== null && this.credential.scopeDesc.length > 0) {
          for (let i = 0; i < this.credential.scopeDesc.length; i++) {
            if (this.credential.scopeDesc[i].operation === 'IN' || this.credential.scopeDesc[i].operation === 'NIN') {
              this.credential.scopeDesc[i].jsonObj = this.buildStr(this.credential.scopeDesc[i].field, this.credential.scopeDesc[i].value)
            } else {
              this.credential.scopeDesc[i].jsonObj = this.credential.scopeDesc[i].value
            }
          }
        } else {
          this.credential.scopeDesc = []
          this.addDomain()
        }
        this.scopeType = this.credential.scope
        this.authType = this.credential.authorizationGrantTypes
      }
      this.$nextTick(() => {
        this.$refs['form'].clearValidate()
      })
    },
    newCredential() {
      const credential = {
        appId: '',
        locked: false,
        authorizationGrantTypes: [],
        homepageUrl: null,
        redirectUri: null,
        avatarUrl: null,
        scope: null,
        scopeDesc: [{ field: '', value: '', operation: '', jsonObj: '' }],
        description: null
      }
      return credential
    }
  }
}</script>

<style scoped>

</style>
