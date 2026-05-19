import request from '@/utils/request'

const PREFIX = '/repository/api/client/upgrade'

export function listClientVersionConfigs(productId, pageNumber, pageSize) {
  const params = {
    pageNumber,
    pageSize
  }
  if (productId) {
    params.productId = productId
  }
  return request({
    url: `${PREFIX}/list`,
    method: 'get',
    params
  })
}

export function upsertClientVersionConfig(data) {
  return request({
    url: `${PREFIX}/upsert`,
    method: 'post',
    data
  })
}

export function batchUpsertClientVersionConfig(dataList) {
  return request({
    url: `${PREFIX}/upsert/batch`,
    method: 'post',
    data: dataList
  })
}

export function deleteClientVersionConfig(id) {
  return request({
    url: `${PREFIX}/${id}`,
    method: 'delete'
  })
}

export function batchDeleteClientVersionConfig(ids) {
  return request({
    url: `${PREFIX}/batch`,
    method: 'delete',
    data: ids
  })
}
