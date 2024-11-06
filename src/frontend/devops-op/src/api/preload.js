import request from '@/utils/request'

const PREFIX_SERVICES = '/repository/api/preload'

export function queryStrategies(projectId, repoName) {
  return request({
    url: `${PREFIX_SERVICES}/strategy/${projectId}/${repoName}`,
    method: 'get'
  })
}

export function deleteStrategy(projectId, repoName, id) {
  return request({
    url: `${PREFIX_SERVICES}/strategy/${projectId}/${repoName}/${id}`,
    method: 'delete'
  })
}

export function createStrategy(data) {
  return request({
    url: `${PREFIX_SERVICES}/strategy/`,
    method: 'post',
    data: data
  })
}

export function updateStrategy(data) {
  return request({
    url: `${PREFIX_SERVICES}/strategy/`,
    method: 'put',
    data: data
  })
}

export function createPlan(data) {
  return request({
    url: `${PREFIX_SERVICES}/plan/`,
    method: 'post',
    data: data
  })
}

export function queryPlans(body) {
  return request({
    url: `${PREFIX_SERVICES}/plan/${body.projectId}/${body.repoName}`,
    method: 'get',
    params: {
      pageNumber: body.pageNumber,
      pageSize: body.pageSize
    }
  })
}

export function deletePlan(projectId, repoName, id) {
  return request({
    url: `${PREFIX_SERVICES}/plan/${projectId}/${repoName}/${id}`,
    method: 'delete'
  })
}

export function deletePlans(projectId, repoName) {
  return request({
    url: `${PREFIX_SERVICES}/plan/${projectId}/${repoName}`,
    method: 'delete'
  })
}
