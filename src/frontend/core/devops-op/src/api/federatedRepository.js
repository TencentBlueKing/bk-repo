import request from '@/utils/request'

const PREFIX_SERVICES = '/replication/api/federation'

export function federations(params) {
  return request({
    url: `${PREFIX_SERVICES}/list/${params.projectId}/${params.repoName}`,
    method: 'get',
    params: {
      federationId: params.federationId === '' ? null : params.federationId
    }
  })
}

export function createFederation(params) {
  return request({
    url: `${PREFIX_SERVICES}/create`,
    method: 'post',
    data: params
  })
}

export function deleteFederation(params) {
  return request({
    url: `${PREFIX_SERVICES}/list/${params.projectId}/${params.repoName}/${params.federationId}`,
    method: 'delete'
  })
}

export function updateFederation(params) {
  return request({
    url: `${PREFIX_SERVICES}/update`,
    method: 'put',
    data: params
  })
}

export function removeCluster(rows, baseInfo) {
  return request({
    url: `${PREFIX_SERVICES}/remove-cluster`,
    method: 'delete',
    data: {
      'projectId': baseInfo.projectId,
      'repoName': baseInfo.repoName,
      'federationId': baseInfo.federationId,
      'federatedClusters': rows
    }
  })
}

export function startFullSync(params) {
  return request({
    url: `${PREFIX_SERVICES}/fullSync/${params.projectId}/${params.repoName}/${params.federationId}`,
    method: 'post'
  })
}

export function stopFullSync(params) {
  return request({
    url: `${PREFIX_SERVICES}/fullSync/end/${params.projectId}/${params.repoName}/${params.federationId}`,
    method: 'put'
  })
}
