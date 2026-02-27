import request from '@/utils/request'

const PREFIX_SERVICES = '/replication/api/federation/tracking'

export function trackRecords(params) {
  return request({
    url: `${PREFIX_SERVICES}/page`,
    method: 'get',
    params: params
  })
}

export function getTrackRecordById(id) {
  return request({
    url: `${PREFIX_SERVICES}/${id}`,
    method: 'get'
  })
}

export function retryTrackRecord(params) {
  return request({
    url: `${PREFIX_SERVICES}/retry`,
    method: 'post',
    data: params
  })
}

export function deleteTrackRecord(params) {
  return request({
    url: `${PREFIX_SERVICES}/delete`,
    method: 'delete',
    data: params
  })
}
