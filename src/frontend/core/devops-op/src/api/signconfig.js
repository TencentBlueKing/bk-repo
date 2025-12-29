import request from '@/utils/request'

const baseUrl = '/opdata/api/sign/config'

export function getSignConfigList(projectId, pageNumber = 1, pageSize = 10) {
  return request({
    url: `${baseUrl}/list`,
    method: 'get',
    params: { projectId, pageNumber, pageSize }
  })
}

export function createSignConfig(data) {
  return request({
    url: `${baseUrl}/create`,
    method: 'post',
    data
  })
}

export function updateSignConfig(data) {
  return request({
    url: `${baseUrl}/update`,
    method: 'post',
    data
  })
}

export function deleteSignConfig(projectId) {
  return request({
    url: `${baseUrl}/delete/${projectId}`,
    method: 'delete'
  })
}