<template>
  <el-dialog title="创建凭据" :visible.sync="showDialog" :before-close="beforeClose">
    <el-form ref="form" :rules="rules" :model="credential" status-icon>
      <el-form-item label="Key" prop="key" required>
        <el-input v-model="credential.key" />
      </el-form-item>
      <el-form-item label="存储类型" prop="type">
        <el-select v-model="credential.type" placeholder="请选择存储类型">
          <el-option :label="storageType" :value="storageType" />
        </el-select>
      </el-form-item>

      <!-- filesystem -->
      <el-form-item v-if="credential.type === STORAGE_TYPE_FILESYSTEM" label="存储路径" prop="path">
        <el-input v-model="credential.path" placeholder="请输入缓存路径，默认为data/store" />
      </el-form-item>

      <!-- s3 and inner-cos -->
      <el-form-item v-if="credential.type === STORAGE_TYPE_S3" label="AccessKey" prop="accessKey" required>
        <el-input v-model="credential.accessKey" />
      </el-form-item>

      <el-form-item v-if="credential.type === STORAGE_TYPE_INNER_COS" label="SecretId" prop="secretId" required>
        <el-input v-model="credential.secretId" />
      </el-form-item>
      <el-form-item
        v-if="isObjectStorage(credential.type)"
        label="SecretKey"
        prop="secretKey"
        required
      >
        <el-input v-model="credential.secretKey" />
      </el-form-item>
      <el-form-item
        v-if="isObjectStorage(credential.type)"
        label="Bucket"
        prop="bucket"
        required
      >
        <el-input v-model="credential.bucket" />
      </el-form-item>
      <el-form-item
        v-if="isObjectStorage(credential.type)"
        required
        label="Region"
        prop="region"
      >
        <el-input v-model="credential.region" />
      </el-form-item>
      <el-form-item v-if="credential.type === STORAGE_TYPE_S3" label="Endpoint" prop="endpoint" required>
        <el-input v-model="credential.endpoint" />
      </el-form-item>
      <el-form-item v-if="credential.type === STORAGE_TYPE_INNER_COS" label="公开类型" prop="public">
        <el-switch v-model="credential.public" />
      </el-form-item>

      <!-- hdfs -->
      <el-form-item v-if="credential.type === STORAGE_TYPE_HDFS" label="集群模式" prop="clusterMode">
        <el-switch v-model="credential.clusterMode" />
      </el-form-item>
      <el-form-item
        v-if="credential.type === STORAGE_TYPE_HDFS && credential.clusterMode"
        label="集群名"
        prop="clusterName"
        required
      >
        <el-input v-model="credential.clusterName" />
      </el-form-item>
      <el-form-item v-if="credential.type === STORAGE_TYPE_HDFS" label="Url" prop="url" required>
        <el-input v-model="credential.url" placeholder="请输入Url，比如: hdfs://hdfs.example.com:9000" />
      </el-form-item>
      <el-form-item v-if="credential.type === STORAGE_TYPE_HDFS" label="用户名" prop="user" required>
        <el-input v-model="credential.user" />
      </el-form-item>
      <el-form-item v-if="credential.type === STORAGE_TYPE_HDFS" label="工作目录" prop="workingDirectory" required>
        <el-input v-model="credential.workingDirectory" placeholder="请输入工作目录，比如/example" />
      </el-form-item>

      <!-- upload config -->
      <el-form-item label="上传路径" prop="upload.location">
        <el-input v-model="credential.upload.location" placeholder="请输入文件上传路径，默认为系统临时文件目录" />
      </el-form-item>

      <!-- cache config -->
      <el-form-item label="开启缓存" prop="cache.enabled">
        <el-switch v-model="credential.cache.enabled" />
      </el-form-item>
      <el-form-item v-if="credential.cache.enabled" label="缓存路径" prop="cache.path">
        <el-input v-model="credential.cache.path" placeholder="请输入缓存路径，默认为data/cached" />
      </el-form-item>
      <el-form-item v-if="credential.cache.enabled" label="优先加载缓存" prop="cache.loadCacheFirst">
        <el-switch v-model="credential.cache.loadCacheFirst" />
      </el-form-item>
      <el-form-item v-if="credential.cache.enabled" label="缓存天数" prop="cache.expireDays">
        <el-input-number v-model="credential.cache.expireDays" controls-position="right" :min="0" :max="30" />
      </el-form-item>
    </el-form>
    <div slot="footer">
      <el-button @click="beforeClose">取 消</el-button>
      <el-button type="primary" @click="handleCreate(credential)">确 定</el-button>
    </div>
  </el-dialog>
</template>

<script>
import {
  createCredential,
  STORAGE_TYPE_FILESYSTEM,
  STORAGE_TYPE_HDFS,
  STORAGE_TYPE_INNER_COS,
  STORAGE_TYPE_S3
} from '@/api/storage'

export default {
  name: 'CreateCredentialDialog',
  props: {
    visible: Boolean,
    storageType: {
      type: String,
      default: STORAGE_TYPE_INNER_COS
    }
  },
  data() {
    return {
      STORAGE_TYPE_FILESYSTEM: STORAGE_TYPE_FILESYSTEM,
      STORAGE_TYPE_INNER_COS: STORAGE_TYPE_INNER_COS,
      STORAGE_TYPE_HDFS: STORAGE_TYPE_HDFS,
      STORAGE_TYPE_S3: STORAGE_TYPE_S3,
      rules: {
        'upload.location': [
          { validator: this.validateFilePathAllowEmpty, trigger: 'change' }
        ],
        'cache.path': [
          { validator: this.validateRelativeFilePath, trigger: 'change' }
        ],
        path: [
          { validator: this.validateRelativeFilePath, trigger: 'change' }
        ],
        url: [
          { validator: this.validateHdfsUrl, trigger: 'change' }
        ],
        workingDirectory: [
          { validator: this.validateFilePath, trigger: 'change' }
        ]
      },
      showDialog: this.visible,
      credential: {}
    }
  },
  watch: {
    visible: function(newVal) {
      if (newVal) {
        this.showDialog = true
      } else {
        this.close()
      }
    },
    'credential.type': function(newVal) {
      this.credential = this.newCredential(newVal)
      this.$refs['form'].clearValidate()
    }
  },
  created() {
    this.credential = this.newCredential()
  },
  methods: {
    validateFilePath(rule, value, callback) {
      this.validateUri(rule, value, callback, /^((\/[\w-]+)+)$/)
    },
    validateFilePathAllowEmpty(rule, value, callback) {
      this.validateUri(rule, value, callback, /^((\/[\w-]+)+)$/, true)
    },
    validateRelativeFilePath(rule, value, callback) {
      this.validateUri(rule, value, callback, /^[\w-]+(\/[\w-]+)*$/)
    },
    validateHdfsUrl(rule, value, callback) {
      this.validateUri(rule, value, callback, /^hdfs:\/\/[\w-]+(\.[\w-]+)*(:\d{1,5})?$/)
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
    beforeClose() {
      this.$confirm('确认关闭？')
        .then(_ => {
          this.close()
        }).catch(_ => {
        })
    },
    close() {
      this.showDialog = false
      this.$emit('update:visible', false)
      this.credential = this.newCredential()
      this.$refs['form'].clearValidate()
    },
    handleCreate(credential) {
      this.$refs['form'].validate((valid) => {
        if (valid) {
          createCredential(credential).then(res => {
            this.$message.success('创建凭据成功')
            this.$emit('created', res.data)
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
          location: ''
        }
      }
      switch (type) {
        case STORAGE_TYPE_FILESYSTEM: {
          credential.path = 'data/store'
          break
        }
        case STORAGE_TYPE_HDFS: {
          credential.workingDirectory = '/'
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
          }
          credential.secretKey = ''
          credential.bucket = ''
        }
      }
      return credential
    }
  }
}
</script>

<style scoped>

</style>
