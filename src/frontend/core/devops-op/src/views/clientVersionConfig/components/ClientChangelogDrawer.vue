<template>
  <el-drawer
    :title="createMode ? '新增更新日志' : '编辑更新日志'"
    :visible.sync="showDrawer"
    :before-close="close"
    size="640px"
    direction="rtl"
  >
    <div class="drawer-body">
      <el-form ref="form" :model="form" :rules="rules" label-width="100px" status-icon>
        <el-form-item label="产品线" prop="productId">
          <el-select
            v-model="form.productId"
            size="small"
            style="width:100%"
            :disabled="!createMode"
            placeholder="请选择产品线"
          >
            <el-option
              v-for="p in productPresets"
              :key="p.productId"
              :label="`${p.label}（${p.productId}）`"
              :value="p.productId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="版本号" prop="version">
          <el-input
            v-model="form.version"
            placeholder="如 3.0.2"
            size="small"
            :disabled="!createMode"
          />
        </el-form-item>
        <el-form-item label="发布日期" prop="releasedAt">
          <el-date-picker
            v-model="form.releasedAt"
            type="date"
            value-format="yyyy-MM-dd"
            placeholder="发布日期"
            size="small"
            style="width:100%"
          />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status" size="small">
            <el-radio-button label="DRAFT">草稿</el-radio-button>
            <el-radio-button label="PUBLISHED">发布</el-radio-button>
          </el-radio-group>
          <div class="form-tip">
            草稿仅管理端可见；发布后客户端可查询。
          </div>
        </el-form-item>
        <el-form-item label="正文（Markdown）" prop="releaseNotes">
          <el-input
            v-model="form.releaseNotes"
            type="textarea"
            :rows="14"
            size="small"
            placeholder="例如：&#10;## 新增&#10;- 支持网络拥塞检测&#10;## 修复&#10;- 修复 mac 上权限问题"
          />
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
import { upsertChangelog, CHANGELOG_STATUS } from '@/api/clientChangelog'
import { PRODUCT_PRESETS } from '../productPresets'

function emptyForm() {
  return {
    id: null,
    productId: '',
    version: '',
    releasedAt: '',
    status: CHANGELOG_STATUS.DRAFT,
    releaseNotes: ''
  }
}

export default {
  name: 'ClientChangelogDrawer',
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
    }
  },
  data() {
    return {
      showDrawer: this.visible,
      submitting: false,
      productPresets: PRODUCT_PRESETS,
      form: emptyForm(),
      rules: {
        productId: [{ required: true, message: '必填', trigger: 'change' }],
        version: [{ required: true, message: '必填', trigger: 'blur' }],
        releasedAt: [{ required: true, message: '必填', trigger: 'change' }],
        status: [{ required: true, message: '必填', trigger: 'change' }],
        releaseNotes: [{ required: true, message: '必填', trigger: 'blur' }]
      }
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
        this.form = {
          ...emptyForm(),
          productId: this.fixedProductId || (this.record && this.record.productId) || '',
          releasedAt: this.todayStr()
        }
      } else {
        const r = this.record || {}
        this.form = {
          id: r.id,
          productId: r.productId || '',
          version: r.version || '',
          releasedAt: r.releasedAt || '',
          status: r.status || CHANGELOG_STATUS.DRAFT,
          releaseNotes: r.releaseNotes || ''
        }
      }
      this.$nextTick(() => this.$refs.form && this.$refs.form.clearValidate())
    },
    todayStr() {
      const d = new Date()
      const m = String(d.getMonth() + 1).padStart(2, '0')
      const day = String(d.getDate()).padStart(2, '0')
      return `${d.getFullYear()}-${m}-${day}`
    },
    close() {
      this.showDrawer = false
    },
    buildPayload() {
      const f = this.form
      const payload = {
        productId: f.productId.trim(),
        version: f.version.trim(),
        releasedAt: (f.releasedAt || '').trim(),
        status: f.status,
        releaseNotes: f.releaseNotes
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
        upsertChangelog(this.buildPayload())
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
.form-tip {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
}
</style>
