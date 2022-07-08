import request from '@/utils/request'

const PREFIX_PLUGIN = '/opdata/api/plugin'

export function listPlugin(scope = null, pageNumber = 1, pageSize = 20) {
  return request({
    url: `${PREFIX_PLUGIN}`,
    method: 'get',
    params: {
      scope,
      pageNumber,
      pageSize
    }
  })
}

export function createPlugin(id, version, scope, description, gitUrl) {
  return request({
    url: `${PREFIX_PLUGIN}`,
    method: 'post',
    data: {
      'id': id,
      'version': version,
      'scope': scope,
      'description': description,
      'gitUrl': gitUrl
    }
  })
}

export function updatePlugin(id, version, scope, description, gitUrl) {
  return request({
    url: `${PREFIX_PLUGIN}`,
    method: 'put',
    data: {
      'id': id,
      'version': version,
      'scope': scope,
      'description': description,
      'gitUrl': gitUrl
    }
  })
}

export function deletePlugin(id) {
  return request({
    url: `${PREFIX_PLUGIN}/${id}`,
    method: 'delete'
  })
}

export function loadPlugin(id, host) {
  return request({
    url: `${PREFIX_PLUGIN}/load/${id}`,
    method: 'post',
    params: {
      host
    }
  })
}

export function unloadPlugin(id, host) {
  return request({
    url: `${PREFIX_PLUGIN}/unload/${id}`,
    method: 'delete',
    params: {
      host
    }
  })
}
