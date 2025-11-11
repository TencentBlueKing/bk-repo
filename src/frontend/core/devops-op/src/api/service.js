import request from '@/utils/request'

const PREFIX_SERVICES = '/opdata/api/services'

export function services() {
  return request({
    url: `${PREFIX_SERVICES}`,
    method: 'get'
  })
}

export function instances(serviceName) {
  return request({
    url: `${PREFIX_SERVICES}/${serviceName}/instances`,
    method: 'get'
  })
}

export function bandwidths(serviceName) {
  return request({
    url: `${PREFIX_SERVICES}/${serviceName}/bandwidth`,
    method: 'get'
  })
}

export function up(serviceName, instanceId) {
  return request({
    url: `${PREFIX_SERVICES}/${serviceName}/instances/${instanceId}/up`,
    method: 'post'
  })
}

export function down(serviceName, instanceId) {
  return request({
    url: `${PREFIX_SERVICES}/${serviceName}/instances/${instanceId}/down`,
    method: 'post'
  })
}

export function checkConsulPattern() {
  return request({
    url: `${PREFIX_SERVICES}/isConsul`,
    method: 'get'
  })
}

export const INSTANCE_STATUS_RUNNING = 'RUNNING'
export const INSTANCE_STATUS_DEREGISTER = 'DEREGISTER'
export const INSTANCE_STATUS_OFFLINE = 'OFFLINE'
