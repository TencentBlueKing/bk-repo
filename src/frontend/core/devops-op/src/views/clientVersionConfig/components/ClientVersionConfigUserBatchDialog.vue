<template>
  <el-dialog
    title="按用户灰度批量新增"
    :visible.sync="showDialog"
    :before-close="close"
    width="640px"
  >
    <el-form ref="form" :model="form" :rules="rules" label-width="120px" size="small" status-icon>
      <el-form-item label="制品产品线">
        <el-input :value="fixedProductLabel" disabled />
      </el-form-item>
      <el-form-item label="平台">
        <el-input :value="fixedPlatformLabel" disabled />
      </el-form-item>
      <el-form-item label="架构">
        <el-input :value="fixedArch" disabled />
      </el-form-item>
      <el-form-item label="目标用户" prop="targetUserIds">
        <div class="user-tag-input">
          <el-tag
            v-for="uid in form.targetUserIds"
            :key="uid"
            closable
            size="small"
            @close="removeUser(uid)"
          >{{ uid }}</el-tag>
          <el-input
            v-if="userInputVisible"
            ref="userInput"
            v-model="userInputVal"
            size="mini"
            class="user-input"
            @keyup.enter.native="confirmUser"
            @blur="confirmUser"
          />
          <el-button v-else size="mini" class="btn-add-user" @click="showUserInput">
            + 添加用户
          </el-button>
        </div>
        <div class="user-tip">至少添加 1 个用户，每人一条灰度配置</div>
      </el-form-item>
      <el-form-item label="最新版本" prop="latestVersion">
        <el-input v-model="form.latestVersion" placeholder="如 1.2.3" />
      </el-form-item>
      <el-form-item label="最低版本(强升)">
        <el-input v-model="form.minVersion" placeholder="可空" />
      </el-form-item>
      <el-form-item label="下载地址" prop="downloadUrl">
        <el-input v-model="form.downloadUrl" type="textarea" :rows="2" />
      </el-form-item>
      <el-form-item label="更新说明">
        <el-input v-model="form.releaseNotes" placeholder="可空" />
      </el-form-item>
      <el-form-item label="启用">
        <el-switch v-model="form.enabled" />
      </el-form-item>
    </el-form>
    <div slot="footer">
      <el-button @click="close">取 消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">
        提 交（{{ form.targetUserIds.length }} 条）
      </el-button>
    </div>
  </el-dialog>
</template>

<script>
import { batchUpsertClientVersionConfig, BATCH_LIMIT } from '@/api/clientVersionConfig'
import { productLabel, platformLabel } from '../productPresets'

export default {
  name: 'ClientVersionConfigUserBatchDialog',
  props: {
    visible: { type: Boolean, default: false },
    fixedProductId: { type: String, default: '' },
    fixedPlatform: { type: String, default: '' },
    fixedArch: { type: String, default: '' }
  },
  data() {
    return {
      showDialog: this.visible,
      submitting: false,
      userInputVisible: false,
      userInputVal: '',
      form: {
        targetUserIds: [],
        latestVersion: '',
        minVersion: '',
        downloadUrl: '',
        releaseNotes: '',
        enabled: true
      },
      rules: {
        targetUserIds: [{
          validator: (rule, value, callback) => {
            if (!value || value.length === 0) {
              callback(new Error('请至少添加一个用户'))
            } else {
              callback()
            }
          },
          trigger: 'change'
        }],
        latestVersion: [{ required: true, message: '必填', trigger: 'blur' }],
        downloadUrl: [{ required: true, message: '必填', trigger: 'blur' }]
      }
    }
  },
  computed: {
    fixedProductLabel() {
      return productLabel(this.fixedProductId)
    },
    fixedPlatformLabel() {
      return platformLabel(this.fixedPlatform)
    }
  },
  watch: {
    visible(val) {
      this.showDialog = val
      if (val) {
        this.reset()
      }
    },
    showDialog(val) {
      this.$emit('update:visible', val)
    }
  },
  methods: {
    reset() {
      this.form = {
        targetUserIds: [],
        latestVersion: '',
        minVersion: '',
        downloadUrl: '',
        releaseNotes: '',
        enabled: true
      }
      this.userInputVisible = false
      this.userInputVal = ''
      this.$nextTick(() => this.$refs.form && this.$refs.form.clearValidate())
    },
    showUserInput() {
      this.userInputVisible = true
      this.$nextTick(() => this.$refs.userInput && this.$refs.userInput.focus())
    },
    confirmUser() {
      const val = (this.userInputVal || '').trim()
      if (val && !this.form.targetUserIds.includes(val)) {
        this.form.targetUserIds.push(val)
      }
      this.userInputVisible = false
      this.userInputVal = ''
      this.$refs.form && this.$refs.form.validateField('targetUserIds')
    },
    removeUser(uid) {
      this.form.targetUserIds = this.form.targetUserIds.filter(u => u !== uid)
      this.$refs.form && this.$refs.form.validateField('targetUserIds')
    },
    close() {
      this.showDialog = false
    },
    submit() {
      this.$refs.form.validate(valid => {
        if (!valid) return
        const users = this.form.targetUserIds
        if (users.length > BATCH_LIMIT) {
          this.$message.warning(`批量新增最多支持 ${BATCH_LIMIT} 条`)
          return
        }
        const payload = users.map(uid => {
          const item = {
            productId: this.fixedProductId,
            platform: this.fixedPlatform,
            arch: this.fixedArch,
            targetUserId: uid,
            latestVersion: this.form.latestVersion.trim(),
            downloadUrl: this.form.downloadUrl.trim(),
            enabled: this.form.enabled
          }
          const minV = (this.form.minVersion || '').trim()
          if (minV) item.minVersion = minV
          const notes = (this.form.releaseNotes || '').trim()
          if (notes) item.releaseNotes = notes
          return item
        })
        this.submitting = true
        batchUpsertClientVersionConfig(payload)
          .then(() => {
            this.$message.success(`成功新增 ${payload.length} 条用户灰度配置`)
            this.$emit('created')
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
.user-tag-input {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 4px 8px;
  min-height: 32px;
}
.user-input {
  width: 120px;
}
.btn-add-user {
  border: 1px dashed #409eff;
  color: #409eff;
  padding: 0 6px;
  height: 24px;
}
.user-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
</style>
