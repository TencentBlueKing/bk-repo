<template>
  <el-dialog
    title="复制到用户"
    :visible.sync="showDialog"
    width="480px"
    :before-close="close"
  >
    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="将源用户在当前产品线/平台/架构下的灰度配置复制给目标用户（仅 1 条）；版本与下载地址不变，仅更换用户。"
      style="margin-bottom: 16px"
    />
    <el-form ref="form" :model="form" :rules="rules" label-width="100px" size="small">
      <el-form-item label="源用户" prop="sourceUserId">
        <el-select
          v-model="form.sourceUserId"
          filterable
          allow-create
          default-first-option
          placeholder="选择或输入 bk 用户名"
          style="width: 100%"
        >
          <el-option
            v-for="uid in sourceUserOptions"
            :key="uid"
            :label="uid"
            :value="uid"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="目标用户" prop="targetUserId">
        <el-input v-model="form.targetUserId" placeholder="新用户 bk 用户名" />
      </el-form-item>
    </el-form>
    <div slot="footer">
      <el-button @click="close">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">确认复制</el-button>
    </div>
  </el-dialog>
</template>

<script>
import { listClientVersionConfigs, upsertClientVersionConfig } from '@/api/clientVersionConfig'

export default {
  name: 'ClientVersionConfigCopyUserDialog',
  props: {
    visible: { type: Boolean, default: false },
    tabContext: { type: Object, default: () => ({}) },
    sourceUserOptions: { type: Array, default: () => [] },
    defaultSourceUserId: { type: String, default: '' }
  },
  data() {
    return {
      showDialog: this.visible,
      submitting: false,
      form: {
        sourceUserId: '',
        targetUserId: ''
      },
      rules: {
        sourceUserId: [{ required: true, message: '必填', trigger: 'change' }],
        targetUserId: [{ required: true, message: '必填', trigger: 'blur' }]
      }
    }
  },
  watch: {
    visible(val) {
      this.showDialog = val
      if (val) {
        this.form.sourceUserId = this.defaultSourceUserId || ''
        this.form.targetUserId = ''
      }
    },
    showDialog(val) {
      this.$emit('update:visible', val)
    }
  },
  methods: {
    close() {
      this.showDialog = false
    },
    fetchSourceRecord() {
      const ctx = this.tabContext
      const arch = (ctx.arch || '').trim().toLowerCase()
      return listClientVersionConfigs({
        productId: ctx.productId,
        platform: ctx.platform,
        arch: ctx.arch,
        scope: 'user',
        targetUserId: (this.form.sourceUserId || '').trim(),
        pageNumber: 1,
        pageSize: 1,
        sortField: 'lastModifiedDate',
        sortDirection: 'DESC'
      }).then(res => {
        const records = (res.data && res.data.records) || []
        return records.find(
          r => (r.arch || '').trim().toLowerCase() === arch && (r.targetUserId || '').trim()
        ) || null
      })
    },
    submit() {
      this.$refs.form.validate(valid => {
        if (!valid) return
        const source = (this.form.sourceUserId || '').trim()
        const target = (this.form.targetUserId || '').trim()
        if (source === target) {
          this.$message.warning('源用户与目标用户不能相同')
          return
        }
        this.submitting = true
        this.fetchSourceRecord()
          .then(record => {
            if (!record) {
              this.$message.warning('源用户在当前架构下没有可复制的配置')
              return
            }
            const payload = {
              productId: record.productId,
              platform: record.platform,
              arch: record.arch,
              targetUserId: target,
              latestVersion: record.latestVersion,
              downloadUrl: record.downloadUrl,
              enabled: record.enabled
            }
            if (record.minVersion) payload.minVersion = record.minVersion
            if (record.releaseNotes) payload.releaseNotes = record.releaseNotes
            return upsertClientVersionConfig(payload).then(() => {
              this.$message.success(`已复制到用户 ${target}`)
              this.$emit('copied')
              this.close()
            })
          })
          .finally(() => {
            this.submitting = false
          })
      })
    }
  }
}
</script>
