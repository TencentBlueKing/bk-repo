<template>
  <el-dialog :title="createMode ? '创建扫描器' : '更新扫描器'" :visible.sync="showDialog" :before-close="close">
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
      <el-form-item label="描述信息" prop="description">
        <el-input v-model="scanner.description" type="textarea" />
      </el-form-item>
      <el-form-item label="根目录" prop="rootPath" required>
        <el-input v-model="scanner.rootPath" />
      </el-form-item>
      <el-form-item label="扫描结束后是否清理" prop="cleanWorkDir" required>
        <el-switch v-model="scanner.cleanWorkDir" />
      </el-form-item>
      <el-form-item label="执行扫描所需最大内存" prop="limitMem" required>
        <el-input v-model.number="scanner.limitMem" type="number">
          <template slot="append">Byte</template>
        </el-input>
      </el-form-item>
      <el-form-item label="执行扫描所需最小内存" prop="requestMem" required>
        <el-input v-model.number="scanner.requestMem" type="number">
          <template slot="append">Byte</template>
        </el-input>
      </el-form-item>
      <el-form-item label="执行扫描所需最大存储空间" prop="limitStorage" required>
        <el-input v-model.number="scanner.limitStorage" type="number">
          <template slot="append">Byte</template>
        </el-input>
      </el-form-item>
      <el-form-item label="执行扫描所需最小存储空间" prop="requestStorage" required>
        <el-input v-model.number="scanner.requestStorage" type="number">
          <template slot="append">Byte</template>
        </el-input>
      </el-form-item>
      <el-form-item label="执行扫描所需最大CPU" prop="limitCpu" required>
        <el-input v-model.number="scanner.limitCpu" type="number" />
      </el-form-item>
      <el-form-item label="执行扫描所需最小Cpu" prop="requestCpu" required>
        <el-input v-model.number="scanner.requestCpu" type="number" />
      </el-form-item>
      <el-form-item label="1MB最大允许扫描时间" prop="maxScanDurationPerMb" required>
        <el-input v-model.number="scanner.maxScanDurationPerMb" type="number">
          <template slot="append">ms</template>
        </el-input>
      </el-form-item>
      <el-form-item label="不支持的制品名称正则 (多个正则用换行分隔)" prop="unsupportedArtifactNameRegex">
        <el-input v-model="unsupportedArtifactNameRegex" type="textarea" />
      </el-form-item>
      <el-form-item label="支持的文件类型" prop="supportFileNameExt">
        <el-tooltip effect="dark" content="仅扫描GENERIC包时生效" placement="top-start">
          <svg-icon icon-class="question" />
        </el-tooltip>
        <br>
        <el-tag
          v-for="(ext,index) in scanner.supportFileNameExt"
          :key="ext"
          :disable-transitions="true"
          style="margin-right: 10px"
          size="small"
          closable
          @close="handleClose(index)"
        >
          {{ ext }}
        </el-tag>
        <el-input
          v-if="extInputVisible"
          ref="extInput"
          v-model="extInputValue"
          class="input-new-ext"
          size="small"
          @keyup.enter.native="handleInputConfirm"
          @blur="handleInputConfirm"
        />
        <el-button v-else class="button-new-ext" size="small" @click="showInput">新增</el-button>
      </el-form-item>
      <el-form-item label="支持的包类型" prop="supportPackageTypes" required>
        <el-select v-model="scanner.supportPackageTypes" multiple placeholder="请选择" style="width: 100%">
          <el-option v-for="item in repoTypes" :key="item" :label="item" :value="item" />
        </el-select>
      </el-form-item>
      <el-form-item label="支持的扫描类型" prop="supportScanTypes" required>
        <el-select v-model="scanner.supportScanTypes" multiple placeholder="请选择" style="width: 100%">
          <el-option v-for="item in scanTypes" :key="item" :label="item" :value="item" />
        </el-select>
      </el-form-item>
      <el-form-item label="支持的分发器" prop="supportDispatchers">
        <el-select v-model="scanner.supportDispatchers" multiple placeholder="请选择" style="width: 100%">
          <el-option v-for="item in dispatchers" :key="item" :label="item" :value="item" />
        </el-select>
      </el-form-item>
      <!-- standard -->
      <el-form-item v-if="scanner.type === SCANNER_TYPE_STANDARD" label="镜像" prop="image" required>
        <el-input v-model="scanner.image" placeholder="镜像，IMAGE:TAG" />
      </el-form-item>
      <el-form-item v-if="scanner.type === SCANNER_TYPE_STANDARD" label="启动命令" prop="cmd" required>
        <el-input v-model="scanner.cmd" placeholder="扫描器容器启动命令" />
      </el-form-item>
      <el-form-item v-if="scanner.type === SCANNER_TYPE_STANDARD" label="参数" prop="args">
        <br>
        <standard-scanner-argument v-for="(arg,index) in scanner.args" :key="index" style="margin-bottom: 10px" :argument="arg" @remove="removeArg(index)" />
        <el-button size="mini" type="primary" @click="addArg(scanner)">增加参数</el-button>
      </el-form-item>
      <!-- arrowhead -->
      <el-form-item v-if="scanner.type === SCANNER_TYPE_ARROWHEAD || scanner.type === SCANNER_TYPE_TRIVY || scanner.type === SCANNER_TYPE_SCANCODE" label="镜像" prop="container.image" required>
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
      <el-button @click="close">取 消</el-button>
      <el-button type="primary" @click="handleCreateOrUpdate(scanner)">确 定</el-button>
    </div>
  </el-dialog>
</template>

<script>
import { IMAGE_REGEX, URL_REGEX } from '@/utils/validate'
import _ from 'lodash'
import {
  createScanner, listDispatchers, SCANNER_TYPE_ARROWHEAD, SCANNER_TYPE_SCANCODE, SCANNER_TYPE_STANDARD,
  SCANNER_TYPE_TRIVY, scanTypes,
  updateScanner
} from '@/api/scan'
import { repoTypes } from '@/api/repository'
import StandardScannerArgument from '@/views/scan/components/StandardScannerArgument'
export default {
  name: 'CreateOrUpdateScannerDialog',
  components: { StandardScannerArgument },
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
      SCANNER_TYPE_STANDARD: SCANNER_TYPE_STANDARD,
      SCANNER_TYPE_ARROWHEAD: SCANNER_TYPE_ARROWHEAD,
      SCANNER_TYPE_TRIVY: SCANNER_TYPE_TRIVY,
      SCANNER_TYPE_SCANCODE: SCANNER_TYPE_SCANCODE,
      scanTypes: scanTypes,
      repoTypes: repoTypes,
      dispatchers: [],
      unsupportedArtifactNameRegex: '',
      rules: {
        'unsupportedArtifactNameRegex': [
          { validator: this.validateUnsupportedArtifactNameRegex, trigger: 'change' }
        ],
        'knowledgeBase.endpoint': [
          { validator: this.validateUrl, trigger: 'change' }
        ],
        'container.image': [
          { validator: this.validateImage, trigger: 'change' }
        ],
        'image': [
          { validator: this.validateImage, trigger: 'change' }
        ],
        'args': [
          { validator: this.validateArgs, trigger: 'change' }
        ]
      },
      types: [
        {
          'value': SCANNER_TYPE_STANDARD,
          'label': 'Standard'
        },
        {
          'value': SCANNER_TYPE_ARROWHEAD,
          'label': 'Arrowhead'
        },
        {
          'value': SCANNER_TYPE_TRIVY,
          'label': 'Trivy'
        },
        {
          'value': SCANNER_TYPE_SCANCODE,
          'label': 'Scancode'
        }
      ],
      showDialog: this.visible,
      extInputVisible: false,
      extInputValue: '',
      scanner: this.newScanner()
    }
  },
  watch: {
    visible: function(newVal) {
      this.extInputVisible = false
      this.extInputValue = ''
      if (newVal) {
        this.resetScanner()
        this.refreshDispatchers()
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
    validateUnsupportedArtifactNameRegex(rule, value, callback) {
      this.unsupportedArtifactNameRegex.split('\n').forEach(v => {
        let isValid = true
        try {
          new RegExp(v)
        } catch (e) {
          isValid = false
        }
        if (!isValid) {
          callback(`正则${v}不符合规则`)
        }
      })
      callback()
    },
    validateUrl(rule, value, callback) {
      this.regexValidate(rule, value, callback, URL_REGEX)
    },
    validateImage(rule, value, callback) {
      this.regexValidate(rule, value, callback, IMAGE_REGEX)
    },
    validateArgs(rule, value, callback) {
      const keys = new Set()
      value.forEach(arg => {
        if (keys.has(arg.key)) {
          callback(new Error(`存在重复的参数KEY[${arg.key}]`))
        }
        if (!arg.key) {
          callback(new Error(`key不能为空`))
        }
        keys.add(arg.key)
      })
      callback()
    },
    regexValidate(rule, value, callback, regex) {
      if (regex.test(value)) {
        callback()
      } else {
        callback(new Error('格式错误'))
      }
    },
    close() {
      this.showDialog = false
      this.$emit('update:visible', false)
    },
    handleClose(index) {
      this.scanner.supportFileNameExt.splice(index, 1)
    },
    showInput() {
      this.extInputVisible = true
      this.$nextTick(_ => {
        this.$refs.extInput.$refs.input.focus()
      })
    },
    handleInputConfirm() {
      const ext = this.extInputValue
      // 输入为空字符串时表示支持无后缀文件名
      if (this.scanner.supportFileNameExt.indexOf(ext) === -1) {
        this.scanner.supportFileNameExt.push(ext)
      }
      this.extInputVisible = false
      this.extInputValue = ''
    },
    refreshDispatchers() {
      listDispatchers().then(res => {
        this.dispatchers = res.data.map(dispatcher => dispatcher.name)
      })
    },
    handleCreateOrUpdate(scanner) {
      this.$refs['form'].validate((valid) => {
        if (valid) {
          scanner.unsupportedArtifactNameRegex = this.unsupportedArtifactNameRegex.split('\n')
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
    addArg(scanner) {
      scanner.args.push({
        type: 'STRING',
        key: null,
        value: null
      })
    },
    removeArg(index) {
      this.scanner.args.splice(index, 1)
    },
    resetScanner(type) {
      if (this.createMode) {
        this.scanner = this.newScanner(type)
      } else {
        this.scanner = _.cloneDeep(this.updatingScanner)
        if (!(this.scanner.unsupportedArtifactNameRegex instanceof (Array)) || this.scanner.unsupportedArtifactNameRegex.length === 0) {
          this.unsupportedArtifactNameRegex = ''
        } else {
          this.unsupportedArtifactNameRegex = this.scanner.unsupportedArtifactNameRegex.join('\n')
        }
      }
      this.$nextTick(() => {
        this.$refs['form'].clearValidate()
      })
    },
    newScanner(type = SCANNER_TYPE_ARROWHEAD) {
      const scanner = {
        type: type,
        description: '',
        rootPath: type,
        cleanWorkDir: true,
        limitMem: 32 * 1024 * 1024 * 1024,
        requestMem: 16 * 1024 * 1024 * 1024,
        limitStorage: 128 * 1024 * 1024 * 1024,
        requestStorage: 16 * 1024 * 1024 * 1024,
        limitCpu: 16.0,
        requestCpu: 4.0,
        maxScanDurationPerMb: 6000,
        supportFileNameExt: [],
        supportPackageTypes: [],
        supportScanTypes: [],
        unsupportedArtifactNameRegex: []
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
      if (type === SCANNER_TYPE_SCANCODE) {
        scanner.container = {}
      }
      if (type === SCANNER_TYPE_STANDARD) {
        scanner.args = []
      }
      return scanner
    }
  }
}
</script>

<style scoped>
.button-new-ext {
  height: 32px;
  line-height: 30px;
  padding-top: 0;
  padding-bottom: 0;
}
.input-new-ext {
  width: 90px;
  vertical-align: bottom;
}
</style>
