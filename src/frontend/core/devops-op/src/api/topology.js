import request from '@/utils/request'

const PREFIX = '/opdata/api/cluster/topology'
const REMOTE_PREFIX = `${PREFIX}/remote`
const METADATA_PREFIX = `${PREFIX}/metadata`

/**
 * 获取拓扑数据 + 默认时段流量摘要。
 */
export function getTopology(params = {}) {
  return request({
    url: PREFIX,
    method: 'get',
    params
  })
}

/**
 * 通道流量汇总。
 */
export function getChannelTraffic(period = '24h') {
  return request({
    url: `${PREFIX}/traffic/channels`,
    method: 'get',
    params: { period }
  })
}

/**
 * 通道流量趋势。
 */
export function getChannelTrend(params) {
  return request({
    url: `${PREFIX}/traffic/channel/trend`,
    method: 'get',
    params
  })
}

/**
 * 节点出入流量汇总。
 */
export function getNodeTraffic(clusterName, period = '24h') {
  return request({
    url: `${PREFIX}/node/${encodeURIComponent(clusterName)}/traffic`,
    method: 'get',
    params: { period }
  })
}

/**
 * REMOTE 节点分页查询。
 */
export function pageRemoteNodes(params) {
  return request({
    url: `${REMOTE_PREFIX}/page`,
    method: 'get',
    params
  })
}

/**
 * REMOTE 节点全局汇总。
 */
export function getRemoteSummary() {
  return request({
    url: `${REMOTE_PREFIX}/summary`,
    method: 'get'
  })
}

/**
 * 查询某个 REMOTE 节点关联的同步任务。
 */
export function getRemoteNodeTasks(name) {
  return request({
    url: `${REMOTE_PREFIX}/tasks`,
    method: 'get',
    params: { name }
  })
}

/**
 * 节点元数据列表。
 */
export function listClusterNodeMetadata() {
  return request({
    url: METADATA_PREFIX,
    method: 'get'
  })
}

/**
 * 更新节点元数据。
 */
export function updateClusterNodeMetadata(clusterName, data) {
  return request({
    url: `${METADATA_PREFIX}/${encodeURIComponent(clusterName)}`,
    method: 'put',
    data
  })
}

/**
 * 字节数可读化格式。
 */
export function formatBytes(bytes) {
  if (bytes === null || bytes === undefined) {
    return '-'
  }
  const num = Number(bytes)
  if (!isFinite(num) || num <= 0) {
    return '0 B'
  }
  const units = ['B', 'KB', 'MB', 'GB', 'TB', 'PB']
  let val = num
  let idx = 0
  while (val >= 1024 && idx < units.length - 1) {
    val /= 1024
    idx++
  }
  return `${val.toFixed(val >= 100 || idx === 0 ? 0 : 2)} ${units[idx]}`
}
