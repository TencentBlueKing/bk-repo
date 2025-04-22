<template>
  <el-dialog :title="createMode ? '创建规则' : '更新规则'" :visible.sync="showDialog" :before-close="close">
    <el-form ref="form" :rules="rules" :model="ignoreRule" status-icon>
      <el-form-item label="规则名" :required="true" prop="name">
        <el-input v-model="ignoreRule.name" placeholder="请输入规则名" />
      </el-form-item>
      <el-form-item label="规则描述" :required="false" prop="description">
        <el-input v-model="ignoreRule.description" placeholder="请输入规则描述" type="textarea" />
      </el-form-item>
      <el-form-item label="生效的项目ID" :required="false" prop="projectIds">
        <br>
        <el-select
          v-if="!includeAllProjects"
          v-model="ignoreRule.projectIds"
          style="margin-right: 20px"
          multiple
          filterable
          remote
          allow-create
          placeholder="请选择规则目标项目"
          :remote-method="queryProjects"
        >
          <el-option
            v-for="item in projects"
            :key="item.name"
            :label="item.name"
            :value="item.name"
          />
        </el-select>
        <el-checkbox v-model="includeAllProjects">对所有项目生效</el-checkbox>
      </el-form-item>
      <el-form-item label="仓库名" :required="false" prop="repoName">
        <el-input v-model="ignoreRule.repoName" placeholder="请输入仓库名" />
      </el-form-item>
      <el-form-item label="路径" :required="false" prop="fullPath">
        <el-input v-model="ignoreRule.fullPath" placeholder="请输入路径正则表达式，例如：/.*\.jar" />
      </el-form-item>
      <el-form-item label="包名" :required="false" prop="packageKey">
        <el-input v-model="ignoreRule.packageKey" placeholder="请输入包名，例如：docker://nginx" />
      </el-form-item>
      <el-form-item label="包版本" :required="false" prop="packageVersion">
        <el-input v-model="ignoreRule.packageVersion" placeholder="请输入包版本，例如：1.7" />
      </el-form-item>
      <el-form-item label="规则类型" prop="type">
        <el-radio-group v-model="ignoreRule.type">
          <el-radio :label="RULE_TYPE_IGNORE">忽略</el-radio>
          <el-radio :label="RULE_TYPE_INCLUDE" @change="ignoreRule.severity = null;ignoreAllVul = false">保留</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="过滤方式">
        <el-select v-model="selectedFilterMethod" placeholder="请选择过滤方式" @change="filterMethodChanged">
          <el-option v-for="m in filterMethods" :key="m.type" :disabled="ignoreRule.type === RULE_TYPE_INCLUDE && m.type === FILTER_METHOD_SEVERITY" :value="m.type" :label="m.name" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="selectedFilterMethod === FILTER_METHOD_SEVERITY && ignoreRule.type === RULE_TYPE_IGNORE && !ignoreAllVul" label="级别" :required="false" prop="severity">
        <el-select v-model="ignoreRule.severity" placeholder="请选择最小漏洞等级">
          <el-option v-for="s in severities" :key="s.level" :value="s.level" :label="s.name" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="selectedFilterMethod === FILTER_METHOD_VUL_ID" label="漏洞ID" :required="false" prop="vulIds">
        <el-checkbox v-if="ignoreRule.type === RULE_TYPE_IGNORE" v-model="ignoreAllVul">忽略全部漏洞</el-checkbox>
        <el-input v-if="!ignoreAllVul" v-model="vulIds" placeholder="请输入漏洞ID，多个漏洞ID通过换行分隔" type="textarea" />
      </el-form-item>
      <el-form-item v-if="selectedFilterMethod === FILTER_METHOD_RISKY_COMPONENT" label="风险组件名" :required="false" prop="riskyPackageKeys">
        <el-input v-model="riskyPackageKeys" :autosize="true" placeholder="请输入风险组件名，多个组件通过换行分隔" type="textarea" />
      </el-form-item>
      <el-form-item v-if="selectedFilterMethod === FILTER_METHOD_RISKY_COMPONENT_VERSION" label="风险组件版本" :required="false" prop="riskyPackageVersions">
        <el-input v-model="riskyPackageVersions" :autosize="true" placeholder="请输入风险组件版本范围，多个组件通过换行分隔，例如org.springframework:spring-messaging >=5.0,<=5.0.4;>=4.3,<=4.3.15;<=4.0.0;>=4.0,<4.3" type="textarea" />
      </el-form-item>
    </el-form>
    <div slot="footer">
      <el-button @click="close">取 消</el-button>
      <el-button type="primary" @click="handleCreateOrUpdate(ignoreRule)">确 定</el-button>
    </div>
  </el-dialog>
</template>

<script>
import _ from 'lodash'
import {
  createFilterRule,
  FILTER_METHOD_RISKY_COMPONENT, FILTER_METHOD_RISKY_COMPONENT_VERSION, FILTER_METHOD_SEVERITY, FILTER_METHOD_VUL_ID,
  RULE_TYPE_IGNORE,
  RULE_TYPE_INCLUDE,
  updateFilterRule
} from '@/api/scan'
import { searchProjects } from '@/api/project'
export default {
  name: 'CreateOrUpdateFilterRuleDialog',
  props: {
    visible: {
      type: Boolean,
      default: false
    },
    /**
     * 仅在更新模式时有值
     */
    updatingRule: {
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
      RULE_TYPE_IGNORE: RULE_TYPE_IGNORE,
      RULE_TYPE_INCLUDE: RULE_TYPE_INCLUDE,
      FILTER_METHOD_VUL_ID: FILTER_METHOD_VUL_ID,
      FILTER_METHOD_SEVERITY: FILTER_METHOD_SEVERITY,
      FILTER_METHOD_RISKY_COMPONENT: FILTER_METHOD_RISKY_COMPONENT,
      FILTER_METHOD_RISKY_COMPONENT_VERSION: FILTER_METHOD_RISKY_COMPONENT_VERSION,
      rules: {
        name: [
          {
            required: true,
            message: '请输入规则名',
            trigger: 'blur'
          },
          {
            regex: /^([\w_-]){1,255}$/,
            message: '规则名格式错误',
            trigger: 'blur'
          }
        ]
      },
      showDialog: false,
      ignoreRule: this.newRule(),
      vulIds: '',
      riskyPackageKeys: '',
      riskyPackageVersions: '',
      ignoreAllVul: false,
      includeAllProjects: false,
      projects: [],
      severities: [
        {
          name: 'Critical',
          level: 3
        },
        {
          name: 'High',
          level: 2
        },
        {
          name: 'Medium',
          level: 1
        },
        {
          name: 'Low',
          level: 0
        }
      ],
      filterMethods: [
        {
          type: FILTER_METHOD_VUL_ID,
          name: '通过漏洞ID过滤'
        },
        {
          type: FILTER_METHOD_SEVERITY,
          name: '通过漏洞等级过滤'
        },
        {
          type: FILTER_METHOD_RISKY_COMPONENT,
          name: '通过风险组件名过滤'
        },
        {
          type: FILTER_METHOD_RISKY_COMPONENT_VERSION,
          name: '通过风险组件版本过滤'
        }
      ],
      selectedFilterMethod: FILTER_METHOD_VUL_ID
    }
  },
  watch: {
    includeAllProjects: function(newVal) {
      if (newVal) {
        this.ignoreRule.projectIds = []
      }
    },
    visible: function(newVal) {
      if (newVal) {
        this.reset()
        this.showDialog = true
      } else {
        this.close()
      }
    }
  },
  methods: {
    filterMethodChanged(newVal) {
      if (newVal !== FILTER_METHOD_VUL_ID) {
        this.vulIds = ''
        this.ignoreAllVul = false
      }
      if (newVal !== FILTER_METHOD_SEVERITY) {
        this.ignoreRule.severity = undefined
      }
      if (newVal !== FILTER_METHOD_RISKY_COMPONENT) {
        this.riskyPackageKeys = ''
      }
      if (newVal !== FILTER_METHOD_RISKY_COMPONENT_VERSION) {
        this.riskyPackageVersions = ''
      }
    },
    close() {
      this.showDialog = false
      this.$emit('update:visible', false)
    },
    queryProjects(queryStr) {
      console.log('query')
      searchProjects(queryStr).then(res => {
        this.projects = res.data.records
      })
    },
    handleCreateOrUpdate(ignoreRule) {
      if (this.includeAllProjects) {
        this.ignoreRule.projectIds = []
      } else if (this.ignoreRule.projectIds.length === 0) {
        this.ignoreRule.projectIds = null
      }

      if (this.ignoreAllVul) {
        this.ignoreRule.vulIds = []
      } else {
        this.ignoreRule.vulIds = this.vulIds ? this.vulIds.trim().split('\n') : null
      }

      if (this.riskyPackageKeys) {
        this.ignoreRule.riskyPackageKeys = this.riskyPackageKeys.trim().split('\n')
      }

      if (this.riskyPackageVersions) {
        this.ignoreRule.riskyPackageVersions = {}
        const versions = this.riskyPackageVersions.trim().split(/\n+/)
        versions.forEach(v => {
          const trimVer = v.trim()
          const indexOfSpace = trimVer.indexOf(' ')
          this.ignoreRule.riskyPackageVersions[trimVer.substring(0, indexOfSpace)] = trimVer.substring(indexOfSpace).trim()
        })
      }

      this.$refs['form'].validate((valid) => {
        if (valid) {
          // 根据是否为创建模式发起不同请求
          let reqPromise
          let msg
          let eventName
          if (this.createMode) {
            reqPromise = createFilterRule(ignoreRule)
            msg = '创建成功'
            eventName = 'created'
          } else {
            reqPromise = updateFilterRule(ignoreRule)
            msg = '更新成功'
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
    reset() {
      if (this.createMode) {
        this.ignoreRule = this.newRule()
      } else {
        this.ignoreRule = _.cloneDeep(this.updatingRule)
      }

      this.includeAllProjects = this.ignoreRule.projectIds && this.ignoreRule.projectIds.length === 0

      if (this.ignoreRule.severity) {
        this.selectedFilterMethod = FILTER_METHOD_SEVERITY
      } else if (this.ignoreRule.riskyPackageKeys) {
        this.selectedFilterMethod = FILTER_METHOD_RISKY_COMPONENT
      } else if (this.ignoreRule.riskyPackageVersions) {
        this.selectedFilterMethod = FILTER_METHOD_RISKY_COMPONENT_VERSION
      } else {
        this.selectedFilterMethod = FILTER_METHOD_VUL_ID
      }

      if (this.ignoreRule.riskyPackageVersions) {
        const pkgVersionRange = []
        for (const pkg in this.ignoreRule.riskyPackageVersions) {
          pkgVersionRange.push(`${pkg} ${this.ignoreRule.riskyPackageVersions[pkg]}`)
        }
        this.riskyPackageVersions = pkgVersionRange.join('\n')
      } else {
        this.riskyPackageVersions = ''
      }

      this.riskyPackageKeys = this.ignoreRule.riskyPackageKeys ? this.ignoreRule.riskyPackageKeys.join('\n') : ''
      this.vulIds = this.ignoreRule.vulIds ? this.ignoreRule.vulIds.join('\n') : ''
      this.ignoreAllVul = this.ignoreRule.vulIds !== undefined && this.ignoreRule.vulIds !== null && this.ignoreRule.vulIds.length === 0
    },
    newRule() {
      return {
        type: RULE_TYPE_IGNORE,
        name: '',
        description: '',
        projectId: '',
        projectIds: []
      }
    }
  }
}
</script>

<style scoped>

</style>
