import request from '@/utils/request'

const PREFIX_SERVICES = '/replication/api/replica/failure'

export function failureRecords(params) {
  return request({
    url: `${PREFIX_SERVICES}/page`,
    method: 'get',
    params: params
  })
}

export function getFailureRecordById(id) {
  return request({
    url: `${PREFIX_SERVICES}/${id}`,
    method: 'get'
  })
}

export function retryFailureRecords(params) {
  return request({
    url: `${PREFIX_SERVICES}/retry`,
    method: 'post',
    data: params
  })
}

export function deleteFailureRecords(params) {
  return request({
    url: `${PREFIX_SERVICES}`,
    method: 'delete',
    data: params
  })
}
