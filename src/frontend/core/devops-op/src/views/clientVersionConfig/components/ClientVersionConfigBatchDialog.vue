<template>
  <el-dialog
    title="批量新增客户端版本配置"
    :visible.sync="showDialog"
    :before-close="close"
    width="860px"
  >
    <el-form ref="commonForm" :model="common" :rules="commonRules" label-width="120px" size="small" status-icon>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="制品产品线" prop="productId">
            <el-input v-model="common.productId" placeholder="如 bkrepo-cli" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="最新版本" prop="latestVersion">
            <el-input v-model="common.latestVersion" placeholder="如 1.2.3" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="最低版本(强升)">
            <el-input v-model="common.minVersion" placeholder="低于此版本强制升级，可空" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="目标用户">
            <div class="user-tag-input">
              <el-tag
                v-for="uid in common.targetUserIds"
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
            <div class="user-tip">空=全员；多用户时每人各生成一条记录</div>
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="更新说明">
            <el-input v-model="common.releaseNotes" placeholder="可空" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="启用">
            <el-switch v-model="common.enabled" />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-divider content-position="left">选择平台/架构并填写下载地址</el-divider>

    <el-table :data="rows" border size="small" style="width:100%">
      <el-table-column width="50" align="center">
        <template slot="header">
          <el-checkbox
            :indeterminate="indeterminate"
            :value="allChecked"
            @change="toggleAll"
          />
        </template>
        <template slot-scope="{ row }">
          <el-checkbox v-model="row.selected" />
        </template>
      </el-table-column>
      <el-table-column label="平台" prop="platform" width="110" align="center" />
      <el-table-column label="架构" prop="arch" width="90" align="center" />
      <el-table-column label="下载地址">
        <template slot-scope="{ row, $index }">
          <el-input
            v-model="row.downloadUrl"
            :class="{ 'is-error': row.selected && urlErrors[$index] }"
            size="small"
            placeholder="必填"
          />
        </template>
      </el-table-column>
    </el-table>

    <div slot="footer">
      <el-button @click="close">取 消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">
        提 交（{{ totalCount }} 条）
      </el-button>
    </div>
  </el-dialog>
</template>

<script>
import { batchUpsertClientVersionConfig } from '@/api/clientVersionConfig'

const PRESETS = [
  { platform: 'windows', arch: 'amd64' },
  { platform: 'windows', arch: 'arm64' },
  { platform: 'windows', arch: 'x64' },
  { platform: 'darwin', arch: 'amd64' },
  { platform: 'darwin', arch: 'arm64' },
  { platform: 'darwin', arch: 'x64' },
  { platform: 'linux', arch: 'amd64' },
  { platform: 'linux', arch: 'arm64' },
  { platform: 'linux', arch: 'x64' }
]
const BATCH_LIMIT = 50

function buildRows() {
  return PRESETS.map(p => ({ ...p, selected: true, downloadUrl: '' }))
}

export default {
  name: 'ClientVersionConfigBatchDialog',
  props: {
    visible: { type: Boolean, default: false }
  },
  data() {
    return {
      showDialog: this.visible,
      submitting: false,
      common: {
        productId: '',
        latestVersion: '',
        minVersion: '',
        targetUserIds: [],
        releaseNotes: '',
        enabled: true
      },
      userInputVisible: false,
      userInputVal: '',
      commonRules: {
        productId: [{ required: true, message: '必填', trigger: 'blur' }],
        latestVersion: [{ required: true, message: '必填', trigger: 'blur' }]
      },
      rows: buildRows(),
      urlErrors: {}
    }
  },
  computed: {
    selectedCount() {
      return this.rows.filter(r => r.selected).length
    },
    totalCount() {
      const users = this.common.targetUserIds.length || 1
      return this.selectedCount * users
    },
    allChecked() {
      return this.rows.length > 0 && this.rows.every(r => r.selected)
    },
    indeterminate() {
      const cnt = this.selectedCount
      return cnt > 0 && cnt < this.rows.length
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
      this.common = {
        productId: '',
        latestVersion: '',
        minVersion: '',
        targetUserIds: [],
        releaseNotes: '',
        enabled: true
      }
      this.userInputVisible = false
      this.userInputVal = ''
      this.rows = buildRows()
      this.urlErrors = {}
      this.$nextTick(() => this.$refs.commonForm && this.$refs.commonForm.clearValidate())
    },
    showUserInput() {
      this.userInputVisible = true
      this.$nextTick(() => this.$refs.userInput && this.$refs.userInput.focus())
    },
    confirmUser() {
      const val = (this.userInputVal || '').trim()
      if (val && !this.common.targetUserIds.includes(val)) {
        this.common.targetUserIds.push(val)
      }
      this.userInputVisible = false
      this.userInputVal = ''
    },
    removeUser(uid) {
      this.common.targetUserIds = this.common.targetUserIds.filter(u => u !== uid)
    },
    close() {
      this.showDialog = false
    },
    toggleAll(val) {
      this.rows.forEach(r => { r.selected = val })
    },
    validate() {
      const errors = {}
      this.rows.forEach((r, i) => {
        if (r.selected && !r.downloadUrl.trim()) {
          errors[i] = true
        }
      })
      this.urlErrors = errors
      return Object.keys(errors).length === 0
    },
    submit() {
      this.$refs.commonForm.validate(valid => {
        if (!valid) return
        if (this.selectedCount === 0) {
          this.$message.warning('请至少选择一个平台/架构')
          return
        }
        if (this.totalCount > BATCH_LIMIT) {
          this.$message.warning(`批量新增最多支持 ${BATCH_LIMIT} 条`)
          return
        }
        if (!this.validate()) {
          this.$message.warning('已选行的下载地址不能为空')
          return
        }
        const c = this.common
        const selectedRows = this.rows.filter(r => r.selected)
        const userList = c.targetUserIds.length ? c.targetUserIds : [null]
        const payload = []
        selectedRows.forEach(r => {
          userList.forEach(uid => {
            const item = {
              productId: c.productId.trim(),
              platform: r.platform,
              arch: r.arch,
              latestVersion: c.latestVersion.trim(),
              downloadUrl: r.downloadUrl.trim(),
              enabled: c.enabled
            }
            if (c.minVersion.trim()) item.minVersion = c.minVersion.trim()
            if (uid) item.targetUserId = uid
            if (c.releaseNotes.trim()) item.releaseNotes = c.releaseNotes.trim()
            payload.push(item)
          })
        })
        this.submitting = true
        batchUpsertClientVersionConfig(payload)
          .then(() => {
            this.$message.success(`成功新增 ${payload.length} 条配置`)
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
.is-error .el-input__inner {
  border-color: #f56c6c;
}
.user-tag-input {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 4px 8px;
  min-height: 32px;
  cursor: text;
}
.user-input {
  width: 100px;
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
