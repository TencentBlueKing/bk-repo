<template>
  <el-dialog :title="createMode ? '创建水印加固配置' : '更新水印加固配置'" :visible.sync="showDialog" :before-close="close" width="700px">
    <el-form ref="form" :rules="rules" :model="signConfig" status-icon>
      <el-form-item label="项目ID" prop="projectId" :rules="[{ required: true, message: '项目ID不能为空'}]">
        <el-input v-model="signConfig.projectId" type="text" size="small" placeholder="请输入项目ID" :disabled="!createMode" />
      </el-form-item>

      <el-form-item label="扫描器配置" prop="scanner">
        <div class="scanner-config-container">
          <div v-for="(item, index) in scannerItems" :key="index" class="scanner-item">
            <el-input v-model="item.key" placeholder="请输入文件类型" style="width: 200px; margin-right: 10px;" />
            <el-input v-model="item.value" placeholder="请输入扫描器名称" style="width: 200px; margin-right: 10px;" />
            <el-button @click.prevent="removeScannerItem(index)" style="margin-top: 32px;">删除</el-button>
          </div>
        </div>
        <el-button type="primary" icon="el-icon-plus" @click="addScannerItem" class="add-scanner-btn">添加扫描器</el-button>
      </el-form-item>

      <el-form-item label="标签" prop="tags">
        <el-select
          v-model="signConfig.tags"
          multiple
          filterable
          allow-create
          default-first-option
          placeholder="请选择或输入标签"
          style="width: 100%;">
          <el-option
            v-for="item in tagOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
    </el-form>
    <div slot="footer">
      <el-button @click="close">取 消</el-button>
      <el-button type="primary" @click="submitForm">确 定</el-button>
    </div>
  </el-dialog>
</template>

<script>
import { createSignConfig, updateSignConfig } from '@/api/signconfig'

export default {
  name: 'CreateOrUpdateSignConfigDialog',
  props: {
    createMode: {
      type: Boolean,
      default: true
    },
    visible: {
      type: Boolean,
      default: false
    },
    updatingSignConfig: {
      type: Object,
      default: () => ({})
    }
  },
  data() {
    return {
      showDialog: this.visible,
      signConfig: {
        projectId: '',
        scanner: {},
        tags: ['Alpha']
      },
      scannerItems: [],
      rules: {
        projectId: [{ required: true, message: '请输入项目ID', trigger: 'blur' }]
      },
      tagOptions: [
        {
          value: 'Alpha',
          label: 'Alpha'
        },
        {
          value: 'Beta',
          label: 'Beta'
        }
      ]
    }
  },
  watch: {
    visible(val) {
      this.showDialog = val
      if (val) {
        this.initForm()
      }
    },
    showDialog(val) {
      this.$emit('update:visible', val)
    },
    scannerItems: {
      handler() {
        this.updateScannerObject()
      },
    }
  },
  methods: {
    initForm() {
      if (this.createMode) {
        this.signConfig = {
          projectId: '',
          scanner: {},
          tags: ['Alpha']
        }
        this.scannerItems = []
      } else {
        this.signConfig = { ...this.updatingSignConfig }
        // 将scanner对象转换为数组形式用于编辑
        this.scannerItems = Object.entries(this.signConfig.scanner || {}).map(([key, value], index) => ({
          key,
          value
        }))
      }
    },
    updateScannerObject() {
      const scanner = {}
      this.scannerItems.forEach(item => {
        if (item.key && item.value) {
          scanner[item.key] = item.value
        }
      })
      this.signConfig.scanner = scanner
    },
    addScannerItem() {
      console.log('添加扫描器按钮被点击')
      this.scannerItems.push({
        key: '',
        value: ''
      })
      console.log('当前scannerItems:', this.scannerItems)
    },
    removeScannerItem(index) {
      this.scannerItems.splice(index, 1)
      this.updateScannerObject()
    },
    close() {
      this.showDialog = false
      this.$refs.form.resetFields()
    },
    submitForm() {
      this.updateScannerObject()
      this.$refs.form.validate((valid) => {
        if (valid) {
          // 确保scanner和tags字段格式正确
          const submitData = {
            projectId: this.signConfig.projectId,
            scanner: this.signConfig.scanner,
            tags: Array.isArray(this.signConfig.tags) ? this.signConfig.tags : ['Alpha']
          }
          
          if (this.createMode) {
            this.createSignConfig(submitData)
          } else {
            this.updateSignConfig(submitData)
          }
        } else {
          this.$message.error('请填写完整的表单信息')
          return false
        }
      })
    },
    createSignConfig(data) {
      createSignConfig(data).then(response => {
        this.$message.success('创建成功')
        this.close()
        this.$emit('created')
      }).catch(error => {
        this.$message.error('创建失败：' + (error.message || '未知错误'))
      })
    },
    updateSignConfig(data) {
      updateSignConfig(data).then(response => {
        this.$message.success('更新成功')
        this.close()
        this.$emit('updated')
      }).catch(error => {
        this.$message.error('更新失败：' + (error.message || '未知错误'))
      })
    }
  }
}
</script>

<style scoped>
.scanner-config-container {
  margin-bottom: 10px;
}

.scanner-item {
  display: flex;
  align-items: center;
  margin-bottom: 10px;
  gap: 10px;
  border: 1px solid #e6e6e6;
  border-radius: 4px;
  padding: 10px;
  background-color: #f9f9f9;
}

.scanner-item:last-child {
  margin-bottom: 0;
}

/* 修改el-form-item布局，让标签和内容垂直排列 */
.el-form-item {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}

.el-form-item__label {
  display: block !important;
  text-align: left !important;
  font-size: 14px !important;
  color: #606266 !important;
  line-height: 1.5 !important;
  padding: 0 0 8px 0 !important;
  margin-bottom: 0 !important;
  width: 100% !important;
}

.el-form-item__content {
  width: 100% !important;
  margin-left: 0 !important;
  line-height: 40px !important;
}

/* 添加扫描器按钮样式 */
.add-scanner-btn {
  margin-top: 10px;
  width: 100%;
  height: 36px;
  font-size: 14px;
  background-color: #409eff;
  border-color: #409eff;
  color: white;
}

.add-scanner-btn:hover {
  background-color: #66b1ff;
  border-color: #66b1ff;
}

.add-scanner-btn:active {
  background-color: #3a8ee6;
  border-color: #3a8ee6;
}

/* 确保按钮不会被其他元素遮挡 */
.el-form-item__content {
  position: relative;
  z-index: 1;
}
</style>
