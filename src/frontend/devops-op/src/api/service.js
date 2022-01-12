import request from '@/utils/request'

const prefix = 'opdata/api/op/services'

export function services() {
  return request({
    url: `/${prefix}`,
    method: 'get'
  })
}

export function instances(serviceName) {
  return request({
    url: `${prefix}/${serviceName}/instances`,
    method: 'get'
  })
}

export function up(serviceName, instanceId) {
  return request({
    url: `${prefix}/${serviceName}/instances/${instanceId}/up`,
    method: 'post'
  })
}

export function down(serviceName, instanceId) {
  return request({
    url: `${prefix}/${serviceName}/instances/${instanceId}/down`,
    method: 'post'
  })
}

export const INSTANCE_STATUS_RUNNING = 'RUNNING'
export const INSTANCE_STATUS_DEREGISTER = 'DEREGISTER'
export const INSTANCE_STATUS_OFFLINE = 'OFFLINE'
