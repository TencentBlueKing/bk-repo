<template>
  <el-dialog
    :title="createMode ? '新增客户端版本配置' : '编辑客户端版本配置'"
    :visible.sync="showDialog"
    :before-close="close"
    width="640px"
  >
    <el-form ref="form" :model="form" :rules="rules" label-width="120px" status-icon>
      <el-form-item label="制品产品线" prop="productId">
        <el-input :value="fixedProductLabel" disabled size="small" />
      </el-form-item>
      <el-form-item label="平台" prop="platform">
        <el-input :value="fixedPlatformLabel" disabled size="small" />
      </el-form-item>
      <el-form-item label="架构" prop="arch">
        <el-input :value="form.arch" disabled size="small" />
      </el-form-item>
      <el-form-item v-if="!userOnly" label="目标用户">
        <el-input value="全员默认" disabled size="small" />
      </el-form-item>
      <el-form-item v-else label="目标用户" prop="targetUserId">
        <el-input
          v-model="form.targetUserId"
          placeholder="必填，bk 用户名"
          size="small"
          :disabled="!createMode"
        />
      </el-form-item>
      <el-form-item label="最低版本(强升)" prop="minVersion">
        <el-input v-model="form.minVersion" placeholder="低于此版本强制升级，可空" size="small" />
      </el-form-item>
      <el-form-item label="最新版本" prop="latestVersion">
        <el-input v-model="form.latestVersion" size="small" />
      </el-form-item>
      <el-form-item label="下载地址" prop="downloadUrl">
        <el-input v-model="form.downloadUrl" type="textarea" :rows="2" size="small" />
      </el-form-item>
      <el-form-item label="更新说明" prop="releaseNotes">
        <el-input v-model="form.releaseNotes" type="textarea" :rows="2" size="small" />
      </el-form-item>
      <el-form-item label="启用" prop="enabled">
        <el-switch v-model="form.enabled" />
      </el-form-item>
    </el-form>
    <div slot="footer">
      <el-button @click="close">取 消</el-button>
      <el-button type="primary" :loading="submitting" @click="submitForm">确 定</el-button>
    </div>
  </el-dialog>
</template>

<script>
import { upsertClientVersionConfig } from '@/api/clientVersionConfig'
import { productLabel, platformLabel } from '../productPresets'

function emptyForm() {
  return {
    id: null,
    productId: '',
    platform: '',
    arch: '',
    targetUserId: '',
    minVersion: '',
    latestVersion: '',
    downloadUrl: '',
    releaseNotes: '',
    enabled: true
  }
}

export default {
  name: 'ClientVersionConfigDialog',
  props: {
    createMode: {
      type: Boolean,
      default: true
    },
    visible: {
      type: Boolean,
      default: false
    },
    record: {
      type: Object,
      default: () => ({})
    },
    fixedProductId: {
      type: String,
      default: ''
    },
    fixedPlatform: {
      type: String,
      default: ''
    },
    fixedArch: {
      type: String,
      default: ''
    },
    userOnly: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      showDialog: this.visible,
      submitting: false,
      form: emptyForm(),
      rules: {
        productId: [{ required: true, message: '必填', trigger: 'blur' }],
        platform: [{ required: true, message: '必填', trigger: 'change' }],
        arch: [{ required: true, message: '必填', trigger: 'change' }],
        targetUserId: [],
        latestVersion: [{ required: true, message: '必填', trigger: 'blur' }],
        downloadUrl: [{ required: true, message: '必填', trigger: 'blur' }]
      }
    }
  },
  computed: {
    fixedProductLabel() {
      return productLabel(this.form.productId)
    },
    fixedPlatformLabel() {
      return platformLabel(this.form.platform)
    }
  },
  watch: {
    visible(val) {
      this.showDialog = val
      if (val) {
        this.syncUserRules()
        this.initForm()
      }
    },
    userOnly() {
      this.syncUserRules()
    },
    showDialog(val) {
      this.$emit('update:visible', val)
    }
  },
  methods: {
    syncUserRules() {
      this.rules.targetUserId = this.userOnly
        ? [{ required: true, message: '用户必填', trigger: 'blur' }]
        : []
    },
    initForm() {
      if (this.createMode) {
        this.form = emptyForm()
        this.form.productId = this.fixedProductId
        this.form.platform = this.fixedPlatform
        this.form.arch = this.fixedArch
      } else {
        const r = this.record || {}
        this.form = {
          id: r.id,
          productId: r.productId || this.fixedProductId,
          platform: (r.platform || this.fixedPlatform).trim().toLowerCase(),
          arch: r.arch || this.fixedArch,
          targetUserId: this.userOnly ? (r.targetUserId || '') : '',
          minVersion: r.minVersion || '',
          latestVersion: r.latestVersion || '',
          downloadUrl: r.downloadUrl || '',
          releaseNotes: r.releaseNotes || '',
          enabled: r.enabled !== false
        }
      }
      if (!this.userOnly) {
        this.form.targetUserId = ''
      }
      this.$nextTick(() => this.$refs.form && this.$refs.form.clearValidate())
    },
    close() {
      this.showDialog = false
    },
    buildPayload() {
      const f = this.form
      const payload = {
        productId: f.productId.trim(),
        platform: f.platform.trim(),
        arch: (f.arch || '').trim(),
        latestVersion: f.latestVersion.trim(),
        downloadUrl: f.downloadUrl.trim(),
        enabled: f.enabled
      }
      if (this.userOnly) {
        payload.targetUserId = (f.targetUserId || '').trim()
      }
      const minV = (f.minVersion || '').trim()
      if (minV) {
        payload.minVersion = minV
      }
      const notes = (f.releaseNotes || '').trim()
      if (notes) {
        payload.releaseNotes = notes
      }
      if (!this.createMode && f.id) {
        payload.id = f.id
      }
      return payload
    },
    submitForm() {
      this.$refs.form.validate(valid => {
        if (!valid) {
          return
        }
        this.submitting = true
        upsertClientVersionConfig(this.buildPayload())
          .then(() => {
            this.$message.success(this.createMode ? '创建成功' : '更新成功')
            this.$emit(this.createMode ? 'created' : 'updated')
            this.close()
          })
          .finally(() => {
            this.submitting = false
          })
      })
    }
  }
}
</script>
