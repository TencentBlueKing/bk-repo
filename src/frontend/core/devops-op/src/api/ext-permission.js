import request from '@/utils/request'

const PREFIX_EXT_PERMISSION = '/auth/api/ext-permission'

export function listExtPermission(projectId, repoName, url, scope, enabled, pageNumber = 1, pageSize = 20) {
  if (projectId === '') projectId = undefined
  if (repoName === '') repoName = undefined
  if (url === '') url = undefined
  if (scope === '') scope = undefined
  if (enabled === '') enabled = undefined
  return request({
    url: `${PREFIX_EXT_PERMISSION}`,
    method: 'get',
    params: {
      projectId,
      repoName,
      url,
      scope,
      enabled,
      pageNumber,
      pageSize
    }
  })
}

export function createExtPermission(projectId, repoName, url, headers, scope, platformWhiteList, enabled) {
  if (platformWhiteList === undefined || platformWhiteList.length === 0) {
    platformWhiteList = []
  } else {
    platformWhiteList = platformWhiteList.split(',')
    platformWhiteList = platformWhiteList.map(element => element.trim())
  }
  console.log(platformWhiteList)
  return request({
    url: `${PREFIX_EXT_PERMISSION}`,
    method: 'post',
    data: {
      'projectId': projectId,
      'repoName': repoName,
      'url': url,
      'headers': headers,
      'scope': scope,
      'platformWhiteList': platformWhiteList,
      'enabled': enabled
    }
  })
}

export function updateExtPermission(id, projectId, repoName, url, headers, scope, platformWhiteList, enabled) {
  if (platformWhiteList === undefined || platformWhiteList.length === 0) {
    platformWhiteList = []
  } else {
    if (typeof (platformWhiteList) === 'string') {
      platformWhiteList = platformWhiteList.split(',')
    } else {
      platformWhiteList = Object.values(platformWhiteList)
    }
    console.log(platformWhiteList)
    platformWhiteList = platformWhiteList.map(element => element.trim())
  }
  return request({
    url: `${PREFIX_EXT_PERMISSION}`,
    method: 'put',
    data: {
      'id': id,
      'projectId': projectId,
      'repoName': repoName,
      'url': url,
      'headers': headers,
      'scope': scope,
      'platformWhiteList': platformWhiteList,
      'enabled': enabled
    }
  })
}

export function deleteExtPermission(id) {
  return request({
    url: `${PREFIX_EXT_PERMISSION}/${id}`,
    method: 'delete'
  })
}
