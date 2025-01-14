<template>
  <div class="app-container node-container">
    <el-form ref="form" :rules="rules" :inline="true" :model="clientQuery">
      <el-form-item label="查询模式" style="margin-left: 15px">
        <el-cascader
          v-model="clientQuery.type"
          :options="cascaderOptions"
          :props="{ expandTrigger: 'hover' }"
          :show-all-levels="false"
        />
      </el-form-item>
      <el-form-item ref="project-form-item" label="项目ID" prop="projectId" :rules="[{ required: true, message: '项目ID不能为空'}]">
        <el-autocomplete
          v-model="clientQuery.projectId"
          class="inline-input"
          :fetch-suggestions="queryProjects"
          placeholder="请输入项目ID"
          size="mini"
          @select="selectProject"
        >
          <template slot-scope="{ item }">
            <div>{{ item.name }}</div>
          </template>
        </el-autocomplete>
      </el-form-item>
      <el-form-item
        ref="repo-form-item"
        style="margin-left: 15px"
        label="仓库名称"
        prop="repoName"
        :rules="[{ required: true, message: '仓库名称不能为空'}]"
      >
        <el-autocomplete
          v-model="clientQuery.repoName"
          class="inline-input"
          :fetch-suggestions="queryRepositories"
          :disabled="!clientQuery.projectId"
          placeholder="请输入仓库名"
          size="mini"
          @select="selectRepo"
          @change="handleRepoChange"
        >
          <template slot-scope="{ item }">
            <div>{{ item.name }}</div>
          </template>
        </el-autocomplete>
      </el-form-item>
      <el-form-item
        v-if="clientQuery.type[1] === 'node' || clientQuery.type[1] === 'coldNode'"
        style="margin-left: 15px"
        label="文件路径"
        :rules="[{ required: clientQuery.type[1] === 'node' || clientQuery.type[1] === 'coldNode', message: '文件路径不能为空'}]"
        prop="fullPath"
      >
        <el-input
          v-model="clientQuery.fullPath"
          placeholder="请输入"
          style="height: 40px; width: 120px"
        />
      </el-form-item>
      <el-form-item
        v-if="clientQuery.type[1] === 'coldVersion' || clientQuery.type[1] === 'version'"
        style="margin-left: 15px"
        label="包KEY"
        :rules="[{ required: clientQuery.type[1] === 'coldVersion' || clientQuery.type[1] === 'version', message: '包KEY不能为空'}]"
        prop="packageKey"
      >
        <el-input
          v-model="clientQuery.packageKey"
          placeholder="请输入"
          style="height: 40px; width: 120px"
        />
      </el-form-item>
      <el-form-item
        v-if="clientQuery.type[1] === 'coldVersion'"
        style="margin-left: 15px"
        label="包版本"
        :rules="[{ required: clientQuery.type[1] === 'coldVersion', message: '包版本不能为空'}]"
        prop="version"
      >
        <el-input
          v-model="clientQuery.version"
          placeholder="请输入"
          style="height: 40px; width: 120px"
        />
      </el-form-item>
      <el-form-item
        v-if="clientQuery.type[1] === 'node' || clientQuery.type[1] === 'version'"
        label="降冷时间"
        :rules="[{ required: clientQuery.type[1] === 'node' || clientQuery.type[1] === 'version', message: '降冷时间不能为空'}]"
        prop="separationDate"
      >
        <el-select v-model="clientQuery.separationDate" placeholder="请选择">
          <el-option
            v-for="item in separationDates"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item
        v-if="clientQuery.type[1] === 'version'"
        style="margin-left: 15px"
        label="包版本"
        prop="versionOption.version"
      >
        <el-input
          v-model="clientQuery.versionOption.version"
          placeholder="请输入"
          style="height: 40px; width: 120px"
        />
      </el-form-item>
      <el-form-item
        v-if="clientQuery.type[1] === 'package'"
        style="margin-left: 15px"
        label="包名"
        prop="packageName"
      >
        <el-input
          v-model="clientQuery.packageOption.packageName"
          placeholder="请输入"
          style="height: 40px; width: 120px"
        />
      </el-form-item>
      <el-form-item v-if="clientQuery.type[1] === 'node'" label="包含目录" style="margin-left: 15px">
        <el-select v-model="clientQuery.nodeOption.includeFolder" placeholder="请选择" style="width: 180px">
          <el-option
            v-for="item in foldOption"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item v-if="clientQuery.type[1] === 'node'" label="包含元数据" style="margin-left: 15px">
        <el-select v-model="clientQuery.nodeOption.includeMetadata" placeholder="请选择" style="width: 180px">
          <el-option
            v-for="item in metaDataOption"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item v-if="clientQuery.type[1] === 'node'" label="深度查询文件" style="margin-left: 15px">
        <el-select v-model="clientQuery.nodeOption.deep" placeholder="请选择" style="width: 180px">
          <el-option
            v-for="item in deepOption"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button
          size="mini"
          type="primary"
          :disabled="!clientQuery.projectId"
          @click="handleCurrentChange(1)"
        >查询</el-button>
      </el-form-item>
    </el-form>
    <el-table v-loading="loading" :data="dataInfos" style="width: 100%">
      <el-table-column v-if="clientQuery.type[1] === 'coldNode' || clientQuery.type[1] === 'node'" prop="name" label="资源名称" align="center" />
      <el-table-column v-if="clientQuery.type[1] === 'coldNode' || clientQuery.type[1] === 'node'" prop="path" label="路径" align="center" />
      <el-table-column v-if="clientQuery.type[1] === 'coldNode' || clientQuery.type[1] === 'node'" prop="folder" label="是否为文件夹" align="center">
        <template slot-scope="scope">
          <span>
            {{ scope.row.folder === true ? '是': '否' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column v-if="clientQuery.type[1] === 'coldNode' || clientQuery.type[1] === 'node'" prop="size" label="文件大小" align="center">
        <template slot-scope="scope">
          <span>
            {{ convertFileSize(scope.row.size) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column v-if="clientQuery.type[1] === 'coldNode' || clientQuery.type[1] === 'node'" prop="nodeNum" label="文件节点个数" align="center" />
      <el-table-column v-if="clientQuery.type[1] === 'coldNode' || clientQuery.type[1] === 'node'" prop="sha256" label="SHA256" align="center" />
      <el-table-column v-if="clientQuery.type[1] === 'coldNode' || clientQuery.type[1] === 'node'" prop="archived" label="是否归档" align="center">
        <template slot-scope="scope">
          <span>
            {{ scope.row.archived === true ? '是': '否' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column v-if="clientQuery.type[1] === 'coldNode' || clientQuery.type[1] === 'node'" prop="compressed" label="是否压缩" align="center">
        <template slot-scope="scope">
          <span>
            {{ scope.row.compressed === true ? '是': '否' }}
          </span>
        </template>
      </el-table-column>

      <el-table-column v-if="clientQuery.type[1] === 'coldVersion' || clientQuery.type[1] === 'version'" prop="name" label="包版本" align="center" />
      <el-table-column v-if="clientQuery.type[1] === 'coldVersion' || clientQuery.type[1] === 'version'" prop="size" label="包大小" align="center">
        <template slot-scope="scope">
          <span>
            {{ convertFileSize(scope.row.size) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column v-if="clientQuery.type[1] === 'coldVersion' || clientQuery.type[1] === 'version'" prop="downloads" label="下载次数" align="center" />
      <el-table-column v-if="clientQuery.type[1] === 'coldVersion' || clientQuery.type[1] === 'version'" prop="tags" label="标签" align="center" />
      <el-table-column v-if="clientQuery.type[1] === 'coldVersion' || clientQuery.type[1] === 'version'" prop="manifestPath" label="清单文件路径" align="center" />

      <el-table-column v-if="clientQuery.type[1] === 'package'" prop="name" label="包名称" align="center" />
      <el-table-column v-if="clientQuery.type[1] === 'package'" prop="key" label="包KEY" align="center" />
      <el-table-column v-if="clientQuery.type[1] === 'package'" prop="type" label="包类型" align="center" />
      <el-table-column v-if="clientQuery.type[1] === 'package'" prop="latest" label="最新版名称" align="center" />
      <el-table-column v-if="clientQuery.type[1] === 'package'" prop="downloads" label="下载次数" align="center" />
      <el-table-column v-if="clientQuery.type[1] === 'package'" prop="versions" label="版本数量" align="center" />
      <el-table-column v-if="clientQuery.type[1] === 'package'" prop="description" label="描述" align="center" />
    </el-table>
    <div style="margin-top:20px">
      <el-pagination
        v-if="total>0 && clientQuery.type[1] !== 'coldNode' && clientQuery.type[1] !== 'coldVersion'"
        :current-page="clientQuery.pageNumber"
        :page-size="clientQuery.pageSize"
        layout="total, prev, pager, next, jumper"
        :total="total"
        @current-change="handleCurrentChange"
      />
    </div>
  </div>
</template>
<script>
import { searchProjects } from '@/api/project'
import { listRepositories } from '@/api/repository'
import { formatNormalDate } from '@/utils/date'
import { convertFileSize } from '@/utils/file'
import {
  queryColdNodeData,
  queryColdVersionData,
  queryNodeData,
  queryPackageData, querySeparateTask,
  queryVersionData
} from '@/api/separate'

export default {
  name: 'PreloadConfig',
  inject: ['reload'],
  beforeRouteUpdate(to, from, next) {
    this.onRouteUpdate(to)
    next()
  },
  data() {
    return {
      loading: false,
      projects: undefined,
      repoCache: {},
      rules: {},
      total: 0,
      clientQuery: {
        projectId: '',
        repoName: '',
        type: [
          'detail',
          'coldNode'
        ],
        fullPath: '',
        packageKey: '',
        version: '',
        separationDate: '',
        packageOption: {
          pageNumber: 1,
          pageSize: 20,
          currentPage: 1,
          packageName: ''
        },
        versionOption: {
          pageNumber: 1,
          pageSize: 20,
          currentPage: 1,
          version: ''
        },
        nodeOption: {
          pageNumber: 1,
          pageSize: 20,
          currentPage: 1,
          includeFolder: true,
          includeMetadata: false,
          deep: false
        }
      },
      cascaderOptions: [
        {
          value: 'detail',
          label: '详细查询',
          children: [
            {
              value: 'coldNode',
              label: '查询冷表中节点信息'
            },
            {
              value: 'coldVersion',
              label: '查询冷表中版本信息'
            }
          ]
        },
        {
          value: 'page',
          label: '分页查询',
          children: [
            {
              value: 'package',
              label: '分页查询包'
            },
            {
              value: 'version',
              label: '分页查询版本'
            },
            {
              value: 'node',
              label: '分页查询节点'
            }
          ]
        }
      ],
      dataInfos: [],
      foldOption: [
        { label: '是', value: true },
        { label: '否', value: false }
      ],
      metaDataOption: [
        { label: '是', value: true },
        { label: '否', value: false }
      ],
      deepOption: [
        { label: '是', value: true },
        { label: '否', value: false }
      ],
      separationDates: []
    }
  },
  watch: {
    'clientQuery.type'(newType) {
      this.dataInfos = []
      this.total = 0
      this.clientQuery.packageOption.pageNumber = 1
      this.clientQuery.packageOption.currentPage = 1
      this.clientQuery.versionOption.pageNumber = 1
      this.clientQuery.versionOption.currentPage = 1
      this.clientQuery.nodeOption.pageNumber = 1
      this.clientQuery.nodeOption.currentPage = 1
    }
  },
  methods: {
    queryProjects(queryStr, cb) {
      searchProjects(queryStr).then(res => {
        this.projects = res.data.records
        cb(this.projects)
      })
    },
    selectProject(project) {
      this.$refs['project-form-item'].resetField()
      this.clientQuery.projectId = project.name
    },
    queryRepositories(queryStr, cb) {
      let repositories = this.repoCache[this.clientQuery.projectId]
      if (!repositories) {
        listRepositories(this.clientQuery.projectId).then(res => {
          repositories = res.data
          this.repoCache[this.clientQuery.projectId] = repositories
          cb(this.doFilter(repositories, queryStr))
        })
      } else {
        cb(this.doFilter(repositories, queryStr))
      }
    },
    selectRepo(repo) {
      this.clientQuery.separationDate = ''
      this.clientQuery.separationDates = []
      this.$refs['repo-form-item'].resetField()
      this.clientQuery.repoName = repo.name
      // 搜索当前repo的降冷任务配置的时间，将时间带入，没有就没有
      const query = {
        projectId: this.clientQuery.projectId
      }
      query.projectId = this.clientQuery.projectId
      query.repoName = this.clientQuery.repoName
      query.taskType = 'SEPARATE'
      querySeparateTask(query).then(res => {
        if (res.data.totalRecords > 0) {
          res.data.records.forEach(record => {
            const data = {
              label: formatNormalDate(record.separationDate),
              value: record.separationDate
            }
            this.separationDates.push(data)
          })
          this.clientQuery.separationDate = res.data.records[0].separationDate
        }
      })
    },
    handleRepoChange(repo) {
      this.clientQuery.separationDate = ''
      this.clientQuery.separationDates = []
      if (repo !== '') {
        // 搜索当前repo的降冷任务配置的时间，将时间带入，没有就没有
        const query = {
          projectId: this.clientQuery.projectId
        }
        query.projectId = this.clientQuery.projectId
        query.repoName = this.clientQuery.repoName
        query.taskType = 'SEPARATE'
        querySeparateTask(query).then(res => {
          if (res.data.totalRecords > 0) {
            res.data.records.forEach(record => {
              const data = {
                label: formatNormalDate(record.separationDate),
                value: record.separationDate
              }
              this.separationDates.push(data)
            })
            this.clientQuery.separationDate = res.data.records[0].separationDate
          }
        })
      }
    },
    doFilter(arr, queryStr) {
      return queryStr ? arr.filter(obj => {
        return obj.name.toLowerCase().indexOf(queryStr.toLowerCase()) !== -1
      }) : arr
    },
    handleCurrentChange(val) {
      this.clientQuery.currentPage = val
      this.queryClients(this.clientQuery)
    },
    queryClients(clientQuery) {
      if (this.$refs['form']) {
        this.$refs['form'].validate((valid) => {
          if (valid) {
            this.doQueryClients(clientQuery)
          } else {
            return false
          }
        })
      }
    },
    doQueryClients(clientQuery) {
      let promise = null
      const param = clientQuery
      if (clientQuery.type[1] === 'node') {
        if (param.separationDate instanceof Date && !isNaN(param.separationDate)) {
          param.separationDate = param.separationDate.toISOString()
        }
        promise = queryNodeData(param)
      } else if (clientQuery.type[1] === 'package') {
        if (param.packageOption.packageName === '') {
          param.packageOption.packageName = null
        }
        promise = queryPackageData(param)
      } else if (this.clientQuery.type[1] === 'version') {
        if (param.separationDate instanceof Date && !isNaN(param.separationDate)) {
          param.separationDate = param.separationDate.toISOString()
        }
        if (param.versionOption.version === '') {
          param.versionOption.version = null
        }
        promise = queryVersionData(param)
      } else if (clientQuery.type[1] === 'coldNode') {
        promise = queryColdNodeData(param)
      } else {
        promise = queryColdVersionData(param)
      }
      promise.then(res => {
        if (res.data) {
          this.dataInfos = res.data.records ? res.data.records : [res.data]
          this.total = res.data.totalRecords ? res.data.totalRecords : res.data.length
        }
        this.$message({
          message: '数据已查询完毕',
          type: 'success'
        })
      }).catch(e => {
        this.$message.error('查询异常')
      })
    },
    formatNormalDate(data) {
      return formatNormalDate(data)
    },
    convertFileSize(size) {
      return convertFileSize(size)
    },
    updated() {
      this.reload()
    }
  }
}
</script>

<style scoped>
</style>

<style>
</style>
