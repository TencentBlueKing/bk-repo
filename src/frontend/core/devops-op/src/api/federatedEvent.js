import request from '@/utils/request'

const PREFIX_SERVICES = '/replication/api/replica/event'

export function events(params) {
  console.log(params)
  return request({
    url: `${PREFIX_SERVICES}/page`,
    method: 'get',
    params: params
  })
}

export function getEventById(id) {
  return request({
    url: `${PREFIX_SERVICES}/${id}`,
    method: 'get'
  })
}

export function retryEvent(params) {
  return request({
    url: `${PREFIX_SERVICES}/retry`,
    method: 'post',
    data: params
  })
}

export function deleteEvent(params) {
  return request({
    url: `${PREFIX_SERVICES}`,
    method: 'delete',
    data: params
  })
}
