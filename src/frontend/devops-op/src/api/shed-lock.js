import request from '@/utils/request'

const PREFIX_SERVICES = '/job/api/shedlock'

export function listShedlock() {
  return request({
    url: `${PREFIX_SERVICES}/list`,
    method: 'get'
  })
}

export function deleteShelock(id) {
  return request({
    url: `${PREFIX_SERVICES}/delete/${id}`,
    method: 'delete'
  })
}
