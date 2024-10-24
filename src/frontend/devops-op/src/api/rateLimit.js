import request from '@/utils/request'

const PREFIX_SERVICES = '/opdata/api/rateLimit'

export function queryRateLimits() {
  return request({
    url: `${PREFIX_SERVICES}/list/`,
    method: 'get'
  })
}

export function deleteRateLimit(id) {
  return request({
    url: `${PREFIX_SERVICES}/delete/${id}`,
    method: 'delete'
  })
}

export function createRateLimit(data) {
  return request({
    url: `${PREFIX_SERVICES}/create/`,
    method: 'post',
    data: data
  })
}

export function updateRateLimit(data) {
  return request({
    url: `${PREFIX_SERVICES}/update/`,
    method: 'post',
    data: data
  })
}

export function getRateLimitConfig() {
  return request({
    url: `${PREFIX_SERVICES}/config/`,
    method: 'get'
  })
}
