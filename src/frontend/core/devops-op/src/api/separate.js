import request from '@/utils/request'

const PREFIX_SERVICES = '/job/api/job/separation'

export function querySeparateTask(data) {
  return request({
    url: `${PREFIX_SERVICES}/tasks`,
    method: 'get',
    params: data
  })
}

export function createSeparateTask(data) {
  return request({
    url: `${PREFIX_SERVICES}/`,
    method: 'post',
    data: data
  })
}

export function updateSeparateTask(taskId) {
  return request({
    url: `${PREFIX_SERVICES}/update/${taskId}/state`,
    method: 'post'
  })
}

// 查询冷表中节点信息
export function queryColdNodeData(body) {
  return request({
    url: `${PREFIX_SERVICES}/node/${body.projectId}/${body.repoName}`,
    method: 'get',
    params: {
      fullPath: body.fullPath
    }
  })
}

// 查询冷表中版本信息
export function queryColdVersionData(body) {
  return request({
    url: `${PREFIX_SERVICES}/version/${body.projectId}/${body.repoName}`,
    method: 'get',
    params: {
      packageKey: body.packageKey,
      version: body.version
    }
  })
}

// 分页查询包
export function queryPackageData(body) {
  return request({
    url: `${PREFIX_SERVICES}/package/page/${body.projectId}/${body.repoName}`,
    method: 'get',
    params: body.packageOption
  })
}

// 分页查询版本
export function queryVersionData(body) {
  return request({
    url: `${PREFIX_SERVICES}/version/page/${body.projectId}/${body.repoName}`,
    method: 'get',
    params: {
      packageKey: body.packageKey,
      separationDate: body.separationDate,
      option: body.versionOption
    }
  })
}

// 分页查询节点
export function queryNodeData(body) {
  return request({
    url: `${PREFIX_SERVICES}/node/page/${body.projectId}/${body.repoName}`,
    method: 'get',
    params: {
      fullPath: body.fullPath,
      separationDate: body.separationDate,
      option: body.nodeOption
    }
  })
}

