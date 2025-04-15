<template>
  <el-dialog :title="title" :visible.sync="showShareDialog" :before-close="close">
    <el-form ref="form" :model="key" status-icon>
      <el-form-item
        label="项目名称"
        prop="projectId"
      >
        <el-input v-model="key.projectId" disabled />
      </el-form-item>
      <el-form-item
        label="仓库名称"
        prop="repoName"
      >
        <el-input v-model="key.repoName" disabled />
      </el-form-item>
      <el-form-item
        label="完整路径"
        prop="artifactUri"
      >
        <el-input v-model="key.artifactUri" disabled />
      </el-form-item>
      <el-form-item
        v-if="!createMode"
        label="分享地址"
        prop="shareUrl"
      >
        <el-input v-model="key.shareUrl" disabled />
      </el-form-item>
      <el-form-item
        v-if="createMode"
        label="分享用户"
        prop="authorizedUserList"
      >
        <el-tag
          v-for="tag in key.authorizedUserList"
          :key="tag"
          closable
          :disable-transitions="false"
          style="margin-right: 5px"
          @close="handleClose(tag)"
        >
          {{ tag }}
        </el-tag>
        <el-input
          v-if="inputUseNameVisible"
          ref="saveTagInput"
          v-model="inputUseName"
          class="input-new-tag"
          size="small"
          @keyup.enter.native="handleInputConfirm"
          @blur="handleInputConfirm"
        />
        <el-button v-else class="button-new-tag" size="small" @click="showInput">+ 用户名</el-button>
      </el-form-item>
      <el-form-item v-if="!createMode" label="分享用户">
        <el-tag
          v-for="tag in key.authorizedUserList"
          :key="tag"
          :disable-transitions="false"
          style="margin-right: 5px"
        >
          {{ tag }}
        </el-tag>
      </el-form-item>
      <el-form-item v-if="!createMode" label="分享IP">
        <el-tag
          v-for="tag in key.authorizedIpList"
          :key="tag"
          :disable-transitions="false"
          style="margin-right: 5px"
        >
          {{ tag }}
        </el-tag>
      </el-form-item>
      <el-form-item v-if="createMode" label="分享IP" prop="authorizedIpList">
        <el-tag
          v-for="tag in key.authorizedIpList"
          :key="tag"
          closable
          :disable-transitions="false"
          style="margin-right: 5px"
          @close="handleIpClose(tag)"
        >
          {{ tag }}
        </el-tag>
        <el-input
          v-if="inputIpVisible"
          ref="saveIpTagInput"
          v-model="inputIp"
          class="input-new-tag"
          size="small"
          @keyup.enter.native="handleIpInputConfirm"
          @blur="handleIpInputConfirm"
        />
        <el-button v-else class="button-new-tag" size="small" @click="showIpInput">+ IP</el-button>
      </el-form-item>
      <el-form-item v-if="createMode" label="有效时间（秒）" prop="expireSeconds">
        <el-input-number v-model="key.expireSeconds" controls-position="right" :min="0" />
      </el-form-item>
      <el-form-item
        v-if="!createMode"
        label="过期时间"
        prop="expireDate"
      >
        <el-input v-model="key.expireDate" disabled />
      </el-form-item>
    </el-form>
    <div slot="footer">
      <el-button v-if="createMode" @click="close">取 消</el-button>
      <el-button v-if="createMode" type="primary" @click="share(key)">确 定</el-button>
    </div>
  </el-dialog>
</template>

<script>
import { shareNode } from '@/api/node'
import { formatNormalDate } from '@/utils/date'
export default {
  name: 'ShareDialog',
  props: {
    visible: Boolean,
    /**
     * 仅在更新模式时有值
     */
    updatingKeys: {
      type: Object,
      default: undefined
    },
    /**
     * 是否为创建模式，true时为创建对象，false时为更新对象
     */
    createKeyMode: Boolean
  },
  data() {
    return {
      title: '创建分享链接',
      showShareDialog: this.visible,
      key: this.newShareInformation(),
      inputUseNameVisible: false,
      inputUseName: '',
      inputIpVisible: false,
      inputIp: '',
      createMode: true
    }
  },
  watch: {
    visible: function(newVal) {
      if (newVal) {
        this.showShareDialog = true
        this.key.projectId = this.updatingKeys.projectId
        this.key.repoName = this.updatingKeys.repoName
        this.key.artifactUri = this.updatingKeys.fullPath
      } else {
        this.close()
      }
    }
  },
  created() {
  },
  methods: {
    close() {
      this.showShareDialog = false
      this.$emit('update:visible', false)
      this.key = this.newShareInformation()
      this.createMode = true
    },
    newShareInformation() {
      const shareInformation = {
        projectId: '',
        repoName: '',
        artifactUri: '',
        authorizedUserList: [],
        authorizedIpList: [],
        expireSeconds: '',
        expireDate: ''
      }
      return shareInformation
    },
    share() {
      shareNode(this.key).then(res => {
        this.title = '分享结果'
        this.key = res.data
        this.key.expireDate = formatNormalDate(res.data.expireDate)
        this.createMode = false
      })
    },
    handleClose(tag) {
      this.key.authorizedUserList.splice(this.key.authorizedUserList.indexOf(tag), 1)
    },
    showInput() {
      this.inputUseNameVisible = true
      this.$nextTick(_ => {
        this.$refs.saveTagInput.$refs.input.focus()
      })
    },
    handleInputConfirm() {
      const inputValue = this.inputUseName
      if (inputValue && !this.key.authorizedUserList.includes(inputValue)) {
        this.key.authorizedUserList.push(inputValue)
      }
      this.inputUseNameVisible = false
      this.inputUseName = ''
    },
    showIpInput() {
      this.inputIpVisible = true
      this.$nextTick(_ => {
        this.$refs.saveIpTagInput.$refs.input.focus()
      })
    },
    handleIpInputConfirm() {
      const inputValue = this.inputIp
      if (!IP_REGEX.test(inputValue)) {
        this.$message.error('请输入正确的ip')
        this.inputIpVisible = false
        this.inputIp = ''
      } else {
        if (inputValue && !this.key.authorizedIpList.includes(inputValue)) {
          this.key.authorizedIpList.push(inputValue)
        }
        this.inputIpVisible = false
        this.inputIp = ''
      }
    },
    handleIpClose(tag) {
      this.key.authorizedIpList.splice(this.key.authorizedIpList.indexOf(tag), 1)
    }
  }
}

import { IP_REGEX } from '@/utils/validate'
</script>

<style scoped>

</style>
