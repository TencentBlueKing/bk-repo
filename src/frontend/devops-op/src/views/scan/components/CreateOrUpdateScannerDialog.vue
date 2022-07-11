<template>
  <el-dialog :title="createMode ? '创建扫描器' : '更新扫描器'" :visible.sync="showDialog" :before-close="beforeClose">
    <el-form ref="form" :rules="rules" :model="scanner" status-icon>
      <el-form-item label="名称" prop="name" required>
        <el-input v-model="scanner.name" :disabled="!createMode" />
      </el-form-item>
      <el-form-item label="类型" prop="type" required>
        <el-select v-model="scanner.type" placeholder="扫描器类型" :disabled="!createMode">
          <el-option v-for="type in types" :key="type.value" :label="type.label" :value="type.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="版本" prop="version" required>
        <el-input v-model="scanner.version" />
      </el-form-item>
      <el-form-item label="根目录" prop="rootPath" required>
        <el-input v-model="scanner.rootPath" />
      </el-form-item>
      <el-form-item label="扫描结束后是否清理" prop="cleanWorkDir" required>
        <el-switch v-model="scanner.cleanWorkDir" />
      </el-form-item>
      <el-form-item label="1MB最大允许扫描时间" prop="maxScanDurationPerMb" required>
        <el-input v-model.number="scanner.maxScanDurationPerMb" type="number">
          <template slot="append">ms</template>
        </el-input>
      </el-form-item>
      <!-- arrowhead -->
      <el-form-item v-if="scanner.type === SCANNER_TYPE_ARROWHEAD || scanner.type === SCANNER_TYPE_TRIVY" label="镜像" prop="container.image" required>
        <el-input v-model="scanner.container.image" placeholder="镜像，IMAGE:TAG" />
      </el-form-item>
      <el-form-item v-if="scanner.type === SCANNER_TYPE_ARROWHEAD" label="知识库URL" prop="knowledgeBase.endpoint" required>
        <el-input v-model="scanner.knowledgeBase.endpoint" placeholder="知识库URL" />
      </el-form-item>
      <el-form-item v-if="scanner.type === SCANNER_TYPE_ARROWHEAD" label="知识库认证ID" prop="knowledgeBase.secretId">
        <el-input v-model="scanner.knowledgeBase.secretId" placeholder="知识库认证SecretId" />
      </el-form-item>
      <el-form-item v-if="scanner.type === SCANNER_TYPE_ARROWHEAD" label="知识库认证KEY" prop="knowledgeBase.secretKey">
        <el-input v-model="scanner.knowledgeBase.secretKey" placeholder="知识库认证SecretKey" />
      </el-form-item>
      <!-- trivy -->
      <el-form-item v-if="scanner.type === SCANNER_TYPE_TRIVY" label="漏洞库来源" prop="vulDbConfig.dbSource" required>
        <el-radio v-model="scanner.vulDbConfig.dbSource" :label="0">从制品库下载</el-radio>
        <el-radio v-model="scanner.vulDbConfig.dbSource" :label="1">Trivy自动下载</el-radio>
      </el-form-item>
      <el-form-item v-if="scanner.type === SCANNER_TYPE_TRIVY && scanner.vulDbConfig.dbSource === 0" label="漏洞库项目" prop="vulDbConfig.projectId" required>
        <el-input v-model="scanner.vulDbConfig.projectId" placeholder="漏洞库所在项目projectId" />
      </el-form-item>
      <el-form-item v-if="scanner.type === SCANNER_TYPE_TRIVY && scanner.vulDbConfig.dbSource === 0" label="漏洞库仓库" prop="vulDbConfig.repo" required>
        <el-input v-model="scanner.vulDbConfig.repo" placeholder="漏洞库所在项目仓库的repoName，会拉取仓库最新的文件作为漏洞库" />
      </el-form-item>
    </el-form>
    <div slot="footer">
      <el-button @click="beforeClose">取 消</el-button>
      <el-button type="primary" @click="handleCreateOrUpdate(scanner)">确 定</el-button>
    </div>
  </el-dialog>
</template>

<script>
import { IMAGE_REGEX, URL_REGEX } from '@/utils/validate'
import _ from 'lodash'
import { createScanner, SCANNER_TYPE_ARROWHEAD, SCANNER_TYPE_TRIVY, updateScanner } from '@/api/scan'
export default {
  name: 'CreateOrUpdateScannerDialog',
  props: {
    visible: Boolean,
    /**
     * 仅在更新模式时有值
     */
    updatingScanner: {
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
      SCANNER_TYPE_ARROWHEAD: SCANNER_TYPE_ARROWHEAD,
      SCANNER_TYPE_TRIVY: SCANNER_TYPE_TRIVY,
      rules: {
        'knowledgeBase.endpoint': [
          { validator: this.validateUrl, trigger: 'change' }
        ],
        'container.image': [
          { validator: this.validateImage, trigger: 'change' }
        ]
      },
      types: [
        {
          'value': SCANNER_TYPE_ARROWHEAD,
          'label': 'Arrowhead'
        },
        {
          'value': SCANNER_TYPE_TRIVY,
          'label': 'Trivy'
        }
      ],
      showDialog: this.visible,
      scanner: this.newScanner()
    }
  },
  watch: {
    visible: function(newVal) {
      if (newVal) {
        this.resetScanner()
        this.showDialog = true
      } else {
        this.close()
      }
    },
    'scanner.type': function(newVal) {
      this.resetScanner(newVal)
    }
  },
  methods: {
    validateUrl(rule, value, callback) {
      this.regexValidate(rule, value, callback, URL_REGEX)
    },
    validateImage(rule, value, callback) {
      this.regexValidate(rule, value, callback, IMAGE_REGEX)
    },
    regexValidate(rule, value, callback, regex) {
      if (regex.test(value)) {
        callback()
      } else {
        callback(new Error('格式错误'))
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
    },
    handleCreateOrUpdate(scanner) {
      this.$refs['form'].validate((valid) => {
        if (valid) {
          // 根据是否为创建模式发起不同请求
          let reqPromise
          let msg
          let eventName
          if (this.createMode) {
            reqPromise = createScanner(scanner)
            msg = '创建扫描器成功'
            eventName = 'created'
          } else {
            reqPromise = updateScanner(scanner)
            msg = '更新扫描器成功'
            eventName = 'updated'
          }

          // 发起请求
          reqPromise.then(res => {
            this.$message.success(msg)
            this.$emit(eventName, res.data)
            this.close()
          })
        } else {
          return false
        }
      })
    },
    resetScanner(type) {
      if (this.createMode) {
        this.scanner = this.newScanner(type)
      } else {
        this.scanner = _.cloneDeep(this.updatingScanner)
      }
      this.$nextTick(() => {
        this.$refs['form'].clearValidate()
      })
    },
    newScanner(type = SCANNER_TYPE_ARROWHEAD) {
      const scanner = {
        type: type,
        rootPath: type,
        cleanWorkDir: true,
        maxScanDurationPerMb: 6000
      }
      if (type === SCANNER_TYPE_ARROWHEAD) {
        scanner.knowledgeBase = {}
        scanner.container = {}
      }
      if (type === SCANNER_TYPE_TRIVY) {
        scanner.container = {}
        scanner.vulDbConfig = {}
        scanner.vulDbConfig.dbSource = 1
      }
      return scanner
    }
  }
}
</script>

<style scoped>

</style>
