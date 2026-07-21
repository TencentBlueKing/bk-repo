<template>
  <div class="app-container">
    <div class="header">
      <h3 style="margin: 0">集群节点元数据管理</h3>
      <el-button icon="el-icon-refresh" size="small" @click="reload">刷新</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column prop="clusterName" label="集群名称" min-width="160" />
      <el-table-column prop="url" label="访问地址" min-width="220" show-overflow-tooltip />
      <el-table-column prop="type" label="类型" width="120">
        <template slot-scope="{ row }">
          <el-tag :type="typeTag(row.type)" size="small">{{ row.type }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="region" label="地域" width="120">
        <template slot-scope="{ row }">{{ row.region || '-' }}</template>
      </el-table-column>
      <el-table-column prop="networkZone" label="网络区域" width="140">
        <template slot-scope="{ row }">{{ row.networkZone || '-' }}</template>
      </el-table-column>
      <el-table-column prop="displayName" label="展示名" min-width="140" show-overflow-tooltip>
        <template slot-scope="{ row }">{{ row.displayName || '-' }}</template>
      </el-table-column>
      <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip>
        <template slot-scope="{ row }">{{ row.description || '-' }}</template>
      </el-table-column>
      <el-table-column prop="lastModifiedBy" label="修改人" width="120">
        <template slot-scope="{ row }">{{ row.lastModifiedBy || '-' }}</template>
      </el-table-column>
      <el-table-column prop="lastModifiedDate" label="修改时间" width="180">
        <template slot-scope="{ row }">{{ row.lastModifiedDate || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="120">
        <template slot-scope="{ row }">
          <el-button size="mini" type="text" @click="openEditor(row)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog :visible.sync="editorVisible" :title="`编辑元数据 - ${editing.clusterName}`" width="540px">
      <el-form ref="editForm" :model="form" label-width="100px" size="small">
        <el-form-item label="地域">
          <el-input v-model="form.region" placeholder="如 sz / sh / hk / sg / na" clearable />
        </el-form-item>
        <el-form-item label="网络区域">
          <el-select v-model="form.networkZone" placeholder="选择网络区域" clearable style="width: 100%">
            <el-option label="IDC内网" value="IDC内网" />
            <el-option label="外网" value="外网" />
            <el-option label="devnet" value="devnet" />
            <el-option label="云研发内网" value="云研发内网" />
          </el-select>
        </el-form-item>
        <el-form-item label="展示名">
          <el-input v-model="form.displayName" maxlength="64" clearable />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="200" />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listClusterNodeMetadata, updateClusterNodeMetadata } from '@/api/topology'

export default {
  name: 'ClusterNodeMetadata',
  data() {
    return {
      loading: false,
      rows: [],
      editorVisible: false,
      saving: false,
      editing: { clusterName: '' },
      form: { region: '', networkZone: '', displayName: '', description: '' }
    }
  },
  mounted() {
    this.reload()
  },
  methods: {
    async reload() {
      this.loading = true
      try {
        const resp = await listClusterNodeMetadata()
        this.rows = resp.data || resp || []
      } catch (e) {
        this.$message.error('加载元数据失败')
      } finally {
        this.loading = false
      }
    },
    typeTag(type) {
      if (type === 'CENTER') return ''
      if (type === 'EDGE') return 'success'
      if (type === 'STANDALONE') return 'warning'
      return 'info'
    },
    openEditor(row) {
      this.editing = row
      this.form = {
        region: row.region || '',
        networkZone: row.networkZone || '',
        displayName: row.displayName || '',
        description: row.description || ''
      }
      this.editorVisible = true
    },
    async save() {
      this.saving = true
      try {
        await updateClusterNodeMetadata(this.editing.clusterName, {
          region: this.form.region || null,
          networkZone: this.form.networkZone || null,
          displayName: this.form.displayName || null,
          description: this.form.description || null
        })
        this.$message.success('保存成功')
        this.editorVisible = false
        this.reload()
      } catch (e) {
        this.$message.error('保存失败')
      } finally {
        this.saving = false
      }
    }
  }
}
</script>

<style scoped>
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
</style>
