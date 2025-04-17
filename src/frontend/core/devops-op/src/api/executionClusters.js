import request from '@/utils/request'

const PREFIX_SERVICES = 'analyst/api/execution/clusters'

export function clusters() {
  return request({
    url: `${PREFIX_SERVICES}`,
    method: 'get'
  })
}

export function update(body) {
  return request({
    url: `${PREFIX_SERVICES}/${body.name}`,
    method: 'put',
    data: body
  })
}

export function create(body) {
  return request({
    url: `${PREFIX_SERVICES}`,
    method: 'post',
    data: body
  })
}

export function remove(name) {
  return request({
    url: `${PREFIX_SERVICES}/${name}`,
    method: 'delete'
  })
}
