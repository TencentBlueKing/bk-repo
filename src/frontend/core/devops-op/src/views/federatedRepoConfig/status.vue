<template>
  <div class="app-container">
    <el-form ref="form" :inline="true" :model="clientQuery">
      <el-form-item ref="project-form-item" label="查询类型" style="margin-left: 10px">
        <el-select
          v-model="type"
          style="margin-left: 10px;"
          placeholder="请选择"
        >
          <el-option
            v-for="item in typeOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
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
        label="仓库"
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
        >
          <template slot-scope="{ item }">
            <div>{{ item.name }}</div>
          </template>
        </el-autocomplete>
      </el-form-item>
      <el-form-item label="联邦仓库ID" style="margin-left: 15px" prop="federationId" :rules="[{ required: type === 'members', message: '联邦仓库ID不能为空'}]">
        <el-input v-model="clientQuery.federationId" />
      </el-form-item>
      <el-form-item>
        <el-button
          size="mini"
          type="primary"
          @click="doQuery()"
        >查询</el-button>
      </el-form-item>
    </el-form>
    <el-table
      ref="statusTable"
      v-loading="loading"
      :data="statusRecords"
      style="width: 100%; margin-top: 20px"
      :max-height="825"
    >
      <el-table-column v-if="type === 'repository'" type="expand">
        <template slot-scope="props">
          <el-table
            :data="props.row.members"
          >
            <!-- 基础信息 -->
            <el-table-column prop="clusterName" label="集群名称" min-width="120" />
            <el-table-column prop="clusterId" label="集群ID" min-width="150" />
            <el-table-column prop="clusterUrl" label="集群URL" min-width="200" />
            <el-table-column prop="projectId" label="项目ID" min-width="100" />
            <el-table-column prop="repoName" label="仓库名称" min-width="120" />
            <el-table-column prop="taskKey" label="任务Key" min-width="180"/>

            <!-- 状态信息 -->
            <el-table-column prop="status" label="成员状态" min-width="100" />
            <el-table-column prop="enabled" label="是否启用" width="80" align="center">
              <template slot-scope="{ row }">
                {{ row.enabled ? '是' : '否' }}
              </template>
            </el-table-column>
            <el-table-column prop="connected" label="是否可连接" width="90" align="center">
              <template slot-scope="{ row }">
                {{ row.connected ? '是' : '否' }}
              </template>
            </el-table-column>

            <!-- 时间信息 -->
            <el-table-column prop="lastSyncTime" label="最后同步时间" min-width="160">
              <template slot-scope="{ row }">
                {{ formatNormalDate(row.lastSyncTime) }}
              </template>
            </el-table-column>
            <el-table-column prop="lastConnectTime" label="最后连接时间" min-width="160">
              <template slot-scope="{ row }">
                {{ formatNormalDate(row.lastConnectTime) }}
              </template>
            </el-table-column>

            <!-- 延迟统计 -->
            <el-table-column prop="eventLag" label="事件延迟" width="90" align="right" />
            <el-table-column prop="fileLag" label="文件延迟" width="90" align="right" />
            <el-table-column prop="failureCount" label="失败记录数" width="100" align="right" />

            <!-- 错误信息 -->
            <el-table-column prop="errorMessage" label="错误信息" min-width="200" show-overflow-tooltip>
              <template slot-scope="{ row }">
                {{ row.errorMessage || '-' }}
              </template>
            </el-table-column>

            <!-- 制品同步统计 -->
            <el-table-column prop="totalSyncArtifacts" label="总制品数" width="100" align="right" />
            <el-table-column prop="successSyncArtifacts" label="成功制品数" width="100" align="right" />
            <el-table-column prop="failedSyncArtifacts" label="失败制品数" width="100" align="right" />

            <!-- 文件同步统计 -->
            <el-table-column prop="totalSyncFiles" label="总文件数" width="100" align="right" />
            <el-table-column prop="successSyncFiles" label="成功文件数" width="100" align="right" />
            <el-table-column prop="failedSyncFiles" label="失败文件数" width="100" align="right" />

            <!-- 传输统计 -->
            <el-table-column prop="syncedBytes" label="已同步量" min-width="120" align="right">
            </el-table-column>
            <el-table-column prop="avgSyncRate" label="平均速率" min-width="120" align="right">
            </el-table-column>
          </el-table>
        </template>
      </el-table-column>
      <!-- 基础信息 -->
      <el-table-column v-if="type==='repository'" prop="federationName" label="联邦名称" min-width="120" />
      <el-table-column prop="repoName" label="仓库名称" width="120" />
      <el-table-column prop="projectId" label="项目ID" width="100" />
      <el-table-column v-if="type==='repository'" prop="currentClusterName" label="当前集群" min-width="120" />

      <!-- 成员计数（仅数字） -->
      <el-table-column v-if="type==='repository'" prop="totalMembers" label="成员总数" width="80" align="right" />
      <el-table-column v-if="type==='repository'" prop="healthyMembers" label="健康成员" width="80" align="right" />
      <el-table-column v-if="type==='repository'" prop="delayedMembers" label="延迟成员" width="80" align="right" />
      <el-table-column v-if="type==='repository'" prop="errorMembers" label="错误成员" width="80" align="right" />
      <el-table-column v-if="type==='repository'" prop="disabledMembers" label="禁用成员" width="80" align="right" />

      <el-table-column v-if="type==='members'" prop="clusterId" label="集群ID" width="80" align="right" />
      <el-table-column v-if="type==='members'" prop="clusterId" label="集群名称" width="80" align="right" />
      <el-table-column v-if="type==='members'" prop="clusterUrl" label="集群URL" width="80" align="right" />
      <el-table-column v-if="type==='members'" prop="taskKey" label="任务key" width="80" align="right" />
      <el-table-column v-if="type==='members'" prop="status" label="成员状态" width="80" align="right" />
      <el-table-column v-if="type==='members'" prop="enabled" label="是否启用" width="80" align="right">
        <template slot-scope="scope">
          {{ scope.row.enabled ? '是': '否'}}
        </template>
      </el-table-column>
      <el-table-column v-if="type==='members'" prop="connected" label="是否可连接" width="100" align="right">
        <template slot-scope="scope">
          {{ scope.row.connected ? '是': '否'}}
        </template>
      </el-table-column>
      <el-table-column v-if="type==='members'" prop="lastSyncTime" label="最后同步时间" width="120" align="right">
        <template slot-scope="scope">
          {{ formatNormalDate(scope.row.lastSyncTime) }}
        </template>
      </el-table-column>
      <el-table-column v-if="type==='members'" prop="lastConnectTime" label="最后连接时间" width="120" align="right">
        <template slot-scope="scope">
          {{ formatNormalDate(scope.row.lastConnectTime) }}
        </template>
      </el-table-column>
      <el-table-column v-if="type==='members'" prop="errorMessage" label="错误信息" width="80" align="right" />
      <el-table-column v-if="type==='members'" prop="syncedBytes" label="已同步字节数" width="110" align="right" />
      <el-table-column v-if="type==='members'" prop="avgSyncRate" label="同步平均速率" width="110" align="right" />

      <!-- 全量同步状态 -->
      <el-table-column v-if="type==='repository'" label="全量同步状态" width="120">
        <template slot-scope="scope">
          {{ scope.row.isFullSyncing ? '同步中' : '空闲'}}
        </template>
      </el-table-column>
      <el-table-column v-if="type==='repository'" prop="lastFullSyncStartTime" label="上次全量开始时间" min-width="160">
        <template slot-scope="scope">
          {{ formatNormalDate(scope.row.lastFullSyncStartTime) }}
        </template>
      </el-table-column>
      <el-table-column v-if="type==='repository'" prop="lastFullSyncEndTime" label="上次全量结束时间" min-width="160">
        <template slot-scope="scope">
          {{ formatNormalDate(scope.row.lastFullSyncEndTime) }}
        </template>
      </el-table-column>
      <el-table-column v-if="type==='repository'" prop="fullSyncDuration" label="全量耗时" width="100" align="right">
      </el-table-column>

      <!-- 延迟统计 -->
      <el-table-column prop="fileLag" label="文件延迟" width="90" align="right" />
      <el-table-column prop="eventLag" label="事件延迟" width="90" align="right" />
      <el-table-column prop="failureCount" label="失败记录数" width="100" align="right" />

      <!-- 制品同步统计 -->
      <el-table-column prop="totalSyncArtifacts" label="总制品数" width="100" align="right" />
      <el-table-column prop="successSyncArtifacts" label="成功制品数" width="100" align="right" />
      <el-table-column prop="failedSyncArtifacts" label="失败制品数" width="100" align="right" />

      <!-- 文件同步统计 -->
      <el-table-column prop="totalSyncFiles" label="总文件数" width="100" align="right" />
      <el-table-column prop="successSyncFiles" label="成功文件数" width="100" align="right" />
      <el-table-column prop="failedSyncFiles" label="失败文件数" width="100" align="right" />

      <!-- 传输统计 -->
      <el-table-column v-if="type==='repository'" prop="totalBytesTransferred" label="总传输量" min-width="120" align="right">
      </el-table-column>
      <el-table-column v-if="type==='repository'" prop="avgTransferRate" label="平均速率" min-width="120" align="right">
      </el-table-column>

      <!-- 时间信息 -->
      <el-table-column v-if="type==='repository'" prop="createdDate" label="创建时间" min-width="160">
        <template slot-scope="scope">
          {{ formatNormalDate(scope.row.createdDate) }}
        </template>
      </el-table-column>
      <el-table-column v-if="type==='repository'" prop="lastModifiedDate" label="最后修改时间" min-width="160">
        <template slot-scope="scope">
          {{ formatNormalDate(scope.row.lastModifiedDate) }}
        </template>
      </el-table-column>
      <el-table-column align="center">
        <template v-if="type === 'members'" slot="header">
          <el-button type="primary" @click="doFresh()">刷新</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script>

import { formatNormalDate } from '@/utils/date'
import { searchProjects } from '@/api/project'
import { listRepositories } from '@/api/repository'
import { getMemberStatus, getRepoStatus, refreshMember } from '@/api/federatedStatus'

export default {
  name: 'Event',
  inject: ['reload'],
  beforeRouteUpdate(to, from, next) {
    this.onRouteUpdate(to)
    next()
  },
  data() {
    return {
      loading: false,
      type: 'repository',
      typeOptions: [
        {
          value: 'repository',
          label: '仓库'
        },
        {
          value: 'members',
          label: '成员'
        }
      ],
      statusRecords: [],
      clientQuery: {
        projectId: '',
        repoName: '',
        federationId: ''
      },
      repoCache: {}
    }
  },
  methods: {
    formatNormalDate,
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
      this.$refs['repo-form-item'].resetField()
      this.clientQuery.repoName = repo.name
    },
    doFilter(arr, queryStr) {
      return queryStr ? arr.filter(obj => {
        return obj.name.toLowerCase().indexOf(queryStr.toLowerCase()) !== -1
      }) : arr
    },
    doQuery() {
      this.$refs['form'].validate((valid) => {
        if (valid) {
          if (this.type === 'repository') {
            getRepoStatus(this.clientQuery).then((res) => {
              this.res = res.data
            })
          } else {
            getMemberStatus(this.clientQuery).then((res) => {
              this.res = res.data
            })
          }
        }
      })
    },
    doFresh() {
      this.$refs['form'].validate((valid) => {
        if (valid) {
          refreshMember(this.clientQuery).then(() => {
            this.$message.success('刷新成功')
            getMemberStatus(this.clientQuery).then((res) => {
              this.res = res.data
            })
          })
        }
      })
    }
  }
}
</script>

<style scoped>

</style>
