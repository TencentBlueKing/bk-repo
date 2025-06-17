<template>
  <div class="app-container node-container">
    <el-form ref="form" :inline="true">
      <el-form-item ref="project-form-item" label="服务">
        <el-select v-model="service" clearable placeholder="请选择" @change="serviceChanged">
          <el-option
            v-for="item in serviceOptions"
            :key="item"
            :label="item"
            :value="item"
          />
        </el-select>
      </el-form-item>
      <el-form-item
        ref="repo-form-item"
        style="margin-left: 15px"
        label="节点"
      >
        <el-select v-model="node" :disabled="!service" clearable placeholder="请选择">
          <el-option
            v-for="item in nodeOptions"
            :key="item"
            :label="item"
            :value="item"
          />
        </el-select>
      </el-form-item>
      <el-form-item
        ref="repo-form-item"
        style="margin-left: 15px"
        label="日志文件"
      >
        <el-select v-model="logName" clearable placeholder="请选择">
          <el-option
            v-for="item in logOptions"
            :key="item"
            :label="item"
            :value="item"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button size="mini" type="primary" @click="pause()">暂停</el-button>
      </el-form-item>
      <el-form-item>
        <el-button size="mini" type="primary" @click="refresh()">刷新</el-button>
      </el-form-item>
      <el-form-item style="margin-left: 15px">
        <span v-if="timeLimit >= 0 && countTimer"> {{ timeLimit + '秒内刷新' }} </span>
      </el-form-item>
    </el-form>
    <div id="editor" ref="contentRef" class="content" />
  </div>
</template>

<script>
import * as monaco from 'monaco-editor'
import { getLog, getLogConfig } from '@/api/server-log'

export default {
  name: 'EditorIndex',
  data() {
    return {
      editor: null,
      text: '',
      timer: null,
      service: '',
      serviceOptions: [],
      node: '',
      nodeOptions: [],
      logName: '',
      logOptions: [],
      timeLimit: -1,
      countTimer: null,
      startPosition: 0,
      originTimeLimit: 0,
      serviceNodeMap: null
    }
  },
  watch: {
    logName(val) {
      this.text = ''
      this.startPosition = 0
      this.editor.setValue('')
      clearInterval(this.timer)
      if (val) {
        this.setNewTimer()
        if (!this.countTimer) {
          this.countTimer = setInterval(() => {
            if (this.timeLimit > 0) {
              this.timeLimit = this.timeLimit - 1
            }
          }, 1000)
        }
      } else {
        this.pause()
      }
    },
    node() {
      this.text = ''
      this.editor.setValue('')
      this.startPosition = 0
      clearInterval(this.timer)
      if (this.logName !== '') {
        this.timeLimit = this.originTimeLimit / 1000
        this.setNewTimer()
      }
    }
  },
  mounted() {
    this.init()
  },
  created() {
    getLogConfig().then(res => {
      this.logOptions = res.data.logs
      this.timeLimit = res.data.refreshRateMillis / 1000
      this.originTimeLimit = res.data.refreshRateMillis
      this.serviceOptions = Object.keys(res.data.nodes)
      this.serviceNodeMap = res.data.nodes
    })
  },
  methods: {
    init() {
      this.editor = monaco.editor.create(document.getElementById('editor'), {
        value: this.text, // 编辑器初始显示文字
        language: 'javascript', // 语言
        automaticLayout: true, // 自动布局
        theme: 'vs-dark', // 官方自带三种主题vs, hc-black, or vs-dark
        minimap: { // 关闭小地图
          enabled: true
        },
        readOnly: true,
        lineNumbers: 'on' // 隐藏控制行号
      })
    },
    serviceChanged() {
      this.text = ''
      this.node = ''
      this.editor.setValue('')
      this.startPosition = 0
      if (this.timer !== null) {
        clearInterval(this.timer)
      }
      if (this.logName !== '') {
        this.setNewTimer()
      }
      this.nodeOptions = this.serviceNodeMap[this.service]
    },
    showLog(val) {
      this.timeLimit = this.originTimeLimit / 1000
      getLog(val, this.node, this.startPosition).then(res => {
        if (this.text === '') {
          this.text = res.data.logContent
        } else {
          this.text = this.text + res.data.logContent
        }
        this.editor.setValue(this.text)
        this.startPosition = res.data.fileSize
      })
    },
    pause() {
      clearInterval(this.timer)
      this.timer = null
      this.timeLimit = -1
    },
    refresh() {
      if (this.timer !== null) {
        this.showLog(this.logName)
      } else if (this.logName !== '') {
        this.setNewTimer()
      } else {
        clearInterval(this.timer)
      }
    },
    setNewTimer() {
      this.showLog(this.logName)
      this.timer = setInterval(() => {
        this.showLog(this.logName)
      }, this.originTimeLimit)
    }
  }
}
</script>

<style lang="scss" scoped>
.content {
  margin-left: 10px;
  width: 90%;
  height: 700px;
}
</style>
