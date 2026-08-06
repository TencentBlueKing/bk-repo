import request from '@/utils/request'

const PREFIX = '/repository/api/client/upgrade'
export const BATCH_LIMIT = 50

export function listClientVersionConfigs(option) {
  return request({
    url: `${PREFIX}/list`,
    method: 'post',
    data: option
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

export async function batchUpsertInChunks(dataList) {
  for (let i = 0; i < dataList.length; i += BATCH_LIMIT) {
    await batchUpsertClientVersionConfig(dataList.slice(i, i + BATCH_LIMIT))
  }
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
