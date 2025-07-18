import request from '@/utils/request'

const PREFIX_SERVICES = '/opdata/api/project/grayscale'

export function list() {
  return request({
    url: `${PREFIX_SERVICES}/list`,
    method: 'get'
  })
}

export function update(config) {
  return request({
    url: `${PREFIX_SERVICES}/update`,
    method: 'put',
    data: config
  })
}

export function create(config) {
  return request({
    url: `${PREFIX_SERVICES}/create`,
    method: 'post',
    data: config
  })
}

export function remove(id) {
  return request({
    url: `${PREFIX_SERVICES}/delete/${id}`,
    method: 'delete'
  })
}

