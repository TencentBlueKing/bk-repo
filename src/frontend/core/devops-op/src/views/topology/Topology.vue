<template>
  <div class="topology-app-container">
    <!-- 顶部操作条 -->
    <div class="topology-toolbar">
      <el-form :inline="true" size="small">
        <el-form-item label="时段">
          <el-select v-model="period" style="width: 140px" @change="reloadAll">
            <el-option label="最近 1 小时" value="1h" />
            <el-option label="最近 24 小时" value="24h" />
            <el-option label="最近 7 天" value="7d" />
            <el-option label="最近 30 天" value="30d" />
          </el-select>
        </el-form-item>
        <el-form-item label="仅启用任务">
          <el-switch v-model="onlyEnabled" @change="reloadTopology" />
        </el-form-item>
        <el-form-item v-if="hideZeroEnabled" label="仅显示有流量通道">
          <el-switch v-model="hideZero" />
        </el-form-item>
        <el-form-item>
          <el-button icon="el-icon-refresh" @click="reloadAll">刷新</el-button>
          <el-button icon="el-icon-zoom-in" @click="zoom(1.2)">放大</el-button>
          <el-button icon="el-icon-zoom-out" @click="zoom(1 / 1.2)">缩小</el-button>
          <el-button icon="el-icon-rank" @click="resetView">复位</el-button>
          <el-button icon="el-icon-full-screen" @click="toggleFullscreen">全屏</el-button>
          <el-button icon="el-icon-picture-outline" @click="exportPng">导出 PNG</el-button>
          <el-button icon="el-icon-document" @click="exportSvg">导出 SVG</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-alert v-if="trafficUnavailable" type="warning" show-icon :closable="false" title="流量数据暂不可用，已降级仅展示拓扑结构" />

    <!-- 主图区域 -->
    <div ref="canvasWrap" class="topology-canvas-wrap" :class="{ fullscreen: isFullscreen }">
      <!-- A5: 外部节点气泡固定在画布右上角，不参与 SVG viewBox 计算 -->
      <div v-if="remoteSummary && remoteSummary.remoteNodeCount > 0" class="remote-bubble" @click="goRemoteList">
        <span class="remote-bubble-icon">📦</span>
        <span class="remote-bubble-text">外部节点 <b>{{ remoteSummary.remoteNodeCount }}</b> · 活跃 <b>{{ remoteSummary.activeRemoteTaskCount }}</b></span>
        <span class="remote-bubble-arrow">›</span>
      </div>
      <svg
        ref="svg"
        class="topology-svg"
        :viewBox="viewBoxStr"
        preserveAspectRatio="xMidYMid meet"
        @mousedown="onPanStart"
        @mousemove="onPanMove"
        @mouseup="onPanEnd"
        @mouseleave="onPanEnd"
        @wheel.prevent="onWheel"
      >
        <defs>
          <marker id="arrow" viewBox="0 0 10 10" refX="10" refY="5" markerWidth="8" markerHeight="8" orient="auto-start-reverse">
            <path d="M 0 0 L 10 5 L 0 10 z" fill="#909399" />
          </marker>
          <marker id="arrowDisabled" viewBox="0 0 10 10" refX="10" refY="5" markerWidth="8" markerHeight="8" orient="auto-start-reverse">
            <path d="M 0 0 L 10 5 L 0 10 z" fill="#c0c4cc" />
          </marker>
        </defs>

        <!-- 网络区域底色分组 -->
        <g class="zones">
          <rect
            v-for="zone in zoneRects"
            :key="zone.id"
            :x="zone.x"
            :y="zone.y"
            :width="zone.w"
            :height="zone.h"
            :fill="zone.color"
            opacity="0.08"
            stroke="#dcdfe6"
            stroke-dasharray="6,4"
            rx="12"
          />
          <!-- A3: zone 标题改为 rect 上方胶囊，避免和节点文字重叠 -->
          <g v-for="zone in zoneRects" :key="`label-${zone.id}`" class="zone-label">
            <rect :x="zone.x + 8" :y="zone.y - 16" :width="zoneLabelWidth(zone.label)" height="22" rx="11" fill="#fff" stroke="#dcdfe6" />
            <text :x="zone.x + 16" :y="zone.y" font-size="13" font-weight="600" fill="#606266">{{ zone.label }}</text>
          </g>
        </g>

        <!-- 通道连线：A1+A2 默认只画线，hover/选中才显示带白底的标签 -->
        <g class="channels">
          <g
            v-for="ch in displayChannels"
            :key="ch.id"
            class="channel-group"
            :class="{ active: hoverChannelId === ch.id || (selected.type === 'channel' && selected.data.id === ch.id) }"
            @mouseenter="hoverChannelId = ch.id"
            @mouseleave="hoverChannelId = null"
            @click.stop="selectChannel(ch)"
          >
            <path
              :d="channelPath(ch)"
              :stroke="ch.allDisabled ? '#c0c4cc' : channelColor(ch)"
              :stroke-width="channelWidth(ch)"
              :stroke-dasharray="ch.allDisabled ? '6,4' : 'none'"
              fill="none"
              :marker-end="ch.allDisabled ? 'url(#arrowDisabled)' : 'url(#arrow)'"
              opacity="0.85"
              class="topology-channel"
            >
              <title>{{ channelLabel(ch) }}</title>
            </path>
            <g class="channel-label">
              <rect
                :x="channelMid(ch).x - channelLabelHalfW(ch)"
                :y="channelMid(ch).y - 18"
                :width="channelLabelHalfW(ch) * 2"
                height="18"
                rx="4"
                fill="#fff"
                stroke="#dcdfe6"
                opacity="0.95"
              />
              <text :x="channelMid(ch).x" :y="channelMid(ch).y - 5" text-anchor="middle" font-size="12" fill="#606266">
                {{ channelLabel(ch) }}
              </text>
            </g>
          </g>
        </g>

        <!-- 节点 -->
        <g class="nodes">
          <g v-for="n in laidOutNodes" :key="n.id" :transform="`translate(${n.x},${n.y})`" @click.stop="selectNode(n)">
            <circle
              :r="22"
              :fill="nodeFill(n)"
              :stroke="nodeStroke(n)"
              stroke-width="3"
              class="topology-node"
            >
              <title>{{ n.displayName || n.name }} · {{ n.type }} · {{ n.status || '-' }}</title>
            </circle>
            <text y="5" text-anchor="middle" font-size="10" fill="#fff" font-weight="600" pointer-events="none">{{ shortType(n.type) }}</text>
            <!-- 节点名加白底，避免被 zone/连线压住 -->
            <g class="node-name">
              <rect :x="-nodeNameHalfW(n)" y="30" :width="nodeNameHalfW(n) * 2" height="16" rx="3" fill="#fff" opacity="0.85" />
              <text y="42" text-anchor="middle" font-size="12" fill="#303133" pointer-events="none">{{ n.displayName || n.name }}</text>
            </g>
            <text v-if="n.region" y="58" text-anchor="middle" font-size="11" fill="#909399" pointer-events="none">{{ n.region }}</text>
            <!-- A4: 心跳超时改成右上角小红点+title，节省空间 -->
            <circle
              v-if="isHeartbeatExpired(n)"
              :cx="16"
              :cy="-16"
              r="5"
              fill="#F56C6C"
              stroke="#fff"
              stroke-width="1.5"
            >
              <title>心跳超时（最近上报：{{ n.lastReportTime || '-' }}）</title>
            </circle>
          </g>
        </g>
      </svg>
    </div>

    <!-- 详情抽屉 -->
    <el-drawer :visible.sync="detailVisible" :title="detailTitle" direction="rtl" size="40%">
      <div v-if="selected.type === 'node'" v-loading="detailLoading" class="detail-pane">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="名称">{{ selected.data.name }}</el-descriptions-item>
          <el-descriptions-item label="访问地址">{{ selected.data.url }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ selected.data.type }}</el-descriptions-item>
          <el-descriptions-item label="健康状态">
            <el-tag :type="selected.data.status === 'HEALTHY' ? 'success' : 'danger'" size="small">{{ selected.data.status || '-' }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item v-if="selected.data.errorReason" label="失败原因">{{ selected.data.errorReason }}</el-descriptions-item>
          <el-descriptions-item label="最近心跳">{{ selected.data.lastReportTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="地域">{{ selected.data.region || '-' }}</el-descriptions-item>
          <el-descriptions-item label="网络区域">{{ selected.data.networkZone || '-' }}</el-descriptions-item>
          <el-descriptions-item label="描述">{{ selected.data.description || '-' }}</el-descriptions-item>
          <el-descriptions-item label="关联通道数">{{ nodeChannelCount(selected.data.name) }}</el-descriptions-item>
          <el-descriptions-item label="入站流量">{{ formatBytes(nodeTraffic.inboundBytes) }}</el-descriptions-item>
          <el-descriptions-item label="出站流量">{{ formatBytes(nodeTraffic.outboundBytes) }}</el-descriptions-item>
        </el-descriptions>
      </div>
      <div v-else-if="selected.type === 'channel'" v-loading="detailLoading" class="detail-pane">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="源集群">{{ selected.data.sourceCluster }}</el-descriptions-item>
          <el-descriptions-item label="目标集群">{{ selected.data.targetCluster }}</el-descriptions-item>
          <el-descriptions-item label="同步类型">
            <el-tag v-for="t in selected.data.replicaTypes" :key="t" size="mini" style="margin-right: 4px">{{ t }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="任务总数">{{ selected.data.totalTaskCount }}</el-descriptions-item>
          <el-descriptions-item label="活跃任务">{{ selected.data.activeTaskCount }}</el-descriptions-item>
          <el-descriptions-item label="是否全部停用">{{ selected.data.allDisabled ? '是' : '否' }}</el-descriptions-item>
          <el-descriptions-item label="最近时段流量">{{ formatBytes(selected.data.recentTrafficBytes) }}</el-descriptions-item>
        </el-descriptions>
        <div class="trend-chart">
          <h4>流量趋势</h4>
          <svg :viewBox="`0 0 ${trendW} ${trendH}`" class="trend-svg">
            <polyline
              v-if="trendPoints.length > 1"
              :points="trendPolylinePoints"
              fill="none"
              stroke="#409EFF"
              stroke-width="2"
            />
            <g v-for="(p, idx) in trendCoords" :key="idx">
              <circle :cx="p.cx" :cy="p.cy" r="2" fill="#409EFF">
                <title>{{ p.label }}</title>
              </circle>
            </g>
            <text v-if="trendPoints.length === 0" :x="trendW / 2" :y="trendH / 2" text-anchor="middle" fill="#909399">暂无趋势数据</text>
          </svg>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script>
import {
  getTopology,
  getChannelTrend,
  getNodeTraffic,
  formatBytes
} from '@/api/topology'

const NETWORK_ZONE_COLORS = {
  IDC内网: '#67C23A',
  外网: '#E6A23C',
  devnet: '#409EFF',
  云研发内网: '#909399',
  未分类: '#C0C4CC'
}

const TYPE_COLORS = {
  CENTER: '#409EFF',
  EDGE: '#67C23A',
  STANDALONE: '#E6A23C'
}

const REPLICA_TYPE_COLORS = {
  REAL_TIME: '#E6A23C',
  SCHEDULED: '#409EFF',
  EDGE_PULL: '#67C23A',
  FEDERATION: '#9C27B0'
}

const HEARTBEAT_THRESHOLD_MS = 5 * 60 * 1000

export default {
  name: 'ClusterTopology',
  data() {
    return {
      period: '24h',
      onlyEnabled: false,
      loading: false,
      trafficUnavailable: false,

      nodes: [],
      channels: [],
      remoteSummary: { remoteNodeCount: 0, activeRemoteTaskCount: 0, completedRemoteTaskCount: 0 },

      // viewBox 控制缩放/平移
      viewBox: { x: 0, y: 0, w: 1200, h: 800 },
      panState: null,
      hoverChannelId: null,

      // 详情抽屉
      detailVisible: false,
      detailLoading: false,
      selected: { type: '', data: {} },
      nodeTraffic: { inboundBytes: 0, outboundBytes: 0 },
      trendPoints: [],
      trendW: 600,
      trendH: 220,

      // 全屏
      isFullscreen: false,

      // 隐藏 0 流量通道
      hideZero: false
    }
  },
  computed: {
    viewBoxStr() {
      return `${this.viewBox.x} ${this.viewBox.y} ${this.viewBox.w} ${this.viewBox.h}`
    },
    /** 节点采用「以 CENTER 为圆心的同心圆」布局 */
    laidOutNodes() {
      return layoutNodes(this.nodes, this.channels)
    },
    /**
     * zone 矩形：仅当存在多个有效 networkZone（即排除「未分类」后仍 ≥2 个）时才绘制，
     * 避免 zone 数据缺失时画一个把所有节点都圈进去的「未分类」大框。
     */
    zoneRects() {
      const zoneSet = new Set(this.laidOutNodes.map((n) => n.zone).filter((z) => z && z !== '未分类'))
      if (zoneSet.size < 2) return []
      return computeZoneRects(this.laidOutNodes.filter((n) => n.zone && n.zone !== '未分类'), NETWORK_ZONE_COLORS)
    },
    displayChannels() {
      const positionMap = new Map(this.laidOutNodes.map((n) => [n.name, n]))
      const channels = this.channels.filter((c) => positionMap.has(c.sourceCluster) && positionMap.has(c.targetCluster))
      const filtered = this.hideZeroEnabled && this.hideZero
        ? channels.filter((c) => (c.recentTrafficBytes || 0) > 0)
        : channels
      return filtered.map((c) => {
        const s = positionMap.get(c.sourceCluster)
        const t = positionMap.get(c.targetCluster)
        return { ...c, sx: s.x, sy: s.y, tx: t.x, ty: t.y }
      })
    },
    /** 通道数大于阈值时启用「隐藏零流量」开关 */
    hideZeroEnabled() {
      return this.channels.length > 200
    },
    detailTitle() {
      if (this.selected.type === 'node') return `节点：${this.selected.data.name}`
      if (this.selected.type === 'channel') return `通道：${this.selected.data.sourceCluster} → ${this.selected.data.targetCluster}`
      return '详情'
    },
    trendCoords() {
      if (!this.trendPoints.length) return []
      const maxBytes = Math.max(...this.trendPoints.map((p) => p.bytes), 1)
      const stepX = (this.trendW - 40) / Math.max(this.trendPoints.length - 1, 1)
      return this.trendPoints.map((p, idx) => ({
        cx: 20 + idx * stepX,
        cy: this.trendH - 20 - ((p.bytes / maxBytes) * (this.trendH - 40)),
        label: `${p.time} : ${formatBytes(p.bytes)}`
      }))
    },
    trendPolylinePoints() {
      return this.trendCoords.map((c) => `${c.cx},${c.cy}`).join(' ')
    }
  },
  mounted() {
    this.reloadAll()
    this._onResize = () => {
      // 简单防抖
      clearTimeout(this._resizeTimer)
      this._resizeTimer = setTimeout(() => this.fitView(), 200)
    }
    window.addEventListener('resize', this._onResize)
  },
  beforeDestroy() {
    if (this._onResize) window.removeEventListener('resize', this._onResize)
    clearTimeout(this._resizeTimer)
  },
  methods: {
    formatBytes,
    isHeartbeatExpired(node) {
      if (!node.lastReportTime) return false
      try {
        const t = new Date(node.lastReportTime).getTime()
        return Date.now() - t > HEARTBEAT_THRESHOLD_MS
      } catch (_) { return false }
    },
    shortType(type) {
      if (type === 'CENTER') return 'C'
      if (type === 'EDGE') return 'E'
      if (type === 'STANDALONE') return 'S'
      return '?'
    },
    nodeFill(n) {
      return TYPE_COLORS[n.type] || '#909399'
    },
    nodeStroke(n) {
      if (n.status === 'HEALTHY') return '#67C23A'
      if (n.status === 'UNHEALTHY') return '#F56C6C'
      return '#909399'
    },
    channelColor(ch) {
      if (!ch.replicaTypes || !ch.replicaTypes.length) return '#909399'
      // 多种类型时选取首个有色映射
      const t = ch.replicaTypes[0]
      return REPLICA_TYPE_COLORS[t] || '#909399'
    },
    channelWidth(ch) {
      const bytes = ch.recentTrafficBytes || 0
      // 1GB 起步加宽，按 log2 递增
      const base = 1.5
      const extra = bytes > 0 ? Math.log2(bytes / (1024 * 1024) + 1) * 0.5 : 0
      return Math.min(8, base + extra)
    },
    channelLabel(ch) {
      const types = (ch.replicaTypes || []).join('/')
      const traffic = ch.recentTrafficBytes != null ? ` · 📊 ${formatBytes(ch.recentTrafficBytes)} / ${this.period}` : ''
      return `${types}${types ? ' · ' : ''}${ch.activeTaskCount}/${ch.totalTaskCount} 活跃${traffic}`
    },
    channelPath(ch) {
      const dx = ch.tx - ch.sx
      const dy = ch.ty - ch.sy
      // 三次贝塞尔，提供轻微弯曲避免重叠
      const cx = (ch.sx + ch.tx) / 2
      const cy = (ch.sy + ch.ty) / 2 - Math.sign(dx + dy) * 30
      return `M ${ch.sx} ${ch.sy} Q ${cx} ${cy} ${ch.tx} ${ch.ty}`
    },
    channelMid(ch) {
      return { x: (ch.sx + ch.tx) / 2, y: (ch.sy + ch.ty) / 2 - 18 }
    },
    nodeChannelCount(name) {
      return this.channels.filter((c) => c.sourceCluster === name || c.targetCluster === name).length
    },
    /** 节点名标签底框宽度（按字符数估算） */
    nodeNameHalfW(n) {
      const text = n.displayName || n.name || ''
      // 中文 12px，英文 7px，简单估算
      const len = Array.from(text).reduce((acc, ch) => acc + (/[\u4e00-\u9fa5]/.test(ch) ? 12 : 7), 0)
      return Math.max(20, Math.min(80, len / 2 + 4))
    },
    /** zone 标题胶囊宽度 */
    zoneLabelWidth(label) {
      const text = label || ''
      const len = Array.from(text).reduce((acc, ch) => acc + (/[\u4e00-\u9fa5]/.test(ch) ? 14 : 8), 0)
      return len + 16
    },
    /** 通道标签底框半宽 */
    channelLabelHalfW(ch) {
      const text = this.channelLabel(ch) || ''
      const len = Array.from(text).reduce((acc, c) => acc + (/[\u4e00-\u9fa5]/.test(c) ? 12 : 7), 0)
      return Math.max(40, len / 2 + 6)
    },
    async reloadAll() {
      await this.reloadTopology()
    },
    async reloadTopology() {
      this.loading = true
      this.trafficUnavailable = false
      try {
        const resp = await getTopology({ onlyEnabled: this.onlyEnabled, trafficPeriod: this.period })
        const data = resp.data || resp
        this.nodes = data.nodes || []
        this.channels = data.channels || []
        this.remoteSummary = data.remoteSummary || { remoteNodeCount: 0, activeRemoteTaskCount: 0, completedRemoteTaskCount: 0 }
        // 检测是否所有通道流量都为 null（说明流量服务降级）
        if (this.channels.length > 0 && this.channels.every((c) => c.recentTrafficBytes == null)) {
          this.trafficUnavailable = true
        }
        // 数据更新后自适应铺满
        this.$nextTick(() => this.fitView())
      } catch (e) {
        this.$message.error('加载拓扑数据失败')
      } finally {
        this.loading = false
      }
    },
    /**
     * 计算所有节点 + zone + remote 气泡的包围盒，按容器宽高比设置 viewBox，使内容铺满画布。
     */
    fitView() {
      const nodes = this.laidOutNodes
      if (!nodes.length) {
        this.viewBox = { x: 0, y: 0, w: 1200, h: 800 }
        return
      }
      // 节点本身占地（半径 22 + 文字 ~60 高）
      const NODE_PAD_X = 80
      const NODE_PAD_TOP = 40
      const NODE_PAD_BOTTOM = 75
      let minX = Infinity
      let minY = Infinity
      let maxX = -Infinity
      let maxY = -Infinity
      nodes.forEach((n) => {
        if (n.x - NODE_PAD_X < minX) minX = n.x - NODE_PAD_X
        if (n.y - NODE_PAD_TOP < minY) minY = n.y - NODE_PAD_TOP
        if (n.x + NODE_PAD_X > maxX) maxX = n.x + NODE_PAD_X
        if (n.y + NODE_PAD_BOTTOM > maxY) maxY = n.y + NODE_PAD_BOTTOM
      })
      // 把 zone 矩形也纳入
      this.zoneRects.forEach((z) => {
        if (z.x < minX) minX = z.x
        if (z.y < minY) minY = z.y
        if (z.x + z.w > maxX) maxX = z.x + z.w
        if (z.y + z.h > maxY) maxY = z.y + z.h
      })
      // remote 气泡已改为 HTML 浮层，不再纳入 SVG 包围盒计算
      const PADDING = 40
      let bbW = (maxX - minX) + PADDING * 2
      let bbH = (maxY - minY) + PADDING * 2
      let bbX = minX - PADDING
      let bbY = minY - PADDING
      // 按容器宽高比扩展短边，保证 preserveAspectRatio=meet 不出现大块留白
      const svgEl = this.$refs.svg
      if (svgEl && svgEl.clientWidth > 0 && svgEl.clientHeight > 0) {
        const containerRatio = svgEl.clientWidth / svgEl.clientHeight
        const contentRatio = bbW / bbH
        if (contentRatio > containerRatio) {
          // 内容更宽：扩高
          const targetH = bbW / containerRatio
          bbY -= (targetH - bbH) / 2
          bbH = targetH
        } else {
          // 内容更高：扩宽
          const targetW = bbH * containerRatio
          bbX -= (targetW - bbW) / 2
          bbW = targetW
        }
      }
      this.viewBox = { x: bbX, y: bbY, w: bbW, h: bbH }
    },
    async selectNode(n) {
      this.selected = { type: 'node', data: n }
      this.detailVisible = true
      this.detailLoading = true
      try {
        const resp = await getNodeTraffic(n.name, this.period)
        this.nodeTraffic = resp.data || resp || { inboundBytes: 0, outboundBytes: 0 }
      } catch (e) {
        this.nodeTraffic = { inboundBytes: 0, outboundBytes: 0 }
      } finally {
        this.detailLoading = false
      }
    },
    async selectChannel(ch) {
      this.selected = { type: 'channel', data: ch }
      this.detailVisible = true
      this.detailLoading = true
      this.trendPoints = []
      try {
        const now = new Date()
        const start = new Date(now.getTime() - periodToHours(this.period) * 3600 * 1000)
        const resp = await getChannelTrend({
          source: ch.sourceCluster,
          target: ch.targetCluster,
          startTime: toIsoLocal(start),
          endTime: toIsoLocal(now)
        })
        const data = resp.data || resp
        this.trendPoints = (data.points || []).map((p) => ({ time: p.time, bytes: p.bytes }))
      } catch (e) {
        this.$message.warning('趋势数据加载失败')
      } finally {
        this.detailLoading = false
      }
    },
    goRemoteList() {
      this.$router.push({ name: 'RemoteNodes' })
    },
    /* 缩放 / 平移 */
    zoom(factor) {
      const cx = this.viewBox.x + this.viewBox.w / 2
      const cy = this.viewBox.y + this.viewBox.h / 2
      this.viewBox.w /= factor
      this.viewBox.h /= factor
      this.viewBox.x = cx - this.viewBox.w / 2
      this.viewBox.y = cy - this.viewBox.h / 2
    },
    onWheel(e) {
      this.zoom(e.deltaY < 0 ? 1.1 : 1 / 1.1)
    },
    onPanStart(e) {
      this.panState = { startX: e.clientX, startY: e.clientY, vx: this.viewBox.x, vy: this.viewBox.y }
    },
    onPanMove(e) {
      if (!this.panState) return
      const ratio = this.viewBox.w / this.$refs.svg.clientWidth
      this.viewBox.x = this.panState.vx - (e.clientX - this.panState.startX) * ratio
      this.viewBox.y = this.panState.vy - (e.clientY - this.panState.startY) * ratio
    },
    onPanEnd() {
      this.panState = null
    },
    resetView() {
      this.fitView()
    },
    toggleFullscreen() {
      this.isFullscreen = !this.isFullscreen
      this.$nextTick(() => this.fitView())
    },
    /* 导出 */
    exportSvg() {
      const svgEl = this.$refs.svg
      const serializer = new XMLSerializer()
      const source = '<?xml version="1.0" standalone="no"?>\r\n' + serializer.serializeToString(svgEl)
      const blob = new Blob([source], { type: 'image/svg+xml' })
      this.downloadBlob(blob, 'cluster-topology.svg')
    },
    exportPng() {
      const svgEl = this.$refs.svg
      const serializer = new XMLSerializer()
      const source = serializer.serializeToString(svgEl)
      const img = new Image()
      img.onload = () => {
        const canvas = document.createElement('canvas')
        canvas.width = svgEl.clientWidth || 1200
        canvas.height = svgEl.clientHeight || 800
        const ctx = canvas.getContext('2d')
        ctx.fillStyle = '#ffffff'
        ctx.fillRect(0, 0, canvas.width, canvas.height)
        ctx.drawImage(img, 0, 0, canvas.width, canvas.height)
        canvas.toBlob((blob) => this.downloadBlob(blob, 'cluster-topology.png'))
      }
      img.src = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(source)
    },
    downloadBlob(blob, fileName) {
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = fileName
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      URL.revokeObjectURL(url)
    }
  }
}

/* === 节点布局：以 CENTER 为圆心的同心圆 === */
/**
 * 布局规则（方案 A）：
 *  1. CENTER 节点：放在画布中心。多个 CENTER 时按横向均匀排开。
 *  2. 内环（半径 R1）：与任一 CENTER 直连（有 channel）的 EDGE / STANDALONE 节点
 *      —— 按"通道总数"降序顺时针排列。
 *  3. 外环（半径 R2）：剩余节点（孤岛或仅与其他 EDGE/STANDALONE 互联）
 *      —— 同样按通道数降序顺时针排列。
 *  4. 半径根据节点数量自适应增长，避免拥挤。
 *  5. 没有 CENTER 时退化为「单环」布局，所有节点均匀分布在一个大圆上。
 */
function layoutNodes(nodes, channels) {
  if (!nodes.length) return []

  const channelList = channels || []

  // 通道度数（每个节点参与的 channel 数）
  const degree = new Map()
  channelList.forEach((c) => {
    degree.set(c.sourceCluster, (degree.get(c.sourceCluster) || 0) + 1)
    degree.set(c.targetCluster, (degree.get(c.targetCluster) || 0) + 1)
  })

  // 每个节点：name -> { node, degree, isCenter, isInner }
  const meta = nodes.map((n) => ({
    node: n,
    degree: degree.get(n.name) || 0,
    isCenter: n.type === 'CENTER'
  }))

  const centers = meta.filter((m) => m.isCenter)
  const centerNames = new Set(centers.map((m) => m.node.name))

  // 与 CENTER 直连的非中心节点
  const innerSet = new Set()
  channelList.forEach((c) => {
    if (centerNames.has(c.sourceCluster) && !centerNames.has(c.targetCluster)) innerSet.add(c.targetCluster)
    if (centerNames.has(c.targetCluster) && !centerNames.has(c.sourceCluster)) innerSet.add(c.sourceCluster)
  })

  const inner = meta
    .filter((m) => !m.isCenter && innerSet.has(m.node.name))
    .sort((a, b) => b.degree - a.degree)
  const outer = meta
    .filter((m) => !m.isCenter && !innerSet.has(m.node.name))
    .sort((a, b) => b.degree - a.degree)

  // 画布中心
  const cx = 600
  const cy = 420

  // 半径自适应：节点越多半径越大，保证最近邻间距
  const NODE_GAP = 110 // 期望相邻节点弧长间距
  const r1Min = 180
  const r2Min = 360
  const r1 = Math.max(r1Min, (inner.length * NODE_GAP) / (2 * Math.PI))
  const r2 = Math.max(r2Min, r1 + 200, (outer.length * NODE_GAP) / (2 * Math.PI))

  const result = []

  // 1. 放置 CENTER（在中心点附近，多个时左右铺开）
  if (centers.length === 1) {
    const m = centers[0]
    result.push({ ...m.node, x: cx, y: cy, zone: m.node.networkZone || '未分类' })
  } else if (centers.length > 1) {
    const span = Math.min(280, (centers.length - 1) * 140)
    const startX = cx - span / 2
    const stepX = centers.length > 1 ? span / (centers.length - 1) : 0
    centers.forEach((m, idx) => {
      result.push({ ...m.node, x: startX + idx * stepX, y: cy, zone: m.node.networkZone || '未分类' })
    })
  }

  // 2. 没有 CENTER 时：所有节点均匀放在一个大圆上
  if (centers.length === 0) {
    const all = [...inner, ...outer]
    const r = Math.max(r2Min, (all.length * NODE_GAP) / (2 * Math.PI))
    placeOnRing(result, all, cx, cy, r, -Math.PI / 2)
    return result
  }

  // 3. 内环：从 12 点钟方向开始顺时针排列
  if (inner.length > 0) {
    placeOnRing(result, inner, cx, cy, r1, -Math.PI / 2)
  }

  // 4. 外环：起始角和内环错开半步，减少视觉对齐
  if (outer.length > 0) {
    const phaseShift = inner.length > 0 ? Math.PI / Math.max(inner.length, 6) : 0
    placeOnRing(result, outer, cx, cy, r2, -Math.PI / 2 + phaseShift)
  }

  return result
}

/** 把 metaList 里的节点均匀分布到一个圆环上 */
function placeOnRing(result, metaList, cx, cy, r, startAngle) {
  const n = metaList.length
  if (n === 0) return
  const step = (2 * Math.PI) / n
  metaList.forEach((m, idx) => {
    const angle = startAngle + idx * step
    const x = cx + r * Math.cos(angle)
    const y = cy + r * Math.sin(angle)
    result.push({ ...m.node, x, y, zone: m.node.networkZone || '未分类' })
  })
}

function computeZoneRects(laidOutNodes, zoneColors) {
  const buckets = {}
  laidOutNodes.forEach((n) => {
    const zone = n.zone || '未分类'
    if (!buckets[zone]) buckets[zone] = []
    buckets[zone].push(n)
  })
  return Object.keys(buckets).map((zone) => {
    const ns = buckets[zone]
    const minX = Math.min(...ns.map((n) => n.x)) - 40
    const minY = Math.min(...ns.map((n) => n.y)) - 40
    const maxX = Math.max(...ns.map((n) => n.x)) + 80
    const maxY = Math.max(...ns.map((n) => n.y)) + 80
    return {
      id: zone,
      label: zone,
      x: minX,
      y: minY,
      w: maxX - minX,
      h: maxY - minY,
      color: zoneColors[zone] || '#C0C4CC'
    }
  })
}

function periodToHours(period) {
  if (period === '1h') return 1
  if (period === '7d') return 24 * 7
  if (period === '30d') return 24 * 30
  return 24
}

function toIsoLocal(date) {
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}
</script>

<style scoped lang="scss">
.topology-app-container {
  padding: 16px;
}

.topology-toolbar {
  margin-bottom: 12px;
}

.topology-canvas-wrap {
  position: relative;
  background: #fafafa;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  overflow: hidden;
  height: 78vh;

  &.fullscreen {
    position: fixed;
    inset: 0;
    z-index: 2000;
    height: 100vh;
    background: #fff;
  }
}

.topology-svg {
  width: 100%;
  height: 100%;
  cursor: grab;
}

.topology-svg:active {
  cursor: grabbing;
}

.topology-channel {
  cursor: pointer;
  transition: stroke-width 0.2s;
}

.topology-channel:hover {
  stroke-width: 6 !important;
}

/* A2: 通道标签默认隐藏，hover/选中时才显示 */
.channel-label {
  opacity: 0;
  transition: opacity 0.15s;
  pointer-events: none;
}

.channel-group.active .channel-label {
  opacity: 1;
}

.channel-group {
  cursor: pointer;
}

/* A5: 外部节点 HTML 浮层 */
.remote-bubble {
  position: absolute;
  top: 12px;
  right: 12px;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background: #ecf5ff;
  border: 1px solid #409EFF;
  border-radius: 18px;
  color: #409EFF;
  font-size: 13px;
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.15);
  transition: transform 0.15s, box-shadow 0.15s;
  user-select: none;
}

.remote-bubble:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.25);
}

.remote-bubble-icon {
  font-size: 15px;
}

.remote-bubble-text b {
  font-weight: 700;
  margin: 0 2px;
}

.remote-bubble-arrow {
  font-size: 18px;
  line-height: 1;
  margin-left: 2px;
}

.topology-node {
  cursor: pointer;
  transition: transform 0.2s;
}

.topology-node:hover {
  filter: brightness(1.1);
}

.detail-pane {
  padding: 0 16px 16px;
}

.trend-chart {
  margin-top: 16px;
}

.trend-svg {
  width: 100%;
  height: 220px;
  background: #f5f7fa;
  border-radius: 4px;
}
</style>
