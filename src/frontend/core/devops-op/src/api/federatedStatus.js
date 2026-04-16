import request from '@/utils/request'

const PREFIX_SERVICES = '/replication/api/federation/status'

export function getRepoStatus(params) {
  return request({
    url: `${PREFIX_SERVICES}/repository`,
    method: 'get',
    params: {
      'projectId': params.projectId,
      'repoName': params.repoName,
      'federationId': params.federationId === '' ? null : params.federationId
    }
  })
}

export function getMemberStatus(params) {
  return request({
    url: `${PREFIX_SERVICES}/members`,
    method: 'get',
    params: {
      'projectId': params.projectId,
      'repoName': params.repoName,
      'federationId': params.federationId
    }
  })
}

export function refreshMember(params) {
  return request({
    url: `${PREFIX_SERVICES}/members/refresh`,
    method: 'post',
    params: {
      'projectId': params.projectId,
      'repoName': params.repoName,
      'federationId': params.federationId
    }
  })
}
