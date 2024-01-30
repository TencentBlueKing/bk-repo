<template>
  <el-dialog :title="createMode ? '创建凭据' : '更新凭据'" :visible.sync="showDialog" :before-close="close">
    <el-form ref="form" :rules="rules" :model="credential" status-icon>
      <el-form-item label="Key" prop="key" required>
        <el-input v-model="credential.key" :disabled="!createMode" />
      </el-form-item>
      <el-form-item label="存储类型" prop="type">
        <el-select v-model="credential.type" placeholder="请选择存储类型" :disabled="!createMode">
          <el-option :label="storageType" :value="storageType" />
        </el-select>
        <el-tooltip effect="dark" content="仅支持创建和和当前系统同类型的存储凭据" placement="top-start">
          <svg-icon style="width: 20px; height: 20px; margin-left: 5px; padding-top: 3px" icon-class="question" />
        </el-tooltip>
      </el-form-item>

      <!-- filesystem -->
      <el-form-item v-if="credential.type === STORAGE_TYPE_FILESYSTEM" label="存储路径" prop="path">
        <el-input v-model="credential.path" placeholder="请输入缓存路径，默认为data/store" :disabled="!createMode" />
      </el-form-item>

      <!-- s3 and inner-cos -->
      <el-form-item v-if="credential.type === STORAGE_TYPE_S3" label="AccessKey" prop="accessKey" required>
        <el-input v-model="credential.accessKey" :disabled="!createMode" />
      </el-form-item>

      <el-form-item v-if="credential.type === STORAGE_TYPE_INNER_COS" label="SecretId" prop="secretId" required>
        <el-input v-model="credential.secretId" :disabled="!createMode" />
      </el-form-item>
      <el-form-item
        v-if="isObjectStorage(credential.type)"
        label="SecretKey"
        prop="secretKey"
        required
      >
        <el-input v-model="credential.secretKey" :disabled="!createMode" />
      </el-form-item>
      <el-form-item
        v-if="isObjectStorage(credential.type)"
        label="Bucket"
        prop="bucket"
        required
      >
        <el-input v-model="credential.bucket" :disabled="!createMode" />
      </el-form-item>
      <el-form-item
        v-if="isObjectStorage(credential.type)"
        required
        label="Region"
        prop="region"
      >
        <el-input v-model="credential.region" :disabled="!createMode" />
      </el-form-item>
      <el-form-item v-if="credential.type === STORAGE_TYPE_INNER_COS" label="CmdId" prop="cmdId">
        <el-input v-model.number="credential.cmdId" type="number" />
      </el-form-item>
      <el-form-item v-if="credential.type === STORAGE_TYPE_INNER_COS" label="ModId" prop="modId">
        <el-input v-model.number="credential.modId" type="number" />
      </el-form-item>
      <el-form-item v-if="credential.type === STORAGE_TYPE_INNER_COS" label="慢日志速度阈值" prop="slowLogSpeed" required>
        <el-input-number v-model="credential.slowLogSpeed" controls-position="right" :min="0" :max="104857600" />
        <el-tooltip effect="dark" content="上传速度小于该值将输出慢日志，小于等于0时将关闭日志，单位B/s" placement="top-start">
          <svg-icon style="width: 20px; height: 20px; margin-left: 5px; padding-top: 3px" icon-class="question" />
        </el-tooltip>
      </el-form-item>
      <el-form-item v-if="credential.type === STORAGE_TYPE_INNER_COS" label="慢日志时间阈值" prop="slowLogTimeInMillis" required>
        <el-input-number v-model="credential.slowLogTimeInMillis" controls-position="right" :min="0" :max="3600000" />
        <el-tooltip effect="dark" content="上传时间大于该值将输出慢日志，小于等于0时将关闭日志，单位ms" placement="top-start">
          <svg-icon style="width: 20px; height: 20px; margin-left: 5px; padding-top: 3px" icon-class="question" />
        </el-tooltip>
      </el-form-item>
      <el-form-item v-if="credential.type === STORAGE_TYPE_INNER_COS" label="线程数" prop="download.workers" required>
        <el-input-number v-model="credential.download.workers" controls-position="right" :min="0" :max="1024" />
        <el-tooltip
          effect="dark"
          content="分片下载并发数，增加并发以增加带宽利用率（因为可能存在单连接限速的情况），但是数值不是越大越好，当下行带宽打满，再增加并发数，反而导致单连接的速度下降。"
          placement="top-start"
        >
          <svg-icon style="width: 20px; height: 20px; margin-left: 5px; padding-top: 3px" icon-class="question" />
        </el-tooltip>
      </el-form-item>
      <el-form-item
        v-if="credential.type === STORAGE_TYPE_INNER_COS"
        label="任务间隔"
        prop="download.taskInterval"
        required
      >
        <el-input-number v-model="credential.download.taskInterval" controls-position="right" :min="0" :max="1000" />
        <el-tooltip
          effect="dark"
          content="分片下载任务间隔时间，用于保证大文件分块下载不占满工作线程，以保证新进来的连接也能开始下载，适当增大间隔可让不同制品下载任务占用的带宽更均匀，单位ms"
          placement="top-start"
        >
          <svg-icon style="width: 20px; height: 20px; margin-left: 5px; padding-top: 3px" icon-class="question" />
        </el-tooltip>
      </el-form-item>
      <el-form-item
        v-if="credential.type === STORAGE_TYPE_INNER_COS"
        label="禁用分片下载时间阈值"
        prop="download.downloadTimeHighWaterMark"
        required
      >
        <el-input-number
          v-model="credential.download.downloadTimeHighWaterMark"
          controls-position="right"
          :min="1000"
          :max="60000"
        />
        <el-tooltip
          effect="dark"
          content="分片下载时，单片下载时间最大限制，超过后将切换为使用单连接下载，单位ms"
          placement="top-start"
        >
          <svg-icon style="width: 20px; height: 20px; margin-left: 5px; padding-top: 3px" icon-class="question" />
        </el-tooltip>
      </el-form-item>
      <el-form-item
        v-if="credential.type === STORAGE_TYPE_INNER_COS"
        label="恢复分片下载时间阈值"
        prop="download.downloadTimeLowWaterMark"
        required
      >
        <el-input-number
          v-model="credential.download.downloadTimeLowWaterMark"
          controls-position="right"
          :min="1000"
          :max="60000"
        />
        <el-tooltip
          effect="dark"
          content="切换为单连接下载后，如果单片下载时间小于该值，将恢复多线程分片下载，单位ms"
          placement="top-start"
        >
          <svg-icon style="width: 20px; height: 20px; margin-left: 5px; padding-top: 3px" icon-class="question" />
        </el-tooltip>
      </el-form-item>
      <el-form-item
        v-if="credential.type === STORAGE_TYPE_INNER_COS"
        label="下载分块大小"
        prop="download.minimumPartSize"
        required
      >
        <el-input-number
          v-model="credential.download.minimumPartSize"
          controls-position="right"
          :min="1"
        />
        <el-tooltip
          effect="dark"
          content="单位Mb"
          placement="top-start"
        >
          <svg-icon style="width: 20px; height: 20px; margin-left: 5px; padding-top: 3px" icon-class="question" />
        </el-tooltip>
      </el-form-item>
      <el-form-item
        v-if="credential.type === STORAGE_TYPE_INNER_COS"
        label="下载分片数量"
        prop="download.maxDownloadParts"
        required
      >
        <el-input-number
          v-model="credential.download.maxDownloadParts"
          controls-position="right"
          :min="1"
        />
        <svg-icon style="width: 20px; height: 20px; margin-left: 5px; padding-top: 3px" icon-class="question" />
      </el-form-item>
      <el-form-item
        v-if="credential.type === STORAGE_TYPE_INNER_COS"
        label="QPS"
        prop="download.qps"
        required
      >
        <el-input-number
          v-model="credential.download.qps"
          controls-position="right"
          :min="1"
        />
        <svg-icon style="width: 20px; height: 20px; margin-left: 5px; padding-top: 3px" icon-class="question" />
      </el-form-item>
      <el-form-item v-if="credential.type === STORAGE_TYPE_S3" label="Endpoint" prop="endpoint" required>
        <el-input v-model="credential.endpoint" :disabled="!createMode" />
      </el-form-item>
      <el-form-item v-if="credential.type === STORAGE_TYPE_INNER_COS" label="公开类型" prop="public">
        <el-switch v-model="credential.public" :disabled="!createMode" />
      </el-form-item>

      <!-- upload config -->
      <el-form-item label="上传路径" prop="upload.location">
        <el-input v-model="credential.upload.location" placeholder="请输入文件上传路径，默认为系统临时文件目录" :disabled="!createMode" />
      </el-form-item>
      <el-form-item label="本地存储路径" prop="upload.localPath">
        <el-input v-model="credential.upload.localPath" placeholder="请输入文件本地存储，默认为系统临时文件目录" />
      </el-form-item>

      <!-- cache config -->
      <el-form-item label="开启缓存" prop="cache.enabled">
        <el-switch v-model="credential.cache.enabled" :disabled="!createMode" />
      </el-form-item>
      <el-form-item v-if="credential.cache.enabled" label="缓存路径" prop="cache.path">
        <el-input v-model="credential.cache.path" placeholder="请输入缓存路径，默认为data/cached" :disabled="!createMode" />
      </el-form-item>
      <el-form-item v-if="credential.cache.enabled" label="优先加载缓存" prop="cache.loadCacheFirst">
        <el-switch v-model="credential.cache.loadCacheFirst" />
      </el-form-item>
      <el-form-item v-if="credential.cache.enabled" label="缓存天数（已废弃）" prop="cache.expireDays">
        <el-input-number v-model="credential.cache.expireDays" controls-position="right" :min="0" :max="30" />
      </el-form-item>
      <el-form-item v-if="credential.cache.enabled" label="缓存时间(秒)" prop="cache.expireDuration">
        <el-input-number v-model="credential.cache.expireDuration" controls-position="right" :min="0" />
      </el-form-item>
    </el-form>
    <div slot="footer">
      <el-button @click="close">取 消</el-button>
      <el-button type="primary" @click="handleCreateOrUpdate(credential)">确 定</el-button>
    </div>
  </el-dialog>
</template>

<script>
import {
  createCredential,
  STORAGE_TYPE_FILESYSTEM,
  STORAGE_TYPE_INNER_COS,
  STORAGE_TYPE_S3,
  updateCredential
} from '@/api/storage'
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
    createMode: Boolean,
    /**
     * 当前默认存储凭据的类型，目前仅支持新增和默认凭据同类型的存储凭据
     */
    storageType: {
      type: String,
      default: STORAGE_TYPE_INNER_COS
    }
  },
  data() {
    return {
      STORAGE_TYPE_FILESYSTEM: STORAGE_TYPE_FILESYSTEM,
      STORAGE_TYPE_INNER_COS: STORAGE_TYPE_INNER_COS,
      STORAGE_TYPE_S3: STORAGE_TYPE_S3,
      rules: {
        'upload.location': [
          { validator: this.validateFilePathAllowEmpty, trigger: 'change' }
        ],
        'upload.localPath': [
          { validator: this.validateFilePathAllowEmpty, trigger: 'change' }
        ],
        'cache.path': [
          { validator: this.validateFilePath, trigger: 'change' }
        ],
        path: [
          { validator: this.validateFilePath, trigger: 'change' }
        ],
        url: [
          { validator: this.validateHdfsUrl, trigger: 'change' }
        ],
        workingDirectory: [
          { validator: this.validateFilePath, trigger: 'change' }
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
    validateFilePath(rule, value, callback) {
      this.validateUri(rule, value, callback, FILE_PATH_REGEX)
    },
    validateFilePathAllowEmpty(rule, value, callback) {
      this.validateUri(rule, value, callback, FILE_PATH_REGEX, this.createMode)
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
            reqPromise = updateCredential(credential.key, credential, credential.default)
            msg = '更新凭据成功'
            eventName = 'updated'
          }

          // 发起请求
          reqPromise.then(res => {
            this.$message.success(msg)
            this.$emit(eventName, credential.default ? credential : res.data)
            this.close()
          })
        } else {
          return false
        }
      })
    },
    isObjectStorage(type) {
      return type === STORAGE_TYPE_S3 || type === STORAGE_TYPE_INNER_COS
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
    newCredential(type = this.storageType) {
      const credential = {
        type: type,
        cache: {
          enabled: true,
          path: 'data/cached',
          loadCacheFirst: true,
          expireDays: 0
        },
        upload: {
          location: '',
          localPath: ''
        }
      }
      switch (type) {
        case STORAGE_TYPE_FILESYSTEM: {
          credential.path = 'data/store'
          break
        }
        default: {
          credential.region = ''
          if (type === STORAGE_TYPE_S3) {
            credential.accessKey = ''
            credential.endpoint = ''
          } else {
            // INNER-COS类型
            credential.secretId = ''
            credential.public = false
            credential.slowLogSpeed = 1024 * 1024
            credential.slowLogTimeInMillis = 30 * 1000
            credential.download = {}
            credential.download.workers = 0
            credential.download.downloadTimeHighWaterMark = 25 * 1000
            credential.download.downloadTimeLowWaterMark = 5 * 1000
            credential.download.taskInterval = 10
            credential.download.minimumPartSize = 10
            credential.download.maxDownloadParts = 10 * 1000
            credential.download.qps = 10
          }
          credential.secretKey = ''
          credential.bucket = ''
        }
      }
      return credential
    }
  }
}

import _ from 'lodash'
import { FILE_PATH_REGEX } from '@/utils/validate'
</script>

<style scoped>

</style>
