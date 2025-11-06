<template>
  <div style="display: flex" class="app-container">
    <el-table
      :data="configs"
      style="width: 50%"
    >
      <el-table-column
        prop="key"
        label="配置名"
        width="280"
      />
      <el-table-column label="操作">
        <template slot-scope="scope">
          <el-button type="primary" size="mini" @click="showDetail(scope.row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div id="editor" ref="contentRef" class="content" />
  </div>
</template>

<script>

import { getServicesConfig } from '@/api/service'
import * as monaco from 'monaco-editor'

export default {
  name: 'Service',
  data() {
    return {
      configs: [],
      title: '',
      editor: null
    }
  },
  mounted() {
    this.init()
  },
  created() {
    getServicesConfig().then(res => {
      this.configs = res.data
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
    showDetail(row) {
      this.editor.setValue('')
      this.text = row.decodedValue
      this.editor.setValue(this.text)
    }
  }
}
</script>

<style lang="scss" scoped>
.content {
  margin-left: 10px;
  width: 50%;
  height: 900px;
}
</style>
