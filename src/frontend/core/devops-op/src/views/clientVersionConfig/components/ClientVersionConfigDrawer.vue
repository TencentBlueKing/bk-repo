<template>
  <el-drawer
    :title="createMode ? '新增客户端版本配置' : '编辑客户端版本配置'"
    :visible.sync="showDrawer"
    :before-close="close"
    size="520px"
    direction="rtl"
  >
    <div class="drawer-body">
      <el-form ref="form" :model="form" :rules="rules" label-width="120px" status-icon>
        <el-form-item label="制品产品线" prop="productId">
          <el-input
            v-model="form.productId"
            placeholder="如 bkrepo-cli"
            size="small"
            :disabled="!createMode"
          />
        </el-form-item>
        <el-form-item label="平台" prop="platform">
          <el-select
            v-model="form.platform"
            placeholder="请选择平台"
            size="small"
            style="width:100%"
            :disabled="!createMode"
          >
            <el-option
              v-for="opt in platformOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="架构" prop="arch">
          <el-select v-model="form.arch" size="small" style="width:100%" :disabled="!createMode">
            <el-option label="amd64" value="amd64" />
            <el-option label="arm64" value="arm64" />
            <el-option label="x64" value="x64" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标用户" prop="targetUserId">
          <el-input
            v-model="form.targetUserId"
            placeholder="空=全员；否则仅该 bk 用户灰度"
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
          <el-input v-model="form.downloadUrl" type="textarea" :rows="3" size="small" />
        </el-form-item>
        <el-form-item label="更新说明" prop="releaseNotes">
          <el-input v-model="form.releaseNotes" type="textarea" :rows="3" size="small" />
        </el-form-item>
        <el-form-item label="启用" prop="enabled">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>
      <div class="drawer-footer">
        <el-button @click="close">取 消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确 定</el-button>
      </div>
    </div>
  </el-drawer>
</template>

<script>
import { upsertClientVersionConfig } from '@/api/clientVersionConfig'

const PLATFORM_PRESETS = [
  { label: 'Windows', value: 'windows' },
  { label: 'macOS (Darwin)', value: 'darwin' },
  { label: 'Linux', value: 'linux' }
]

function normalizePlatformForForm(platform) {
  const s = (platform || '').trim()
  const lower = s.toLowerCase()
  const known = PLATFORM_PRESETS.map(p => p.value)
  return known.includes(lower) ? lower : s
}

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
  name: 'ClientVersionConfigDrawer',
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
    }
  },
  data() {
    return {
      showDrawer: this.visible,
      submitting: false,
      form: emptyForm(),
      rules: {
        productId: [{ required: true, message: '必填', trigger: 'blur' }],
        platform: [{ required: true, message: '必填', trigger: 'change' }],
        arch: [{ required: true, message: '必填', trigger: 'change' }],
        latestVersion: [{ required: true, message: '必填', trigger: 'blur' }],
        downloadUrl: [{ required: true, message: '必填', trigger: 'blur' }]
      }
    }
  },
  computed: {
    platformOptions() {
      const raw = (this.form.platform || '').trim()
      const lower = raw.toLowerCase()
      const knownVals = PLATFORM_PRESETS.map(p => p.value)
      if (raw && !knownVals.includes(lower)) {
        return [...PLATFORM_PRESETS, { label: raw, value: raw }]
      }
      return PLATFORM_PRESETS
    }
  },
  watch: {
    visible(val) {
      this.showDrawer = val
      if (val) {
        this.initForm()
      }
    },
    showDrawer(val) {
      this.$emit('update:visible', val)
    }
  },
  methods: {
    initForm() {
      if (this.createMode) {
        const r = this.record || {}
        this.form = {
          ...emptyForm(),
          productId: r.productId || '',
          platform: normalizePlatformForForm(r.platform),
          arch: r.arch || '',
          targetUserId: r.targetUserId || ''
        }
      } else {
        const r = this.record || {}
        this.form = {
          id: r.id,
          productId: r.productId || '',
          platform: normalizePlatformForForm(r.platform),
          arch: r.arch || '',
          targetUserId: r.targetUserId || '',
          minVersion: r.minVersion || '',
          latestVersion: r.latestVersion || '',
          downloadUrl: r.downloadUrl || '',
          releaseNotes: r.releaseNotes || '',
          enabled: r.enabled !== false
        }
      }
      this.$nextTick(() => this.$refs.form && this.$refs.form.clearValidate())
    },
    close() {
      this.showDrawer = false
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
      const uid = (f.targetUserId || '').trim()
      if (uid) {
        payload.targetUserId = uid
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

<style scoped>
.drawer-body {
  padding: 0 20px 72px;
}
.drawer-footer {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  padding: 12px 20px;
  text-align: right;
  background: #fff;
  border-top: 1px solid #e8e8e8;
}
</style>
