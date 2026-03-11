<template>
  <el-dialog :title="createMode ? '创建联邦仓库' : '更新联邦仓库'" :visible.sync="showDialog" :before-close="close">
    <el-form ref="form" :rules="rules" :model="repoConfig" status-icon>
      <el-form-item label="名称" prop="name" :rules="[{ required: true, message: '名称不能为空'}]">
        <el-input v-model="repoConfig.name" style="height: 40px ; width: 400px;" />
      </el-form-item>
      <el-form-item label="联邦仓库ID" prop="federationId" :rules="[{ required: true, message: '联邦仓库ID不能为空'}]">
        <el-input v-model="repoConfig.federationId" style="height: 40px ; width: 400px;" />
      </el-form-item>
      <el-form-item label="项目名" prop="projectId" :rules="[{ required: true, message: '项目名不能为空'}]">
        <el-input v-model="repoConfig.projectId" style="height: 40px ; width: 400px;" />
      </el-form-item>
      <el-form-item label="仓库名" prop="repoName" :rules="[{ required: true, message: '仓库名不能为空'}]">
        <el-input v-model="repoConfig.repoName" style="height: 40px ; width: 400px;" />
      </el-form-item>
      <el-form-item label="集群ID" prop="clusterId" :rules="[{ required: true, message: '集群ID不能为空'}]">
        <el-input v-model="repoConfig.clusterId" style="height: 40px ; width: 400px;" />
      </el-form-item>
      <el-form-item
        v-for="(item,index) in repoConfig.federatedClusters"
        :key="index"
        label="集群配置"
        prop="target"
      >
        <div>
          <div>
            <span>项目名：</span>
            <el-input
              v-model="item.projectId"
              style="height: 40px ; width: 35%;"
              placeholder="请输入数据"
              @input="changeEnable"
            />
            <span style="margin-left: 5px">仓库名：</span>
            <el-input
              v-model="item.repoName"
              style="height: 40px ; width: 35%;"
              placeholder="请输入数据"
              @input="changeEnable"
            />
          </div>
          <div style="margin-top: 10px">
            <span>集群ID：</span>
            <el-input
              v-model="item.clusterId"
              style="height: 40px ; width: 35%;"
              placeholder="请输入数据"
              @input="changeEnable"
            />
            <span style="margin-left: 5px">是否启用：</span>
            <el-switch
              v-model="item.enabled"
            />
          </div>
          <div style="margin-top: 10px">
            <span>任务ID：</span>
            <el-input
              v-model="item.taskId"
              style="height: 40px ; width: 35%;"
              placeholder="请输入数据"
              @input="changeEnable"
            />
            <span style="margin-left: 5px">记录ID：</span>
            <el-input
              v-model="item.recordId"
              style="height: 40px ; width: 35%;"
              placeholder="请输入数据"
              @input="changeEnable"
            />
            <i
              class="el-icon-circle-close"
              style="color: red"
              @click.prevent="removeDomain(item)"
            />
            <i
              v-if="index === repoConfig.federatedClusters.length - 1"
              class="el-icon-circle-plus-outline"
              style="margin: 0px 20px"
              @click.prevent="addDomain()"
            />
          </div>
        </div>
      </el-form-item>
    </el-form>
    <div slot="footer">
      <el-button @click="close">取 消</el-button>
      <el-button type="primary" @click="handleUpdate()">确 定</el-button>
    </div>
  </el-dialog>
</template>
<script>
import _ from 'lodash'

import { createFederation, updateFederation } from '@/api/federatedRepository'

export default {
  name: 'EditRepoConfigDialog',
  props: {
    visible: Boolean,
    updatingRepoConfig: {
      type: Object,
      default: undefined
    },
    createMode: Boolean
  },
  data() {
    return {
      repoCache: {},
      showDialog: false,
      cleanValue: '',
      repoConfig: {
        name: '',
        federationId: '',
        projectId: '',
        repoName: '',
        clusterId: '',
        federatedClusters: [
          { projectId: '',
            repoName: '',
            clusterId: '',
            enabled: true,
            taskId: '',
            recordId: '' }
        ]
      },
      data: '',
      rules: {}
    }
  },
  watch: {
    visible: function(newVal) {
      if (newVal) {
        this.resetRepoConfig()
        this.showDialog = true
      } else {
        this.close()
      }
    }
  },
  methods: {
    resetRepoConfig() {
      if (!this.createMode) {
        this.repoConfig = _.cloneDeep(this.updatingRepoConfig)
        console.log(this.repoConfig)
        if (!this.repoConfig.federatedClusters || !this.repoConfig.federatedClusters.length) {
          this.repoConfig.federatedClusters = [
            { projectId: '',
              repoName: '',
              clusterId: '',
              enabled: true,
              taskId: '',
              recordId: '' }
          ]
        }
      }
      this.$nextTick(() => {
        this.$refs['form'].clearValidate()
      })
    },
    removeDomain(item) {
      this.$forceUpdate()
      const index = this.repoConfig.federatedClusters.indexOf(item)
      if (index !== -1 && this.repoConfig.federatedClusters.length !== 1) {
        this.repoConfig.federatedClusters.splice(index, 1)
      }
    },
    addDomain() {
      this.$forceUpdate()
      this.repoConfig.federatedClusters.push({
        projectId: '',
        repoName: '',
        clusterId: '',
        enabled: true,
        taskId: '',
        recordId: ''
      }
      )
    },
    close() {
      this.showDialog = false
      this.repoConfig = {
        federationId: '',
        projectId: '',
        repoName: '',
        federatedClusters: [
          { projectId: '',
            repoName: '',
            clusterId: '',
            enabled: true,
            taskId: '',
            recordId: '' }
        ]
      }
      this.$refs['form'].resetFields()
      this.$emit('update:visible', false)
    },
    handleUpdate() {
      this.$refs['form'].validate((valid) => {
        if (valid) {
          const federations = this.repoConfig
          const res = []
          for (let i = 0; i < federations.federatedClusters.length; i++) {
            if (federations.federatedClusters[i].projectId.trim() !== '' &&
              federations.federatedClusters[i].repoName.trim() !== '' &&
              federations.federatedClusters[i].clusterId.trim() !== ''
            ) {
              res.push(federations.federatedClusters[i])
            }
          }
          federations.federatedClusters = res
          // 根据是否为创建模式发起不同请求
          let reqPromise
          let msg
          let eventName
          if (this.createMode) {
            reqPromise = createFederation(federations)
            msg = '创建联邦仓库成功'
            eventName = 'created'
          } else {
            reqPromise = updateFederation(federations)
            msg = '更新联邦仓库成功'
            eventName = 'updated'
          }
          // 发起请求
          reqPromise.then(res => {
            this.$message.success(msg)
            if (!this.createMode) {
              this.$emit(eventName, res)
            } else {
              this.$emit(eventName, res.data)
            }
            this.close()
            this.$emit('updated')
          })
        } else {
          return false
        }
      })
    },
    changeEnable() {
      this.$forceUpdate()
    }
  }
}
</script>

<style scoped>

</style>
