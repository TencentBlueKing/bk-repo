import request from '@/utils/request'

const PREFIX_SERVICES = '/job/api/cache'

export function queryFileCache() {
  return request({
    url: `${PREFIX_SERVICES}/list/`,
    method: 'get'
  })
}

export function deleteFileCache(id) {
  return request({
    url: `${PREFIX_SERVICES}/delete/${id}`,
    method: 'delete'
  })
}

export function createFileCache(data) {
  return request({
    url: `${PREFIX_SERVICES}/create/`,
    method: 'post',
    data: data
  })
}

export function updateFileCache(data) {
  return request({
    url: `${PREFIX_SERVICES}/update/`,
    method: 'post',
    data: data
  })
}
